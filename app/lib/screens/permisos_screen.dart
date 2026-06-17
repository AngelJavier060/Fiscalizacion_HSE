import 'dart:io';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../models/permit_model.dart';
import '../services/permiso_service.dart';
import '../services/permiso_offline_service.dart';
import '../services/auth_service.dart';
import 'nuevo_permiso_screen.dart';
import 'permit_card_screen.dart';
import 'extension_screen.dart';
import 'document_scanner_screen.dart';

// ── Colores del diseño ──────────────────────────────────────────
class _Pal {
  static const brand = Color(0xFF154295);
  static const brandLight = Color(0xFFeef2ff);
  static const surface = Color(0xFFF8F9FB);
  static const card = Color(0xFFFFFFFF);
  static const textPrimary = Color(0xFF1E293B);
  static const textSecondary = Color(0xFF64748B);
  static const textLight = Color(0xFF94A3B8);

  static const greenBg = Color(0xFFDCFCE7);
  static const greenText = Color(0xFF166534);
  static const amberBg = Color(0xFFFEF3C7);
  static const amberText = Color(0xFF92400E);
  static const redBg = Color(0xFFFEE2E2);
  static const redText = Color(0xFF991B1B);
  static const warningText = Color(0xFFA67C00);
  static const blueBg = Color(0xFFE8F0FE);
  static const blueText = Color(0xFF1A73E8);
}

class PermisosScreen extends StatefulWidget {
  const PermisosScreen({super.key});
  @override State<PermisosScreen> createState() => _PermisosScreenState();
}

class _PermisosScreenState extends State<PermisosScreen> {
  List<PermitModel> _permisos = [];
  List<PermitModel> _filteredPermisos = [];
  bool _isLoading = true;
  String? _error;
  final _searchController = TextEditingController();
  bool _showSearch = false;

  @override void initState() { super.initState(); _searchController.addListener(_filtrar); _loadUserAndPermisos(); }
  @override void dispose() { _searchController.dispose(); super.dispose(); }

  void _filtrar() {
    final q = _searchController.text.trim().toLowerCase();
    setState(() => _filteredPermisos = q.isEmpty ? List.from(_permisos) : _permisos.where((p) => p.id.toLowerCase().contains(q) || p.title.toLowerCase().contains(q) || p.area.toLowerCase().contains(q)).toList());
  }

  Future<void> _loadUserAndPermisos() async {
    setState(() => _isLoading = true);
    try {
      final userData = await AuthService().getUserData();
      final empresaId = (userData['empresaId'] as num?)?.toInt() ?? 0;
      List<PermitModel> server = [];
      try { server = await PermisoService.listar(empresaId); } catch (_) {}
      final locales = await PermisoOfflineService.listarPermisos();
      final map = <String, PermitModel>{};
      for (final p in server) map[p.id] = p;
      for (final p in locales) map[p.id] = p;
      final combined = map.values.toList()..sort((a, b) => b.startDate.compareTo(a.startDate));
      if (mounted) setState(() { _permisos = combined; _filteredPermisos = List.from(combined); _isLoading = false; _error = server.isEmpty && locales.isNotEmpty ? 'Sin internet. Tus datos estan respaldados en la app local.' : server.isEmpty && locales.isEmpty ? 'Sin internet. Trabajando con datos locales.' : null; });
    } catch (e) {
      if (mounted) {
        final locales = await PermisoOfflineService.listarPermisos();
        setState(() { _permisos = locales.isNotEmpty ? locales : PermitModel.demoPermits(); _filteredPermisos = List.from(_permisos); _isLoading = false; _error = locales.isNotEmpty ? 'Sin internet. Tus datos estan respaldados en la app local.' : 'Sin internet. Mostrando datos de demostracion local.'; });
      }
    }
  }

  void _abrirPermiso(PermitModel p) async {
    final r = await Navigator.push(context, MaterialPageRoute(builder: (_) => PermitCardScreen(permit: p)));
    if (r != null && r is PermitModel) {
      final idx = _permisos.indexWhere((x) => x.id == r.id);
      if (idx >= 0) setState(() => _permisos[idx] = r); else setState(() => _permisos.insert(0, r));
      _filtrar();
    }
  }

