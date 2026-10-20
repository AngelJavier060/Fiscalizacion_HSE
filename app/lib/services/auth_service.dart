import 'dart:convert';
import 'package:http/http.dart' as http;
import '../config/api_config.dart';
import 'package:shared_preferences/shared_preferences.dart';

class AuthService {
  final http.Client _client = http.Client();

  static const _keyToken    = 'jwt_token';
  static const _keyUserData = 'cached_user_data';
  static const _keyApiBase  = 'api_base_url';

  /// Si la app cambió de local a producción, borra el token y el historial cacheado.
  static Future<void> _alinearServidor() async {
    final prefs = await SharedPreferences.getInstance();
    final actual = prefs.getString(_keyApiBase);
    if (actual == ApiConfig.baseUrl) return;
    await prefs.remove(_keyToken);
    await prefs.remove(_keyUserData);
    await prefs.setString(_keyApiBase, ApiConfig.baseUrl);
  }

  /// Login del usuario. Guarda token y datos del usuario en caché.
  Future<Map<String, dynamic>> login(String email, String password) async {
    await _alinearServidor();
    try {
      return await _loginOnce(email, password);
    } on AuthException {
      rethrow;
    } catch (_) {
      throw AuthException(
        'No hay conexión con el servidor. Revise internet o intente de nuevo.',
        0,
      );
    }
  }

  Future<Map<String, dynamic>> _loginOnce(String email, String password) async {
    final response = await _client.post(
      ApiConfig.uri(ApiConfig.authLogin),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'email': email.trim().toLowerCase(), 'password': password}),
    );

    if (response.statusCode == 200) {
      final data = jsonDecode(response.body) as Map<String, dynamic>;
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_keyToken, data['token'] as String? ?? '');
      await prefs.setString(_keyUserData, jsonEncode(data));
      return data;
    }

    Map<String, dynamic>? error;
    try {
      error = jsonDecode(response.body) as Map<String, dynamic>;
    } catch (_) {}
    throw AuthException(
      error?['mensaje'] as String? ??
          error?['message'] as String? ??
          'Credenciales incorrectas (${response.statusCode})',
      response.statusCode,
    );
  }

  /// Obtiene datos del usuario desde caché local (no requiere backend activo).
  Future<Map<String, dynamic>> getUserData() async {
    final prefs = await SharedPreferences.getInstance();
    final cached = prefs.getString(_keyUserData);
    if (cached != null) {
      return jsonDecode(cached) as Map<String, dynamic>;
    }
    throw AuthException('Sesión no encontrada. Inicia sesión nuevamente.', 401);
  }

  /// Refresca datos del usuario desde el backend (opcional, no bloquea si falla).
  Future<Map<String, dynamic>?> refreshUserData() async {
    final token = await getToken();
    if (token == null) return null;
    try {
      final response = await _client.get(
        ApiConfig.uri(ApiConfig.authMe),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $token',
        },
      ).timeout(const Duration(seconds: 5));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body) as Map<String, dynamic>;
        final prefs = await SharedPreferences.getInstance();
        await prefs.setString(_keyUserData, jsonEncode(data));
        return data;
      }
      return null;
    } catch (_) {
      return null; // Si falla, no importa — usamos caché
    }
  }

  /// Obtener token guardado
  static Future<String?> getToken() async {
    await _alinearServidor();
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_keyToken);
  }

  /// Obtener headers con autenticación
  static Future<Map<String, String>> getAuthHeaders() async {
    final token = await getToken();
    return {
      'Content-Type': 'application/json',
      if (token != null) 'Authorization': 'Bearer $token',
    };
  }

  /// Cerrar sesión
  Future<void> logout() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_keyToken);
    await prefs.remove(_keyUserData);
  }

  void dispose() {
    _client.close();
  }
}

class AuthException implements Exception {
  final String message;
  final int statusCode;

  AuthException(this.message, this.statusCode);

  @override
  String toString() => message;
}
