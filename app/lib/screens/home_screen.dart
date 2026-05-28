import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../services/auth_service.dart';
import '../services/notificacion_service.dart';
import '../models/user_model.dart';

/// Paleta clara MD3 (consistente con FISCALIZA-AI y documentos).
class _Pal {
  static const bg = Color(0xFFFAF8FF);
  static const surface = Color(0xFFFFFFFF);
  static const low = Color(0xFFF2F3FF);
  static const high = Color(0xFFE2E7FF);
  static const border = Color(0xFFC3C5D9);
  static const primary = Color(0xFF003EC7);
  static const onSurface = Color(0xFF131B2E);
  static const onSurfaceVar = Color(0xFF434656);
  static const secondary = Color(0xFF006B5B);
}

/// Datos de una tarjeta de acceso rápido
class _Acceso {
  final IconData icon;
  final String label;
  final Color iconBg;
  final Color iconColor;
  final String ruta;
  final bool conBadge;

  const _Acceso({
    required this.icon,
    required this.label,
    required this.iconBg,
    required this.iconColor,
    required this.ruta,
    this.conBadge = false,
  });
}

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  UserModel? _user;
  int _notificacionesNoLeidas = 0;
  bool _isLoading = true;

  /// Rutas ya implementadas en main.dart
  static const Set<String> _rutasDisponibles = {
    '/documentos',
    '/notificaciones',
    '/ia-chat',
    '/perfil',
  };

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final token = prefs.getString('jwt_token');

      if (token == null || token.isEmpty) {
        if (mounted) Navigator.pushReplacementNamed(context, '/login');
        return;
      }

      final authService = AuthService();
      final userData = await authService.getUserData();

      if (mounted) {
        setState(() {
          _user = UserModel.fromJson(userData);
          _isLoading = false;
        });
      }

      authService.refreshUserData().then((fresh) {
        if (fresh != null && mounted) {
          setState(() => _user = UserModel.fromJson(fresh));
        }
      });

      _loadNotificaciones();
    } on AuthException {
      if (mounted) Navigator.pushReplacementNamed(context, '/login');
    } catch (e) {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _loadNotificaciones() async {
    try {
      final count = await NotificacionService.contarNoLeidas();
      if (mounted) setState(() => _notificacionesNoLeidas = count);
    } catch (_) {}
  }

  Future<void> _logout() async {
    await AuthService().logout();
    if (mounted) Navigator.pushReplacementNamed(context, '/login');
  }

  /// Navega si la ruta existe; si no, avisa "Próximamente"
  void _abrir(String ruta, String nombre) {
    if (_rutasDisponibles.contains(ruta)) {
      Navigator.pushNamed(context, ruta);
    } else {
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          SnackBar(
            content: Text(
              '"$nombre" estará disponible próximamente',
              style: const TextStyle(color: _Pal.onSurface),
            ),
            behavior: SnackBarBehavior.floating,
            backgroundColor: _Pal.surface,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
              side: const BorderSide(color: _Pal.border),
            ),
            duration: const Duration(seconds: 2),
          ),
        );
    }
  }

  List<_Acceso> get _accesos => const [
        _Acceso(
          icon: Icons.description_outlined,
          label: 'Documentos',
          iconBg: Color(0xFFECFDF5),
          iconColor: Color(0xFF059669),
          ruta: '/documentos',
        ),
        _Acceso(
          icon: Icons.notifications_outlined,
          label: 'Notificaciones',
          iconBg: Color(0xFFEFF6FF),
          iconColor: Color(0xFF2563EB),
          ruta: '/notificaciones',
          conBadge: true,
        ),
        _Acceso(
          icon: Icons.chat_bubble_outline,
          label: 'FISCALIZA-AI',
          iconBg: Color(0xFFFAF5FF),
          iconColor: Color(0xFF9333EA),
          ruta: '/ia-chat',
        ),
        _Acceso(
          icon: Icons.person_outline,
          label: 'Mi Perfil',
          iconBg: Color(0xFFFFF7ED),
          iconColor: Color(0xFFF97316),
          ruta: '/perfil',
        ),
        _Acceso(
          icon: Icons.group_outlined,
          label: 'Usuarios',
          iconBg: Color(0xFFEFF6FF),
          iconColor: Color(0xFF2563EB),
          ruta: '/usuarios',
        ),
        _Acceso(
          icon: Icons.business_outlined,
          label: 'Empresas',
          iconBg: Color(0xFFECFDF5),
          iconColor: Color(0xFF059669),
          ruta: '/empresas',
        ),
        _Acceso(
          icon: Icons.event_available_outlined,
          label: 'Actividades diarias',
          iconBg: Color(0xFFEEF2FF),
          iconColor: Color(0xFF4F46E5),
          ruta: '/actividades',
        ),
        _Acceso(
          icon: Icons.verified_user_outlined,
          label: 'Controles críticos',
          iconBg: Color(0xFFFFF1F2),
          iconColor: Color(0xFFE11D48),
          ruta: '/controles',
        ),
        _Acceso(
          icon: Icons.lock_outline,
          label: 'Permiso',
          iconBg: Color(0xFFFFFBEB),
          iconColor: Color(0xFFD97706),
          ruta: '/permisos',
        ),
        _Acceso(
          icon: Icons.menu_book_outlined,
          label: 'Conocimientos',
          iconBg: Color(0xFFECFEFF),
          iconColor: Color(0xFF0891B2),
          ruta: '/conocimientos',
        ),
        _Acceso(
          icon: Icons.star_outline,
          label: 'Puntos clave',
          iconBg: Color(0xFFFEFCE8),
          iconColor: Color(0xFFCA8A04),
          ruta: '/puntos-clave',
        ),
      ];

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(
        backgroundColor: _Pal.bg,
        body: Center(child: CircularProgressIndicator(color: _Pal.primary)),
      );
    }

    return Scaffold(
      backgroundColor: _Pal.bg,
      appBar: _buildHeader(),
      bottomNavigationBar: _buildBottomNav(),
      body: RefreshIndicator(
        onRefresh: _loadData,
        color: _Pal.primary,
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 24, 16, 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // ── Saludo ─────────────────────────────────
              Text(
                '¡Hola, ${_user?.nombre.split(' ').first ?? 'Usuario'}!',
                style: const TextStyle(
                  color: _Pal.onSurface,
                  fontSize: 28,
                  fontWeight: FontWeight.bold,
                  letterSpacing: -0.5,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                _user?.empresaNombre ?? 'Bienvenido a tu panel de control',
                style: const TextStyle(color: _Pal.onSurfaceVar, fontSize: 14),
              ),
              const SizedBox(height: 28),

              // ── Acceso Rápido ──────────────────────────
              Row(
                children: const [
                  Icon(Icons.bolt, color: _Pal.primary, size: 20),
                  SizedBox(width: 6),
                  Text(
                    'Acceso Rápido',
                    style: TextStyle(
                      color: _Pal.onSurface,
                      fontSize: 18,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              GridView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: _accesos.length,
                gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: 2,
                  mainAxisSpacing: 14,
                  crossAxisSpacing: 14,
                  childAspectRatio: 1.15,
                ),
                itemBuilder: (context, i) {
                  final a = _accesos[i];
                  return _AccesoCard(
                    acceso: a,
                    badge: a.conBadge && _notificacionesNoLeidas > 0
                        ? '$_notificacionesNoLeidas'
                        : null,
                    onTap: () => _abrir(a.ruta, a.label),
                  );
                },
              ),
              const SizedBox(height: 32),

              // ── Últimos Documentos ─────────────────────
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: const [
                      Icon(Icons.schedule, color: _Pal.primary, size: 20),
                      SizedBox(width: 6),
                      Text(
                        'Últimos Documentos',
                        style: TextStyle(
                          color: _Pal.onSurface,
                          fontSize: 18,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                  TextButton(
                    onPressed: () => _abrir('/documentos', 'Documentos'),
                    style: TextButton.styleFrom(
                      foregroundColor: _Pal.primary,
                      padding: EdgeInsets.zero,
                      minimumSize: const Size(0, 0),
                      tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                    ),
                    child: const Text(
                      'Ver todos',
                      style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              _buildDocsEmpty(),
              const SizedBox(height: 24),

              Center(
                child: Text(
                  'Fiscalización HSE v1.0.0',
                  style: TextStyle(color: _Pal.onSurfaceVar.withOpacity(0.6), fontSize: 12),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ── Header ───────────────────────────────────────────
  PreferredSizeWidget _buildHeader() {
    return AppBar(
      backgroundColor: _Pal.surface,
      surfaceTintColor: _Pal.surface,
      elevation: 0,
      scrolledUnderElevation: 0,
      systemOverlayStyle: SystemUiOverlayStyle.dark,
      shape: const Border(
        bottom: BorderSide(color: _Pal.high, width: 1),
      ),
      titleSpacing: 16,
      title: Row(
        children: [
          Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: _Pal.secondary,
              borderRadius: BorderRadius.circular(10),
              boxShadow: [
                BoxShadow(
                  color: _Pal.secondary.withOpacity(0.3),
                  blurRadius: 8,
                  offset: const Offset(0, 2),
                ),
              ],
            ),
            child: const Icon(Icons.verified_outlined, color: Colors.white, size: 22),
          ),
          const SizedBox(width: 12),
          const Text(
            'Fiscalización HSE',
            style: TextStyle(
              color: _Pal.onSurface,
              fontSize: 18,
              fontWeight: FontWeight.w600,
              letterSpacing: -0.3,
            ),
          ),
        ],
      ),
      actions: [
        // Notificaciones
        Stack(
          alignment: Alignment.center,
          children: [
            IconButton(
              icon: const Icon(Icons.notifications_none_rounded, color: _Pal.onSurfaceVar),
              onPressed: () => Navigator.pushNamed(context, '/notificaciones'),
            ),
            if (_notificacionesNoLeidas > 0)
              Positioned(
                right: 10,
                top: 10,
                child: Container(
                  width: 9,
                  height: 9,
                  decoration: BoxDecoration(
                    color: const Color(0xFFEF4444),
                    shape: BoxShape.circle,
                    border: Border.all(color: _Pal.surface, width: 1.5),
                  ),
                ),
              ),
          ],
        ),
        // Perfil
        PopupMenuButton<void>(
          icon: const Icon(Icons.account_circle_outlined, color: _Pal.onSurfaceVar),
          color: _Pal.surface,
          itemBuilder: (context) => <PopupMenuEntry<void>>[
            PopupMenuItem<void>(
              onTap: () => Navigator.pushNamed(context, '/perfil'),
              child: Row(
                children: [
                  const Icon(Icons.person, color: _Pal.onSurface),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Text('Mi Perfil', style: TextStyle(color: _Pal.onSurface)),
                        if (_user != null && _user!.email.isNotEmpty)
                          Text(
                            _user!.email,
                            style: const TextStyle(color: _Pal.onSurfaceVar, fontSize: 12),
                          ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const PopupMenuDivider(),
            PopupMenuItem<void>(
              onTap: _logout,
              child: const Row(
                children: [
                  Icon(Icons.logout, color: Color(0xFFEF4444)),
                  SizedBox(width: 12),
                  Text('Cerrar Sesión', style: TextStyle(color: Color(0xFFEF4444))),
                ],
              ),
            ),
          ],
        ),
        const SizedBox(width: 4),
      ],
    );
  }

  // ── Estado vacío de documentos ───────────────────────
  Widget _buildDocsEmpty() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(28),
      decoration: BoxDecoration(
        color: _Pal.surface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: _Pal.border),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.04),
            blurRadius: 6,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        children: [
          Container(
            width: 64,
            height: 64,
            decoration: BoxDecoration(
              color: _Pal.low,
              shape: BoxShape.circle,
            ),
            child: const Icon(Icons.description_outlined, size: 32, color: Color(0xFF9CA3AF)),
          ),
          const SizedBox(height: 16),
          const SizedBox(
            width: 250,
            child: Text(
              'Ve a la sección de documentos para ver los documentos normativos de tu empresa',
              textAlign: TextAlign.center,
              style: TextStyle(color: _Pal.onSurfaceVar, fontSize: 14, height: 1.5),
            ),
          ),
          const SizedBox(height: 20),
          ElevatedButton(
            onPressed: () => _abrir('/documentos', 'Documentos'),
            style: ElevatedButton.styleFrom(
              backgroundColor: _Pal.primary,
              foregroundColor: Colors.white,
              elevation: 2,
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(30)),
            ),
            child: const Text(
              'Ir a Documentos',
              style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
            ),
          ),
        ],
      ),
    );
  }

  // ── Barra inferior ───────────────────────────────────
  Widget _buildBottomNav() {
    return Container(
      decoration: BoxDecoration(
        color: _Pal.surface,
        border: const Border(top: BorderSide(color: _Pal.border)),
      ),
      child: SafeArea(
        top: false,
        child: SizedBox(
          height: 64,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              _navItem(Icons.home_rounded, 'Inicio', active: true, onTap: () {}),
              _navItem(Icons.description_outlined, 'Documentos',
                  onTap: () => _abrir('/documentos', 'Documentos')),
              _navItem(Icons.chat_bubble_outline, 'FISCALIZA-AI',
                  onTap: () => _abrir('/ia-chat', 'FISCALIZA-AI')),
              _navItem(Icons.person_outline, 'Perfil',
                  onTap: () => _abrir('/perfil', 'Perfil')),
            ],
          ),
        ),
      ),
    );
  }

  Widget _navItem(IconData icon, String label,
      {bool active = false, required VoidCallback onTap}) {
    final color = active ? _Pal.primary : _Pal.onSurfaceVar;
    return Expanded(
      child: InkWell(
        onTap: onTap,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, color: color, size: 24),
            const SizedBox(height: 4),
            Text(
              label,
              style: TextStyle(
                color: color,
                fontSize: 10,
                fontWeight: FontWeight.w500,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ── Tarjeta de acceso rápido ───────────────────────────
class _AccesoCard extends StatelessWidget {
  final _Acceso acceso;
  final String? badge;
  final VoidCallback onTap;

  const _AccesoCard({
    required this.acceso,
    required this.onTap,
    this.badge,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: _Pal.surface,
      borderRadius: BorderRadius.circular(18),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(18),
        child: Container(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(18),
            border: Border.all(color: _Pal.border),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.04),
                blurRadius: 6,
                offset: const Offset(0, 2),
              ),
            ],
          ),
          padding: const EdgeInsets.all(14),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Stack(
                clipBehavior: Clip.none,
                children: [
                  Container(
                    width: 54,
                    height: 54,
                    decoration: BoxDecoration(
                      color: acceso.iconBg,
                      shape: BoxShape.circle,
                    ),
                    child: Icon(acceso.icon, color: acceso.iconColor, size: 27),
                  ),
                  if (badge != null)
                    Positioned(
                      right: -2,
                      top: -2,
                      child: Container(
                        padding: const EdgeInsets.all(5),
                        constraints: const BoxConstraints(minWidth: 20),
                        decoration: BoxDecoration(
                          color: const Color(0xFFEF4444),
                          shape: BoxShape.circle,
                          border: Border.all(color: _Pal.surface, width: 1.5),
                        ),
                        child: Text(
                          badge!,
                          textAlign: TextAlign.center,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 9,
                            fontWeight: FontWeight.bold,
                            height: 1,
                          ),
                        ),
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 12),
              Text(
                acceso.label,
                textAlign: TextAlign.center,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: _Pal.onSurface,
                  fontSize: 13,
                  fontWeight: FontWeight.w500,
                  height: 1.2,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