  void _nuevoPermiso() async {
    final r = await Navigator.push(context, MaterialPageRoute(builder: (_) => const NuevoPermisoScreen()));
    if (r != null && r is PermitModel) {
      final dup = _permisos.any((x) => x.id == r.id);
      if (dup) {
        if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('El permiso \"\\" ya existe. Se actualizara.'), backgroundColor: _Pal.warningText, behavior: SnackBarBehavior.floating));
        final idx = _permisos.indexWhere((x) => x.id == r.id);
        if (idx >= 0) setState(() => _permisos[idx] = r);
      } else { setState(() => _permisos.insert(0, r)); }
      _filtrar();
    }
  }

  void _agregarExtension(PermitModel p) async {
    final r = await Navigator.push(context, MaterialPageRoute(builder: (_) => ExtensionScreen(permit: p)));
    if (r != null && r is PermitModel) {
      final idx = _permisos.indexWhere((x) => x.id == r.id);
      if (idx >= 0) setState(() => _permisos[idx] = r); else setState(() => _permisos.insert(0, r));
      _filtrar();
    }
  }

  int _contarPdfs() => _permisos.where((p) => p.tieneScan).length;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: _Pal.surface,
      appBar: PreferredSize(
        preferredSize: const Size.fromHeight(88),
        child: Container(
          color: _Pal.card,
          child: SafeArea(
            bottom: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 8, 20, 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      GestureDetector(
                        onTap: () => Navigator.pushReplacementNamed(context, '/home'),
                        child: const Icon(Icons.arrow_back_rounded, color: _Pal.brand, size: 24),
                      ),
                      const Spacer(),
                      if (_contarPdfs() > 0)
                        Container(
                          margin: const EdgeInsets.only(right: 8),
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                          decoration: BoxDecoration(color: _Pal.blueBg, borderRadius: BorderRadius.circular(8)),
                          child: Row(mainAxisSize: MainAxisSize.min, children: [
                            const Icon(Icons.picture_as_pdf, size: 14, color: _Pal.blueText),
                            const SizedBox(width: 4),
                            Text('()', style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: _Pal.blueText)),
                          ]),
                        ),
                      GestureDetector(
                        onTap: () => setState(() => _showSearch = !_showSearch),
                        child: Icon(_showSearch ? Icons.close : Icons.search, color: _Pal.brand, size: 24),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  const Text('Permisos de Trabajo', style: TextStyle(fontSize: 26, fontWeight: FontWeight.w800, color: _Pal.brand, letterSpacing: -0.5)),
                  const Text('HSE Management System', style: TextStyle(fontSize: 10, color: _Pal.textLight, fontWeight: FontWeight.w600, letterSpacing: 1.5)),
                ],
              ),
            ),
          ),
        ),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator(color: _Pal.brand))
          : RefreshIndicator(
              onRefresh: _loadUserAndPermisos,
              color: _Pal.brand,
              child: ListView(
                padding: const EdgeInsets.fromLTRB(20, 16, 20, 100),
                children: [
                  if (_error != null)
                    Container(
                      margin: const EdgeInsets.only(bottom: 16),
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                      decoration: BoxDecoration(color: _Pal.brandLight, borderRadius: BorderRadius.circular(10)),
                      child: Row(children: [
                        const Icon(Icons.info_outline, size: 16, color: _Pal.brand),
                        const SizedBox(width: 8),
                        Expanded(child: Text(_error!, style: const TextStyle(fontSize: 11, color: _Pal.brand, fontWeight: FontWeight.w500))),
                      ]),
                    ),
                  GestureDetector(
                    onTap: _nuevoPermiso,
                    child: Container(
                      width: double.infinity,
                      padding: const EdgeInsets.symmetric(vertical: 18),
                      decoration: BoxDecoration(color: _Pal.brand, borderRadius: BorderRadius.circular(16), boxShadow: [BoxShadow(color: _Pal.brand.withValues(alpha: 0.2), blurRadius: 16, offset: const Offset(0, 6))]),
                      child: const Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                        Icon(Icons.add, color: Colors.white, size: 24),
                        SizedBox(width: 8),
                        Text('NUEVO PERMISO', style: TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.w700, letterSpacing: 0.5)),
                      ]),
                    ),
                  ),
                  const SizedBox(height: 24),
                  if (_searchController.text.isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: Text('\ resultado(s) para "\"', style: const TextStyle(fontSize: 12, color: _Pal.textSecondary)),
                    ),
                  if (!_showSearch && _filteredPermisos.isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 12, left: 4),
                      child: Text('PERMISOS RECIENTES', style: TextStyle(fontSize: 10, fontWeight: FontWeight.w700, color: _Pal.textLight, letterSpacing: 1.5)),
                    ),
                  if (_showSearch)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: TextField(
                        controller: _searchController,
                        autofocus: true,
                        decoration: InputDecoration(
                          hintText: 'Buscar por ID, titulo o area...',
                          prefixIcon: const Icon(Icons.search, color: _Pal.textLight, size: 20),
                          filled: true, fillColor: _Pal.card,
                          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                          border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
                          enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
                          focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: _Pal.brand, width: 2)),
                        ),
                        style: const TextStyle(fontSize: 14, color: _Pal.textPrimary),
                      ),
                    ),
                  if (_filteredPermisos.isEmpty && !_isLoading)
                    Container(
                      padding: const EdgeInsets.all(40),
                      child: Column(children: [
                        Icon(_searchController.text.isNotEmpty ? Icons.search_off : Icons.event_available_outlined, size: 64, color: _Pal.textLight),
                        const SizedBox(height: 16),
                        Text(_searchController.text.isNotEmpty ? 'No se encontraron permisos' : 'No hay permisos registrados', style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: _Pal.textPrimary)),
                        const SizedBox(height: 8),
                        Text(_searchController.text.isNotEmpty ? 'Intente con otro termino de busqueda.' : 'Cree su primer permiso presionando el boton superior.', textAlign: TextAlign.center, style: const TextStyle(fontSize: 14, color: _Pal.textSecondary)),
                      ]),
                    ),
                  ..._filteredPermisos.map((p) => _PermisoCard(permit: p, onTap: () => _abrirPermiso(p), onExtension: () => _agregarExtension(p))),
                ],
              ),
            ),
      bottomNavigationBar: Container(
        decoration: const BoxDecoration(color: _Pal.card, border: Border(top: BorderSide(color: Color(0xFFF1F5F9)))),
        child: SafeArea(top: false, child: SizedBox(
          height: 64,
          child: Row(mainAxisAlignment: MainAxisAlignment.spaceAround, children: [
            _navItem(Icons.home_outlined, 'Inicio', active: false),
            _navItem(Icons.assignment, 'Permisos', active: true),
            _navItem(Icons.qr_code_scanner_outlined, 'Escaneo', active: false),
            _navItem(Icons.person_outline, 'Perfil', active: false),
          ]),
        )),
      ),
    );
  }

  Widget _navItem(IconData icon, String label, {required bool active}) {
    final c = active ? _Pal.brand : _Pal.textLight;
    return Expanded(
      child: GestureDetector(
        onTap: () {
          if (!active) {
            if (label == 'Inicio') Navigator.pushReplacementNamed(context, '/home');
            else if (label == 'Perfil') Navigator.pushNamed(context, '/perfil');
            else if (label == 'Escaneo') Navigator.push(context, MaterialPageRoute(builder: (_) => const DocumentScannerScreen()));
            else ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('\"\\" proximamente'), behavior: SnackBarBehavior.floating));
          }
        },
        child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
          Icon(icon, color: c, size: 26),
          const SizedBox(height: 4),
          Text(label, style: TextStyle(color: c, fontSize: 10, fontWeight: FontWeight.w700)),
        ]),
      ),
    );
  }
}

