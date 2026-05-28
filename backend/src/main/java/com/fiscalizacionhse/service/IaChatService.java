package com.fiscalizacionhse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio de Chat con IA usando DeepSeek.
 * Implementa RAG (Retrieval-Augmented Generation) para responder
 * preguntas sobre los documentos de la empresa.
 */
@Service
@Slf4j
public class IaChatService {

    private static final Set<String> STOPWORDS_CONSULTA = Set.of(
            "quiero", "saber", "pregunta", "dime", "digame", "hola", "buenas", "este", "esta", "estos", "estas",
            "eso", "esa", "esos", "esas", "para", "por", "con", "sin", "como", "cual", "cuales", "donde",
            "cuando", "quien", "sobre", "desde", "hasta", "toda", "todo", "todos", "todas", "algo", "nada",
            "muy", "mas", "menos", "debe", "puede", "hacer", "tener", "ser", "son", "era", "fue", "han",
            "hay", "les", "del", "las", "los", "unos", "una", "informacion", "información", "dice");

    /** Pregunta informal/meta sobre capacidades propias (“¿qué más haces?”). No es contenido PDF. */
    private static final List<String> PATRONES_META_ASISTENTE = List.of(
            "que mas haces",
            "no mas haces",
            "que mas sabes",
            "que mas puede",
            "que mas pueden",
            "que puedes hacer",
            "para que sirves",
            "en que me puedes ayud",
            "en que me ayud",
            "en que puedes ayud",
            "como funcionas",
            "quien eres",
            "presentate",
            "que haces con mis document");

    private static final int SNIPPET_BEFORE = 320;
    private static final int SNIPPET_AFTER = 520;
    private static final int MAX_SNIPPETS = 14;
    private static final int MAX_LINEAS_ENCABEZADOS_TEMATICOS = 45;
    private static final int MAX_ESQUEMA_LINEAS_TOTAL = 250;
    private static final int MAX_MUESTRA_INICIO = 14000;
    private static final int MAX_MUESTRA_FIN = 12000;
    /** Tamaño máximo de una sección temática completa enviada al modelo. */
    private static final int MAX_SECCION_TEMATICA_CHARS = 90000;
    /** Tope de sección enviada al modelo en consultas informales («háblame de…»). */
    private static final int MAX_SECCION_RESUMEN_CHARS = 5500;

