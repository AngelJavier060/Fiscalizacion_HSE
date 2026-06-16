import "dart:io";
import "package:flutter/material.dart";
import "package:intl/intl.dart";
import "package:open_filex/open_filex.dart";
import "../models/permit_model.dart";
import "../services/permiso_offline_service.dart";
import "document_scanner_screen.dart";
import "extension_screen.dart";
import "nuevo_permiso_screen.dart";

class _C {
  static const primary = Color(0xFF154295);
  static const surface = Color(0xFFF8F9FB);
  static const card = Color(0xFFFFFFFF);
  static const onSurface = Color(0xFF1E293B);
  static const subtitle = Color(0xFF64748B);
  static const textLight = Color(0xFF94A3B8);
  static const border = Color(0xFFF1F5F9);
  static const green = Color(0xFF166534);
  static const greenBg = Color(0xFFDCFCE7);
  static const amber = Color(0xFF92400E);
  static const amberBg = Color(0xFFFEF3C7);
  static const red = Color(0xFF991B1B);
  static const redBg = Color(0xFFFEE2E2);
  static const blue = Color(0xFF1A73E8);
  static const blueBg = Color(0xFFE8F0FE);
  static const brandLight = Color(0xFFeef2ff);
}

class PermitCardScreen extends StatefulWidget {
  final PermitModel permit;
  const PermitCardScreen({super.key, required this.permit});
  @override
  State<PermitCardScreen> createState() => _PermitCardScreenState();
}

class _PermitCardScreenState extends State<PermitCardScreen> {
  late PermitModel _permit;
  @override
  void initState() { super.initState(); _permit = widget.permit; }

  Color get _sc => switch (_permit.status) { PermitStatus.active => _C.green, PermitStatus.warning => _C.amber, PermitStatus.expired => _C.red };
  Color get _sbg => switch (_permit.status) { PermitStatus.active => _C.greenBg, PermitStatus.warning => _C.amberBg, PermitStatus.expired => _C.redBg };

  String get _statusLbl => switch (_permit.status) { PermitStatus.active => "Vigente", PermitStatus.warning => "Por vencer", PermitStatus.expired => "Expirado" };

  String get _remainingLbl {
    if (_permit.status == PermitStatus.expired) return "Venció hace ${_permit.remainingDays.abs()} días";
    if (_permit.remainingDays == 0) return "Vence hoy";
    return "Vence en ${_permit.remainingDays} días";
  }

  void _editar() => Navigator.push(context, MaterialPageRoute(builder: (_) => NuevoPermisoScreen(permit: _permit)))
      .then((r) { if (r != null && mounted) setState(() => _permit = r as PermitModel); });

  Future<void> _scan() async {
    final r = await Navigator.push<PermitModel>(context, MaterialPageRoute(builder: (_) => DocumentScannerScreen(permit: _permit)));
    if (r != null && mounted) { setState(() => _permit = r); _guardarOffline(); Navigator.pop(context, _permit); }
  }

  Future<void> _ext() async {
    final r = await Navigator.push<PermitModel>(context, MaterialPageRoute(builder: (_) => ExtensionScreen(permit: _permit)));
    if (r != null && mounted) { setState(() => _permit = r); _guardarOffline(); }
  }

    Future<void> _guardarOffline() async {
    await PermisoOfflineService.guardarPermiso(_permit);
    // Sincronizar inmediatamente si hay conexion
    await PermisoOfflineService.sincronizarPendientes();
  }

