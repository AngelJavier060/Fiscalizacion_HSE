import 'dart:convert';
import 'package:http/http.dart' as http;
import '../config/api_config.dart';
import 'auth_service.dart';

/// Servicio base para llamadas API autenticadas
class ApiService {
  static final http.Client _client = http.Client();

  /// GET request autenticado
  static Future<Map<String, dynamic>> get(
    String endpoint, {
    Map<String, String>? params,
    Duration? receiveTimeout,
  }) async {
    try {
      final headers = await AuthService.getAuthHeaders();
      final request = http.Request(
        'GET',
        ApiConfig.uri(endpoint, params: params),
      )..headers.addAll(headers);

      final streamed = await _client.send(request).timeout(
        receiveTimeout ?? ApiConfig.receiveTimeout,
        onTimeout: () => throw ApiException(
          'La respuesta tardó demasiado. Verifica tu conexión e inténtalo de nuevo.',
        ),
      );
      final response = await http.Response.fromStream(streamed);
      return _handleResponse(response);
    } catch (e) {
      if (e is ApiException) rethrow;
      throw ApiException('Error GET $endpoint: $e');
    }
  }

  /// GET request que retorna lista
  static Future<List<dynamic>> getList(
    String endpoint, {
    Map<String, String>? params,
  }) async {
    try {
      final headers = await AuthService.getAuthHeaders();
      final response = await _client.get(
        ApiConfig.uri(endpoint, params: params),
        headers: headers,
      );
      return _handleListResponse(response);
    } catch (e) {
      throw ApiException('Error GET $endpoint: $e');
    }
  }

  /// POST request autenticado
  static Future<Map<String, dynamic>> post(
    String endpoint, {
    Map<String, dynamic>? body,
  }) async {
    try {
      final headers = await AuthService.getAuthHeaders();
      final response = await _client.post(
        ApiConfig.uri(endpoint),
        headers: headers,
        body: body != null ? jsonEncode(body) : null,
      );
      return _handleResponse(response);
    } catch (e) {
      throw ApiException('Error POST $endpoint: $e');
    }
  }

  /// PUT request autenticado
  static Future<Map<String, dynamic>> put(
    String endpoint, {
    Map<String, dynamic>? body,
    Duration? receiveTimeout,
  }) async {
    try {
      final headers = await AuthService.getAuthHeaders();
      final request = http.Request('PUT', ApiConfig.uri(endpoint))
        ..headers.addAll(headers)
        ..body = body != null ? jsonEncode(body) : '';
      final streamed = await _client.send(request).timeout(
        receiveTimeout ?? ApiConfig.receiveTimeout,
        onTimeout: () => throw ApiException(
          'La respuesta tardó demasiado. Verifica tu conexión e inténtalo de nuevo.',
        ),
      );
      final response = await http.Response.fromStream(streamed);
      return _handleResponse(response);
    } catch (e) {
      if (e is ApiException) rethrow;
      throw ApiException('Error PUT $endpoint: $e');
    }
  }

  /// PATCH request autenticado
  static Future<Map<String, dynamic>> patch(
    String endpoint, {
    Map<String, dynamic>? body,
  }) async {
    try {
      final headers = await AuthService.getAuthHeaders();
      final response = await _client.patch(
        ApiConfig.uri(endpoint),
        headers: headers,
        body: body != null ? jsonEncode(body) : null,
      );
      return _handleResponse(response);
    } catch (e) {
      throw ApiException('Error PATCH $endpoint: $e');
    }
  }

  /// DELETE request autenticado
  static Future<void> delete(String endpoint) async {
    try {
      final headers = await AuthService.getAuthHeaders();
      await _client.delete(
        ApiConfig.uri(endpoint),
        headers: headers,
      );
    } catch (e) {
      throw ApiException('Error DELETE $endpoint: $e');
    }
  }

  /// Manejar respuesta individual
  static Map<String, dynamic> _handleResponse(http.Response response) {
    if (response.statusCode >= 200 && response.statusCode < 300) {
      if (response.body.isEmpty) return {};
      return jsonDecode(response.body) as Map<String, dynamic>;
    }
    if (response.statusCode == 401) {
      throw ApiException('Sesión expirada. Inicia sesión nuevamente.');
    }
    final error = response.body.isNotEmpty
        ? jsonDecode(response.body)
        : {'mensaje': 'Error ${response.statusCode}'};
    throw ApiException(error['mensaje'] ?? 'Error del servidor');
  }

  /// Manejar respuesta de lista
  static List<dynamic> _handleListResponse(http.Response response) {
    if (response.statusCode >= 200 && response.statusCode < 300) {
      final data = jsonDecode(response.body);
      if (data is List) return data;
      if (data is Map && data.containsKey('content')) {
        return data['content'] as List<dynamic>;
      }
      return [];
    }
    throw ApiException('Error ${response.statusCode}');
  }
}

class ApiException implements Exception {
  final String message;
  ApiException(this.message);
  @override
  String toString() => message;
}
