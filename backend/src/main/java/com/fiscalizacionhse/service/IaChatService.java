package com.fiscalizacionhse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalizacionhse.config.DeepSeekClientConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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
 * 
 * MODELOS: usa deepseek-chat (rápido) por defecto y deepseek-reasoner
 * para consultas complejas. Configurable via application.yml.
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
    private final DeepSeekClientConfig deepSeekConfig;
    private final OllamaClient ollamaClient;
    private final String apiKey;
    private final String apiUrl;

    private static final String MODELO_RAPIDO = "deepseek-chat";
    private static final String MODELO_RAZONAMIENTO = "deepseek-reasoner";

    /** Override temporal del modelo para el hilo actual (lo setea el controller). */
    private static final ThreadLocal<String> MODELO_OVERRIDE = new ThreadLocal<>();

    public static void setModeloOverride(String modelo) {
        if (modelo != null && (MODELO_RAPIDO.equals(modelo) || MODELO_RAZONAMIENTO.equals(modelo))) {
            MODELO_OVERRIDE.set(modelo);
        } else {
            MODELO_OVERRIDE.remove();
        }
    }

    public static void clearModeloOverride() {
        MODELO_OVERRIDE.remove();
    }

    private String modeloEfectivo() {
        String override = MODELO_OVERRIDE.get();
        return override != null ? override : deepSeekConfig.getDefaultModel();
    }

    public IaChatService(
            CatalogoControlesCriticosService catalogoCcService,
            CriteriosCalidadExtraccionService criteriosCalidadService,
            TranscripcionLibroConsultaService transcripcionLibroService,
            ConsultaLibroPrecisaService consultaLibroPrecisaService,
            DeepSeekClientConfig deepSeekConfig,
            OllamaClient ollamaClient,
            @Qualifier("deepSeekRestTemplate") RestTemplate restTemplate,
            @Value("${app.ia.deepseek.api-key:}") String apiKey,
            @Value("${app.ia.deepseek.api-url:https://api.deepseek.com/v1/chat/completions}") String apiUrl) {
        this.catalogoCcService = catalogoCcService;
        this.criteriosCalidadService = criteriosCalidadService;
        this.transcripcionLibroService = transcripcionLibroService;
        this.consultaLibroPrecisaService = consultaLibroPrecisaService;
        this.deepSeekConfig = deepSeekConfig;
        this.ollamaClient = ollamaClient;
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.apiKey = "";
        this.apiUrl = apiUrl;

        log.info("IaChatService: PDF de la empresa + Ollama local si está listo");
    }

    /**
     * Responde con los PDF de la empresa. Si Ollama está en este servidor, redacta;
     * si no, devuelve las citas extraídas.
     */
    public String generarRespuestaRag(String pregunta, String contexto, String documentosRef) {
        String extractivo = generarRespuestaDesdeDocumentos(pregunta, contexto);
        if (contexto == null || contexto.replaceAll("\\s+", "").length() < 40) {
            return extractivo;
        }
        String user = "PREGUNTA:\n" + pregunta + "\n\nCONTEXTO (únicos PDF de la empresa):\n"
                + truncarParaPrompt(contexto, 10000);
        return ollamaClient.chatSiListo(construirSystemPrompt(
                        documentosRef != null ? documentosRef : "[]"), user)
                .map(IaChatService::sanitizarHtmlRespuesta)
                .orElse(extractivo);
    }

    /**
     * Crea resumen automático de un documento
     */
    public String generarResumen(String titulo, String texto) {
        return generarResumenSimulado(titulo, texto);
    }

    /** Motor interno: responde con los PDF de la empresa. */
    public boolean deepseekDisponible() {
        return true;
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
        String extractivo = generarBusquedaAsistidaSimulada(consulta, contextoFragmentos);
        if (contextoFragmentos == null || contextoFragmentos.isBlank()) {
            return extractivo;
        }
        String sistema = """
                Eres FISCALIZA-AI. Analiza SOLO los fragmentos de PDF de esta empresa.
                No inventes normas externas. Español, Markdown, breve.
                Si no hay base en los fragmentos, dilo.
                """;
        String user = "Consulta: " + consulta + "\n\nFragmentos:\n" + truncarParaPrompt(contextoFragmentos, 8000);
        return ollamaClient.chatSiListo(sistema, user).map(IaChatService::sanitizarHtmlRespuesta).orElse(extractivo);
    }

    private String generarBusquedaAsistidaSimulada(String consulta, String contextoFragmentos) {
        String ctx = contextoFragmentos != null ? contextoFragmentos : "";
        if (ctx.length() > 6000) {
            ctx = ctx.substring(0, 6000)
                    + "\n\n … *(vista truncada aquí solo por tamaño en pantalla; no indica problema con tus documentos).*";
        }
        return String.format("""
                ### Resultado en sus PDF: «%s»

                %s
                """, consulta, ctx.isBlank() ? "*No hay coincidencias en el texto extraído.*" : ctx);
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

    private String generarRespuestaDesdeDocumentos(String pregunta, String contexto) {
        if (contexto != null && !contexto.isBlank() && contexto.replaceAll("\\s+", "").length() > 40) {
            String ctx = contexto.trim();
            if (ctx.length() > 14000) {
                ctx = ctx.substring(0, 14000) + "\n\n… *(recorte de pantalla; el texto completo está en sus PDF)*";
            }
            return String.format("""
                ## Lo que dicen sus documentos

                **Pregunta:** «%s»

                %s

                *Fuente: PDF cargados en su empresa. Sin servicios externos.*
                """, pregunta, ctx);
        }
        return String.format("""
            ## No está en los documentos cargados

            **Pregunta:** «%s»

            En la biblioteca HSE de su empresa no aparece un apartado claro para esto.
            Suba el PDF, use **Reprocesar PDF** si es infografía, o pregunte con el nombre del estándar (altura, EPP, CC1, permiso de trabajo).
            """, pregunta);
    }

    private String generarRespuestaSimulada(String pregunta, String contexto) {
        return generarRespuestaDesdeDocumentos(pregunta, contexto);
    }

    private String generarResumenSimulado(String titulo, String texto) {
        String muestra = texto == null ? "" : texto.trim();
        if (muestra.length() > 1800) {
            muestra = muestra.substring(0, 1800) + "\n\n…";
        }
        return String.format("""
            ## Resumen de «%s»

            Texto extraído de su PDF:

            %s
            """, titulo, muestra.isBlank() ? "*Este archivo no tiene letras extraídas.*" : muestra);
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
        String nombreDetectado = estandarOpt.orElse("estándar").toUpperCase(Locale.ROOT);
        final String nombreEstandar = (!nombreDetectado.startsWith("ESTÁNDAR") && !nombreDetectado.startsWith("ESTANDAR"))
                ? "ESTÁNDAR DE " + nombreDetectado
                : nombreDetectado;

        if (textoFocal == null || textoFocal.replaceAll("\\s+", "").length() < 80) {
            return "## " + nombreEstandar + "\n\n"
                    + "No localicé este apartado en **«" + tituloDocumento + "»**. "
                    + "Revise que en el **Editor de Contenido** exista un encabezado "
                    + "*(por ejemplo: «3. ESTÁNDAR DE TRABAJO EN CALIENTE»)* con el texto debajo.\n";
        }

        String sistema = """
                Eres FISCALIZA-AI, asistente HSE. El usuario preguntó por UN estándar en UN documento.
                Redacta en español con Markdown claro (## título, ### subtítulos, **negritas** en roles y CC).
                Máximo 400 palabras. No copies párrafos literales largos del PDF.
                Estructura: 1–2 frases intro → ### Objetivo → ### Alcance → ### Requisitos clave (5–8 viñetas).
                Solo este estándar; no menciones otros capítulos. No uses normas que no estén en el texto.
                Cierra con: *Fuente: «título del documento»*
                """;
        String user = "Documento: " + tituloDocumento + "\n\n"
                + "--- SECCIÓN DEL ESTÁNDAR (única fuente) ---\n"
                + truncarParaPrompt(textoFocal, 6500)
                + "\n--- FIN ---\n\n"
                + "Pregunta: " + pregunta;
        return ollamaClient.chatSiListo(sistema, user)
                .map(IaChatService::sanitizarHtmlRespuesta)
                .orElseGet(() -> formatearResumenLocalEstandar(pregunta, tituloDocumento, nombreEstandar, textoFocal));
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

        String sistema = """
                Eres FISCALIZA-AI, asistente HSE. El usuario pidió un RESUMEN BREVE de UN apartado de un libro.
                Redacta en español con Markdown (## título, viñetas si aplica).
                Máximo 180 palabras. No copies párrafos largos literales.
                Estructura: 1 párrafo introductorio + 3–5 viñetas con ideas clave.
                Solo este apartado; no menciones Introducción, Alcance ni estándares posteriores.
                Cierra con: *Fuente: «título del documento»*
                """;
        String user = "Documento: " + tituloDocumento + "\nApartado: " + tituloApartado + "\n\n"
                + "--- TEXTO DEL APARTADO ---\n"
                + truncarParaPrompt(cuerpoApartado, 5000)
                + "\n--- FIN ---\n\n"
                + "Pregunta: " + pregunta;
        return ollamaClient.chatSiListo(sistema, user)
                .map(IaChatService::sanitizarHtmlRespuesta)
                .orElseGet(() -> formatearResumenLocalApartado(pregunta, tituloDocumento, tituloApartado, cuerpoApartado));
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
            cuerpo = cuerpo.substring(0, 3200).trim() + "\n\n…";
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
        if (!esConsultaResumenInformal(pregunta)) {
            Optional<String> trans = transcripcionLibroService.responderConTranscripcion(
                    tituloDocumento, textoDocumento, pregunta);
            if (trans.isPresent()) {
                return trans.get();
            }
        }

        IaChatService.ContextoTematicoDocumento tematico = analizarTextoParaConsulta(textoDocumento, pregunta);
        String contextoOllama = (tematico.seccionCompleta() + "\n\n" + tematico.bloqueControlesCriticos()).trim();
        if (contextoOllama.length() < 80) {
            contextoOllama = truncarParaPrompt(textoDocumento, 8000);
        }
        String sistemaDoc = """
                Eres FISCALIZA-AI. El usuario pregunta sobre UN documento de la empresa.
                Responde solo con el texto recibido. No inventes normativa externa.
                Español, Markdown claro, tono profesional HSE.
                Cierra con: *Fuente: «título del documento»*
                """;
        String userDoc = "Documento: " + tituloDocumento + "\nPregunta: " + pregunta
                + "\n\n--- TEXTO ---\n" + truncarParaPrompt(contextoOllama, 9000);
        return ollamaClient.chatSiListo(sistemaDoc, userDoc)
                .map(IaChatService::sanitizarHtmlRespuesta)
                .orElseGet(() -> generarRespuestaSimuladaSobreDocumento(pregunta, tituloDocumento, textoDocumento));
    }

    /**
     * Modo sin modelo: responde enfocado en lo que preguntaste (extracción local desde el texto del PDF).
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
                        ? seccionCompleta.substring(0, 12000) + "\n\n…"
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
                    _Respuesta desde el texto de este PDF._

                    """);

        }

        sb.append("""
                *Fuente: texto de este PDF.*

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