// ═════════════════════════════════════════════════════════════════
// TARJETA DE PERMISO — Diseño limpio tipo Tailwind
// ═════════════════════════════════════════════════════════════════
class _PermisoCard extends StatelessWidget {
  final PermitModel permit;
  final VoidCallback onTap;
  final VoidCallback onExtension;

  const _PermisoCard({required this.permit, required this.onTap, required this.onExtension});

  Color get _statusBg => switch (permit.status) { PermitStatus.active => _Pal.greenBg, PermitStatus.warning => _Pal.amberBg, PermitStatus.expired => _Pal.redBg };
  Color get _statusText => switch (permit.status) { PermitStatus.active => _Pal.greenText, PermitStatus.warning => _Pal.amberText, PermitStatus.expired => _Pal.redText };
  Color get _statusIcon => switch (permit.status) { PermitStatus.active => _Pal.greenText, PermitStatus.warning => _Pal.amberText, PermitStatus.expired => _Pal.redText };
  String get _statusLabel => switch (permit.status) { PermitStatus.active => 'Activo', PermitStatus.warning => 'Por vencer', PermitStatus.expired => 'Expirado' };

  String get _diasRestantes {
    final d = permit.remainingDaysEfectivo;
    if (d <= 0) return 'Vencido';
    if (d == 0) return 'Vence hoy';
    if (d == 1) return '1 día';
    return '\ días';
  }

