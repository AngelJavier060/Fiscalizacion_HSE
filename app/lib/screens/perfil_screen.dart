import 'package:flutter/material.dart';
import '../models/user_model.dart';
import '../services/auth_service.dart';

/// Paleta clara MD3 (consistente con el resto de la app).
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
  static const error = Color(0xFFBA1A1A);
}

class PerfilScreen extends StatefulWidget {
  const PerfilScreen({super.key});

  @override
  State<PerfilScreen> createState() => _PerfilScreenState();
}

class _PerfilScreenState extends State<PerfilScreen> {
  UserModel? _user;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadUserData();
  }

  Future<void> _loadUserData() async {
    try {
      final authService = AuthService();
      final userData = await authService.getUserData();
      if (mounted) {
        setState(() {
          _user = UserModel.fromJson(userData);
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _logout() async {
    final authService = AuthService();
    await authService.logout();
    if (mounted) {
      Navigator.pushNamedAndRemoveUntil(context, '/login', (route) => false);
    }
  }

  Future<void> _confirmarLogout() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: _Pal.surface,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const Text(
          'Cerrar Sesión',
          style: TextStyle(color: _Pal.onSurface, fontWeight: FontWeight.w700),
        ),
        content: const Text(
          '¿Estás seguro de cerrar sesión?',
          style: TextStyle(color: _Pal.onSurfaceVar),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancelar', style: TextStyle(color: _Pal.onSurfaceVar)),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Cerrar Sesión', style: TextStyle(color: _Pal.error)),
          ),
        ],
      ),
    );
    if (ok == true) await _logout();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: _Pal.bg,
      appBar: AppBar(
        backgroundColor: _Pal.surface,
        foregroundColor: _Pal.onSurface,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        shape: const Border(
          bottom: BorderSide(color: _Pal.high, width: 1),
        ),
        iconTheme: const IconThemeData(color: _Pal.onSurfaceVar),
        title: const Text(
          'Mi Perfil',
          style: TextStyle(
            color: _Pal.onSurface,
            fontWeight: FontWeight.w700,
            fontSize: 16,
          ),
        ),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator(color: _Pal.primary))
          : _user == null
              ? const Center(
                  child: Text(
                    'Error al cargar perfil',
                    style: TextStyle(color: _Pal.error),
                  ),
                )
              : ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    // Avatar
                    Center(
                      child: Column(
                        children: [
                          Container(
                            width: 80,
                            height: 80,
                            decoration: BoxDecoration(
                              gradient: const LinearGradient(
                                colors: [_Pal.primary, Color(0xFF0052FF)],
                              ),
                              borderRadius: BorderRadius.circular(20),
                              boxShadow: [
                                BoxShadow(
                                  color: _Pal.primary.withValues(alpha: 0.25),
                                  blurRadius: 12,
                                  offset: const Offset(0, 4),
                                ),
                              ],
                            ),
                            child: Center(
                              child: Text(
                                _user!.iniciales,
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 28,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ),
                          ),
                          const SizedBox(height: 16),
                          Text(
                            _user!.nombre,
                            style: const TextStyle(
                              color: _Pal.onSurface,
                              fontSize: 20,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const SizedBox(height: 8),
                          Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 12, vertical: 4),
                            decoration: BoxDecoration(
                              color: _user!.isAdmin
                                  ? const Color(0xFFFFF7ED)
                                  : _Pal.low,
                              borderRadius: BorderRadius.circular(99),
                              border: Border.all(
                                color: _user!.isAdmin
                                    ? const Color(0xFFFDBA74)
                                    : _Pal.border,
                              ),
                            ),
                            child: Text(
                              _user!.rol.replaceAll('_', ' '),
                              style: TextStyle(
                                color: _user!.isAdmin
                                    ? const Color(0xFFB45309)
                                    : _Pal.secondary,
                                fontSize: 12,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 28),

                    _SectionCard(
                      title: 'Información Personal',
                      children: [
                        _InfoRow(
                          icon: Icons.email_outlined,
                          label: 'Correo',
                          value: _user!.email,
                        ),
                        _InfoRow(
                          icon: Icons.business_outlined,
                          label: 'Empresa',
                          value: _user!.empresaNombre,
                        ),
                        _InfoRow(
                          icon: Icons.badge_outlined,
                          label: 'Rol',
                          value: _user!.rol,
                        ),
                        _InfoRow(
                          icon: Icons.verified_outlined,
                          label: 'Estado',
                          value: _user!.activo ? 'Activo' : 'Inactivo',
                          valueColor:
                              _user!.activo ? _Pal.secondary : _Pal.error,
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),

                    _SectionCard(
                      title: 'Información de la App',
                      children: [
                        const _InfoRow(
                          icon: Icons.info_outline,
                          label: 'Versión',
                          value: '1.0.0',
                        ),
                        const _InfoRow(
                          icon: Icons.update_outlined,
                          label: 'Última actualización',
                          value: 'Febrero 2025',
                        ),
                        const _InfoRow(
                          icon: Icons.cloud_outlined,
                          label: 'Estado',
                          value: 'Conectado',
                          valueColor: _Pal.secondary,
                        ),
                      ],
                    ),
                    const SizedBox(height: 28),

                    SizedBox(
                      width: double.infinity,
                      child: OutlinedButton.icon(
                        icon: const Icon(Icons.logout, color: _Pal.error),
                        label: const Text(
                          'Cerrar Sesión',
                          style: TextStyle(color: _Pal.error),
                        ),
                        style: OutlinedButton.styleFrom(
                          side: BorderSide(
                              color: _Pal.error.withValues(alpha: 0.35)),
                          backgroundColor: _Pal.surface,
                          padding: const EdgeInsets.symmetric(vertical: 14),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                        ),
                        onPressed: _confirmarLogout,
                      ),
                    ),
                    const SizedBox(height: 16),
                    const Center(
                      child: Text(
                        'Fiscalización HSE © 2025',
                        style: TextStyle(color: _Pal.onSurfaceVar, fontSize: 12),
                      ),
                    ),
                  ],
                ),
    );
  }
}

class _SectionCard extends StatelessWidget {
  final String title;
  final List<Widget> children;

  const _SectionCard({required this.title, required this.children});

  @override
  Widget build(BuildContext context) {
    return Card(
      color: _Pal.surface,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: const BorderSide(color: _Pal.border),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style: const TextStyle(
                color: _Pal.onSurface,
                fontSize: 15,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 16),
            ...children,
          ],
        ),
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final Color? valueColor;

  const _InfoRow({
    required this.icon,
    required this.label,
    required this.value,
    this.valueColor,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        children: [
          Icon(icon, size: 20, color: _Pal.onSurfaceVar),
          const SizedBox(width: 12),
          Text(
            label,
            style: const TextStyle(color: _Pal.onSurfaceVar, fontSize: 13),
          ),
          const Spacer(),
          Flexible(
            child: Text(
              value,
              textAlign: TextAlign.right,
              style: TextStyle(
                color: valueColor ?? _Pal.onSurface,
                fontSize: 13,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
