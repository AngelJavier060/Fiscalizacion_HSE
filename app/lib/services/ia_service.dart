import '../config/api_config.dart';
import '../models/notificacion_model.dart';
import 'api_service.dart';

class IaService {
  /// Consultar con RAG (pregunta + contexto de documentos).
  /// Si se pasa [documentoId], la respuesta se acota a ese documento.
  static Future<Map<String, dynamic>> consultar({
    required String pregunta,
    required int empresaId,
    int? documentoId,
  }) async {
    return await ApiService.post(
      ApiConfig.iaConsultar,
      body: {
        'pregunta': pregunta,
        'empresaId': empresaId,
        if (documentoId != null) 'documentoId': documentoId,
      },
    );
  }

  /// Estado del motor IA y catálogo de documentos de la empresa.
  static Future<Map<String, dynamic>> estado(int empresaId) async {
    return await ApiService.get('${ApiConfig.iaEstado}/$empresaId');
  }

  /// Búsqueda semántica (solo resultados)
  static Future<List<dynamic>> buscar({
    required String consulta,
    required int empresaId,
    int limite = 10,
  }) async {
    final response = await ApiService.post(
      ApiConfig.iaBuscar,
      body: {
        'consulta': consulta,
        'empresaId': empresaId,
        'limite': limite,
      },
    );
    return response['resultados'] as List<dynamic>? ?? [];
  }

  /// Obtener historial de consultas
  static Future<List<ConsultaIaModel>> getHistorial() async {
    final response = await ApiService.get(ApiConfig.iaHistorial);
    final content = response['content'] as List<dynamic>? ?? [];
    return content
        .map((e) => ConsultaIaModel.fromJson(e as Map<String, dynamic>))
        .toList();
  }
}
