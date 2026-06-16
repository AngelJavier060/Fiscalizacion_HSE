import '../config/api_config.dart';
import '../models/documento_model.dart';
import 'api_service.dart';

class DocumentoService {
  /// Obtener documentos de la empresa del usuario
  static Future<List<DocumentoModel>> getDocumentos(int empresaId) async {
    final response = await ApiService.get(
      '${ApiConfig.documentosList}/$empresaId',
      params: {'page': '0', 'size': '200'},
    );
    final content = response['content'] as List<dynamic>? ?? [];
    return content
        .map((e) => DocumentoModel.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Metadata de un documento (estado de procesamiento, etc.).
  static Future<DocumentoModel> getDocumento(int documentoId) async {
    final response = await ApiService.get(
      '${ApiConfig.documentosDetalle}/$documentoId',
    );
    return DocumentoModel.fromJson(response);
  }

  /// Obtener detalle de un documento con sus puntos clave
  static Future<DocumentoDetalle> getDocumentoDetalle(int documentoId) async {
    final docResponse = await ApiService.get(
      '${ApiConfig.documentosDetalle}/$documentoId',
    );

    final puntosResponse = await ApiService.getList(
      '${ApiConfig.puntosClave}/$documentoId',
    );

    final documento = DocumentoModel.fromJson(docResponse);
    final puntos = puntosResponse
        .map((e) => PuntoClaveModel.fromJson(e as Map<String, dynamic>))
        .toList();

    return DocumentoDetalle(documento: documento, puntosClave: puntos);
  }

  /// Obtener el texto completo extraído del PDF (para lectura por voz).
  static Future<DocumentoTexto> getTextoCompleto(int documentoId) async {
    final response = await ApiService.get(
      '${ApiConfig.documentosDetalle}/$documentoId/texto-completo',
      receiveTimeout: ApiConfig.textoCompletoTimeout,
    );
    return DocumentoTexto.fromJson(response);
  }

  /// Reprocesar PDF en segundo plano (ERROR o atascado).
  static Future<DocumentoModel> reprocesar(int documentoId) async {
    final response = await ApiService.post(
      '${ApiConfig.documentosDetalle}/$documentoId/reprocesar',
      body: {},
    );
    return DocumentoModel.fromJson(response);
  }

  /// Guardar texto editado (sincroniza con la web).
  static Future<DocumentoModel> guardarTextoExtraido(
    int documentoId,
    String texto,
  ) async {
    final response = await ApiService.put(
      '${ApiConfig.documentosDetalle}/$documentoId/texto-extraido',
      body: {'texto': texto},
      receiveTimeout: ApiConfig.textoCompletoTimeout,
    );
    return DocumentoModel.fromJson(response);
  }

  /// Lista de empresas (solo SUPER_ADMIN). Devuelve pares {id, nombre}.
  static Future<List<EmpresaOpcion>> getEmpresas() async {
    final response = await ApiService.getList(
      ApiConfig.empresas,
      params: {'size': '100'},
    );
    return response
        .map((e) => EmpresaOpcion(
              id: (e as Map<String, dynamic>)['id'] as int,
              nombre: e['nombre'] as String? ?? 'Empresa',
            ))
        .toList();
  }

  /// ========== PUNTOS CLAVE CRUD ==========

  /// Crear un punto clave manual
  static Future<PuntoClaveModel> crearPuntoClave(
    int documentoId,
    String contenido,
  ) async {
    final response = await ApiService.post(
      ApiConfig.puntoClaveById,
      body: {
        'documentoId': documentoId,
        'contenido': contenido,
      },
    );
    return PuntoClaveModel.fromJson(response);
  }

  /// Editar un punto clave
  static Future<PuntoClaveModel> editarPuntoClave(
    int puntoId,
    int documentoId,
    String contenido,
  ) async {
    final response = await ApiService.put(
      '${ApiConfig.puntoClaveById}/$puntoId',
      body: {
        'contenido': contenido,
        'documentoId': documentoId,
      },
    );
    return PuntoClaveModel.fromJson(response);
  }

  /// Eliminar un punto clave
  static Future<void> eliminarPuntoClave(int puntoId) async {
    await ApiService.delete('${ApiConfig.puntoClaveById}/$puntoId');
  }

  /// Marcar un punto clave como revisado
  static Future<PuntoClaveModel> marcarPuntoRevisado(int puntoId) async {
    final response = await ApiService.patch(
      '${ApiConfig.puntoClaveById}/$puntoId/revisado',
      body: {},
    );
    return PuntoClaveModel.fromJson(response);
  }

  /// Marcar todos los puntos IA como revisados
  static Future<void> marcarTodosRevisados(int documentoId) async {
    await ApiService.post(
      '${ApiConfig.puntosClave}/$documentoId/revisar-todos',
      body: {},
    );
  }

  /// Regenerar puntos clave con IA
  static Future<void> regenerarPuntosIa(int documentoId) async {
    await ApiService.post(
      '${ApiConfig.puntosClave}/$documentoId/regenerar',
      body: {},
    );
  }

  /// Buscar documentos por texto
  static Future<List<DocumentoModel>> buscarDocumentos(
    int empresaId,
    String query,
  ) async {
    final response = await ApiService.getList(
      '${ApiConfig.documentosBuscar}/$empresaId',
      params: {'q': query},
    );
    return response
        .map((e) => DocumentoModel.fromJson(e as Map<String, dynamic>))
        .toList();
  }
}

/// Opción simple de empresa para selectores.
class EmpresaOpcion {
  final int id;
  final String nombre;
  const EmpresaOpcion({required this.id, required this.nombre});
}
