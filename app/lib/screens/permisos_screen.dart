import 'package:flutter/material.dart';
import '../models/permit_model.dart';
import '../services/permiso_service.dart';
import '../services/auth_service.dart';
import 'nuevo_permiso_screen.dart';
import 'permit_card_screen.dart';

// ── Colores del tema (MD3 adaptado del HTML) ────────────────────────
class _Pal {
  static const primary = Color(0xFF003398);
  static const onPrimary = Color(0xFFFFFFFF);
  static const surface = Color(0xFFF9F9F9);
  static const surfaceLowest = Color(0xFFFFFFFF);
  static const onSurface = Color(0xFF1A1C1C);
  static const onSurfaceVariant = Color(0xFF434654);
  static const outline = Color(0xFF747686);
  static const outlineVariant = Color(0xFFC3C5D7);
  static const error = Color(0xFFBA1A1A);
  static const errorContainer = Color(0xFFFFDAD6);
  static const warningText = Color(0xFFA67C00);
  static const warningBg = Color(0xFFFFF8E1);
  static const greenText = Color(0xFF3B6D11);
  static const greenBg = Color(0xFFEAF3DE);
}

class PermisosScreen extends StatefulWidget {
  const PermisosScreen({super.key});

  @override
  State<PermisosScreen> createState() => _PermisosScreenState();
}

