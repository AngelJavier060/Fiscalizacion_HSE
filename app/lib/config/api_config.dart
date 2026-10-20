import 'package:flutter/foundation.dart';

/// Configuración de la API para la app móvil y Flutter web.
class ApiConfig {
  static const String productionBase = 'https://hse.improvement-solution.com/api/v1';

  /// Teléfono físico en la misma Wi‑Fi: IP de esta PC, ej. '192.168.1.20'.
  /// Deje null para emulador (10.0.2.2) o iOS/Windows (localhost).
  static const String? lanHost = null;

  static String? _forcedBase;

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

  static const String simuladorInicio = '/api/simulador/inicio';
  static const String simuladorExamenes = '/api/simulador/examenes';
  static const String simuladorHistorial = '/api/simulador/historial';
  static const String simuladorPreguntas = '/api/simulador/preguntas';

  static bool get isLocal {
    final u = baseUrl;
    return u.contains('localhost') || u.contains('127.0.0.1') || u.contains('10.0.2.2');
  }

  static void useProduction() {
    _forcedBase = productionBase;
  }

  /// Release (APK y https://hse.../app/) → VPS.
  /// Debug → backend local; si no responde, ApiService cambia a producción.
  static String get baseUrl {
    if (_forcedBase != null) return _forcedBase!;
    if (kReleaseMode) {
      if (kIsWeb) {
        final origin = Uri.base.origin;
        if (origin.startsWith('http')) return '$origin/api/v1';
      }
      return productionBase;
    }
    if (lanHost != null && lanHost!.isNotEmpty) {
      return 'http://$lanHost:8080/api/v1';
    }
    if (kIsWeb) {
      return 'http://localhost:8080/api/v1';
    }
    if (defaultTargetPlatform == TargetPlatform.android) {
      return 'http://10.0.2.2:8080/api/v1';
    }
    return 'http://localhost:8080/api/v1';
  }

  static Uri uri(String endpoint, {Map<String, String>? params}) {
    final parsed = Uri.parse('$baseUrl$endpoint');
    if (params != null && params.isNotEmpty) {
      return parsed.replace(queryParameters: params);
    }
    return parsed;
  }
}
