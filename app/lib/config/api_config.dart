import 'package:flutter/foundation.dart';

/// Configuración de la API para la app móvil
class ApiConfig {
  // Local primero. Para producción cambie a Enviroment.production.
  static const Enviroment _env = Enviroment.development;

  // URLs base según entorno
  static const Map<Enviroment, String> _baseUrls = {
    Enviroment.development: 'http://10.0.2.2:8080/api/v1', // Android Emulator
    Enviroment.staging: 'https://hse.improvement-solution.com/api/v1',
    Enviroment.production: 'https://hse.improvement-solution.com/api/v1',
  };

  /// Teléfono físico en la misma Wi‑Fi: IP de esta PC, ej. '192.168.1.20'.
  /// Deje null para emulador (10.0.2.2) o iOS/Windows (localhost).
  static const String? lanHost = null;

  // Timeouts — texto-completo de PDFs grandes puede tardar más
  static const Duration connectTimeout = Duration(seconds: 30);
  static const Duration receiveTimeout = Duration(seconds: 60);
  static const Duration textoCompletoTimeout = Duration(seconds: 120);

  // Endpoints
  // OJO: el context-path del backend es /api/v1 y los controladores REST usan
  // @RequestMapping("/api/..."), por lo que la ruta real es /api/v1/api/...
  // (auth es la excepción: vive en /api/v1/auth).
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

  /// URL base según el entorno configurado.
  /// Android emulador: 10.0.2.2 · iOS/Windows/web local: localhost.
  static String get baseUrl {
    if (_env != Enviroment.development) {
      return _baseUrls[_env]!;
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

  /// Construye URL completa para un endpoint
  static Uri uri(String endpoint, {Map<String, String>? params}) {
    final url = '$baseUrl$endpoint';
    final uri = Uri.parse(url);
    if (params != null && params.isNotEmpty) {
      return uri.replace(queryParameters: params);
    }
    return uri;
  }
}

enum Enviroment { development, staging, production }
