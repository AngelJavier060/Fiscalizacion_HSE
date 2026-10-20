import 'dart:typed_data';

import '../config/api_config.dart';
import '../models/simulador_model.dart';
import 'api_service.dart';

class SimuladorService {
  Future<SsoInicio> inicio() async {
    final data = await ApiService.get(ApiConfig.simuladorInicio);
    return SsoInicio.fromJson(data);
  }

  Future<SsoExamen> iniciarExamen(
    String modo, {
    int? temaId,
    int? preguntasPorBanco,
    bool nuevoSet = true,
  }) async {
    final data = await ApiService.post(
      ApiConfig.simuladorExamenes,
      body: {
        'modo': modo,
        'temaId': temaId,
        'preguntasPorBanco': preguntasPorBanco,
        'nuevoSet': nuevoSet,
      },
    );
    return SsoExamen.fromJson(data);
  }

  Future<SsoExamen> obtenerExamen(int id) async {
    final data = await ApiService.get('${ApiConfig.simuladorExamenes}/$id');
    return SsoExamen.fromJson(data);
  }

  Future<SsoExamen> responder(int examenId, int preguntaId, int? opcionId) async {
    final data = await ApiService.put(
      '${ApiConfig.simuladorExamenes}/$examenId/respuestas',
      body: {
        'preguntaId': preguntaId,
        'opcionId': opcionId,
      },
    );
    return SsoExamen.fromJson(data);
  }

  Future<SsoResultado> finalizar(int id) async {
    final data = await ApiService.post('${ApiConfig.simuladorExamenes}/$id/finalizar');
    return SsoResultado.fromJson(data);
  }

  Future<SsoResultado> resultado(int id) async {
    final data = await ApiService.get('${ApiConfig.simuladorExamenes}/$id/resultado');
    return SsoResultado.fromJson(data);
  }

  Future<SustentoArchivo> descargarSustento(int preguntaId, {String? nombre, String? tipo}) async {
    final res = await ApiService.getRaw('${ApiConfig.simuladorPreguntas}/$preguntaId/sustento');
    final mime = (tipo ?? res.headers['content-type'] ?? 'application/octet-stream')
        .split(';')
        .first
        .trim();
    return SustentoArchivo(
      bytes: Uint8List.fromList(res.bodyBytes),
      nombre: (nombre == null || nombre.isEmpty) ? 'sustento' : nombre,
      mime: mime,
    );
  }
}
