import 'dart:convert';
import 'package:http/http.dart' as http;
import '../config/api_config.dart';
import '../models/notificacion_model.dart';
import 'api_service.dart';
import 'auth_service.dart';

class NotificacionService {
  /// Obtener bandeja de notificaciones
  static Future<List<NotificacionModel>> getBandeja() async {
    final response = await ApiService.get(ApiConfig.notificacionesBandeja);
    final content = response['content'] as List<dynamic>? ?? [];
    return content
        .map((e) => NotificacionModel.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Obtener notificaciones no leídas
  static Future<List<NotificacionModel>> getNoLeidas() async {
    final response = await ApiService.getList(ApiConfig.notificacionesNoLeidas);
    return response
        .map((e) => NotificacionModel.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Contar notificaciones no leídas
  static Future<int> contarNoLeidas() async {
    final response = await ApiService.get(ApiConfig.notificacionesContar);
    return (response['cantidad'] as int?) ?? 0;
  }

  /// Marcar notificación como leída
  static Future<void> marcarLeida(int notificacionId) async {
    await ApiService.patch(
      '${ApiConfig.notificacionesMarcarLeida}/$notificacionId/leida',
    );
  }

  /// Marcar todas como leídas
  static Future<void> marcarTodasLeidas() async {
    await ApiService.post(ApiConfig.notificacionesMarcarTodas);
  }

  /// Descargar audio de notificación
  static Future<http.Response?> descargarAudio(int notificacionId) async {
    try {
      final headers = await AuthService.getAuthHeaders();
      final response = await http.get(
        ApiConfig.uri('${ApiConfig.notificacionesAudio}/$notificacionId'),
        headers: headers,
      );
      if (response.statusCode == 200) {
        return response;
      }
      return null;
    } catch (e) {
      return null;
    }
  }
}
