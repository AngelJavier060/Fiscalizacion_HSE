import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../services/auth_service.dart';
import '../services/biometric_service.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _formKey        = GlobalKey<FormState>();
  final _emailCtrl      = TextEditingController();
  final _passwordCtrl   = TextEditingController();
  final _bioService     = BiometricService();

  bool _obscurePassword  = true;
  bool _isLoading        = false;
  bool _isBioLoading     = false;
  String? _errorMessage;
  bool _fromLanding      = false;

  // Estado biométrico
  bool _bioDisponible    = false;
  bool _bioHabilitado    = false;
  String? _emailGuardado;

  // Paleta clara MD3 (consistente con el resto de la app)
  static const Color _primary = Color(0xFF003EC7);
  static const Color _primaryLight = Color(0xFF0052FF);
  static const Color _bg = Color(0xFFFAF8FF);
  static const Color _surface = Color(0xFFFFFFFF);
  static const Color _textMain = Color(0xFF131B2E);
  static const Color _textSub = Color(0xFF434656);
  static const Color _border = Color(0xFFC3C5D9);
  static const Color _error = Color(0xFFBA1A1A);

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final args = ModalRoute.of(context)?.settings.arguments;
    if (args is Map && args['fromLanding'] == true) {
      _fromLanding = true;
    }
    _initBiometria();
  }

  Future<void> _initBiometria() async {
    final disponible = await _bioService.disponible;
    final habilitado = disponible ? await _bioService.habilitado : false;
    final email      = habilitado ? await _bioService.emailGuardado : null;
    if (mounted) {
      setState(() {
        _bioDisponible  = disponible;
        _bioHabilitado  = habilitado;
        _emailGuardado  = email;
      });
    }
  }

  @override
  void dispose() {
    _emailCtrl.dispose();
    _passwordCtrl.dispose();
    super.dispose();
  }

  // ---------------------------------------------------------------------------
  // Login con credenciales
  // ---------------------------------------------------------------------------

  Future<void> _login() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() { _isLoading = true; _errorMessage = null; });

    try {
      final authService = AuthService();
      final response = await authService.login(
        _emailCtrl.text.trim().toLowerCase(),
        _passwordCtrl.text,
      );
      if (!mounted) return;

      // El auth_service ya guardó token y datos en SharedPreferences.
      // Solo necesitamos el token para biometría.
      final token = response['token'] as String? ?? '';

      // Ofrecer biometría si está disponible pero no configurada aún
      if (_bioDisponible && !_bioHabilitado && token.isNotEmpty) {
        await _mostrarDialogoBiometria(token, _emailCtrl.text.trim());
      }

      if (mounted) Navigator.pushReplacementNamed(context, '/home');
    } on AuthException catch (e) {
      setState(() => _errorMessage = e.message);
    } catch (_) {
      setState(() => _errorMessage = 'Error de conexión. Verifica tu internet.');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  // ---------------------------------------------------------------------------
  // Login con biometría
  // ---------------------------------------------------------------------------

  Future<void> _loginBiometrico() async {
    setState(() { _isBioLoading = true; _errorMessage = null; });

    try {
      final token = await _bioService.autenticarConBiometria();
      if (!mounted) return;

      if (token == null) {
        setState(() => _errorMessage = 'No se pudo verificar la huella. Intenta con tu contraseña.');
        return;
      }

      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('jwt_token', token);
      if (mounted) Navigator.pushReplacementNamed(context, '/home');
    } catch (_) {
      setState(() => _errorMessage = 'Error al verificar biometría. Usa tu contraseña.');
    } finally {
      if (mounted) setState(() => _isBioLoading = false);
    }
  }

  // ---------------------------------------------------------------------------
  // Diálogo para habilitar biometría post-login
  // ---------------------------------------------------------------------------

  Future<void> _mostrarDialogoBiometria(String token, String email) async {
    final habilitar = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: _surface,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: _primary.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(8),
              ),
              child: const Icon(Icons.fingerprint, color: _primary, size: 24),
            ),
            const SizedBox(width: 12),
            const Expanded(
              child: Text(
                'Habilitar huella',
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                  color: _textMain,
                ),
              ),
            ),
          ],
        ),
        content: const Text(
          '¿Deseas ingresar más rápido con tu huella dactilar en próximas sesiones?',
          style: TextStyle(fontSize: 14, color: _textSub),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Ahora no', style: TextStyle(color: _textSub)),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: FilledButton.styleFrom(backgroundColor: _primary),
            child: const Text('Habilitar'),
          ),
        ],
      ),
    );

    if (habilitar == true) {
      await _bioService.guardarCredencialesBiometricas(token: token, email: email);
    }
  }

  // ---------------------------------------------------------------------------
  // Build
  // ---------------------------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: _bg,
      body: Stack(
        children: [
          // Fondo decorativo superior
          Positioned(
            top: 0,
            left: 0,
            right: 0,
            child: Container(
              height: 220,
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  colors: [_primary, _primaryLight],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.only(
                  bottomLeft: Radius.circular(40),
                  bottomRight: Radius.circular(40),
                ),
              ),
            ),
          ),

          SafeArea(
            child: Column(
              children: [
                // Topbar
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  child: Row(
                    children: [
                      if (_fromLanding)
                        IconButton(
                          onPressed: () => Navigator.of(context).pushReplacementNamed('/landing'),
                          icon: const Icon(Icons.arrow_back_rounded, color: Colors.white),
                        )
                      else
                        const SizedBox(width: 48),
                      const Spacer(),
                      Text(
                        'Fiscalización HSE',
                        style: GoogleFonts.hankenGrotesk(
                          color: Colors.white,
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const Spacer(),
                      const SizedBox(width: 48),
                    ],
                  ),
                ),

                // Logo en la zona azul
                const SizedBox(height: 16),
                Container(
                  width: 72,
                  height: 72,
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(20),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withValues(alpha: 0.15),
                        blurRadius: 16,
                        offset: const Offset(0, 6),
                      ),
                    ],
                  ),
                  child: const Icon(
                    Icons.verified_user_rounded,
                    color: _primary,
                    size: 38,
                  ),
                ),
                const SizedBox(height: 10),
                Text(
                  'Bienvenido',
                  style: GoogleFonts.hankenGrotesk(
                    color: Colors.white,
                    fontSize: 20,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'Ingresa tus credenciales para continuar',
                  style: GoogleFonts.inter(
                    color: Colors.white.withValues(alpha: 0.8),
                    fontSize: 13,
                  ),
                ),

                // Tarjeta del formulario
                const SizedBox(height: 24),
                Expanded(
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.fromLTRB(20, 0, 20, 32),
                    child: Container(
                      decoration: BoxDecoration(
                        color: _surface,
                        borderRadius: BorderRadius.circular(24),
                        boxShadow: [
                          BoxShadow(
                            color: _primary.withValues(alpha: 0.1),
                            blurRadius: 20,
                            offset: const Offset(0, 6),
                          ),
                        ],
                      ),
                      padding: const EdgeInsets.all(24),
                      child: Form(
                        key: _formKey,
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Iniciar sesión',
                              style: GoogleFonts.hankenGrotesk(
                                fontSize: 18,
                                fontWeight: FontWeight.w800,
                                color: _textMain,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              'Accede al sistema de fiscalización',
                              style: GoogleFonts.inter(fontSize: 13, color: _textSub),
                            ),
                            const SizedBox(height: 20),

                            // Error
                            if (_errorMessage != null)
                              Container(
                                width: double.infinity,
                                padding: const EdgeInsets.all(12),
                                margin: const EdgeInsets.only(bottom: 16),
                                decoration: BoxDecoration(
                                  color: const Color(0xFFFEF2F2),
                                  border: Border.all(color: _error.withValues(alpha: 0.3)),
                                  borderRadius: BorderRadius.circular(10),
                                ),
                                child: Row(
                                  children: [
                                    const Icon(Icons.error_outline, color: _error, size: 18),
                                    const SizedBox(width: 8),
                                    Expanded(
                                      child: Text(
                                        _errorMessage!,
                                        style: const TextStyle(color: _error, fontSize: 12),
                                      ),
                                    ),
                                    GestureDetector(
                                      onTap: () => setState(() => _errorMessage = null),
                                      child: const Icon(Icons.close, color: _error, size: 16),
                                    ),
                                  ],
                                ),
                              ),

                            // Email
                            _FieldLabel(label: 'Correo electrónico'),
                            const SizedBox(height: 6),
                            TextFormField(
                              controller: _emailCtrl,
                              keyboardType: TextInputType.emailAddress,
                              style: const TextStyle(color: _textMain, fontSize: 14),
                              decoration: _inputDeco(
                                hint: 'usuario@empresa.com',
                                icon: Icons.email_outlined,
                              ),
                              validator: (v) {
                                if (v == null || v.trim().isEmpty) return 'Ingresa tu correo';
                                if (!v.contains('@')) return 'Correo inválido';
                                return null;
                              },
                            ),
                            const SizedBox(height: 16),

                            // Contraseña
                            _FieldLabel(label: 'Contraseña'),
                            const SizedBox(height: 6),
                            TextFormField(
                              controller: _passwordCtrl,
                              obscureText: _obscurePassword,
                              style: const TextStyle(color: _textMain, fontSize: 14),
                              decoration: _inputDeco(
                                hint: '••••••••',
                                icon: Icons.lock_outlined,
                              ).copyWith(
                                suffixIcon: IconButton(
                                  icon: Icon(
                                    _obscurePassword
                                        ? Icons.visibility_off_outlined
                                        : Icons.visibility_outlined,
                                    color: _textSub,
                                    size: 20,
                                  ),
                                  onPressed: () =>
                                      setState(() => _obscurePassword = !_obscurePassword),
                                ),
                              ),
                              validator: (v) {
                                if (v == null || v.isEmpty) return 'Ingresa tu contraseña';
                                if (v.length < 6) return 'Mínimo 6 caracteres';
                                return null;
                              },
                            ),
                            const SizedBox(height: 24),

                            // Botón Iniciar Sesión
                            SizedBox(
                              width: double.infinity,
                              child: FilledButton(
                                onPressed: _isLoading ? null : _login,
                                style: FilledButton.styleFrom(
                                  backgroundColor: _primary,
                                  foregroundColor: Colors.white,
                                  padding: const EdgeInsets.symmetric(vertical: 15),
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(12),
                                  ),
                                  textStyle: GoogleFonts.inter(
                                    fontWeight: FontWeight.w600,
                                    fontSize: 15,
                                  ),
                                ),
                                child: _isLoading
                                    ? const SizedBox(
                                        width: 20,
                                        height: 20,
                                        child: CircularProgressIndicator(
                                          color: Colors.white,
                                          strokeWidth: 2,
                                        ),
                                      )
                                    : const Text('Iniciar Sesión'),
                              ),
                            ),

                            // Sección biométrica
                            if (_bioDisponible) ...[
                              const SizedBox(height: 20),
                              Row(
                                children: [
                                  const Expanded(
                                    child: Divider(color: Color(0xFFE2E7FF), thickness: 1),
                                  ),
                                  Padding(
                                    padding: const EdgeInsets.symmetric(horizontal: 12),
                                    child: Text(
                                      'o continúa con',
                                      style: GoogleFonts.inter(
                                        fontSize: 12,
                                        color: _textSub,
                                      ),
                                    ),
                                  ),
                                  const Expanded(
                                    child: Divider(color: Color(0xFFE2E7FF), thickness: 1),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 16),

                              // Botón Huella
                              SizedBox(
                                width: double.infinity,
                                child: OutlinedButton(
                                  onPressed: (_isBioLoading || !_bioHabilitado)
                                      ? null
                                      : _loginBiometrico,
                                  style: OutlinedButton.styleFrom(
                                    side: BorderSide(
                                      color: _bioHabilitado
                                          ? _primary
                                          : const Color(0xFFCDD0E3),
                                      width: 1.5,
                                    ),
                                    padding: const EdgeInsets.symmetric(vertical: 13),
                                    shape: RoundedRectangleBorder(
                                      borderRadius: BorderRadius.circular(12),
                                    ),
                                    foregroundColor: _primary,
                                  ),
                                  child: _isBioLoading
                                      ? const SizedBox(
                                          width: 20,
                                          height: 20,
                                          child: CircularProgressIndicator(
                                            color: _primary,
                                            strokeWidth: 2,
                                          ),
                                        )
                                      : Row(
                                          mainAxisAlignment: MainAxisAlignment.center,
                                          children: [
                                            Icon(
                                              Icons.fingerprint,
                                              size: 22,
                                              color: _bioHabilitado
                                                  ? _primary
                                                  : const Color(0xFFCDD0E3),
                                            ),
                                            const SizedBox(width: 8),
                                            Text(
                                              _bioHabilitado
                                                  ? 'Ingresar con huella dactilar'
                                                  : 'Huella no configurada',
                                              style: GoogleFonts.inter(
                                                fontWeight: FontWeight.w600,
                                                fontSize: 14,
                                                color: _bioHabilitado
                                                    ? _primary
                                                    : const Color(0xFFCDD0E3),
                                              ),
                                            ),
                                          ],
                                        ),
                                ),
                              ),

                              if (_bioHabilitado && _emailGuardado != null) ...[
                                const SizedBox(height: 8),
                                Center(
                                  child: Text(
                                    'Cuenta: $_emailGuardado',
                                    style: GoogleFonts.inter(
                                      fontSize: 11,
                                      color: _textSub,
                                    ),
                                  ),
                                ),
                              ],

                              if (!_bioHabilitado) ...[
                                const SizedBox(height: 8),
                                Center(
                                  child: Text(
                                    'Inicia sesión una vez para habilitar la huella',
                                    style: GoogleFonts.inter(
                                      fontSize: 11,
                                      color: _textSub,
                                    ),
                                    textAlign: TextAlign.center,
                                  ),
                                ),
                              ],
                            ],

                            const SizedBox(height: 24),
                            Center(
                              child: Text(
                                'v1.0.0 · Fiscalización HSE',
                                style: GoogleFonts.inter(
                                  fontSize: 11,
                                  color: const Color(0xFFBEC2D4),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  InputDecoration _inputDeco({required String hint, required IconData icon}) {
    return InputDecoration(
      hintText: hint,
      hintStyle: TextStyle(color: _textSub.withValues(alpha: 0.7), fontSize: 14),
      prefixIcon: Icon(icon, color: _textSub, size: 20),
      filled: true,
      fillColor: const Color(0xFFF2F3FF),
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: _border),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: _border),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: _primary, width: 1.5),
      ),
      errorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: _error),
      ),
      focusedErrorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: _error, width: 1.5),
      ),
    );
  }
}

class _FieldLabel extends StatelessWidget {
  const _FieldLabel({required this.label});
  final String label;

  @override
  Widget build(BuildContext context) {
    return Text(
      label,
      style: GoogleFonts.inter(
        fontSize: 13,
        fontWeight: FontWeight.w600,
        color: const Color(0xFF131B2E),
      ),
    );
  }
}