    private static final Pattern PATRON_ESTANDAR_DE = Pattern.compile(
            "^\\s*(EST[AÁ]NDAR\\s+DE\\s.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PATRON_ESTANDAR_DE_MULTILINEA = Pattern.compile(
            "(?m)^(EST[AÁ]NDAR\\s+DE\\s+.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PATRON_CAP_SEC = Pattern.compile(
            "^\\s*((?:CAP[ÍI]TULO|SECCI[OÓ]N)\\s+[\\dIVXLC]+\\.?\\s+.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** Controles críticos / factores de calidad típicos en libros HSE (CC1, CC2, Fc1, etc.). */
    private static final Pattern PATRON_CONTROL_CRITICO = Pattern.compile(
            "(?m)^\\s*((?:CC|Fc|FC)\\s*\\d+\\b[^\\n]{0,120}|Factor(?:es)?\\s+de\\s+calidad\\s*\\d+[^\\n]{0,80})$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PATRON_SIGUIENTE_ESTANDAR_O_CAP = Pattern.compile(
            "(?m)^(EST[AÁ]NDAR\\s+DE\\s+|CAP[ÍI]TULO\\s+|SECCI[ÓO]N\\s+\\d)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CatalogoControlesCriticosService catalogoCcService;
    private final CriteriosCalidadExtraccionService criteriosCalidadService;
    private final TranscripcionLibroConsultaService transcripcionLibroService;
    private final ConsultaLibroPrecisaService consultaLibroPrecisaService;
    private final String apiKey;
    private final String apiUrl;

    public IaChatService(
            CatalogoControlesCriticosService catalogoCcService,
            CriteriosCalidadExtraccionService criteriosCalidadService,
            TranscripcionLibroConsultaService transcripcionLibroService,
            ConsultaLibroPrecisaService consultaLibroPrecisaService,
            @Value("${app.ia.deepseek.api-key:}") String apiKey,
            @Value("${app.ia.deepseek.api-url:https://api.deepseek.com/v1/chat/completions}") String apiUrl) {
        this.catalogoCcService = catalogoCcService;
        this.criteriosCalidadService = criteriosCalidadService;
        this.transcripcionLibroService = transcripcionLibroService;
        this.consultaLibroPrecisaService = consultaLibroPrecisaService;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
    }

    /**
     * Genera respuesta usando RAG (contexto + pregunta)
     */
    public String generarRespuestaRag(String pregunta, String contexto, String documentosRef) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("⚠️ No hay API Key. Usando modo simulado para RAG.");
            return generarRespuestaSimulada(pregunta, contexto);
        }

        boolean sinChunksEnServidor = (contexto == null || contexto.isBlank())
                || esReferenciasDocumentosVacias(documentosRef);

        boolean catalogoEmpresaEnContexto = contexto != null && contexto.contains("CATÁLOGO DE PDF DISPONIBLE EN LA EMPRESA");
        boolean seccionesCompletasEnContexto = contexto != null && contexto.contains("SECCIONES TEMÁTICAS COMPLETAS");

        String sistema = construirSystemPrompt(documentosRef);

        boolean consultaPorCapacidades = esConsultaSobreCapacidadesDelAsistente(pregunta);
        boolean modoConciso = esConsultaResumenInformal(pregunta) && !preguntaPideListaCompleta(pregunta);
        StringBuilder sufijoInterpretacion = new StringBuilder();

        if (consultaPorCapacidades) {
            sufijoInterpretacion.append("""


                    ⚙️ Lectura especial: el usuario preguntó de manera **informal abierta sobre ti** (¿qué haces?, ¿qué más sabes hacer?, etc.).
                    Responde con **lista clara breve**, tono humano cercano para personal de obra, explicando **qué ofreces con FISCALIZA-AI** dentro de seguridad HSE de la empresa
                    (texto PDF cargados multi-documento, documento puntual íntegro cuando lo eligen, vistas visuales de portada página 1, etc.).
                    **No rechaces la consulta como “fuera de HSE”;** es válida porque consulta tus funciones.""")
                    ;

        } else if (seccionesCompletasEnContexto && !modoConciso) {
            sufijoInterpretacion.append("""


                    📑 SECCIONES_TEMÁTICAS_COMPLETAS: En el contexto hay bloques **íntegros** de estándares/apartados
                    extraídos del PDF completo (no solo fragmentos RAG sueltos).
                    - **Prioriza** esos bloques sobre los fragmentos numerados [F 1], [F 2], etc.
                    - Si el usuario pide factores de calidad, CC1, CC2… **enumera TODOS** los ítems que aparezcan
                      en la sección temática o bloque de controles críticos, **sin omitir ninguno del medio**.
                    - Cita el **título exacto del documento** de donde sale cada listado.
                    - Responde con listas numeradas o viñetas completas, no resúmenes parciales.""");

        } else if (modoConciso) {
            sufijoInterpretacion.append("""


                    🎯 MODO CONCISO: El usuario hizo una pregunta **general/informal** sobre un tema HSE.
                    - Responde **solo lo esencial** del tema preguntado (máximo **12 viñetas** o **400 palabras**).
                    - Estructura obligatoria con Markdown:
                      ## [Nombre del estándar o tema]
                      Breve intro en **negrita** (1–2 frases).
                      ### Alcance
                      ### Requisitos clave
                      (viñetas con términos importantes en **negrita**)
                    - **No copies** párrafos literales del PDF ni bloques HTML.
                    - **No incluyas** otros estándares ajenos al tema (ej. si preguntan altura, no hables de caliente/conducción).
                    - Si hay controles críticos (CC), menciona **solo códigos y título breve**, no el texto completo.
                    - **No listes** PDF, libros, fragmentos ni inventario de archivos cargados (eso lo muestra la UI aparte).
                    - **No repitas** nombres de documentos salvo **uno** al final: *Fuente: «título exacto»*.
                    - Si el bloque «SECCIÓN FOCAL DEL ESTÁNDAR» está en el contexto, **priorízalo** sobre fragmentos sueltos [F n].""");

        } else if (catalogoEmpresaEnContexto) {
            boolean contextoCombinaFrag = contexto != null && contexto.contains("Fragmentos recuperados relacionados");
            sufijoInterpretacion.append(contextoCombinaFrag
                    ? """

                    📋 INVENTARIO_Y_FRAGMENTOS: En el mensaje aparece catálogo de PDF **y** fragmentos recuperados.
                    - Lista el inventario si el usuario lo pide explícitamente.
                    - La parte **normativa** debe partir **solo** de los fragmentos; no hagas tabla negativa («sin fragmentos sobre…», «📄 archivo… no halló texto…») recorriendo **cada** PDF del catálogo salvo donde el contenido recuperado así lo indique."""
                    : """

                    📋 INVENTARIO_EN_CONTEXTO: El bloque «CATÁLOGO DE PDF DISPONIBLE EN LA EMPRESA» son **títulos reales** de archivos subidos.
                    - Si el usuario pregunta qué documentos, libros o PDF tiene, abre con una sección **«Documentos cargados en la plataforma»** y enumera **todos** los ítems con el **título exacto** (entre comillas tipográficas «») y el **ID** si ayuda.
                    - No confundas «tener el archivo» con «haber hallado texto coincidente con la pregunta»: si solo hay catálogo y no hay fragmentos útiles, dilo con educación (posible falta de indexación, PDF escaneado, o conveniencia de reformular).
                    - No inventes títulos que no aparezcan en ese catálogo.""");

        } else if (sinChunksEnServidor) {
            sufijoInterpretacion.append("""


                    ⚠️ CONTEXT_STATUS: Esta petición llegó sin fragmentos de PDF recuperados.
                    Para preguntas **abiertas** sobre seguridad HSE de los archivos cargados por la empresa, responde con **cordialidad profesional**, enfatizando límites técnicos
                    posibles (PDF escaneados sin OCR, falta indexar en FISCALIZA-AI, oportuno reformular usando **sinónimos** o el nombre oficial del programa en los archivos si lo conoces).
                    **No** abras con un párrafo abrupto tipo “fuera del ámbito”. Si la intención es legítimo HSE pero no hay texto, cerrá con consejos prácticos de reformulación y verificación.

                    📌 MUY IMPORTANTE cuando no llega texto de ningún PDF en el contexto de esta llamada:
                    - **No** hagas inventario archivo por archivo (evita líneas repetidas tipo «📄 …» donde afirmás que ese PDF no tiene contenido sobre el tema).
                    - **No cites nombres concretos de documentos cargados**, porque ese listado **no figura aquí**: inventarlos equivaldría a alucinar.
                    - **No especules** con frases tipo «el título sugiere», «podría contener», «verifique índice de…», «ESV seguramente incluye».
                    - Responde de forma **breve** en bloques muy cortos (resumen honesto en 3–8 líneas y 3–6 viñetas de acciones: indexar, OCR, reformular sinónimos, elegir documento puntual desde el selector, etc.).
                    """);

        }

        double temperatura = consultaPorCapacidades ? 0.45 : modoConciso ? 0.18 : sinChunksEnServidor ? 0.35 : 0.22;
        int maxTokens = modoConciso ? 1400
                : (seccionesCompletasEnContexto || preguntaPideListaCompleta(pregunta) ? 8192 : 4096);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "deepseek-chat");
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", sistema + sufijoInterpretacion),
                    Map.of("role", "user", "content",
                            "Consulta del usuario (respóndela con la interpretación especial si aplica; usa SOLO texto de contexto si hay contexto útil):\n"
                                    + pregunta
                                    + "\n\n═══════════════════════════════════════════════════════\n"
                                    + "CONTEXTO (fragmentos PDF o vacío; ignora información irrelevante al tema):\n"
                                    + (contexto == null || contexto.isBlank()
                                    ? "(No se recuperaron fragmentos en esta ronda desde la biblioteca)."
                                    : contexto))
            ));
            requestBody.put("temperature", temperatura);
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("stream", false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    JsonNode.class
            );

            String respuesta = response.getBody()
                    .get("choices").get(0)
                    .get("message").get("content")
                    .asText();

            log.info("✅ Respuesta RAG generada ({} caracteres, maxTokens={})", respuesta.length(), maxTokens);
            return sanitizarHtmlRespuesta(respuesta);

        } catch (Exception e) {
            log.error("❌ Error en RAG: {}", e.getMessage());
            return generarRespuestaSimulada(pregunta, contexto);
        }
    }

    /**
     * Crea resumen automático de un documento
     */
    public String generarResumen(String titulo, String texto) {
        if (apiKey == null || apiKey.isBlank()) {
            return generarResumenSimulado(titulo, texto);
        }

        String textoLimitado = texto.length() > 6000 ? texto.substring(0, 6000) + "..." : texto;

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "deepseek-chat");
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content",
                            "Eres un experto en normativas HSE. Genera un resumen ejecutivo " +
                            "del siguiente documento. Incluye: propósito, puntos principales, " +
                            "requisitos clave, y conclusiones. Máximo 3 párrafos."),
                    Map.of("role", "user", "content",
                            "Documento: " + titulo + "\n\n" + textoLimitado)
            ));
            requestBody.put("temperature", 0.3);
            requestBody.put("max_tokens", 1024);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    JsonNode.class
            );

            return response.getBody()
                    .get("choices").get(0)
                    .get("message").get("content")
                    .asText();

        } catch (Exception e) {
            log.error("❌ Error generando resumen: {}", e.getMessage());
            return generarResumenSimulado(titulo, texto);
        }
    }

    /** Indica si hay API DeepSeek configurada (respuestas completas vs. modo demostración). */
    public boolean deepseekDisponible() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Contexto temático extraído de un PDF para consultas RAG o sobre documento. */
    public record ContextoTematicoDocumento(String seccionCompleta, String bloqueControlesCriticos) {}

    /** Si la pregunta pide listado exhaustivo (CC1–CC10, todos los factores, etc.). */
    public boolean preguntaPideListaCompleta(String pregunta) {
        return esPreguntaQuePideListaCompleta(pregunta);
    }

    /** Pregunta abierta sin pedir transcripción literal ni listado completo. */
    public boolean esConsultaResumenInformal(String pregunta) {
        return EstandarConsultaHelper.esConsultaResumenInformal(pregunta);
    }

    /** Preguntas sobre estándares, factores de calidad, CC, altura, etc. */
    public boolean preguntaRequiereExtraccionTematicaCompleta(String pregunta) {
        return esPreguntaQuePideListaCompleta(pregunta) || esPreguntaTematicaEstructurada(pregunta);
    }

    /** Analiza el texto completo de un PDF y extrae sección + controles críticos según la pregunta. */
    public ContextoTematicoDocumento analizarTextoParaConsulta(String textoDocumento, String pregunta) {
        if (textoDocumento == null || textoDocumento.isBlank()) {
            return new ContextoTematicoDocumento("", "");
        }
        LinkedHashSet<String> terminos = terminosConsultaParaBusquedaLocal(pregunta);
        boolean resumenInformal = esConsultaResumenInformal(pregunta);
        String seccion = extraerSeccionTematicaCompleta(textoDocumento, terminos, pregunta, resumenInformal);
        String cc = extraerBloqueControlesCriticos(textoDocumento, terminos, pregunta, resumenInformal);
        return new ContextoTematicoDocumento(
                seccion != null ? seccion : "",
                cc != null ? cc : "");
    }

    private static boolean esPreguntaTematicaEstructurada(String pregunta) {
        if (pregunta == null || pregunta.isBlank()) {
            return false;
        }
        String n = normalizarSinAcento(pregunta);
        return n.contains("estandar") || n.contains("standard")
                || n.contains("factor") || n.contains("calidad")
                || n.contains("altura") || n.contains("alturas")
                || n.contains(" cc") || n.contains("cc1") || n.contains("cc2")
                || n.contains("critico") || n.contains("control")
                || n.contains("compromiso") || n.contains("epp");
    }

    /**
     * Análisis en lenguaje natural de una búsqueda: solo usa los fragmentos y referencias internas (sin fuentes externas).
     */
    public String generarBusquedaAsistida(String consulta, String contextoFragmentos, String documentosRefJson) {
        if (apiKey == null || apiKey.isBlank()) {
            return generarBusquedaAsistidaSimulada(consulta, contextoFragmentos);
        }

        String sistema = """
                Eres FISCALIZA-AI. El usuario busca información en documentos PDF que ya cargó en el sistema.

                REGLAS OBLIGATORIAS:
                - Usa ÚNICAMENTE el texto de los fragmentos numerados y los metadatos JSON de documentos que se te proporcionan.
                - Si aparece el bloque «SECCIONES TEMÁTICAS COMPLETAS», **priorízalo** sobre fragmentos sueltos.
                - Para listados (CC1, CC2, factores de calidad): enumera **todos** los ítems sin omitir ninguno del medio.
                - No inventes requisitos legales ni normativa que no aparezca literalmente o claramente en esos fragmentos.
                - No uses conocimiento general de internet ni fuentes externas: el entorno es cerrado.
                - Si algo no está en los fragmentos, dilo explícitamente.
                - Responde en español, en Markdown, con esta estructura:
                  ## Hallazgos relacionados con la consulta
                  ## Resumen por documento (uno o dos frases por PDF, según los fragmentos)
                  ## Términos clave (lista: término — explicación breve en lenguaje sencillo — en qué documento aparece)
                - Si la consulta sugiere «certificado», «cargo», «declaro», etc., relaciona lo que digan los fragmentos sin suposiciones.
                """;

        boolean seccionesCompletas = contextoFragmentos != null
                && contextoFragmentos.contains("SECCIONES TEMÁTICAS COMPLETAS");
        int maxTokens = seccionesCompletas || preguntaPideListaCompleta(consulta) ? 8192 : 3072;

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "deepseek-chat");
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", sistema),
                    Map.of("role", "user", "content",
                            "Consulta: " + consulta + "\n\n"
                                    + "Documentos (JSON): " + documentosRefJson + "\n\n"
                                    + "--- Fragmentos ---\n" + contextoFragmentos)
            ));
            requestBody.put("temperature", 0.25);
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("stream", false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    JsonNode.class
            );

            return response.getBody()
                    .get("choices").get(0)
                    .get("message").get("content")
                    .asText();
        } catch (Exception e) {
            log.error("❌ Error búsqueda asistida: {}", e.getMessage());
            return generarBusquedaAsistidaSimulada(consulta, contextoFragmentos);
        }
    }

    private String generarBusquedaAsistidaSimulada(String consulta, String contextoFragmentos) {
        String ctx = contextoFragmentos != null ? contextoFragmentos : "";
        if (ctx.length() > 6000) {
            ctx = ctx.substring(0, 6000)
                    + "\n\n … *(vista truncada aquí solo por tamaño en pantalla; no indica problema con tus documentos).*";
        }
        return String.format("""
                ### 🔎 Resultado de tu búsqueda: «%s»

                Por ahora ves **solo el texto recuperado** de tus PDF indexados —como cuando abres varios expedientes y resaltamos las páginas donde aparece algo parecido a lo que buscas.

                **Con el asistente activado** (clave DeepSeek en el servidor), la misma consulta vendría acompañada de un texto que **ordenara los hallazgos**, explicara términos en lenguaje sencillo y dijera **de qué documento** sale cada parte.

                **Ejemplo típico** (solo ilustrativo): si preguntaras *«qué dicen tus normas sobre trabajo en altura»*, responderíamos algo como una mini-ficha por documento («Procedimiento X — EPP obligatorios», «Manual Y — altura desde 1,8 m…»).

                ---
                #### Fragmentos de tus documentos (datos brutos)

                %s
                """, consulta, ctx.isBlank() ? "*Todavía no hay fragmentos recuperados para esta búsqueda; puede que convenga indexar de nuevo los PDF desde FISCALIZA-AI.*" : ctx);
    }

    private String construirSystemPrompt(String documentosRef) {
        return """
            Eres **FISCALIZA-AI**, asistente HSE de esta empresa.

            CÍRCULO CERRADO — reglas obligatorias:
            1. Responde **únicamente** con información que aparezca en el CONTEXTO (fragmentos de PDF cargados en la plataforma).
            2. **Prohibido** usar conocimiento externo, normativa genérica de internet o suposiciones si no están en el contexto.
            3. Si el contexto no contiene la respuesta, dilo con claridad: «No encontré esa información en los documentos cargados de su empresa».
            4. Responde en **español**, con Markdown **muy legible** (ver formato visual abajo).
            5. Al usar un fragmento, indica el **título exacto del PDF** de origen.
            6. No generes código ni scripts salvo que el usuario lo pida explícitamente y haya base documental.
            7. Por defecto sé **breve y directo**: resume en viñetas; no vuelques texto literal del PDF salvo que pidan listado completo o transcripción.
            8. Si aparece **CONTENIDO EDITOR ESTRUCTURADO**, es la fuente **prioritaria** (texto guardado en el Editor de Contenido de la plataforma). No omitas requisitos, CC ni criterios que figuren ahí.

            FORMATO VISUAL (obligatorio — que invite a seguir consultando):
            - Abre con **1–2 frases** que respondan directo la pregunta (tono cercano y profesional).
            - Usa `##` para el título principal del tema y `###` para subtemas (Alcance, Requisitos clave, Controles críticos).
            - Pon en **negrita** nombres de estándares, roles (Autoridad de Área, Vigía), códigos **CC1**, **CC2**, y palabras clave de riesgo.
            - Listas **numeradas** para pasos o requisitos obligatorios; **viñetas** para detalles.
            - Párrafos cortos (máx. 3–4 líneas). Separa bloques con línea en blanco.
            - Cierra con una línea: *Fuente: «título exacto del documento»* (solo un documento salvo comparativo).
            - No uses bloques enormes sin títulos; no repitas otros estándares ajenos a la pregunta.

            Documentos referenciados en esta consulta (JSON interno):
            """ + documentosRef;
    }

    private String generarRespuestaSimulada(String pregunta, String contexto) {
        if (contexto != null && !contexto.isBlank() && contexto.length() > 80) {
            return String.format("""
                ## Respuesta parcial (DeepSeek no disponible)

                **Su pregunta:** «%s»

                Se recuperaron fragmentos de sus documentos (%d caracteres), pero el motor **DeepSeek** no está conectado en el servidor.

                **Qué hacer:** configure `DEEPSEEK_API_KEY` y reinicie el backend. Entonces podrá obtener respuestas redactadas solo con sus PDF.

                *Los fragmentos relacionados aparecen abajo en el panel de referencias.*
                """, pregunta, contexto.length());
        }
        return String.format("""
            ## FISCALIZA-AI — motor no conectado

            **Su pregunta:** «%s»

            FISCALIZA-AI responde **solo** con documentos PDF cargados en su empresa. Para redactar la respuesta hace falta **DeepSeek** (`DEEPSEEK_API_KEY` en el servidor).

            Si ya subió documentos, indexe desde aquí o haga una pregunta (se indexa automáticamente en la primera consulta).
            """, pregunta);
    }

    private String generarResumenSimulado(String titulo, String texto) {
        return String.format("""
            ## 📋 Tu documento «%s» — resumen disponible después de activar la IA
            
            **Tamaño útil ya extraído:** %d caracteres de texto (listos para que un modelo los lea como un libro digital).
            
            ### Qué vas a obtener al conectar DeepSeek
            Un **resumen ejecutivo de 3–5 párrafos**: de qué trata el archivo, para quién sirve en obra/planta y qué exigencias o estándares aparecen más a menudo —**sin inventar nada fuera del PDF**.
            
            ### Ejemplo de tono esperado *(ilustrativo)*
            *«Este folleto agrupa estándares de ENAP/anexos: trabajo en caliente en el cap.…, aislamiento de energías más adelante, y anexos con checklists revisables antes de iniciar cada turno…»*
            
            **Siguiente paso:** configurar **DEEPSEEK_API_KEY** en el proceso que ejecuta este backend para generar ese resumen de verdad desde **%s**.
            """, titulo, texto.length(), titulo);
    }

    /**
     * Respuesta cuando el usuario pregunta sobre un documento concreto (texto completo en contexto).
     * Prioriza cláusulas tipo "Certifico", "Declaro", "compromiso", listas con guiones.
     */
    /**
     * «Háblame del estándar de…» sobre un solo PDF: solo la sección focal, respuesta redactada y corta.
     */
    public String generarRespuestaConcisaEstandarDocumento(
            String pregunta, String tituloDocumento, String textoFocal) {

        Optional<String> estandarOpt = EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta);
        String nombreEstandar = estandarOpt.orElse("estándar").toUpperCase(Locale.ROOT);
        if (!nombreEstandar.startsWith("ESTÁNDAR") && !nombreEstandar.startsWith("ESTANDAR")) {
            nombreEstandar = "ESTÁNDAR DE " + nombreEstandar;
        }

        if (textoFocal == null || textoFocal.replaceAll("\\s+", "").length() < 80) {
            return "## " + nombreEstandar + "\n\n"
                    + "No localicé este apartado en **«" + tituloDocumento + "»**. "
                    + "Revise que en el **Editor de Contenido** exista un encabezado "
                    + "*(por ejemplo: «3. ESTÁNDAR DE TRABAJO EN CALIENTE»)* con el texto debajo.\n";
        }

        if (apiKey == null || apiKey.isBlank()) {
            return formatearResumenLocalEstandar(pregunta, tituloDocumento, nombreEstandar, textoFocal);
        }

        String sistema = """
                Eres FISCALIZA-AI, asistente HSE. El usuario preguntó por UN estándar en UN documento.
                Redacta en español con Markdown claro (## título, ### subtítulos, **negritas** en roles y CC).
                Máximo 400 palabras. No copies párrafos literales largos del PDF.
                Estructura: 1–2 frases intro → ### Objetivo → ### Alcance → ### Requisitos clave (5–8 viñetas).
                Solo este estándar; no menciones otros capítulos.
                Cierra con: *Fuente: «título del documento»*
                """;

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "deepseek-chat");
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", sistema),
                    Map.of("role", "user", "content",
                            "Documento: " + tituloDocumento + "\n\n"
                                    + "--- SECCIÓN DEL ESTÁNDAR (única fuente) ---\n"
                                    + truncarParaPrompt(textoFocal, 6500)
                                    + "\n--- FIN ---\n\n"
                                    + "Pregunta: " + pregunta)
            ));
            requestBody.put("temperature", 0.25);
            requestBody.put("max_tokens", 2048);
            requestBody.put("stream", false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST, new HttpEntity<>(requestBody, headers), JsonNode.class);

            String raw = response.getBody()
                    .get("choices").get(0).get("message").get("content").asText();
            return sanitizarHtmlRespuesta(raw);
        } catch (Exception e) {
            log.error("Error respuesta concisa estándar: {}", e.getMessage());
            return formatearResumenLocalEstandar(pregunta, tituloDocumento, nombreEstandar, textoFocal);
        }
    }

    /**
     * «Dime un resumen del compromiso…»: 1–2 párrafos cortos, no el libro ni transcripción literal.
     */
    public String generarResumenBreveApartado(
            String pregunta, String tituloDocumento, String tituloApartado, String cuerpoApartado) {

        if (cuerpoApartado == null || cuerpoApartado.replaceAll("\\s+", "").length() < 80) {
            return "No localicé el apartado **«" + tituloApartado + "»** en **«" + tituloDocumento + "»**. "
                    + "Revise el **Libro estructurado** (editor guardado).\n";
        }

        if (apiKey == null || apiKey.isBlank()) {
            return formatearResumenLocalApartado(pregunta, tituloDocumento, tituloApartado, cuerpoApartado);
        }

        String sistema = """
                Eres FISCALIZA-AI, asistente HSE. El usuario pidió un RESUMEN BREVE de UN apartado de un libro.
                Redacta en español con Markdown (## título, viñetas si aplica).
                Máximo 180 palabras. No copies párrafos largos literales.
                Estructura: 1 párrafo introductorio + 3–5 viñetas con ideas clave.
                Solo este apartado; no menciones Introducción, Alcance ni estándares posteriores.
                Cierra con: *Fuente: «título del documento»*
                """;

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "deepseek-chat");
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", sistema),
                    Map.of("role", "user", "content",
                            "Documento: " + tituloDocumento + "\nApartado: " + tituloApartado + "\n\n"
                                    + "--- TEXTO DEL APARTADO ---\n"
                                    + truncarParaPrompt(cuerpoApartado, 5000)
                                    + "\n--- FIN ---\n\n"
                                    + "Pregunta: " + pregunta)
            ));
            requestBody.put("temperature", 0.25);
            requestBody.put("max_tokens", 900);
            requestBody.put("stream", false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST, new HttpEntity<>(requestBody, headers), JsonNode.class);

            String raw = response.getBody()
                    .get("choices").get(0).get("message").get("content").asText();
            return sanitizarHtmlRespuesta(raw);
        } catch (Exception e) {
            log.error("Error resumen breve apartado: {}", e.getMessage());
            return formatearResumenLocalApartado(pregunta, tituloDocumento, tituloApartado, cuerpoApartado);
        }
    }

    private String formatearResumenLocalApartado(
            String pregunta, String tituloDoc, String tituloApartado, String cuerpo) {

        String limpio = cuerpo.replaceAll("\\s+", " ").trim();
        int fin = Math.min(limpio.length(), 520);
        String intro = limpio.substring(0, fin);
        if (limpio.length() > fin) {
            intro += "…";
        }
        return """
                ## Resumen: %s
                
                **Su consulta:** «%s»
                
                %s
                
                - Apartado tomado del libro guardado en la plataforma.
                - Para el **texto completo** del apartado, pregunte: *«Háblame del %s»*.
                
                *Fuente: «%s»*
                """.formatted(tituloApartado, pregunta, intro, tituloApartado, tituloDoc);
    }

    private static String truncarParaPrompt(String texto, int max) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= max ? texto : texto.substring(0, max) + "\n…";
    }

    private String formatearResumenLocalEstandar(
            String pregunta, String titulo, String nombreEstandar, String textoFocal) {

        String cuerpo = textoFocal;
        int idx = cuerpo.indexOf("### ");
        if (idx >= 0) {
            cuerpo = cuerpo.substring(idx);
        }
        if (cuerpo.length() > 3200) {
            cuerpo = cuerpo.substring(0, 3200).trim() + "\n\n… *(Activa DeepSeek para un resumen redactado.)*";
        }

        return """
                ## %s
                
                **Su consulta:** «%s»
                
                Resumen extraído de la sección correspondiente en el documento (modo sin IA conectada):
                
                %s
                
                ---
                *Fuente: «%s»*
                """.formatted(nombreEstandar, pregunta, cuerpo, titulo).trim();
    }

    public String generarRespuestaSobreDocumento(String pregunta, String tituloDocumento, String textoDocumento) {
        if (textoDocumento == null || textoDocumento.isBlank()) {
            return "No hay texto disponible de este documento para analizar.";
        }

        if (esConsultaResumenInformal(pregunta)
                && EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta).isPresent()) {
            return generarRespuestaConcisaEstandarDocumento(pregunta, tituloDocumento, textoDocumento);
        }

        Optional<String> consultaPrecisa = consultaLibroPrecisaService.responderOMensaje(
                tituloDocumento, textoDocumento, pregunta);
        if (consultaPrecisa.isPresent()) {
            return consultaPrecisa.get();
        }

        if (apiKey == null || apiKey.isBlank()) {
            if (catalogoCcService.esPreguntaSobreInventarioControlesCriticos(pregunta)) {
                CatalogoControlesCriticosService.CatalogoCc cat =
                        catalogoCcService.extraerCatalogo(tituloDocumento, textoDocumento);
                return catalogoCcService.formatearRespuestaInventario(cat, pregunta);
            }
            if (criteriosCalidadService.esPreguntaSobreCriteriosCalidad(pregunta)) {
                Optional<String> especifico = criteriosCalidadService.responderCriteriosDeEstandar(
                        tituloDocumento, textoDocumento, pregunta);
                if (especifico.isPresent()) {
                    return especifico.get();
                }
                CriteriosCalidadExtraccionService.CatalogoCriterios cat =
                        criteriosCalidadService.extraerCatalogo(tituloDocumento, textoDocumento);
                return criteriosCalidadService.formatearRespuesta(
                        criteriosCalidadService.filtrarPorConsulta(cat, pregunta), pregunta);
            }
            Optional<String> trans = transcripcionLibroService.responderConTranscripcion(
                    tituloDocumento, textoDocumento, pregunta);
            if (trans.isPresent()) {
                return trans.get();
            }
            return generarRespuestaSimuladaSobreDocumento(pregunta, tituloDocumento, textoDocumento);
        }

        if (criteriosCalidadService.esPreguntaSobreCriteriosCalidad(pregunta)) {
            Optional<String> especifico = criteriosCalidadService.responderCriteriosDeEstandar(
                    tituloDocumento, textoDocumento, pregunta);
            if (especifico.isPresent()) {
                return especifico.get();
            }
            CriteriosCalidadExtraccionService.CatalogoCriterios cat =
                    criteriosCalidadService.extraerCatalogo(tituloDocumento, textoDocumento);
            CriteriosCalidadExtraccionService.CatalogoCriterios filtrado =
                    criteriosCalidadService.filtrarPorConsulta(cat, pregunta);
            if (filtrado.total() > 0) {
                return criteriosCalidadService.formatearRespuesta(filtrado, pregunta);
            }
        }

        if (!esConsultaResumenInformal(pregunta)) {
            Optional<String> transcripcion = transcripcionLibroService.responderConTranscripcion(
                    tituloDocumento, textoDocumento, pregunta);
            if (transcripcion.isPresent()) {
                return transcripcion.get();
            }
        }

        String bloqueCatalogoCc = "";
        if (catalogoCcService.esPreguntaSobreInventarioControlesCriticos(pregunta)
                || esPreguntaQuePideListaCompleta(pregunta)) {
            CatalogoControlesCriticosService.CatalogoCc cat =
                    catalogoCcService.extraerCatalogo(tituloDocumento, textoDocumento);
            if (cat.total() > 0) {
                bloqueCatalogoCc = "\n--- CATÁLOGO COMPLETO CC (escaneo automático de todo el PDF) ---\n\n"
                        + catalogoCcService.formatearRespuestaInventario(cat, pregunta)
                        + "\n--- FIN CATÁLOGO CC ---\n";
            }
        }

        String bloqueCriteriosCalidad = "";
        CriteriosCalidadExtraccionService.CatalogoCriterios catCriterios =
                criteriosCalidadService.extraerCatalogo(tituloDocumento, textoDocumento);
        if (catCriterios.total() > 0 && (criteriosCalidadService.esPreguntaSobreCriteriosCalidad(pregunta)
                || esPreguntaQuePideListaCompleta(pregunta))) {
            bloqueCriteriosCalidad = "\n--- CRITERIOS DE CALIDAD COMPLETOS (cada ítem a., b., c., d.…) ---\n\n"
                    + criteriosCalidadService.formatearRespuesta(catCriterios, pregunta)
                    + "\n--- FIN CRITERIOS DE CALIDAD ---\n";
        }

        String esquema = extraerEsquemaHeuristico(textoDocumento);
        String muestras = construirCuerpoMuestraParaPrompt(textoDocumento);
        LinkedHashSet<String> terminosPregunta = terminosConsultaParaBusquedaLocal(pregunta);
        List<String> pasajesTematicos = extraerSnippetsSegunConsulta(textoDocumento, terminosPregunta);
        String bloquePasajesTematicos = construirAnexoPasajesTematicos(pasajesTematicos);
        String seccionTematicaCompleta = extraerSeccionTematicaCompleta(textoDocumento, terminosPregunta, pregunta);
        String bloqueControlesCriticos = extraerBloqueControlesCriticos(textoDocumento, terminosPregunta, pregunta);
        boolean pideListaCompleta = esPreguntaQuePideListaCompleta(pregunta);

        String sistema = """
            Eres FISCALIZA-AI, asistente experto en normativas HSE y documentos de fiscalización.
            El usuario pregunta sobre UN SOLO documento.

            RECIBIRÁS (según corresponda):
            1) **ÍNDICE / ESTRUCTURA** escaneando todo el PDF.
            2) **SECCIÓN TEMÁTICA COMPLETA** del estándar o apartado que coincide con la pregunta (texto íntegro de ese bloque, no recortes).
            3) **CONTROLES CRÍTICOS / FACTORES DE CALIDAD** detectados (CC1, CC2, Fc1, etc.) con su texto asociado.
            4) Muestras de inicio/final y pasajes temáticos como respaldo.

            REGLAS:
            - Si la pregunta menciona un **tema concreto** (ej. trabajo en altura, factores de calidad), responde **primero y principalmente** con la SECCIÓN TEMÁTICA COMPLETA y los CONTROLES CRÍTICOS.
            - Cuando el usuario pide **todos los factores**, **listado completo**, **CC1 al CC10** o similar: enumera **CADA ítem por separado** con su numeración original (CC1, CC2, … CC10, CC11, etc.) y su descripción. **No resumas ni omitas ítems del medio.**
            - Si hay 10, 15 o más controles/factores en el texto recibido, inclúyelos **todos** en la respuesta, en orden.
            - Responde en español, con tono profesional, usando Markdown con listas numeradas o viñetas.
            - Básate solo en el material proporcionado. No uses conocimiento externo.
            - El modelo no recibe fotos del PDF (solo texto).
            - Si preguntan «¿qué contiene el documento?» o «resume»: usa la estructura + muestras.
            - Si falta información en lo recibido, dilo sin inventar.
            """;

        String bloqueEsquema = esquema.isBlank()
                ? "(No se detectaron encabezados tipo «ESTÁNDAR DE …» ni similares al escanear el PDF. Use las muestras de texto.)\n"
                : esquema + "\n";

        String bloqueSeccion = seccionTematicaCompleta.isBlank()
                ? ""
                : "\n--- SECCIÓN TEMÁTICA COMPLETA (texto íntegro del apartado relacionado con la pregunta) ---\n"
                  + seccionTematicaCompleta + "\n--- FIN SECCIÓN TEMÁTICA ---\n";

        String bloqueCc = bloqueControlesCriticos.isBlank()
                ? ""
                : "\n--- CONTROLES CRÍTICOS / FACTORES DE CALIDAD (extracción completa del documento) ---\n"
                  + bloqueControlesCriticos + "\n--- FIN CONTROLES CRÍTICOS ---\n";

        String instruccionLista = pideListaCompleta
                ? "\n\n⚠️ El usuario pidió un **listado completo**. Enumera **todos** los factores/controles que aparezcan en la sección temática o bloque CC, sin omitir ninguno.\n"
                : "";

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "deepseek-chat");
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", sistema),
                    Map.of("role", "user", "content",
                            "Título del documento: " + tituloDocumento + "\n\n"
                                    + "--- ESTRUCTURA E ÍNDICE (todo el archivo, detección automática) ---\n"
                                    + bloqueEsquema + "\n"
                                    + bloqueCatalogoCc
                                    + bloqueCriteriosCalidad
                                    + bloqueSeccion
                                    + bloqueCc
                                    + "--- MUESTRAS DE TEXTO (inicio ± final; documentos grandes) ---\n"
                                    + muestras + "\n--- FIN MUESTRAS ---"
                                    + bloquePasajesTematicos + "\n"
                                    + "Pregunta del usuario: " + pregunta
                                    + instruccionLista
                                    + "\n(Prioriza SECCIÓN TEMÁTICA COMPLETA y CONTROLES CRÍTICOS sobre muestras parciales.)")
            ));
            requestBody.put("temperature", 0.2);
            requestBody.put("max_tokens", 8192);
            requestBody.put("stream", false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    JsonNode.class
            );

            return response.getBody()
                    .get("choices").get(0)
                    .get("message").get("content")
                    .asText();
        } catch (Exception e) {
            log.error("❌ Error respuesta sobre documento: {}", e.getMessage());
            return generarRespuestaSimuladaSobreDocumento(pregunta, tituloDocumento, textoDocumento);
        }
    }

    /**
     * Modo sin DeepSeek: responde enfocado en lo que preguntaste (extracción local desde el texto del PDF).
     */
    private String generarRespuestaSimuladaSobreDocumento(String pregunta, String titulo, String texto) {
        String esquema = extraerEsquemaHeuristico(texto);
        LinkedHashSet<String> terminos = terminosConsultaParaBusquedaLocal(pregunta);
        boolean hayTemaParaFiltrar = !terminos.isEmpty();

        String seccionCompleta = extraerSeccionTematicaCompleta(texto, terminos, pregunta);
        String bloqueCc = extraerBloqueControlesCriticos(texto, terminos, pregunta);

        String encabezadosTema = hayTemaParaFiltrar
                ? filtrarEsquemaPorTerminos(esquema, terminos)
                : "";
        List<String> snippets = hayTemaParaFiltrar && seccionCompleta.isBlank() && bloqueCc.isBlank()
                ? extraerSnippetsSegunConsulta(texto, terminos)
                : Collections.emptyList();

        String bloqueClausulas = construirBloqueClausulasOpcional(pregunta, texto);

        StringBuilder sb = new StringBuilder();
        sb.append("## 📘 ").append(titulo).append("\n\n");
        sb.append("**Tu pregunta:** «").append(pregunta).append("»\n\n");

        boolean huboAlgoDelTema = !encabezadosTema.isBlank() || !snippets.isEmpty()
                || !seccionCompleta.isBlank() || !bloqueCc.isBlank();

        if (huboAlgoDelTema) {
            sb.append("### Relacionado con tu consulta dentro de este PDF\n\n");
            if (!seccionCompleta.isBlank()) {
                sb.append("**Sección temática completa** (apartado íntegro detectado en el documento):\n\n");
                String sec = seccionCompleta.length() > 12000
                        ? seccionCompleta.substring(0, 12000) + "\n\n… *(sección truncada en pantalla; con DeepSeek activo se envía completa al modelo)*"
                        : seccionCompleta;
                sb.append(sec).append("\n\n");
            }
            if (!bloqueCc.isBlank()) {
                sb.append("**Controles críticos / factores de calidad** detectados:\n\n");
                sb.append(bloqueCc).append("\n\n");
            }
            if (!encabezadosTema.isBlank()) {
                sb.append("**Encabezados y estándares** que coinciden con lo que preguntaste:\n");
                sb.append(encabezadosTema).append('\n');
            }
            if (!snippets.isEmpty()) {
                sb.append("**Extractos del texto** (automático, alrededor de las coincidencias):\n\n");
                for (int i = 0; i < snippets.size(); i++) {
                    sb.append(String.format(Locale.ROOT, "%d. %s\n\n", i + 1, snippets.get(i)));
                }
            }
            sb.append("""
                    _Los extractos intentan responder solo a tu tema; no están redactados por una IA._

                    ---
                    """);

        } else if (hayTemaParaFiltrar) {
            sb.append("""
                    ### Tu consulta y el contenido disponible

                    No encontramos en el texto **coincidencias claras** con los términos de tu pregunta (¿otra forma de decir lo mismo o un sinónimo?).

                    Más abajo puedes revisar una **lista general** de encabezados del PDF si el modo sin IA sigue activo.

                    ---
                    """);
            if (!esquema.isBlank()) {
                sb.append("#### Lista general de encabezados detectados (todo el archivo)\n");
                sb.append(esquema).append('\n');
            }

        } else {
            sb.append("""
                    **Sugerencia:** formula la pregunta con palabras concretas (ej. «trabajo en altura», «EPP», «bloqueo de energía»).

                    ---
                    #### Encabezados detectados en el PDF
                    """);

            sb.append(esquema.isBlank()
                    ? "_No hay encabezados tipo «ESTÁNDAR DE…» evidentes._\n"
                    : esquema + "\n");

        }

        if (!hayTemaParaFiltrar || !huboAlgoDelTema) {
            // Sin término útil: breve aclaración
            sb.append("""
                    ---
                    _Modo rápido **sin modelo de lenguaje** conectado: solo listas y búsqueda de texto._

                    """);

        }

        sb.append("""
                #### ¿Quieres párrafos ordenados tipo informe?

                Configure **`DEEPSEEK_API_KEY`** al arrancar Spring Boot *(o use `application-local.yml` en su PC)* para que una IA lean solo este archivo y redacten la respuesta.

                """);

        if (!bloqueClausulas.isBlank()) {
            sb.append(bloqueClausulas);
        }

        if (texto.length() < 8000) {
            sb.append("_Poco texto extraíble: puede ser PDF escaneado sin OCR — conviene archivo con texto seleccionable._\n");
        }
        return sb.toString();
    }

    private static boolean esReferenciasDocumentosVacias(String documentosReferenciaJson) {
        if (documentosReferenciaJson == null || documentosReferenciaJson.isBlank()) {
            return true;
        }
        String t = documentosReferenciaJson.trim();
        return "[]".equals(t) || "{}".equals(t);
    }

    private static boolean esConsultaSobreCapacidadesDelAsistente(String pregunta) {
        if (pregunta == null || pregunta.isBlank()) {
            return false;
        }
        if (pregunta.length() > 220) {
            return false;
        }
        String n = normalizarSinAcento(pregunta.trim());
        return PATRONES_META_ASISTENTE.stream().anyMatch(n::contains);
    }

    private static String normalizarSinAcento(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder();
        for (int cp : n.codePoints().toArray()) {
            int t = Character.getType(cp);
            if (t != Character.NON_SPACING_MARK && t != Character.ENCLOSING_MARK) {
                sb.appendCodePoint(cp);
            }
        }
        return sb.toString();
    }

    /** Extrae palabras útiles + sinónimos de dominio HSE (altura, espacios confinados…). */
    private static LinkedHashSet<String> terminosConsultaParaBusquedaLocal(String pregunta) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        if (pregunta == null || pregunta.isBlank()) {
            return terms;
        }

        Optional<String> estandar = EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta);
        if (estandar.isPresent()) {
            String filtro = normalizarSinAcento(estandar.get());
            for (String w : filtro.split("\\s+")) {
                if (w.length() >= 4 && !STOPWORDS_CONSULTA.contains(w)) {
                    terms.add(w);
                }
            }
            expandirTerminosDominioHse(normalizarSinAcento(pregunta), terms);
            terms.removeIf(t -> t.length() < 4);
            return terms;
        }

        String n = normalizarSinAcento(pregunta);

        Matcher m = Pattern.compile("[a-zñ0-9]{4,}").matcher(n);
        while (m.find()) {
            String w = m.group();
            if (!STOPWORDS_CONSULTA.contains(w)) {
                terms.add(w);
                if (w.endsWith("s") && w.length() > 5) {
                    terms.add(w.substring(0, w.length() - 1));
                }
            }
        }

        expandirTerminosDominioHse(n, terms);
        terms.removeIf(t -> t.length() < 4);
        return terms;
    }

    private static void expandirTerminosDominioHse(String preguntaNorm, LinkedHashSet<String> terms) {
        if (contieneSinNormalizarMas(preguntaNorm, "altura", "alturas")) {
            Collections.addAll(terms, "altura", "alturas", "altitud", "caida", "caidas", "caída",
                    "arnes", "arneses", "andamio", "andamiaje", "plataforma", "trabajo");
        }
        if (contieneSinNormalizarMas(preguntaNorm, "factor", "factores", "calidad", "calidades")) {
            Collections.addAll(terms, "factor", "factores", "calidad", "calidades", "critico", "criticos",
                    "control", "controles", "criterios", "criterios de calidad");
        }
        if (preguntaNorm.contains("cc") || preguntaNorm.matches(".*\\bcc\\s*\\d+.*")) {
            terms.add("cc");
        }
        if (contieneSinNormalizarMas(preguntaNorm, "electric", "tension", "tensión")) {
            Collections.addAll(terms, "electrico", "electrica", "electrico", "tension");
        }
        if (preguntaNorm.contains("confin")) {
            Collections.addAll(terms, "confinados", "confinado", "espacios confinados");
            terms.add("confin");
        }
        if (preguntaNorm.contains("calient") || preguntaNorm.contains("fuego")) {
            Collections.addAll(terms, "caliente", "ignicion");
        }
        if (preguntaNorm.contains("energ")) {
            Collections.addAll(terms, "energia", "energías", "bloqueo");
        }
        if (preguntaNorm.contains("excav")) {
            terms.add("excavaci");
        }
        if (preguntaNorm.contains("levant") || preguntaNorm.contains("guinche") || preguntaNorm.contains("grua")) {
            Collections.addAll(terms, "levantamiento", "izaje", "izajes");
        }
        if (preguntaNorm.contains("transport") || preguntaNorm.contains("carga")) {
            Collections.addAll(terms, "transporte");
        }
    }

    private static boolean contieneSinNormalizarMas(String yaNormalizadoSinAcento, String... sub) {
        for (String s : sub) {
            if (yaNormalizadoSinAcento.contains(normalizarSinAcento(s))) {
                return true;
            }
        }
        return false;
    }

    private static String filtrarEsquemaPorTerminos(String esquemaMultilinea, LinkedHashSet<String> terminosNorm) {
        if (esquemaMultilinea == null || esquemaMultilinea.isBlank() || terminosNorm.isEmpty()) {
            return "";
        }
        LinkedHashSet<String> ordenados = new LinkedHashSet<>();
        for (String linea : esquemaMultilinea.split("\n")) {
            String l = linea.trim().replaceFirst("^-\\s*", "");
            if (l.isBlank()) {
                continue;
            }
            String lc = normalizarSinAcento(l);
            for (String t : terminosNorm) {
                if (t.length() >= 4 && lc.contains(t)) {
                    ordenados.add("- " + l);
                    break;
                }
            }
            if (ordenados.size() >= MAX_LINEAS_ENCABEZADOS_TEMATICOS) {
                break;
            }
        }
        return ordenados.isEmpty() ? "" : String.join("\n", ordenados) + "\n";
    }

    /** Busca cada término en el texto real y arma extractos centrados en la coincidencia. */
    private static List<String> extraerSnippetsSegunConsulta(String textoCompleto, LinkedHashSet<String> terminosNorm) {
        if (textoCompleto.isBlank()) {
            return Collections.emptyList();
        }
        List<String> snippets = new ArrayList<>();
        List<int[]> usados = new ArrayList<>();

        List<String> termsOrden = terminosNorm.stream()
                .filter(t -> t.length() >= 4)
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toList();

        for (String term : termsOrden) {
            try {
                Pattern p = Pattern.compile(Pattern.quote(term), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                Matcher mat = p.matcher(textoCompleto);
                while (mat.find()) {
                    int idx = mat.start();
                    int endMatch = mat.end();
                    int s = Math.max(0, idx - SNIPPET_BEFORE);
                    int e = Math.min(textoCompleto.length(), endMatch + SNIPPET_AFTER);
                    while (s > 0 && textoCompleto.charAt(s) != '\n' && idx - s < SNIPPET_BEFORE + 60) {
                        s--;
                        if (idx - s > SNIPPET_BEFORE + 200) {
                            break;
                        }
                    }
                    while (s < textoCompleto.length() && Character.isWhitespace(textoCompleto.charAt(s))) {
                        s++;
                    }
                    if (solapaConIntervalosUsados(usados, s, e)) {
                        continue;
                    }
                    usados.add(new int[]{s, e});
                    String excerpt = textoCompleto.substring(s, e).replaceAll("\\s+", " ").trim();
                    excerpt = excerpt.length() > 920 ? excerpt.substring(0, 917) + "…" : excerpt;
                    snippets.add("- " + excerpt);

                    if (snippets.size() >= MAX_SNIPPETS) {
                        return snippets;
                    }
                }
            } catch (Exception ignored) {
                // patrón raro → omitir término
            }
        }
        return snippets;
    }

    /** Anexa a la petición a DeepSeek fragmentos donde coinciden términos de la pregunta (zonas típicamente en medio del PDF). */
    private static String construirAnexoPasajesTematicos(List<String> pasajesBullets) {
        if (pasajesBullets == null || pasajesBullets.isEmpty()) {
            return "";
        }
        StringBuilder bx = new StringBuilder();
        bx.append("""
                
                --- PASAJES RELACIONADOS CON LAS PALABRAS DE LA PREGUNTA (motor local sobre el PDF completo) ---
                
                """);

        bx.append(String.join("\n\n", pasajesBullets));
        bx.append("\n--- FIN PASAJES TEMÁTICOS ---\n");
        return bx.toString();
    }

    private static boolean solapaConIntervalosUsados(List<int[]> usados, int s, int e) {
        int mid = (s + e) / 2;
        for (int[] r : usados) {
            if (solapan(r[0], r[1], s, e) || mid >= r[0] && mid <= r[1]) {
                return true;
            }
        }
        return false;
    }

    private static boolean solapan(int a0, int a1, int b0, int b1) {
        return !(a1 < b0 || b1 < a0);
    }

    /** Solo incluye la sección de Certifico/Declaro si la pregunta va en esa línea o hubo coincidencias. */
    private static String construirBloqueClausulasOpcional(String pregunta, String texto) {
        String pq = pregunta != null ? pregunta.toLowerCase() : "";
        boolean preguntaSobreClausula = pq.contains("certific")
                || pq.contains("declar")
                || pq.contains("cláusul")
                || pq.contains("clausul")
                || pq.contains("compromiso")
                || pq.contains("epp")
                || pq.contains("firma")
                || pq.contains("obligacion")
                || pq.contains("obligación");

        String lower = texto.toLowerCase();
        StringBuilder pistas = new StringBuilder();
        if (lower.contains("certifico")) {
            pistas.append("- Aparece **Certifico** en el texto extraído.\n");
        }
        if (lower.contains("declaro")) {
            pistas.append("- Aparece **Declaro** en el texto extraído.\n");
        }
        if (lower.contains("clausula") || lower.contains("cláusula")) {
            pistas.append("- Hay mención de **cláusula** u obligaciones similares.\n");
        }

        if (pistas.length() > 0) {
            return "\n### Cláusulas / formulario (detección simple)\n" + pistas + "\n";
        }
        if (preguntaSobreClausula) {
            return "\n### Cláusulas / formulario (detección simple)\n"
                    + "- No se encontraron coincidencias claras con certificación, declaración o cláusula en el texto extraíble.\n\n";
        }
        return "";
    }

    /** Recorre el texto completo (barato en CPU) y arma un índice de encabezados HSE típicos. */
    private String extraerEsquemaHeuristico(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }

        LinkedHashSet<String> estandares = new LinkedHashSet<>();
        LinkedHashSet<String> otros = new LinkedHashSet<>();

        for (String raw : texto.split("\\R")) {
            String line = raw.trim().replaceAll("\\s+", " ");
            if (line.length() < 12 || line.length() > 220) {
                continue;
            }
            Matcher mEst = PATRON_ESTANDAR_DE.matcher(line);
            if (mEst.matches()) {
                estandares.add(mEst.group(1).trim());
                continue;
            }
            Matcher mCap = PATRON_CAP_SEC.matcher(line);
            if (mCap.matches()) {
                otros.add(line);
                continue;
            }
            if (line.length() <= 100 && tituloTipoMayusculas(line)) {
                otros.add(line);
            }
        }

        List<String> salida = new ArrayList<>(estandares);
        int restantes = MAX_ESQUEMA_LINEAS_TOTAL - salida.size();
        for (String o : otros) {
            if (restantes <= 0) {
                break;
            }
            salida.add(o);
            restantes--;
        }
        if (salida.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (String s : salida) {
            sb.append("- ").append(s).append('\n');
        }
        return sb.toString();
    }

    private static boolean tituloTipoMayusculas(String linea) {
        long letras = linea.codePoints().filter(Character::isLetter).count();
        if (letras < 14) {
            return false;
        }
        long mayus = linea.codePoints().filter(ch -> Character.isLetter(ch) && Character.isUpperCase(ch)).count();
        return mayus * 100 / letras >= 72;
    }

    /** Parte inicial + parte final para no omitir contenido importante en PDF largos (el modelo no recibe 150 k de golpe). */
    private static String construirCuerpoMuestraParaPrompt(String textoCompleto) {
        if (textoCompleto.length() <= MAX_MUESTRA_INICIO) {
            return textoCompleto;
        }
        StringBuilder sb = new StringBuilder(MAX_MUESTRA_INICIO + MAX_MUESTRA_FIN + 80);
        sb.append("(El documento es largo; se envían muestras de inicio y final. Para temas concretos, priorice la SECCIÓN TEMÁTICA COMPLETA.)\n\n");
        sb.append("--- INICIO ---\n");
        sb.append(textoCompleto, 0, MAX_MUESTRA_INICIO);
        sb.append("\n\n--- FINAL ---\n");
        sb.append(textoCompleto.substring(Math.max(0, textoCompleto.length() - MAX_MUESTRA_FIN)));
        return sb.toString();
    }

    /** Detecta si el usuario pide un listado exhaustivo (CC1-CC10, todos los factores, etc.). */
    private static boolean esPreguntaQuePideListaCompleta(String pregunta) {
        if (pregunta == null || pregunta.isBlank()) {
            return false;
        }
        String n = normalizarSinAcento(pregunta);
        return n.contains("todos") || n.contains("todas") || n.contains("completo") || n.contains("completa")
                || n.contains("listado") || n.contains("lista ") || n.contains("enumera")
                || n.contains("cuales son") || n.contains("cuantos") || n.contains("factores")
                || n.contains("factor de calidad") || n.contains("factores de calidad")
                || n.contains("cc1") || n.contains("cc 1") || n.contains("control critico")
                || n.contains("controles criticos") || n.contains("cada uno") || n.contains("uno por uno");
    }

    /**
     * Extrae el bloque completo del estándar/apartado que mejor coincide con la pregunta.
     * Recorre todo el PDF y devuelve desde el encabezado «ESTÁNDAR DE …» hasta el siguiente estándar/capítulo.
     */
    private static String extraerSeccionTematicaCompleta(
            String texto, LinkedHashSet<String> terminos, String pregunta) {
        return extraerSeccionTematicaCompleta(texto, terminos, pregunta, false);
    }

    private static String extraerSeccionTematicaCompleta(
            String texto, LinkedHashSet<String> terminos, String pregunta, boolean resumenInformal) {
        if (texto == null || texto.isBlank()) {
            return "";
        }

        Optional<String> estandarOpt = EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta);
        if (estandarOpt.isPresent()) {
            int inicio = EstandarConsultaHelper.localizarSeccionEstandar(texto, estandarOpt.get());
            if (inicio >= 0) {
                int fin = EstandarConsultaHelper.encontrarFinSeccionEstandar(texto, inicio, estandarOpt.get());
                String seccion = texto.substring(inicio, fin).trim();
                int limite = resumenInformal ? MAX_SECCION_RESUMEN_CHARS : MAX_SECCION_TEMATICA_CHARS;
                if (seccion.length() > limite) {
                    seccion = seccion.substring(0, limite)
                            + "\n... [sección truncada; consulte el PDF para detalle completo]";
                }
                return seccion;
            }
        }

        List<int[]> encabezados = new ArrayList<>();
        Matcher m = PATRON_ESTANDAR_DE_MULTILINEA.matcher(texto);
        while (m.find()) {
            encabezados.add(new int[]{m.start(), m.end()});
        }
        if (encabezados.isEmpty()) {
            return extraerSubseccionPorPalabrasClave(texto, terminos, pregunta, resumenInformal);
        }

        int mejorIdx = -1;
        int mejorPuntaje = 0;
        for (int i = 0; i < encabezados.size(); i++) {
            int start = encabezados.get(i)[0];
            int endHeader = encabezados.get(i)[1];
            String titulo = texto.substring(start, endHeader).trim();
            int puntaje = puntuarEncabezadoContraConsulta(titulo, terminos, pregunta);
            if (puntaje > mejorPuntaje) {
                mejorPuntaje = puntaje;
                mejorIdx = i;
            }
        }

        if (mejorIdx < 0 || mejorPuntaje < 2) {
            String sub = extraerSubseccionPorPalabrasClave(texto, terminos, pregunta, resumenInformal);
            return sub.isBlank() ? "" : sub;
        }

        int sectionStart = encabezados.get(mejorIdx)[0];
        int sectionEnd = texto.length();
        if (mejorIdx + 1 < encabezados.size()) {
            sectionEnd = encabezados.get(mejorIdx + 1)[0];
        } else {
            Matcher siguiente = PATRON_SIGUIENTE_ESTANDAR_O_CAP.matcher(texto.substring(sectionStart + 20));
            if (siguiente.find()) {
                int rel = siguiente.start();
                if (rel > 200) {
                    sectionEnd = Math.min(texto.length(), sectionStart + 20 + rel);
                }
            }
        }

        String seccion = texto.substring(sectionStart, sectionEnd).trim();
        int limite = resumenInformal ? MAX_SECCION_RESUMEN_CHARS : MAX_SECCION_TEMATICA_CHARS;
        if (seccion.length() > limite) {
            seccion = seccion.substring(0, limite)
                    + "\n... [sección truncada; consulte el PDF para detalle completo]";
        }
        return seccion;
    }

    private static String sanitizarHtmlRespuesta(String respuesta) {
        if (respuesta == null || respuesta.isBlank()) {
            return respuesta;
        }
        if (!respuesta.contains("<") && !respuesta.contains("&lt;")) {
            return respuesta;
        }
        String s = respuesta;
        s = s.replaceAll("(?is)</p>\\s*<p>", "\n\n");
        s = s.replaceAll("(?is)<br\\s*/?>", "\n");
        s = s.replaceAll("(?is)<li>", "\n- ");
        s = s.replaceAll("(?is)</li>", "");
        s = s.replaceAll("(?is)<[^>]+>", "");
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }

    /** Busca subsecciones tipo «Factores de calidad» o «Trabajo en altura» cuando no hay encabezado ESTÁNDAR DE. */
    private static String extraerSubseccionPorPalabrasClave(
            String texto, LinkedHashSet<String> terminos, String pregunta, boolean resumenInformal) {
        String nPregunta = normalizarSinAcento(pregunta != null ? pregunta : "");
        List<String> frasesBusqueda = new ArrayList<>();
        if (nPregunta.contains("factor") || nPregunta.contains("calidad")) {
            frasesBusqueda.add("factores de calidad");
            frasesBusqueda.add("factor de calidad");
        }
        if (contieneSinNormalizarMas(nPregunta, "altura", "alturas")) {
            frasesBusqueda.add("trabajo en altura");
            frasesBusqueda.add("estandar de trabajo en altura");
            frasesBusqueda.add("estándar de trabajo en altura");
        }
        for (String t : terminos) {
            if (t.length() >= 5) {
                frasesBusqueda.add(t);
            }
        }

        int mejorStart = -1;
        int mejorLen = 0;
        String textoNorm = normalizarSinAcento(texto);

        for (String frase : frasesBusqueda) {
            String f = normalizarSinAcento(frase);
            int idx = textoNorm.indexOf(f);
            if (idx < 0) {
                continue;
            }
            int start = Math.max(0, idx - 400);
            int end = Math.min(texto.length(), idx + (resumenInformal ? 6000 : 25000));
            Matcher fin = PATRON_SIGUIENTE_ESTANDAR_O_CAP.matcher(texto.substring(idx + f.length()));
            if (fin.find() && fin.start() > 500) {
                end = Math.min(end, idx + f.length() + fin.start());
            }
            int len = end - start;
            if (len > mejorLen) {
                mejorLen = len;
                mejorStart = start;
            }
        }

        if (mejorStart < 0) {
            return "";
        }
        String bloque = texto.substring(mejorStart, Math.min(texto.length(), mejorStart + mejorLen)).trim();
        int limite = resumenInformal ? MAX_SECCION_RESUMEN_CHARS : MAX_SECCION_TEMATICA_CHARS;
        if (bloque.length() > limite) {
            bloque = bloque.substring(0, limite)
                    + "\n... [subsección truncada; consulte el PDF para detalle completo]";
        }
        return bloque;
    }

    private static int puntuarEncabezadoContraConsulta(
            String tituloEncabezado, LinkedHashSet<String> terminos, String pregunta) {
        String tituloNorm = normalizarSinAcento(tituloEncabezado);
        String preguntaNorm = normalizarSinAcento(pregunta != null ? pregunta : "");
        int puntaje = 0;

        Optional<String> estandar = EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta);
        if (estandar.isPresent()) {
            String tit = tituloNorm.replaceFirst("(?i)estandar de\\s+", "");
            if (EstandarConsultaHelper.coincideNombreEstandar(tit, normalizarSinAcento(estandar.get()))) {
                return 50;
            }
        }

        for (String t : terminos) {
            if (t.length() >= 4 && tituloNorm.contains(t)) {
                puntaje += t.length() >= 6 ? 3 : 2;
            }
        }
        if (preguntaNorm.contains("altura") && tituloNorm.contains("altura")) {
            puntaje += 8;
        }
        if (preguntaNorm.contains("factor") && tituloNorm.contains("altura")) {
            puntaje += 4;
        }
        if (preguntaNorm.contains("calidad") && tituloNorm.contains("altura")) {
            puntaje += 4;
        }
        if (preguntaNorm.contains("estandar") && tituloNorm.contains("estandar")) {
            puntaje += 2;
        }
        return puntaje;
    }

    /**
     * Extrae todos los bloques CC1, CC2, Fc1, «Factor de calidad N», etc. con su texto asociado.
     */
    private static String extraerBloqueControlesCriticos(
            String texto, LinkedHashSet<String> terminos, String pregunta) {
        return extraerBloqueControlesCriticos(texto, terminos, pregunta, false);
    }

    private static String extraerBloqueControlesCriticos(
            String texto, LinkedHashSet<String> terminos, String pregunta, boolean resumenInformal) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        if (resumenInformal && !esPreguntaQuePideListaCompleta(pregunta)) {
            return "";
        }
        String nPregunta = normalizarSinAcento(pregunta != null ? pregunta : "");
        boolean relevante = esPreguntaQuePideListaCompleta(pregunta)
                || nPregunta.contains("factor") || nPregunta.contains("calidad")
                || nPregunta.contains("cc") || contieneSinNormalizarMas(nPregunta, "altura", "alturas")
                || nPregunta.contains("critico") || nPregunta.contains("control");
        if (!relevante) {
            return "";
        }

        List<int[]> marcadores = new ArrayList<>();
        Matcher m = PATRON_CONTROL_CRITICO.matcher(texto);
        while (m.find()) {
            marcadores.add(new int[]{m.start(), m.end()});
        }
        if (marcadores.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < marcadores.size(); i++) {
            int start = marcadores.get(i)[0];
            int end = (i + 1 < marcadores.size()) ? marcadores.get(i + 1)[0] : texto.length();
            if (end - start > 8000) {
                end = start + 8000;
            }
            String bloque = texto.substring(start, end).trim().replaceAll("\\s+", " ");
            if (bloque.length() > 30) {
                sb.append("### ").append(texto.substring(marcadores.get(i)[0],
                        Math.min(marcadores.get(i)[1], texto.length())).trim().replaceAll("\\s+", " "));
                sb.append("\n").append(bloque).append("\n\n");
            }
        }
        String resultado = sb.toString().trim();
        if (resultado.length() > MAX_SECCION_TEMATICA_CHARS) {
            resultado = resultado.substring(0, MAX_SECCION_TEMATICA_CHARS)
                    + "\n... [bloque CC truncado por límite técnico]";
        }
        return resultado;
    }
}
