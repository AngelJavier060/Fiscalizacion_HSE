import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';

/// Marcador de avance de lectura/escucha de un documento.
class LecturaProgreso {
  final int documentoId;
  final int indiceFragmento;
  final int totalFragmentos;
  final int actualizadoEnMs;

  LecturaProgreso({
    required this.documentoId,
    required this.indiceFragmento,
    required this.totalFragmentos,
    required this.actualizadoEnMs,
  });

  double get porcentaje =>
      totalFragmentos <= 0 ? 0 : (indiceFragmento + 1) / totalFragmentos;

  int get porcentajeEntero => (porcentaje * 100).clamp(0, 100).round();

  Map<String, dynamic> toJson() => {
        'documentoId': documentoId,
        'indiceFragmento': indiceFragmento,
        'totalFragmentos': totalFragmentos,
        'actualizadoEnMs': actualizadoEnMs,
      };

  factory LecturaProgreso.fromJson(Map<String, dynamic> json) {
    return LecturaProgreso(
      documentoId: json['documentoId'] as int? ?? 0,
      indiceFragmento: json['indiceFragmento'] as int? ?? 0,
      totalFragmentos: json['totalFragmentos'] as int? ?? 0,
      actualizadoEnMs: json['actualizadoEnMs'] as int? ?? 0,
    );
  }
}

/// Guarda y recupera dónde quedó la lectura de cada documento (SharedPreferences).
class LecturaProgresoService {
  static const _prefijo = 'lectura_progreso_';

  static String _clave(int documentoId) => '$_prefijo$documentoId';

  static Future<void> guardar({
    required int documentoId,
    required int indiceFragmento,
    required int totalFragmentos,
  }) async {
    if (totalFragmentos <= 0 || indiceFragmento < 0) return;
    final prefs = await SharedPreferences.getInstance();
    final progreso = LecturaProgreso(
      documentoId: documentoId,
      indiceFragmento: indiceFragmento,
      totalFragmentos: totalFragmentos,
      actualizadoEnMs: DateTime.now().millisecondsSinceEpoch,
    );
    await prefs.setString(_clave(documentoId), jsonEncode(progreso.toJson()));
  }

  static Future<LecturaProgreso?> obtener(int documentoId) async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_clave(documentoId));
    if (raw == null) return null;
    try {
      final progreso =
          LecturaProgreso.fromJson(jsonDecode(raw) as Map<String, dynamic>);
      if (progreso.documentoId != documentoId || progreso.indiceFragmento < 0) {
        return null;
      }
      return progreso;
    } catch (_) {
      return null;
    }
  }

  static Future<void> limpiar(int documentoId) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_clave(documentoId));
  }
}
