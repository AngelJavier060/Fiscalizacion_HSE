package com.fiscalizacionhse.service;

import com.fiscalizacionhse.dto.response.BusquedaIaResponse;
import com.fiscalizacionhse.model.Documento;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agente FISCALIZA-AI: enruta la consulta del usuario según intención
 * (inventario de PDF, capacidades del asistente, consulta sobre contenido).
 */
@Service
public class FiscalizaAgentService {

    public enum IntencionAgente {
        SALUDO,
        INVENTARIO_DOCUMENTOS,
        CAPACIDADES_AGENTE,
        CONSULTA_CONTENIDO
    }

    private static final Set<String> TOKENS_SALUDO = Set.of(
            "hola", "holas", "buenas", "buenos", "dias", "dia", "tardes", "noches",
            "saludos", "saludo", "hey", "que", "tal", "como", "estas", "esta", "estan");

    private static final List<String> PATRONES_INVENTARIO = List.of(
            "que documentos", "cuales documentos", "cual documento", "listado de document", "lista de document",
            "documentos disponibles", "documentos cargados", "documentos subidos", "documentos tengo",
            "documentos tiene", "documentos hay", "documento sub", "archivos cargados", "archivos subidos",
            "archivos tengo", "archivos tiene", "pdf cargados", "pdf subidos", "que archivos", "que pdf",
            "mis documentos", "mis pdf", "mis archivos", "tengo cargado", "tengo subido",
            "inventario de document", "documentos de la empresa", "documentos en fiscaliza",
            "los titulos", "los títulos", "nombres de los document", "que libros", "libros disponibles",
            "cuantos documentos", "cuántos documentos", "cuantos pdf", "cuántos pdf",
            "what documents", "which documents", "uploaded files", "my documents", "list documents");

    private static final List<String> PATRONES_CAPACIDADES = List.of(
            "que puedes hacer", "qué puedes hacer", "que sabes hacer", "qué sabes hacer",
            "para que sirves", "para qué sirves", "como funcionas", "cómo funcionas",
            "que eres", "qué eres", "quien eres", "quién eres", "ayuda", "help",
            "que haces", "qué haces", "como me ayudas", "cómo me ayudas");

    public IntencionAgente detectarIntencion(String pregunta) {
        if (pregunta == null || pregunta.isBlank()) {
            return IntencionAgente.CONSULTA_CONTENIDO;
        }
        if (esSaludo(pregunta)) {
            return IntencionAgente.SALUDO;
        }
        String n = normalizar(pregunta);
        for (String p : PATRONES_CAPACIDADES) {
            if (n.contains(p)) {
                return IntencionAgente.CAPACIDADES_AGENTE;
            }
        }
        for (String p : PATRONES_INVENTARIO) {
            if (n.contains(p)) {
                return IntencionAgente.INVENTARIO_DOCUMENTOS;
            }
        }
        return IntencionAgente.CONSULTA_CONTENIDO;
    }