  Future<void> _abrirPdf(String pdfPath) async {
    try {
      await OpenFilex.open(pdfPath);
    } catch (e) {
      if (mounted) {
        showDialog(context: context, builder: (ctx) => AlertDialog(
          title: const Text("Abrir PDF"),
          content: Text("No se pudo abrir el PDF: $e"),
          actions: [TextButton(onPressed: () => Navigator.pop(ctx), child: const Text("Cerrar"))],
        ));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final ds = DateFormat("dd/MM/yy", "es");
    final ts = DateFormat("HH:mm", "es");
    return Scaffold(
      backgroundColor: _C.surface,
      appBar: AppBar(
        backgroundColor: _C.card, surfaceTintColor: Colors.transparent, elevation: 0, scrolledUnderElevation: 1,
        leading: IconButton(icon: const Icon(Icons.arrow_back_rounded, color: _C.primary), onPressed: () => Navigator.pop(context, _permit)),
        title: const Text("Detalle", style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600, color: _C.primary)),
        actions: [
          IconButton(icon: const Icon(Icons.edit_outlined, color: _C.primary, size: 22), onPressed: _editar),
          IconButton(icon: const Icon(Icons.camera_alt_outlined, color: _C.primary, size: 22), onPressed: _scan),
          const SizedBox(width: 8),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(children: [
          _Card(child: Column(children: [
            Row(children: [
              Expanded(child: Text(_permit.id, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: _C.onSurface))),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                decoration: BoxDecoration(color: _sbg, borderRadius: BorderRadius.circular(20)),
                child: Row(mainAxisSize: MainAxisSize.min, children: [
                  Icon(_permit.status == PermitStatus.active ? Icons.check_circle : _permit.status == PermitStatus.warning ? Icons.access_time : Icons.cancel, size: 14, color: _sc),
                  const SizedBox(width: 4),
                  Text(_statusLbl, style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: _sc)),
                ]),
              ),
            ]),
            const SizedBox(height: 6),
            Text(_permit.title, style: const TextStyle(fontSize: 15, color: _C.subtitle)),
            const SizedBox(height: 16),
            _buildVigenciaBar(),
          ])),
          const SizedBox(height: 12),
          _Card(child: Column(children: [
            _infoRow(Icons.description_outlined, "Descripción", _permit.description ?? "—"),
            if (_permit.criticalTask != null) ...[const SizedBox(height: 12), _infoRow(Icons.warning_amber_rounded, "Tarea crítica", _permit.criticalTask!.label)],
            const SizedBox(height: 12), _infoRow(Icons.location_on_outlined, "Área / Ubicación", _permit.area),
            const SizedBox(height: 12), _infoRow(Icons.person_outline, "Responsable", _permit.responsible),
            const SizedBox(height: 12),
            Row(children: [
              Expanded(child: _infoRow(Icons.calendar_today, "Inicio", ds.format(_permit.startDate))),
              const SizedBox(width: 12),
              Expanded(child: _infoRow(Icons.event_busy, "Vencimiento", ds.format(_permit.endDate))),
            ]),
            if (_permit.startTime != null && _permit.endTime != null) ...[
              const SizedBox(height: 12),
              Row(children: [
                Expanded(child: _infoRow(Icons.access_time, "Hora inicio", ts.format(_permit.startTime!))),
                const SizedBox(width: 12),
                Expanded(child: _infoRow(Icons.access_time, "Hora fin", ts.format(_permit.endTime!))),
              ]),
            ],
          ])),
          if (_permit.emisor != null || _permit.ejecutante != null || _permit.empresaEjecutante != null) ...[
            const SizedBox(height: 12),
            _Card(title: "Emisión y Ejecución", icon: Icons.assignment_outlined, child: Column(children: [
              if (_permit.emisor != null && _permit.emisor!.isNotEmpty) _infoRow(Icons.edit_outlined, "Emisor", _permit.emisor!),
              if (_permit.ejecutante != null && _permit.ejecutante!.isNotEmpty) ...[const SizedBox(height: 12), _infoRow(Icons.engineering_outlined, "Ejecutante", _permit.ejecutante!)],
              if (_permit.empresaEjecutante != null && _permit.empresaEjecutante!.isNotEmpty) ...[const SizedBox(height: 12), _infoRow(Icons.business_outlined, "Empresa ejecutante", _permit.empresaEjecutante!)],
            ])),
          ],
          if (_permit.nota != null && _permit.nota!.isNotEmpty) ...[
            const SizedBox(height: 12),
            _Card(title: "Nota", icon: Icons.note_outlined, child: Text(_permit.nota!, style: const TextStyle(fontSize: 14, color: _C.onSurface, height: 1.5))),
          ],
          const SizedBox(height: 12),
          _buildDocumentSection(),
          const SizedBox(height: 12),
          _buildExtensionSection(),
          const SizedBox(height: 32),
        ]),
      ),
    );
  }

  Widget _buildVigenciaBar() {
    final pct = _permit.remainingPercent.clamp(0.0, 100.0);
    final frac = _permit.progressFraction.clamp(0.0, 1.0);
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(color: _sbg.withValues(alpha: 0.25), borderRadius: BorderRadius.circular(12), border: Border.all(color: _sc.withValues(alpha: 0.12))),
      child: Column(children: [
        Row(children: [
          const Icon(Icons.access_time, size: 16, color: _C.subtitle),
          const SizedBox(width: 6),
          const Text("Vigencia", style: TextStyle(fontSize: 13, fontWeight: FontWeight.w500, color: _C.subtitle)),
          const Spacer(),
          Text("${pct.toStringAsFixed(0)}%", style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: _sc)),
        ]),
        const SizedBox(height: 10),
        Stack(children: [
          Container(height: 8, width: double.infinity, decoration: BoxDecoration(color: _C.border, borderRadius: BorderRadius.circular(4))),
          FractionallySizedBox(widthFactor: frac, child: Container(height: 8, decoration: BoxDecoration(borderRadius: BorderRadius.circular(4), gradient: LinearGradient(colors: [_sc.withValues(alpha: 0.7), _sc], begin: Alignment.centerLeft, end: Alignment.centerRight)))),
        ]),
        const SizedBox(height: 8),
        Row(children: [Icon(Icons.error_outline, size: 13, color: _sc), const SizedBox(width: 4), Text(_remainingLbl, style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: _sc))]),
      ]),
    );
  }

  Widget _infoRow(IconData icon, String label, String value) {
    return Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Container(padding: const EdgeInsets.all(6), decoration: BoxDecoration(color: _C.primary.withValues(alpha: 0.06), borderRadius: BorderRadius.circular(8)), child: Icon(icon, size: 16, color: _C.primary.withValues(alpha: 0.7))),
      const SizedBox(width: 10),
      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(label, style: const TextStyle(fontSize: 11, color: _C.subtitle, fontWeight: FontWeight.w500)),
        const SizedBox(height: 2),
        Text(value, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: _C.onSurface, height: 1.3)),
      ])),
    ]);
  }

  Widget _buildDocumentSection() {
    final paths = _permit.imagePath != null && _permit.imagePath!.isNotEmpty ? _permit.imagePath!.split("|") : <String>[];
    String? pdfPath; String? firstImg;
    for (final p in paths) {
      if (p.endsWith(".pdf") && File(p).existsSync()) pdfPath = p;
      else if (firstImg == null && File(p).existsSync()) firstImg = p;
    }
    final hasDoc = pdfPath != null || firstImg != null;
    return _Card(title: "Documento", icon: Icons.document_scanner_outlined, child: Column(children: [
      const SizedBox(height: 4),
      if (pdfPath != null)
        Container(height: 160, decoration: BoxDecoration(color: const Color(0xFFFCE4EC), borderRadius: BorderRadius.circular(12)),
          child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
            const Icon(Icons.picture_as_pdf, size: 40, color: Color(0xFFD32F2F)), const SizedBox(height: 6),
            Text("PDF (${paths.length - 1} páginas)", style: const TextStyle(color: Color(0xFFD32F2F), fontSize: 13, fontWeight: FontWeight.w600)),
            const SizedBox(height: 4), Text(pdfPath!.split("/").last, style: const TextStyle(color: _C.subtitle, fontSize: 11)),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              icon: const Icon(Icons.open_in_new, size: 16),
              label: const Text("Abrir PDF"),
              onPressed: () => _abrirPdf(pdfPath!),
              style: OutlinedButton.styleFrom(foregroundColor: const Color(0xFFD32F2F), side: const BorderSide(color: Color(0xFFF8BBD0)), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8))),
            ),
          ]))
      else if (firstImg != null)
        ClipRRect(borderRadius: BorderRadius.circular(12), child: Image.file(File(firstImg), fit: BoxFit.contain, width: double.infinity, height: 220))
      else
        Container(height: 100, decoration: BoxDecoration(color: _C.surface, borderRadius: BorderRadius.circular(12), border: Border.all(color: _C.border)),
          child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
            Icon(Icons.image_outlined, size: 28, color: _C.subtitle.withValues(alpha: 0.5)), const SizedBox(width: 8),
            Text("Sin documento", style: TextStyle(fontSize: 14, color: _C.subtitle.withValues(alpha: 0.6))),
          ])),
      const SizedBox(height: 12),
      SizedBox(width: double.infinity, child: OutlinedButton.icon(
        onPressed: _scan, icon: Icon(hasDoc ? Icons.refresh : Icons.camera_alt_outlined, size: 18),
        label: Text(hasDoc ? "Re-escanear" : "Escanear documento"),
        style: OutlinedButton.styleFrom(foregroundColor: _C.primary, side: const BorderSide(color: _C.border), padding: const EdgeInsets.symmetric(vertical: 12), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10))),
      )),
    ]));
  }

  Widget _buildExtensionSection() {
    final exts = _permit.extensiones;
    final df = DateFormat("dd/MM/yyyy");
    return _Card(title: "Extensiones (${exts.length})", icon: Icons.update, child: Column(children: [
      if (exts.isEmpty)
        Container(height: 60, decoration: BoxDecoration(color: _C.surface, borderRadius: BorderRadius.circular(10)),
          child: const Center(child: Text("Sin extensiones registradas", style: TextStyle(color: _C.subtitle, fontSize: 13))))
      else
        ...exts.asMap().entries.map((e) {
          final i = e.key; final ext = e.value;
          final hasFile = File(ext.scanPath).existsSync();
          return Container(
            margin: const EdgeInsets.only(bottom: 8),
            decoration: BoxDecoration(color: _C.brandLight, borderRadius: BorderRadius.circular(10)),
            child: ExpansionTile(
              tilePadding: const EdgeInsets.symmetric(horizontal: 12),
              leading: CircleAvatar(radius: 14, backgroundColor: _C.primary.withValues(alpha: 0.1), child: Text("${i + 1}", style: const TextStyle(fontWeight: FontWeight.bold, color: _C.primary, fontSize: 12))),
              title: Text("Extensión ${i + 1}", style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: _C.onSurface)),
              subtitle: Text("Vence: ${df.format(ext.fechaExtension)}", style: const TextStyle(fontSize: 11, color: _C.subtitle)),
                            children: [
                if (hasFile)
                  Padding(padding: const EdgeInsets.fromLTRB(12, 0, 12, 12), child: Column(children: [
                    ClipRRect(borderRadius: BorderRadius.circular(8), child: SizedBox(
                      width: double.infinity, height: 140,
                      child: ext.scanPath.endsWith(".pdf")
                          ? Container(color: const Color(0xFFFCE4EC), child: Center(child: Column(children: [
                              const Icon(Icons.picture_as_pdf, size: 32, color: Color(0xFFD32F2F)),
                              Text("PDF ext. ${i + 1}", style: const TextStyle(color: Color(0xFFD32F2F), fontSize: 12, fontWeight: FontWeight.w600)),
                              const SizedBox(height: 6),
                              OutlinedButton.icon(
                                icon: const Icon(Icons.open_in_new, size: 14),
                                label: const Text("Abrir PDF", style: TextStyle(fontSize: 12)),
                                onPressed: () => _abrirPdf(ext.scanPath),
                                style: OutlinedButton.styleFrom(foregroundColor: const Color(0xFFD32F2F), side: const BorderSide(color: Color(0xFFF8BBD0)), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8))),
                              ),
                            ])))
                          : Image.file(File(ext.scanPath), fit: BoxFit.cover, errorBuilder: (_, __, ___) => Container(color: _C.surface, child: const Icon(Icons.broken_image, color: _C.subtitle))),
                    )),
                  ]))
                else
                  const Padding(padding: EdgeInsets.fromLTRB(12, 0, 12, 12), child: Text("Documento no disponible", style: TextStyle(color: _C.subtitle, fontSize: 12))),
              ],
            ),
          );
        }),
      const SizedBox(height: 8),
      SizedBox(width: double.infinity, child: OutlinedButton.icon(
        onPressed: _ext, icon: const Icon(Icons.add_circle_outline, size: 18),
        label: Text(exts.isEmpty ? "Agregar extensión" : "Nueva extensión"),
        style: OutlinedButton.styleFrom(foregroundColor: _C.primary, side: const BorderSide(color: _C.border), padding: const EdgeInsets.symmetric(vertical: 12), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10))),
      )),
    ]));
  }
}

class _Card extends StatelessWidget {
  final String? title; final IconData? icon; final Widget child;
  const _Card({this.title, this.icon, required this.child});
  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      decoration: BoxDecoration(color: _C.card, borderRadius: BorderRadius.circular(16), border: Border.all(color: _C.border)),
      padding: const EdgeInsets.all(18),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        if (title != null || icon != null) ...[
          Row(children: [
            if (icon != null) ...[Icon(icon, size: 18, color: _C.primary), const SizedBox(width: 8)],
            if (title != null) Text(title!, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: _C.onSurface)),
          ]),
          const SizedBox(height: 14),
        ],
        child,
      ]),
    );
  }
}
