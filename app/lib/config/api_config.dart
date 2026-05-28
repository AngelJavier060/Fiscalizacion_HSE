/// Configuración de la API para la app móvil
class ApiConfig {
  // Cambiar según entorno: desarrollo, staging, producción
  static const Enviroment _env = Enviroment.development;

  // URLs base según entorno
  static const Map<Enviroment, String> _baseUrls = {
    Enviroment.development: 'http://10.0.2.2:8080/api/v1', // Android Emulator
    Enviroment.staging: 'https://staging-api.fiscalizacionhse.com/api/v1',
    Enviroment.production: 'https://api.fiscalizacionhse.com/api/v1',
  };

  // URLs para dispositivos iOS (cambian por el simulador)
  static const Map<Enviroment, String> _iosBaseUrls = {
    Enviroment.development: 'http://localhost:8080/api/v1',
    Enviroment.staging: 'https://staging-api.fiscalizacionhse.com/api/v1',
    Enviroment.production: 'https://api.fiscalizacionhse.com/api/v1',
  };

  // Timeouts
  static const Duration connectTimeout = Duration(seconds: 15);
  static const Duration receiveTimeout = Duration(seconds: 30);

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

  /// Obtiene la URL base según plataforma y entorno
  static String get baseUrl {
    // Detectar plataforma
    if (const bool.fromEnvironment('dart.library.io')) {
      // En iOS usa localhost, en Android usa 10.0.2.2
      if (const bool.fromEnvironment('is_ios')) {
        return _iosBaseUrls[_env]!;
      }
    }
    return _baseUrls[_env]!;
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