    public String responderInventarioDocumentos(List<Documento> documentos) {
        if (documentos == null || documentos.isEmpty()) {
            return """
                    ## Documentos de su empresa

                    **No hay PDF cargados** todavía.

                    Suba archivos en la sección **Documentos** y vuelva a preguntarme. Solo puedo responder con archivos que usted haya subido a la plataforma.
                    """;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Documentos HSE cargados en su empresa\n\n");
        sb.append("Tiene **").append(documentos.size()).append("** PDF activo(s):\n\n");

        int n = 1;
        for (Documento d : documentos) {
            sb.append(n++).append(". **").append(escapar(d.getTitulo())).append("**");
            sb.append(" *(ID ").append(d.getId()).append(")*");
            String desc = truncar(d.getDescripcion(), 200);
            if (desc != null && !desc.isBlank()) {
                sb.append("\n   - ").append(desc);
            }
            sb.append('\n');
        }

        sb.append("""
                
                ---
                **Siguiente paso:** pregúnteme sobre el **contenido** de cualquier documento, por ejemplo:
                - «¿Qué dice sobre EPP en [nombre del PDF]?»
                - «Resume los requisitos de trabajo en altura»
                - «¿Qué controles críticos aparecen en nuestros archivos?»
                """);
        return sb.toString().trim();
    }

    public String responderCapacidadesAgente(List<Documento> documentos, boolean deepseekActivo) {
        int total = documentos != null ? documentos.size() : 0;
        String motor = deepseekActivo ? "**DeepSeek** (conectado)" : "**DeepSeek** (no configurado en el servidor)";

        StringBuilder sb = new StringBuilder();
        sb.append("## Soy su agente FISCALIZA-AI\n\n");
        sb.append("Trabajo en **círculo cerrado**: solo uso los PDF que usted cargó en la plataforma.\n\n");
        sb.append("**Motor:** ").append(motor).append("\n");
        sb.append("**Documentos disponibles:** ").append(total).append(" PDF\n\n");
        sb.append("""
                ### Qué puedo hacer por usted
                1. **Listar** los documentos HSE de su empresa (como ahora).
                2. **Buscar y responder** preguntas sobre el contenido de esos PDF.
                3. **Resumir** secciones o extraer obligaciones, EPP, controles críticos, etc.
                4. **Citar** de qué archivo sale cada respuesta.

                ### Qué no hago
                - No uso internet ni normativa externa.
                - No invento información que no esté en sus archivos.

                **Pruebe:** «¿Qué documentos tengo?» o «¿Qué dice sobre permisos de trabajo?»
                """);

        if (total > 0 && total <= 8) {
            sb.append("\n### Sus documentos actuales\n");
            int i = 1;
            for (Documento d : documentos) {
                sb.append(i++).append(". ").append(escapar(d.getTitulo())).append("\n");
            }
        }

        return sb.toString().trim();
    }

    /**
     * Saludo visual: tono cercano, título ESV y lista numerada con negritas (Markdown).
     */
    public String responderSaludoComparativo(
            List<Documento> documentos,
            Map<String, List<EstandarConsultaHelper.IndiceEstandar>> indicePorLibro,
            boolean deepseekActivo) {
        int totalPdf = documentos != null ? documentos.size() : 0;
        Map<String, List<EstandarConsultaHelper.IndiceEstandar>> libros =
                indicePorLibro != null ? indicePorLibro : Map.of();

        List<EstandarConsultaHelper.IndiceEstandar> indice =
                EstandarConsultaHelper.indiceUnificadoParaSaludo(libros.values());

        StringBuilder sb = new StringBuilder();
        sb.append("Hola. Soy **FISCALIZA-AI**, tu agente para **consultar** y **prepararte** en gestión de **fiscalización HSE**.\n\n");
        sb.append("Tengo información de los estándares");
        if (totalPdf > 0) {
            sb.append(" en sus **").append(totalPdf).append("** documento(s) cargado(s)");
        }
        sb.append(". Le enumero lo que puede preguntar:\n\n");
        sb.append("## ESTÁNDARES QUE SALVAN VIDAS\n\n");

        for (EstandarConsultaHelper.IndiceEstandar item : indice) {
            sb.append(EstandarConsultaHelper.lineaIndiceMarkdown(item)).append("\n");
        }

        if (libros.size() > 1) {
            sb.append("\n### Sus libros (comparativo)\n\n");
            for (String tituloLibro : libros.keySet()) {
                sb.append("- **").append(escapar(tituloLibro)).append("**\n");
            }
        } else if (libros.size() == 1) {
            sb.append("\n*Fuente:* **").append(escapar(libros.keySet().iterator().next())).append("**\n");
        }

        sb.append("""
                
                ---
                
                **¿Cómo preguntar?**
                - *«Háblame del estándar de trabajo en caliente»*
                - *«Criterios de calidad / CC del estándar de excavaciones»*
                - *«Compara altura entre mis libros»*
                
                Respondo **solo** con el **Editor de Contenido** guardado en la plataforma (libro estructurado en base de datos).
                """);

        if (!deepseekActivo) {
            sb.append("\n---\n⚠️ Active **DeepSeek** (`DEEPSEEK_API_KEY`) para respuestas redactadas con el mismo formato.\n");
        }

        return sb.toString().trim();
    }

    /** Saludos cortos sin pregunta de contenido («hola», «hola hola», «buenas tardes»). */
    public boolean esSaludo(String pregunta) {
        if (pregunta == null || pregunta.isBlank()) {
            return false;
        }
        String n = normalizar(pregunta);
        if (n.length() > 90) {
            return false;
        }
        if (n.contains("estandar") || n.contains("document") || n.contains("pdf")
                || n.contains("cc") || n.contains("criterio") || n.contains("altura")
                || n.contains("excav") || n.contains("epp")) {
            return false;
        }
        String[] tokens = n.split("\\s+");
        if (tokens.length == 0) {
            return false;
        }
        for (String token : tokens) {
            if (token.length() < 2) {
                continue;
            }
            if (!TOKENS_SALUDO.contains(token)) {
                return false;
            }
        }
        return n.contains("hola") || n.contains("buenas") || n.contains("buenos")
                || n.contains("saludos") || n.contains("hey") || n.contains("que tal")
                || n.contains("como estas");
    }

    public List<BusquedaIaResponse.CatalogoEmpresaDoc> catalogoUi(List<Documento> documentos) {
        if (documentos == null || documentos.isEmpty()) {
            return List.of();
        }
        return documentos.stream()
                .map(d -> BusquedaIaResponse.CatalogoEmpresaDoc.builder()
                        .id(d.getId())
                        .titulo(d.getTitulo())
                        .descripcion(truncar(d.getDescripcion(), 240))
                        .build())
                .collect(Collectors.toList());
    }

    private static String normalizar(String s) {
        if (s == null) return "";
        String corta = s.length() > 500 ? s.substring(0, 500) : s;
        return Normalizer.normalize(corta, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String escapar(String s) {
        if (s == null || s.isBlank()) return "Sin título";
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String truncar(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String one = s.replaceAll("\\s+", " ").trim();
        if (one.length() <= max) return one;
        return one.substring(0, Math.max(1, max - 1)).trim() + "…";
    }
}
