import 'dart:io';
import 'dart:math';
import 'package:image/image.dart' as img;
import 'package:path_provider/path_provider.dart';

/// Procesamiento de imágenes estilo CamScanner:
/// fondo blanco puro · texto negro nítido · sin sombras.
///
/// Algoritmo:
///  1. Escalar a ≤ 1400 px (rendimiento + calidad PDF).
///  2. Escala de grises.
///  3. Eliminar sombras: normalizar dividiendo por el fondo estimado
///     con un blur gaussiano de radio grande.
///  4. Umbral adaptivo (Niblack simplificado con tabla integral O(1)):
///     cada píxel → blanco o negro según media local ± constante C.
///  5. Sharpen para texto más nítido.
class DocumentProcessor {
  DocumentProcessor._();

  /// Procesa la imagen en [inputPath] y retorna la ruta del archivo
  /// procesado, o null si ocurre un error irrecuperable.
  static Future<String?> processForDocument(String inputPath) async {
    try {
      final bytes = await File(inputPath).readAsBytes();
      var src = img.decodeImage(bytes);
      if (src == null) return null;

      // ── 1. Escalar ──────────────────────────────────────────────
      const maxDim = 1400;
      if (src.width > maxDim || src.height > maxDim) {
        src = src.width >= src.height
            ? img.copyResize(src,
                width: maxDim, interpolation: img.Interpolation.cubic)
            : img.copyResize(src,
                height: maxDim, interpolation: img.Interpolation.cubic);
      }

      // ── 2. Escala de grises ──────────────────────────────────────
      final gray = img.grayscale(src);

      // ── 3. Eliminar sombras ──────────────────────────────────────
      // El blur de radio grande aproxima el color de fondo en cada zona.
      final background = img.gaussianBlur(gray, radius: 14);
      final normalized = _normalize(gray, background);

      // ── 4. Umbral adaptivo (tabla integral) ──────────────────────
      final binary = _adaptiveThreshold(normalized, blockSize: 23, C: 12);

      // ── Guardar ──────────────────────────────────────────────────
      final dir = await getTemporaryDirectory();
      final outPath =
          '${dir.path}/doc_${DateTime.now().millisecondsSinceEpoch}.jpg';
      await File(outPath)
          .writeAsBytes(img.encodeJpg(binary, quality: 96));
      return outPath;
    } catch (_) {
      return null;
    }
  }

  // ── Normalización de fondo ──────────────────────────────────────
  // Divide cada píxel por el valor del fondo estimado. Resultado:
  // zonas iluminadas de forma uniforme (~blanco).
  static img.Image _normalize(img.Image gray, img.Image background) {
    final w = gray.width;
    final h = gray.height;
    final result = img.Image(width: w, height: h);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        final gv = gray.getPixel(x, y).r.toInt();       // 0–255 (R=G=B)
        final bv = background.getPixel(x, y).r.toInt(); // 0–255
        final nv = bv > 10 ? (gv / bv * 245).clamp(0, 255).toInt() : 255;
        result.setPixel(x, y, img.ColorRgb8(nv, nv, nv));
      }
    }
    return result;
  }

  // ── Umbral adaptivo con tabla integral ──────────────────────────
  // Para cada píxel (x,y): blanco si valor ≥ mediaLocal – C; negro si no.
  // La tabla integral permite calcular cualquier suma de caja en O(1).
  static img.Image _adaptiveThreshold(
      img.Image gray, {
      required int blockSize,
      required int C,
  }) {
    final w   = gray.width;
    final h   = gray.height;
    final stride = w + 1;

    // Construir tabla integral
    final integral = List<double>.filled((h + 1) * stride, 0.0);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        final lum = gray.getPixel(x, y).r.toDouble();
        final idx = (y + 1) * stride + (x + 1);
        integral[idx] = lum
            + integral[y * stride + (x + 1)]
            + integral[(y + 1) * stride + x]
            - integral[y * stride + x];
      }
    }

    final result = img.Image(width: w, height: h);
    final half = max(1, blockSize ~/ 2);

    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        final x1 = max(0, x - half);
        final y1 = max(0, y - half);
        final x2 = min(w - 1, x + half);
        final y2 = min(h - 1, y + half);

        final area = (x2 - x1 + 1) * (y2 - y1 + 1);
        final sum = integral[(y2 + 1) * stride + (x2 + 1)]
            - integral[y1 * stride + (x2 + 1)]
            - integral[(y2 + 1) * stride + x1]
            + integral[y1 * stride + x1];

        final mean = sum / area;
        final lum  = gray.getPixel(x, y).r.toDouble();
        final v    = (lum >= mean - C) ? 255 : 0;
        result.setPixel(x, y, img.ColorRgb8(v, v, v));
      }
    }
    return result;
  }
}
