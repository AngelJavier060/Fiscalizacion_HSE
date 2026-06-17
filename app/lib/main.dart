import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'screens/splash_screen.dart';
import 'presentation/screens/landing_screen.dart';
import 'screens/login_screen.dart';
import 'screens/home_screen.dart';
import 'screens/documentos_screen.dart';
import 'screens/documento_detalle_screen.dart';
import 'screens/documento_lector_screen.dart';
import 'screens/documento_editor_screen.dart';
import 'screens/documento_pdf_screen.dart';
import 'screens/notificaciones_screen.dart';
import 'screens/ia_chat_screen.dart';
import 'screens/perfil_screen.dart';
import 'screens/permisos_screen.dart';
import 'services/auth_service.dart';
import 'services/permiso_offline_service.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'widgets/inactivity_watcher.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeDateFormatting('es');
  
  // Iniciar escucha de conectividad para sincronización automática
  _initConnectivityListener();
  
  final prefs = await SharedPreferences.getInstance();
  final token = prefs.getString('jwt_token');
  
  runApp(
    ProviderScope(
      child: FiscalizacionHSEApp(isLoggedIn: token != null),
    ),
  );
}

/// Escucha cambios de conectividad y sincroniza datos pendientes
/// automáticamente cuando se restablece la conexión a internet.
void _initConnectivityListener() {
  Connectivity().onConnectivityChanged.listen((List<ConnectivityResult> results) {
    final isOnline = results.any((c) => c != ConnectivityResult.none);
    if (isOnline) {
      debugPrint('🔄 Conectividad restablecida, sincronizando datos pendientes...');
      // Sincronizar permisos pendientes
      PermisoOfflineService.sincronizarPendientes();
    }
  });
  
  // Intentar sincronizar al inicio también (después de que el usuario haya iniciado sesión)
  Future.delayed(const Duration(seconds: 3), () {
    debugPrint('🔄 Verificando sincronización pendiente al iniciar app...');
    PermisoOfflineService.sincronizarPendientes();
  });
}

class FiscalizacionHSEApp extends StatelessWidget {
  final bool isLoggedIn;
  
  const FiscalizacionHSEApp({super.key, required this.isLoggedIn});

  @override
  Widget build(BuildContext context) {
    return InactivityWatcher(
      child: MaterialApp(
      navigatorKey: navigatorKey,
      scaffoldMessengerKey: scaffoldMessengerKey,
      title: 'Fiscalización HSE',
      debugShowCheckedModeBanner: false,
      locale: const Locale('es', 'ES'),
      supportedLocales: const [
        Locale('es', 'ES'),
        Locale('en', 'US'),
      ],
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      theme: ThemeData(
        brightness: Brightness.dark,
        primaryColor: const Color(0xFF059669), // Verde HSE
        scaffoldBackgroundColor: const Color(0xFF0F172A),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFF059669),
          secondary: Color(0xFF0284C7),
          tertiary: Color(0xFF7C3AED),
          surface: Color(0xFF1E293B),
        ),
        textTheme: GoogleFonts.interTextTheme(ThemeData.dark().textTheme),
        appBarTheme: const AppBarTheme(
          backgroundColor: Color(0xFF1E293B),
          elevation: 0,
        ),
        cardTheme: CardThemeData(
          color: const Color(0xFF1E293B),
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
            side: const BorderSide(color: Color(0xFF334155)),
          ),
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: const Color(0xFF0F172A),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(10),
            borderSide: const BorderSide(color: Color(0xFF334155)),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(10),
            borderSide: const BorderSide(color: Color(0xFF334155)),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(10),
            borderSide: const BorderSide(color: Color(0xFF059669), width: 2),
          ),
        ),
        elevatedButtonTheme: ElevatedButtonThemeData(
          style: ElevatedButton.styleFrom(
            backgroundColor: const Color(0xFF059669),
            foregroundColor: Colors.white,
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(10),
            ),
          ),
        ),
      ),
      initialRoute: '/splash',
      routes: {
        '/splash': (context) => const SplashScreen(),
        '/landing': (context) => const LandingScreen(),
        '/login': (context) => const LoginScreen(),
        '/home': (context) => const HomeScreen(),
        '/documentos': (context) => const DocumentosScreen(),
        '/notificaciones': (context) => const NotificacionesScreen(),
        '/ia-chat': (context) => const IaChatScreen(),
        '/perfil': (context) => const PerfilScreen(),
    '/permisos': (context) => const PermisosScreen(),
      },
      onGenerateRoute: (settings) {
        if (settings.name == '/documento-detalle') {
          final args = settings.arguments as Map<String, dynamic>;
          return MaterialPageRoute(
            builder: (context) => DocumentoDetalleScreen(
              documentoId: args['id'] as int,
              titulo: args['titulo'] as String,
            ),
          );
        }
        if (settings.name == '/documento-lector') {
          final args = settings.arguments as Map<String, dynamic>;
          return MaterialPageRoute(
            builder: (context) => DocumentoLectorScreen(
              documentoId: args['id'] as int,
              titulo: args['titulo'] as String,
              abrirEnEdicion: args['editar'] == true,
            ),
          );
        }
        if (settings.name == '/documento-pdf') {
          final args = settings.arguments as Map<String, dynamic>;
          return MaterialPageRoute(
            builder: (context) => DocumentoPdfScreen(
              documentoId: args['id'] as int,
              titulo: args['titulo'] as String,
            ),
          );
        }
        if (settings.name == '/documento-editor') {
          final args = settings.arguments as Map<String, dynamic>;
          return MaterialPageRoute(
            builder: (context) => DocumentoEditorScreen(
              documentoId: args['id'] as int,
              titulo: args['titulo'] as String,
            ),
          );
        }
        return null;
      },
      ),
    );
  }
}

// Providers globales
final authProvider = ChangeNotifierProvider<AuthProvider>((ref) {
  return AuthProvider();
});

class AuthProvider extends ChangeNotifier {
  final AuthService _authService = AuthService();
  Map<String, dynamic>? _userData;
  String? _token;
  bool _isLoading = false;

  Map<String, dynamic>? get userData => _userData;
  String? get token => _token;
  bool get isLoading => _isLoading;
  bool get isLoggedIn => _token != null;

  Future<bool> login(String email, String password) async {
    _isLoading = true;
    notifyListeners();

    try {
      final response = await _authService.login(email, password);
      _token = response['token'];
      _userData = response['usuario'];
      
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('jwt_token', _token!);
      await prefs.setString('user_data', _userData.toString());
      
      _isLoading = false;
      notifyListeners();
      return true;
    } catch (e) {
      _isLoading = false;
      notifyListeners();
      return false;
    }
  }

  Future<void> logout() async {
    _token = null;
    _userData = null;
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('jwt_token');
    await prefs.remove('user_data');
    notifyListeners();
  }
}
