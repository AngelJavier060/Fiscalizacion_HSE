package com.fiscalizacionhse.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extrae puntos clave estructurados por estándar/tema y controles críticos (CC1, CC7, …)
 * con el texto íntegro de cada bloque — ideal para libros HSE tipo ESV/ENAP.
 */
@Service
@Slf4j
public class PuntoClaveExtraccionService {

    private static final int MAX_CONTENIDO_POR_PUNTO = 120_000;

    private static final Pattern PATRON_ESTANDAR = Pattern.compile(
            "(?m)^(EST[AÁ]NDAR\\s+DE\\s+.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern PATRON_CC = Pattern.compile(
            "(?i)\\b(CC)\\s*(\\d{1,2})\\b\\s*[-–—:\\.]?\\s*([^\\n\\r]{2,280})");

    /** CC al inicio de línea (bloques de contenido largo). */
    private static final Pattern PATRON_CC_LINEA = Pattern.compile(
            "(?m)^\\s*(CC\\s*(\\d+))\\b\\s*[-–—:\\.]?\\s*(.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Recorre el PDF completo y genera un punto por control crítico (CC) o por estándar si no hay CC.
     */
    public List<IaService.PuntoClaveIa> extraerPorTemasYControles(String textoCompleto) {
        if (textoCompleto == null || textoCompleto.isBlank()) {
            return Collections.emptyList();
        }

        List<BloqueEstandar> bloques = dividirPorEstandares(textoCompleto);
        List<IaService.PuntoClaveIa> puntos = new ArrayList<>();
        int orden = 0;

        if (bloques.isEmpty()) {
            bloques = List.of(new BloqueEstandar(null, textoCompleto));
        }

        for (BloqueEstandar bloque : bloques) {
            List<MarcadorCc> marcadores = encontrarControlesCriticos(bloque.texto());
            if (marcadores.isEmpty()) {
                String contenido = normalizarContenido(bloque.texto());
                if (contenido.length() < 80) {
                    continue;
                }
                IaService.PuntoClaveIa p = new IaService.PuntoClaveIa();
                p.setTitulo(bloque.titulo() != null ? bloque.titulo() : "Contenido del documento");
                p.setTema(bloque.titulo());
                p.setCodigo(null);
                p.setTipo("ESTANDAR");
                p.setContenido(contenido);
                p.setConfianza(BigDecimal.valueOf(0.92));
                p.setOrden(orden++);
                puntos.add(p);
                continue;
            }

            for (int i = 0; i < marcadores.size(); i++) {
                MarcadorCc cc = marcadores.get(i);
                int fin = (i + 1 < marcadores.size()) ? marcadores.get(i + 1).inicio() : bloque.texto().length();
                String cuerpo = bloque.texto().substring(cc.inicio(), fin).trim();
                String contenido = normalizarContenido(cuerpo);
                if (contenido.length() < 40) {
                    continue;
                }

                String tituloLinea = cc.tituloResto() != null && !cc.tituloResto().isBlank()
                        ? cc.tituloResto().trim()
                        : extraerPrimeraLineaUtil(cuerpo, cc.codigoNormalizado());

                IaService.PuntoClaveIa p = new IaService.PuntoClaveIa();
                p.setCodigo(cc.codigoNormalizado());
                p.setTitulo(tituloLinea);
                p.setTema(bloque.titulo());
                p.setTipo("CONTROL_CRITICO");
                p.setContenido(formatearContenidoCc(cc.codigoNormalizado(), tituloLinea, contenido));
                p.setConfianza(BigDecimal.valueOf(0.95));
                p.setOrden(orden++);
                puntos.add(p);
            }
        }

        log.info("📑 Extracción estructurada: {} puntos (estándares/CC) desde {} caracteres",
                puntos.size(), textoCompleto.length());
        return puntos;
    }

    private static String formatearContenidoCc(String codigo, String titulo, String cuerpo) {
        String encabezado = "**" + codigo + "**";
        if (titulo != null && !titulo.isBlank()) {
            encabezado += " — " + titulo;
        }
        if (cuerpo.startsWith(codigo) || cuerpo.toUpperCase(Locale.ROOT).startsWith(codigo.toUpperCase(Locale.ROOT))) {
            return cuerpo.length() > MAX_CONTENIDO_POR_PUNTO
                    ? cuerpo.substring(0, MAX_CONTENIDO_POR_PUNTO) + "\n\n… *(contenido truncado)*"
                    : cuerpo;
        }
        String combinado = encabezado + "\n\n" + cuerpo;
        return combinado.length() > MAX_CONTENIDO_POR_PUNTO
                ? combinado.substring(0, MAX_CONTENIDO_POR_PUNTO) + "\n\n… *(contenido truncado)*"
                : combinado;
    }

    private static String extraerPrimeraLineaUtil(String cuerpo, String codigo) {
        for (String linea : cuerpo.split("\\R")) {
            String l = linea.trim();
            if (l.length() < 8) {
                continue;
            }
            if (l.toUpperCase(Locale.ROOT).startsWith(codigo.toUpperCase(Locale.ROOT))) {
                String resto = l.substring(codigo.length()).replaceFirst("^\\s*[-–—:\\.]?\\s*", "").trim();
                if (resto.length() >= 5) {
                    return resto.length() > 200 ? resto.substring(0, 197) + "…" : resto;
                }
            }
            if (l.length() >= 10 && !l.matches("(?i)^control\\s+critico.*")) {
                return l.length() > 200 ? l.substring(0, 197) + "…" : l;
            }
        }
        return "Control crítico " + codigo;
    }

    private static String normalizarContenido(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private record BloqueEstandar(String titulo, String texto) {}

    private record MarcadorCc(int inicio, String codigoNormalizado, String tituloResto) {}

    private static List<BloqueEstandar> dividirPorEstandares(String texto) {
        List<int[]> headers = new ArrayList<>();
        Matcher m = PATRON_ESTANDAR.matcher(texto);
        while (m.find()) {
            headers.add(new int[]{m.start(), m.end()});
        }
        if (headers.isEmpty()) {
            return Collections.emptyList();
        }

        List<BloqueEstandar> bloques = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            int start = headers.get(i)[0];
            int endHeader = headers.get(i)[1];
            int end = (i + 1 < headers.size()) ? headers.get(i + 1)[0] : texto.length();
            String titulo = texto.substring(start, endHeader).trim().replaceAll("\\s+", " ");
            String cuerpo = texto.substring(start, end).trim();
            bloques.add(new BloqueEstandar(titulo, cuerpo));
        }
        return bloques;
    }

    private static List<MarcadorCc> encontrarControlesCriticos(String textoBloque) {
        Map<Integer, MarcadorCc> porNumero = new TreeMap<>();
        Matcher mLinea = PATRON_CC_LINEA.matcher(textoBloque);
        while (mLinea.find()) {
            int num = Integer.parseInt(mLinea.group(2));
            String codigoRaw = "CC" + mLinea.group(2);
            String tituloResto = mLinea.group(3) != null ? mLinea.group(3).trim() : "";
            porNumero.putIfAbsent(num, new MarcadorCc(mLinea.start(), codigoRaw, tituloResto));
        }
        Matcher mFlex = PATRON_CC.matcher(textoBloque);
        while (mFlex.find()) {
            int num = Integer.parseInt(mFlex.group(2));
            if (!porNumero.containsKey(num)) {
                String codigoRaw = "CC" + mFlex.group(2);
                String tituloResto = mFlex.group(3) != null ? mFlex.group(3).trim() : "";
                porNumero.put(num, new MarcadorCc(mFlex.start(), codigoRaw, tituloResto));
            }
        }
        return porNumero.values().stream()
                .sorted(Comparator.comparingInt(m -> m.inicio()))
                .collect(Collectors.toList());
    }
}