  IconData get _statusIconData => switch (permit.status) { PermitStatus.active => Icons.check_circle, PermitStatus.warning => Icons.access_time, PermitStatus.expired => Icons.cancel };

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(24),
          child: Container(
            decoration: BoxDecoration(
              color: _Pal.card,
              borderRadius: BorderRadius.circular(24),
              border: Border.all(color: const Color(0xFFF1F5F9)),
              boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.04), blurRadius: 8, offset: const Offset(0, 2))],
            ),
            padding: const EdgeInsets.all(20),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              // Fila superior: ID + Estado
              Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        if (permit.tieneScan) ...[
                          const Icon(Icons.picture_as_pdf, size: 14, color: _Pal.brand),
                          const SizedBox(width: 4),
                        ],
                        Text(permit.id, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: _Pal.textLight)),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(permit.title, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700, color: _Pal.textPrimary, height: 1.2)),
                  ]),
                ),
                const SizedBox(width: 12),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(color: _statusBg, borderRadius: BorderRadius.circular(20)),
                  child: Row(mainAxisSize: MainAxisSize.min, children: [
                    Icon(_statusIconData, size: 12, color: _statusIcon),
                    const SizedBox(width: 3),
                    Text(_statusLabel.toUpperCase(), style: TextStyle(fontSize: 9, fontWeight: FontWeight.w700, color: _statusText, letterSpacing: 0.3)),
                  ]),
                ),
              ]),
              const SizedBox(height: 12),
              // Ubicación
              Row(children: [
                const Icon(Icons.location_on_outlined, size: 18, color: _Pal.textLight),
                const SizedBox(width: 6),
                Expanded(child: Text(permit.area, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500, color: _Pal.textSecondary))),
              ]),
              const SizedBox(height: 12),
              // Separador + info inferior
              const Divider(height: 1, color: Color(0xFFF1F5F9)),
              const SizedBox(height: 12),
              Row(children: [
                // Duración / días restantes
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text('DURACIÓN', style: TextStyle(fontSize: 8, fontWeight: FontWeight.w700, color: _Pal.textLight, letterSpacing: 1)),
                    const SizedBox(height: 4),
                    Row(children: [
                      Icon(_statusIconData, size: 16, color: _statusIcon),
                      const SizedBox(width: 4),
                      Text(_diasRestantes, style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: _statusText)),
                    ]),
                  ]),
                ),
                // Fecha
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
                    Text('FECHA', style: TextStyle(fontSize: 8, fontWeight: FontWeight.w700, color: _Pal.textLight, letterSpacing: 1)),
                    const SizedBox(height: 4),
                    Row(mainAxisSize: MainAxisSize.min, children: [
                      const Icon(Icons.calendar_today, size: 14, color: _Pal.textSecondary),
                      const SizedBox(width: 4),
                      Text(DateFormat('dd/MM/yyyy').format(permit.fechaFinalEfectiva), style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: _Pal.textSecondary)),
                    ]),
                  ]),
                ),
              ]),
              const SizedBox(height: 12),
              // Botón extensión
              Align(
                alignment: Alignment.centerRight,
                child: GestureDetector(
                  onTap: onExtension,
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                    decoration: BoxDecoration(color: _Pal.brandLight, borderRadius: BorderRadius.circular(12)),
                    child: Row(mainAxisSize: MainAxisSize.min, children: [
                      const Icon(Icons.add_circle_outline, size: 16, color: _Pal.brand),
                      const SizedBox(width: 6),
                      const Text('Gestionar Extensión', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: _Pal.brand)),
                    ]),
                  ),
                ),
              ),
            ]),
          ),
        ),
      ),
    );
  }
}
