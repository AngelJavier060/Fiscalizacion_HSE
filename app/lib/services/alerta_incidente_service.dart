import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';

import '../config/api_config.dart';
import '../models/alerta_incidente.dart';
import 'api_service.dart';
import 'auth_service.dart';

class AlertaIncidenteService {
  static Future<List<AlertaIncidente>> listar() async {
    final data = await ApiService.getList(ApiConfig.alertasIncidentes);
    final lista = data
        .map((e) => AlertaIncidente.fromJson(e as Map<String, dynamic>))
        .toList();
    lista.sort((a, b) {
      final porFecha = b.fecha.compareTo(a.fecha);
      if (porFecha != 0) return porFecha;
      return b.id.compareTo(a.id);
    });
    return lista;
  }

  static Future<AlertaIncidente> crear({
    required Map<String, dynamic> datos,
    Uint8List? archivo,
    String? nombreArchivo,
  }) async {
    final headers = await AuthService.getAuthHeaders();
    headers.remove('Content-Type');
    final request = http.MultipartRequest('POST', ApiConfig.uri(ApiConfig.alertasIncidentes));
    request.headers.addAll(headers);
    request.files.add(http.MultipartFile.fromBytes(
      'datos',
      utf8.encode(jsonEncode(datos)),
      filename: 'datos.json',
      contentType: MediaType('application', 'json'),
    ));
    if (archivo != null && archivo.isNotEmpty) {
      request.files.add(http.MultipartFile.fromBytes(
        'archivo',
        archivo,
        filename: nombreArchivo ?? 'evidencia.pdf',
      ));
    }
    final streamed = await request.send();
    final response = await http.Response.fromStream(streamed);
    if (response.statusCode >= 200 && response.statusCode < 300) {
      return AlertaIncidente.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
    }
    throw ApiException(_mensaje(response));
  }

  static Future<Uint8List> descargar(int id) async {
    final headers = await AuthService.getAuthHeaders();
    headers.remove('Content-Type');
    final response = await http.get(
      ApiConfig.uri('${ApiConfig.alertasIncidentes}/$id/archivo'),
      headers: headers,
    );
    if (response.statusCode >= 200 && response.statusCode < 300) {
      return response.bodyBytes;
    }
    throw ApiException(_mensaje(response));
  }

  static String _mensaje(http.Response response) {
    try {
      final body = jsonDecode(response.body);
      if (body is Map) {
        final errores = body['errores'];
        if (errores is List && errores.isNotEmpty) return errores.join(' ');
        return (body['mensaje'] ?? body['message'] ?? 'No se pudo completar la operación').toString();
      }
    } catch (_) {}
    return 'No se pudo completar la operación (${response.statusCode})';
  }
}
