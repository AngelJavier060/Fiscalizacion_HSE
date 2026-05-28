import 'dart:convert';
import 'package:http/http.dart' as http;
import '../config/api_config.dart';
import 'package:shared_preferences/shared_preferences.dart';

class AuthService {
  final http.Client _client = http.Client();

  static const _keyToken    = 'jwt_token';
  static const _keyUserData = 'cached_user_data';

  /// Login del usuario. Guarda token y datos del usuario en caché.
  Future<Map<String, dynamic>> login(String email, String password) async {
    try {
      final response = await _client.post(
        ApiConfig.uri(ApiConfig.authLogin),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'email': email, 'password': password}),
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body) as Map<String, dynamic>;

        // Guardar en caché para que HomeScreen no necesite llamar a /auth/me
        final prefs = await SharedPreferences.getInstance();
        await prefs.setString(_keyToken, data['token'] as String? ?? '');
        await prefs.setString(_keyUserData, jsonEncode(data));

        return data;
      } else {
        final body = response.body;
        Map<String, dynamic>? error;
        try { error = jsonDecode(body) as Map<String, dynamic>; } catch (_) {}
        throw AuthException(
          error?['mensaje'] as String? ??
          error?['message'] as String? ??
          'Credenciales incorrectas (${response.statusCode})',
          response.statusCode,
        );
      }
    } on AuthException {
      rethrow;
    } catch (e) {
      throw AuthException('Sin conexión con el servidor. Verifica que el backend esté activo.', 0);
    }
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
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString('jwt_token');
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