class _PermisosScreenState extends State<PermisosScreen> {
    List<PermitModel> _permisos = [];
  bool _isLoading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadUserAndPermisos();
  }

  Future<void> _loadUserAndPermisos() async {
    try {
      final userData = await AuthService().getUserData();
      final empresaId = (userData['empresaId'] as num?)?.toInt() ?? 0;
      final permisos = await PermisoService.listar(empresaId);
      if (mounted) {
        setState(() {
          _permisos = permisos;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        // Fallback a datos demo si no hay conexión
        setState(() {
          _permisos = PermitModel.demoPermits();
          _isLoading = false;
          _error = 'Sin conexión al servidor. Mostrando datos locales.';
        });
      }
    }
  }

  void _abrirPermiso(PermitModel p) {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => PermitCardScreen(permit: p)),
    );
  }

  void _nuevoPermiso() {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const NuevoPermisoScreen()),
    ).then((result) {
      if (result != null && result is PermitModel) {
        setState(() => _permisos.insert(0, result));
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: _Pal.surface,
      // ── TopAppBar ────────────────────────────────────────────
      appBar: PreferredSize(
        preferredSize: const Size.fromHeight(64),
        child: Container(
          color: _Pal.surfaceLowest,
          child: SafeArea(
            bottom: false,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Row(
                children: [
                  // Botón menú
                  Material(
                    color: Colors.transparent,
                    child: InkWell(
                      borderRadius: BorderRadius.circular(20),
                      onTap: () {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('Menú de navegación'),
                            behavior: SnackBarBehavior.floating,
                          ),
                        );
                      },
                      child: const Padding(
                        padding: EdgeInsets.all(8),
                        child: Icon(Icons.menu, color: _Pal.primary, size: 24),
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  // Título
                  const Expanded(
                    child: Text(
                      'Permisos de trabajo',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w600,
                        color: _Pal.primary,
                      ),
                    ),
                  ),
                  // Botón búsqueda
                  Material(
                    color: Colors.transparent,
                    child: InkWell(
                      borderRadius: BorderRadius.circular(20),
                      onTap: () {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('Búsqueda de permisos'),
                            behavior: SnackBarBehavior.floating,
                          ),
                        );
                      },
                      child: const Padding(
                        padding: EdgeInsets.all(8),
                        child: Icon(Icons.search, color: _Pal.primary, size: 24),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
      // ── Cuerpo ──────────────────────────────────────────────
      body: _isLoading
          ? const Center(child: CircularProgressIndicator(color: _Pal.primary))
          : RefreshIndicator(
              onRefresh: _loadUserAndPermisos,
              color: _Pal.primary,
              child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 100),
        children: [
          // Mensaje de error en modo offline
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: _Pal.warningBg,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: _Pal.warningText.withValues(alpha: 0.3)),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.cloud_off, size: 18, color: _Pal.warningText),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        _error!,
                        style: const TextStyle(fontSize: 12, color: _Pal.warningText),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          // Botón Nuevo Permiso (prominente)
          Material(
            color: _Pal.primary,
            borderRadius: BorderRadius.circular(12),
            child: InkWell(
              onTap: _nuevoPermiso,
              borderRadius: BorderRadius.circular(12),
              child: Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(vertical: 16),
                child: const Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.add, color: _Pal.onPrimary, size: 20),
                    SizedBox(width: 8),
                    Text(
                      'NUEVO PERMISO',
                      style: TextStyle(
                        color: _Pal.onPrimary,
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                        letterSpacing: 0.5,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
          const SizedBox(height: 24),
          // Lista vacía
          if (_permisos.isEmpty && !_isLoading)
            Container(
              padding: const EdgeInsets.all(40),
              child: const Column(
                children: [
                  Icon(Icons.event_available_outlined, size: 64, color: _Pal.outline),
                  SizedBox(height: 16),
                  Text(
                    'No hay permisos registrados',
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: _Pal.onSurface),
                  ),
                  SizedBox(height: 8),
                  Text(
                    'Crea tu primer permiso de trabajo presionando el botón superior.',
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 14, color: _Pal.onSurfaceVariant),
                  ),
                ],
              ),
            ),
          // Lista de permisos
          ..._permisos.map(
            (p) => _PermisoCard(permit: p, onTap: () => _abrirPermiso(p)),
          ),
        ],
      ),
    ),
      // ── BottomNavBar ─────────────────────────────────────────
      bottomNavigationBar: Container(
        decoration: const BoxDecoration(
          color: _Pal.surfaceLowest,
          border: Border(top: BorderSide(color: _Pal.outlineVariant)),
        ),
        child: SafeArea(
          top: false,
          child: SizedBox(
            height: 64,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _navItem(Icons.home_outlined, 'Inicio', active: false),
                _navItem(Icons.assignment, 'Permisos', active: true),
                _navItem(Icons.qr_code_scanner_outlined, 'Escaneo', active: false),
                _navItem(Icons.person_outline, 'Perfil', active: false),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _navItem(IconData icon, String label, {required bool active}) {
    final color = active ? _Pal.primary : _Pal.onSurfaceVariant;
    final weight = active ? FontWeight.w700 : FontWeight.w500;
    return Expanded(
      child: InkWell(
        onTap: () {
          if (!active) {
            if (label == 'Inicio') {
              Navigator.pushReplacementNamed(context, '/home');
            } else if (label == 'Perfil') {
              Navigator.pushNamed(context, '/perfil');
            } else {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text('"$label" próximamente'),
                  behavior: SnackBarBehavior.floating,
                ),
              );
            }
          }
        },
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, color: color, size: 24),
            const SizedBox(height: 4),
            Text(
              label,
              style: TextStyle(
                color: color,
                fontSize: 12,
                fontWeight: weight,
                letterSpacing: 0.5,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ── Tarjeta individual de permiso ────────────────────────────────────
class _PermisoCard extends StatelessWidget {
  final PermitModel permit;
  final VoidCallback onTap;

  const _PermisoCard({required this.permit, required this.onTap});

  Color get _statusBarColor {
    return switch (permit.status) {
      PermitStatus.active => _Pal.greenText,
      PermitStatus.warning => _Pal.warningText,
      PermitStatus.expired => _Pal.error,
    };
  }

  Color get _statusBg {
    return switch (permit.status) {
      PermitStatus.active => _Pal.greenBg,
      PermitStatus.warning => _Pal.warningBg,
      PermitStatus.expired => _Pal.errorContainer,
    };
  }

  Color get _statusTextColor {
    return switch (permit.status) {
      PermitStatus.active => _Pal.greenText,
      PermitStatus.warning => _Pal.warningText,
      PermitStatus.expired => _Pal.error,
    };
  }

  String get _statusLabel {
    return switch (permit.status) {
      PermitStatus.active => 'Vigente',
      PermitStatus.warning => 'Por vencer',
      PermitStatus.expired => 'Expirado',
    };
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Material(
        color: _Pal.surfaceLowest,
        borderRadius: BorderRadius.circular(12),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(12),
          child: Container(
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: _Pal.outlineVariant.withValues(alpha: 0.3)),
            ),
            child: IntrinsicHeight(
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // Status bar lateral
                  Container(
                    width: 6,
                    decoration: BoxDecoration(
                      color: _statusBarColor,
                      borderRadius: const BorderRadius.only(
                        topLeft: Radius.circular(11),
                        bottomLeft: Radius.circular(11),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  // Contenido
                  Expanded(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 4),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          // ID del permiso
                          Padding(
                            padding: const EdgeInsets.only(bottom: 4),
                            child: Text(
                              permit.id,
                              style: const TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.w500,
                                letterSpacing: 0.5,
                                color: _Pal.onSurfaceVariant,
                              ),
                            ),
                          ),
                          // Título
                          Text(
                            permit.title,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.w600,
                              color: _Pal.onSurface,
                            ),
                          ),
                          const SizedBox(height: 8),
                          // Ubicación
                          Row(
                            children: [
                              Icon(
                                Icons.location_on_outlined,
                                size: 16,
                                color: _Pal.onSurfaceVariant.withValues(alpha: 0.8),
                              ),
                              const SizedBox(width: 4),
                              Text(
                                permit.area,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(
                                  fontSize: 12,
                                  color: _Pal.onSurfaceVariant.withValues(alpha: 0.8),
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ),
                  // Badge + chevron
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 8),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        // Badge de estado
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 4,
                          ),
                          decoration: BoxDecoration(
                            color: _statusBg,
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Text(
                            _statusLabel.toUpperCase(),
                            style: TextStyle(
                              fontSize: 10,
                              fontWeight: FontWeight.bold,
                              color: _statusTextColor,
                            ),
                          ),
                        ),
                        const Icon(
                          Icons.chevron_right,
                          color: _Pal.outline,
                          size: 20,
                        ),
                      ],
                    ),
                  ),
              ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
