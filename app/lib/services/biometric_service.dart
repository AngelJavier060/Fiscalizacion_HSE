import 'package:flutter/services.dart';
import 'package:local_auth/local_auth.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Gestiona la autenticación biométrica (huella dactilar / facial).
/// Flujo:
///   1. Login con credenciales → [guardarCredencialesBiometricas] guarda token+email.
///   2. Próximas entradas → [autenticarConBiometria] verifica huella → devuelve token.
class BiometricService {
  static const _storage = FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
  );

  static const _keyToken     = 'bio_token';
  static const _keyEmail     = 'bio_email';
  static const _keyHabilitado = 'bio_habilitado';

  final LocalAuthentication _auth = LocalAuthentication();

  // ---------------------------------------------------------------------------
  // Consultas de capacidad
  // ---------------------------------------------------------------------------

  /// ¿El dispositivo soporta biometría Y tiene al menos una huella registrada?
  Future<bool> get disponible async {
    try {
      final puede = await _auth.canCheckBiometrics;
      if (!puede) return false;
      final tipos = await _auth.getAvailableBiometrics();
      return tipos.isNotEmpty;
    } catch (_) {
      return false;
    }
  }

  /// ¿El usuario YA habilitó biometría en esta app?
  Future<bool> get habilitado async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_keyHabilitado) ?? false;
  }

  // ---------------------------------------------------------------------------
  // Registro
  // ---------------------------------------------------------------------------

  /// Llama después de un login exitoso con credenciales.
  /// Guarda el [token] y el [email] para recuperarlos tras verificación biométrica.
  Future<void> guardarCredencialesBiometricas({
    required String token,
    required String email,
  }) async {
    await _storage.write(key: _keyToken, value: token);
    await _storage.write(key: _keyEmail, value: email);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keyHabilitado, true);
  }

  /// Elimina las credenciales y deshabilita la biometría en esta app.
  Future<void> deshabilitarBiometria() async {
    await _storage.delete(key: _keyToken);
    await _storage.delete(key: _keyEmail);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keyHabilitado, false);
  }

  // ---------------------------------------------------------------------------
  // Autenticación
  // ---------------------------------------------------------------------------

  /// Solicita verificación biométrica.
  /// Retorna el [token] guardado si la verificación fue exitosa, o `null` si falló.
  Future<String?> autenticarConBiometria() async {
    try {
      final ok = await _auth.authenticate(
        localizedReason: 'Verifica tu huella para ingresar al sistema',
        options: const AuthenticationOptions(
          stickyAuth: true,
          biometricOnly: false, // permite PIN como fallback
        ),
      );
      if (!ok) return null;
      return await _storage.read(key: _keyToken);
    } on PlatformException {
      return null;
    }
  }

  /// Email guardado (para mostrar en UI).
  Future<String?> get emailGuardado async {
    return _storage.read(key: _keyEmail);
  }
}
