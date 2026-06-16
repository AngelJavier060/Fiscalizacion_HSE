import 'dart:async';

import 'package:flutter/material.dart';

import '../services/auth_service.dart';

/// Llave global de navegación para poder cerrar sesión sin un BuildContext.
final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();

/// Llave global para mostrar avisos (SnackBar) sin un BuildContext.
final GlobalKey<ScaffoldMessengerState> scaffoldMessengerKey =
    GlobalKey<ScaffoldMessengerState>();

/// Envuelve la app para detectar inactividad. Por defecto el tiempo es muy alto
/// (24h) para no cerrar sesión automáticamente. El usuario cierra sesión
/// manualmente desde el botón de salida en el menú.
class InactivityWatcher extends StatefulWidget {
  final Widget child;
  final Duration limite;

  const InactivityWatcher({
    super.key,
    required this.child,
    this.limite = const Duration(hours: 24),
  });

  @override
  State<InactivityWatcher> createState() => _InactivityWatcherState();
}

class _InactivityWatcherState extends State<InactivityWatcher>
    with WidgetsBindingObserver {
  Timer? _timer;
  bool _cerrando = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _reiniciar();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _timer?.cancel();
    super.dispose();
  }

  void _reiniciar() {
    _timer?.cancel();
    _timer = Timer(widget.limite, _cerrarPorInactividad);
  }

  void _actividad([_]) => _reiniciar();

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Al volver a primer plano se reinicia el conteo.
    if (state == AppLifecycleState.resumed) _reiniciar();
  }

  Future<void> _cerrarPorInactividad() async {
    if (_cerrando) return;

    // Solo cierra si hay sesión activa (evita actuar en login/landing/splash).
    final token = await AuthService.getToken();
    if (token == null) {
      _reiniciar();
      return;
    }

    _cerrando = true;
    try {
      await AuthService().logout();
    } catch (_) {}

    final nav = navigatorKey.currentState;
    if (nav != null) {
      nav.pushNamedAndRemoveUntil('/login', (route) => false);
      scaffoldMessengerKey.currentState?.showSnackBar(
        const SnackBar(content: Text('Sesión cerrada por inactividad')),
      );
    }

    _cerrando = false;
    _reiniciar();
  }

  @override
  Widget build(BuildContext context) {
    return Listener(
      behavior: HitTestBehavior.translucent,
      onPointerDown: _actividad,
      onPointerMove: _actividad,
      onPointerHover: _actividad,
      onPointerSignal: _actividad,
      child: widget.child,
    );
  }
}
