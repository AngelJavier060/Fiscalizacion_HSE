import 'package:flutter/foundation.dart';

/// En Chrome local lee el mismo servidor que la web (donde están las alertas cargadas).
/// En el teléfono y en el sitio publicado sigue usando producción.
class ApiConfig {
  static const String productionBase = 'https://hse.improvement-solution.com/api/v1';
  static const String localBase = 'http://localhost:8080/api/v1';

  static const Duration connectTimeout = Duration(seconds: 30);
  static const Duration receiveTimeout = Duration(seconds: 60);
  static const Duration textoCompletoTimeout = Duration(seconds: 120);

  static const String authLogin = '/auth/login';
  static const String authMe = '/api/me';

  static const String empresas = '/api/empresas';
  static const String documentosList = '/api/documentos/empresa';
  static const String documentosDetalle = '/api/documentos';
  static const String documentosBuscar = '/api/documentos/buscar';
  static const String puntosClave = '/api/puntos-clave/documento';
  static const String puntoClaveById = '/api/puntos-clave';

  static const String notificacionesBandeja = '/api/notificaciones/bandeja';
  static const String notificacionesNoLeidas = '/api/notificaciones/no-leidas';
  static const String notificacionesContar = '/api/notificaciones/contar-no-leidas';
  static const String notificacionesMarcarLeida = '/api/notificaciones';
  static const String notificacionesMarcarTodas = '/api/notificaciones/marcar-todas-leidas';
  static const String notificacionesAudio = '/api/notificaciones/audio';

  static const String iaConsultar = '/api/ia/consultar';
  static const String iaBuscar = '/api/ia/buscar';
  static const String iaHistorial = '/api/ia/historial';
  static const String iaEstado = '/api/ia/estado';

  static const String permisosTrabajo = '/api/permisos-trabajo';
  static const String alertasIncidentes = '/api/alertas-incidentes';

  static const String simuladorInicio = '/api/simulador/inicio';
  static const String simuladorExamenes = '/api/simulador/examenes';
  static const String simuladorHistorial = '/api/simulador/historial';
  static const String simuladorPreguntas = '/api/simulador/preguntas';

  static bool get isLocal => kIsWeb && Uri.base.host == 'localhost';

  static void useProduction() {}

  static String get baseUrl => isLocal ? localBase : productionBase;

  static Uri uri(String endpoint, {Map<String, String>? params}) {
    final parsed = Uri.parse('$baseUrl$endpoint');
    if (params != null && params.isNotEmpty) {
      return parsed.replace(queryParameters: params);
    }
    return parsed;
  }
}
