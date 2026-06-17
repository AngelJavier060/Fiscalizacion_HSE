import "dart:io";
import "package:flutter/material.dart";
import "package:intl/intl.dart";
import "package:open_filex/open_filex.dart";
import "../models/permit_model.dart";
import "../services/permiso_offline_service.dart";
import "document_scanner_screen.dart";
import "extension_screen.dart";
import "nuevo_permiso_screen.dart";

// ── Colores del diseño HTML ──────────────────────────────────────
class _C {
  static const primary = Color(0xFF002045);
  static const primaryContainer = Color(0xFF1A365D);
  static const onPrimaryContainer = Color(0xFF86A0CD);

  static const surface = Color(0xFFF7F9FB);
  static const surfaceContainerLow = Color(0xFFF2F4F6);
  static const surfaceContainerHigh = Color(0xFFE6E8EA);
  static const card = Color(0xFFFFFFFF);

  static const onSurface = Color(0xFF191C1E);
  static const onSurfaceVariant = Color(0xFF43474E);
  static const outline = Color(0xFF74777F);
  static const outlineVariant = Color(0xFFC4C6CF);

  static const green = Color(0xFF006E2F);
  static const greenBg = Color(0xFFD1FAE5);

  static const amber = Color(0xFF92400E);
  static const amberBg = Color(0xFFFEF3C7);

  static const red = Color(0xFFBA1A1A);
  static const redBg = Color(0xFFFFDAD6);
  static const errorContainer = Color(0xFFFFDAD6);

  static const blueInfo = Color(0xFF1D4ED8);
  static const blueInfoBg = Color(0xFFEFF6FF);
  static const blueInfoBorder = Color(0xFFBFDBFE);
  static const verifyBg = Color(0xFFF0FDF4);
  static const verifyBorder = Color(0xFFBBF7D0);
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

  // ═════════════════════════════════════════════════════════════════
  // BUILD
  // ═════════════════════════════════════════════════════════════════
  @override
  Widget build(BuildContext context) {
    final ds = DateFormat("dd/MM/yy", "es");
    final ts = DateFormat("HH:mm", "es");
    return Scaffold(
      backgroundColor: _C.surface,
      appBar: PreferredSize(
        preferredSize: const Size.fromHeight(64),
        child: Container(
          decoration: BoxDecoration(
            color: _C.card,
            border: Border(bottom: BorderSide(color: _C.outlineVariant.withValues(alpha: 0.5))),
          ),
          child: SafeArea(
            bottom: false,
            child: SizedBox(
              height: 64,
              child: Row(
                children: [
                  const SizedBox(width: 4),
                  IconButton(icon: const Icon(Icons.arrow_back, color: _C.primary), onPressed: () => Navigator.pop(context, _permit)),
                  const SizedBox(width: 4),
                  Text("Detalle", style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700, color: _C.primary)),
                  const Spacer(),
                  IconButton(icon: const Icon(Icons.edit_outlined, color: _C.primary, size: 22), onPressed: _editar, tooltip: "Editar"),
                  IconButton(icon: const Icon(Icons.photo_camera_outlined, color: _C.primary, size: 22), onPressed: _scan, tooltip: "Escanear"),
                  const SizedBox(width: 8),
                ],
              ),
            ),
          ),
        ),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 100),
        child: Column(children: [
          _buildStatusHeader(),
          const SizedBox(height: 12),
          _buildVigenciaCard(),
          const SizedBox(height: 12),
          if (_permit.description != null && _permit.description!.isNotEmpty) ...[_buildDescriptionCard(), const SizedBox(height: 12)],
          _buildMainInfoCard(),
          const SizedBox(height: 12),
          _buildTimestampsRow(ds, ts),
          const SizedBox(height: 12),
          if (_permit.emisor != null || _permit.ejecutante != null || _permit.empresaEjecutante != null) ...[
            _buildEmisionCard(),
            const SizedBox(height: 12),
          ],
          if (_permit.nota != null && _permit.nota!.isNotEmpty) ...[_buildNotaCard(), const SizedBox(height: 12)],
          _buildDocumentSection(),
          const SizedBox(height: 12),
          _buildVerificacionSection(),
          const SizedBox(height: 12),
          _buildExtensionSection(),
          const SizedBox(height: 24),
        ]),
      ),
    );
  }

  // ── Status Header ──────────────────────────────────────────────
  Widget _buildStatusHeader() {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Row(children: [
        Text(_permit.id, style: const TextStyle(fontFamily: "JetBrains Mono", fontSize: 14, fontWeight: FontWeight.w500, color: _C.outline)),
        const SizedBox(width: 8),
        _buildStatusBadge(),
      ]),
      const SizedBox(height: 6),
      Text(_permit.title, style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w700, color: _C.primary, height: 1.2)),
    ]);
  }

  Widget _buildStatusBadge() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(color: _sbg, borderRadius: BorderRadius.circular(20)),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        Icon(_permit.status == PermitStatus.active ? Icons.check_circle : _permit.status == PermitStatus.warning ? Icons.access_time : Icons.cancel, size: 14, color: _sc),
        const SizedBox(width: 4),
        Text(_statusLbl.toUpperCase(), style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: _sc, letterSpacing: 0.05)),
      ]),
    );
  }

  // ── Vigencia Card ──────────────────────────────────────────────
  Widget _buildVigenciaCard() {
    final pct = _permit.remainingPercent.clamp(0.0, 100.0);
    final frac = _permit.progressFraction.clamp(0.0, 1.0);
    return Container(
      width: double.infinity,
      decoration: BoxDecoration(
        color: _C.card,
        borderRadius: BorderRadius.circular(8),
        border: Border(left: BorderSide(color: _sc, width: 4), top: BorderSide(color: _C.outlineVariant.withValues(alpha: 0.5)), right: BorderSide(color: _C.outlineVariant.withValues(alpha: 0.5)), bottom: BorderSide(color: _C.outlineVariant.withValues(alpha: 0.5))),
        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.04), blurRadius: 2, offset: const Offset(0, 1))],
      ),
      padding: const EdgeInsets.all(16),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          const Icon(Icons.schedule, size: 18, color: _C.onSurfaceVariant),
          const SizedBox(width: 8),
          Text("Vigencia", style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: _C.onSurfaceVariant)),
          const Spacer(),
          Text("${pct.toStringAsFixed(0)}%", style: TextStyle(fontFamily: "JetBrains Mono", fontSize: 14, fontWeight: FontWeight.w700, color: _sc)),
        ]),
        const SizedBox(height: 12),
        ClipRRect(
          borderRadius: BorderRadius.circular(4),
          child: Container(height: 8, width: double.infinity, color: _C.surfaceContainerHigh, alignment: Alignment.centerLeft,
            child: FractionallySizedBox(widthFactor: frac, child: Container(height: 8, decoration: BoxDecoration(borderRadius: BorderRadius.circular(4), color: _sc))),
          ),
        ),
        const SizedBox(height: 10),
        Row(children: [Icon(Icons.info, size: 14, color: _sc), const SizedBox(width: 4), Text(_remainingLbl, style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: _sc, letterSpacing: 0.05))]),
      ]),
    );
  }

  // ── Description Card ────────────────────────────────────────────
  Widget _buildDescriptionCard() {
    return _DataCard(
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Container(padding: const EdgeInsets.all(8), decoration: BoxDecoration(color: _C.primaryContainer, borderRadius: BorderRadius.circular(8)),
          child: const Icon(Icons.description_outlined, size: 20, color: _C.onPrimaryContainer)),
        const SizedBox(width: 12),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text("DESCRIPCIÓN", style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: _C.outline, letterSpacing: 0.05)),
          const SizedBox(height: 4),
          Text(_permit.description!, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: _C.primary, height: 1.3)),
        ])),
      ]),
    );
  }

  // ── Main Info ───────────────────────────────────────────────────
  Widget _buildMainInfoCard() {
    return _DataCard(
      child: Column(children: [
        if (_permit.criticalTask != null) ...[_buildInfoRow(icon: Icons.report_problem, iconColor: _C.red, label: "TAREA CRÍTICA", value: _permit.criticalTask!.label), const SizedBox(height: 12)],
        _buildInfoRow(icon: Icons.location_on, iconColor: _C.primary, label: "ÁREA / UBICACIÓN", value: _permit.area),
        Padding(padding: const EdgeInsets.symmetric(vertical: 12), child: Divider(height: 1, color: _C.outlineVariant.withValues(alpha: 0.5))),
        _buildInfoRow(icon: Icons.person, iconColor: _C.primary, label: "RESPONSABLE", value: _permit.responsible),
      ]),
    );
  }

  Widget _buildInfoRow({required IconData icon, required Color iconColor, required String label, required String value}) {
    return Row(children: [
      Icon(icon, size: 22, color: iconColor),
      const SizedBox(width: 12),
      Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(label, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: _C.outline, letterSpacing: 0.05)),
        const SizedBox(height: 2),
        Text(value, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: _C.primary)),
      ]),
    ]);
  }

  // ── Timestamps ─────────────────────────────────────────────────
  Widget _buildTimestampsRow(DateFormat ds, DateFormat ts) {
    return Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Expanded(child: _DataCard(padding: const EdgeInsets.all(12), child: _buildTimeCol(
        label: "INICIO", date: ds.format(_permit.startDate),
        time: _permit.startTime != null ? ts.format(_permit.startTime!) : null, timeIcon: Icons.alarm,
      ))),
      const SizedBox(width: 12),
      Expanded(child: _DataCard(padding: const EdgeInsets.all(12), child: _buildTimeCol(
        label: "VENCIMIENTO", date: ds.format(_permit.fechaFinalEfectiva),
        time: _permit.endTime != null ? ts.format(_permit.endTime!) : null, timeIcon: Icons.alarm_off,
      ))),
    ]);
  }

  Widget _buildTimeCol({required String label, required String date, String? time, required IconData timeIcon}) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(label, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: _C.outline, letterSpacing: 0.05)),
      const SizedBox(height: 8),
      Row(children: [
        const Icon(Icons.calendar_today, size: 18, color: _C.primary),
        const SizedBox(width: 4),
        Text(date, style: const TextStyle(fontFamily: "JetBrains Mono", fontSize: 13, fontWeight: FontWeight.w500, color: _C.primary)),
      ]),
      if (time != null) ...[const SizedBox(height: 4),
        Row(children: [
          Icon(timeIcon, size: 18, color: _C.primary),
          const SizedBox(width: 4),
          Text(time, style: const TextStyle(fontFamily: "JetBrains Mono", fontSize: 13, fontWeight: FontWeight.w500, color: _C.primary)),
        ]),
      ],
    ]);
  }

  // ── Emisión y Ejecución ─────────────────────────────────────────
  Widget _buildEmisionCard() {
    return _DataCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Container(
          width: double.infinity,
          margin: const EdgeInsets.only(bottom: 16),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          decoration: BoxDecoration(color: _C.surfaceContainerLow, border: Border(bottom: BorderSide(color: _C.outlineVariant.withValues(alpha: 0.5)))),
          child: Row(children: [
            const Icon(Icons.assignment_turned_in, size: 20, color: _C.primary),
            const SizedBox(width: 8),
            Text("Emisión y Ejecución", style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: _C.primary)),
          ]),
        ),
        _buildPersonRow(icon: Icons.history_edu, label: "EMISOR", value: _permit.emisor ?? "—"),
        if (_permit.ejecutante != null && _permit.ejecutante!.isNotEmpty) ...[const SizedBox(height: 16), _buildPersonRow(icon: Icons.engineering, label: "EJECUTANTE", value: _permit.ejecutante!)],
        if (_permit.empresaEjecutante != null && _permit.empresaEjecutante!.isNotEmpty) ...[const SizedBox(height: 16), _buildPersonRow(icon: Icons.domain, label: "EMPRESA EJECUTANTE", value: _permit.empresaEjecutante!)],
      ]),
    );
  }

  Widget _buildPersonRow({required IconData icon, required String label, required String value}) {
    return Row(children: [
      Icon(icon, size: 22, color: _C.outline),
      const SizedBox(width: 12),
      Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(label, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: _C.outline, letterSpacing: 0.05)),
        const SizedBox(height: 2),
        Text(value, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: _C.primary)),
      ]),
    ]);
  }

  // ── Nota ────────────────────────────────────────────────────────
  Widget _buildNotaCard() {
    return _DataCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          const Icon(Icons.note, size: 20, color: _C.primary),
          const SizedBox(width: 8),
          Text("NOTA", style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: _C.outline, letterSpacing: 0.05)),
        ]),
        const SizedBox(height: 8),
        Text(_permit.nota!, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w400, fontStyle: FontStyle.italic, color: _C.onSurfaceVariant, height: 1.4)),
      ]),
    );
  }

  // ── Documento / PDF ────────────────────────────────────────────
  Widget _buildDocumentSection() {
    final paths = _permit.imagePath != null && _permit.imagePath!.isNotEmpty ? _permit.imagePath!.split("|") : <String>[];
    String? pdfPath; String? firstImg;
    for (final p in paths) {
      if (p.endsWith(".pdf") && File(p).existsSync()) pdfPath = p;
      else if (firstImg == null && File(p).existsSync()) firstImg = p;
    }
    final hasDoc = pdfPath != null || firstImg != null;
    return _DataCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          const Icon(Icons.folder_open, size: 20, color: _C.primary),
          const SizedBox(width: 8),
          Text("Permiso de trabajo", style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: _C.primary)),
        ]),
        const SizedBox(height: 16),
        if (pdfPath != null)
          _buildPdfPreview(pdfPath, paths.length - 1)
        else if (firstImg != null)
          ClipRRect(borderRadius: BorderRadius.circular(8), child: Image.file(File(firstImg), fit: BoxFit.contain, width: double.infinity, height: 200))
        else
          _buildEmptyDocument(),
        const SizedBox(height: 12),
        SizedBox(width: double.infinity, child: OutlinedButton.icon(
          onPressed: _scan, icon: Icon(hasDoc ? Icons.sync : Icons.camera_alt_outlined, size: 18),
          label: Text(hasDoc ? "Re-escanear permiso" : "Escanear permiso de trabajo"),
          style: OutlinedButton.styleFrom(foregroundColor: _C.primary, side: const BorderSide(color: _C.surfaceContainerHigh), padding: const EdgeInsets.symmetric(vertical: 14), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8))),
        )),
      ]),
    );
  }

  Widget _buildPdfPreview(String pdfPath, int pages) {
    return Container(
      width: double.infinity,
      decoration: BoxDecoration(color: _C.errorContainer.withValues(alpha: 0.2), borderRadius: BorderRadius.circular(12), border: Border.all(color: _C.red.withValues(alpha: 0.2))),
      padding: const EdgeInsets.symmetric(vertical: 24),
      child: Column(children: [
        Icon(Icons.picture_as_pdf, size: 64, color: _C.red.withValues(alpha: 0.8)),
        const SizedBox(height: 8),
        Text("PDF ($pages páginas)", style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: _C.red)),
        const SizedBox(height: 4),
        Text(pdfPath.split("/").last, style: const TextStyle(fontFamily: "JetBrains Mono", fontSize: 13, fontWeight: FontWeight.w500, color: _C.outline)),
        const SizedBox(height: 16),
        OutlinedButton.icon(
          onPressed: () => _abrirPdf(pdfPath), icon: const Icon(Icons.open_in_new, size: 18),
          label: const Text("Abrir PDF"),
          style: OutlinedButton.styleFrom(foregroundColor: _C.red, side: BorderSide(color: _C.red.withValues(alpha: 0.3)), padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 10), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8))),
        ),
      ]),
    );
  }

  Widget _buildEmptyDocument() {
    return Container(
      width: double.infinity,
      decoration: BoxDecoration(color: _C.surfaceContainerLow, borderRadius: BorderRadius.circular(12), border: Border.all(color: _C.outlineVariant.withValues(alpha: 0.5))),
      padding: const EdgeInsets.symmetric(vertical: 32),
      child: Column(children: [
        Icon(Icons.picture_as_pdf, size: 48, color: _C.outline.withValues(alpha: 0.4)),
        const SizedBox(height: 8),
        Text("Sin permiso escaneado", style: TextStyle(fontSize: 14, color: _C.outline.withValues(alpha: 0.6))),
      ]),
    );
  }

  // ── Verificación ───────────────────────────────────────────────
  Widget _buildVerificacionSection() {
    final paths = _permit.imagePath != null && _permit.imagePath!.isNotEmpty ? _permit.imagePath!.split("|") : <String>[];
    String? pdfPath; String? firstImg;
    for (final p in paths) {
      if (p.endsWith(".pdf") && File(p).existsSync()) pdfPath = p;
      else if (firstImg == null && File(p).existsSync()) firstImg = p;
    }
    final hasDoc = pdfPath != null || firstImg != null;
    return _DataCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          const Icon(Icons.verified, size: 20, color: _C.primary),
          const SizedBox(width: 8),
          Text("Verificación de permiso de trabajo", style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: _C.primary)),
        ]),
        const SizedBox(height: 12),
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(color: _C.blueInfoBg, borderRadius: BorderRadius.circular(8), border: Border.all(color: _C.blueInfoBorder)),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Icon(Icons.info, size: 18, color: _C.blueInfo),
            const SizedBox(width: 8),
            const Expanded(child: Text("Escanee el permiso firmado y verificado por el supervisor.", style: TextStyle(fontSize: 13, fontWeight: FontWeight.w400, color: _C.blueInfo, height: 1.3))),
          ]),
        ),
        const SizedBox(height: 12),
        if (hasDoc)
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(color: _C.verifyBg, borderRadius: BorderRadius.circular(8), border: Border.all(color: _C.verifyBorder)),
            child: Row(children: [
              const Icon(Icons.check_circle, size: 24, color: _C.green),
              const SizedBox(width: 8),
              Text("Documento de verificación adjunto", style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: _C.green)),
            ]),
          )
        else
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(color: _C.surfaceContainerLow, borderRadius: BorderRadius.circular(8), border: Border.all(color: _C.outlineVariant.withValues(alpha: 0.5))),
            child: const Row(mainAxisAlignment: MainAxisAlignment.center, children: [
              Icon(Icons.verified_outlined, size: 24, color: _C.outline),
              SizedBox(width: 8),
              Text("Pendiente de verificación", style: TextStyle(fontSize: 14, color: _C.outline)),
            ]),
          ),
      ]),
    );
  }

  // ── Extensiones ────────────────────────────────────────────────
  Widget _buildExtensionSection() {
    final exts = _permit.extensiones;
    final df = DateFormat("dd/MM/yyyy");
    return _DataCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          const Icon(Icons.update, size: 20, color: _C.primary),
          const SizedBox(width: 8),
          Text("Extensiones (${exts.length})", style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: _C.primary)),
        ]),
        const SizedBox(height: 16),
        if (exts.isEmpty)
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(vertical: 24),
            decoration: BoxDecoration(color: _C.surfaceContainerLow, borderRadius: BorderRadius.circular(8)),
            child: const Center(child: Text("Sin extensiones registradas", style: TextStyle(fontSize: 13, color: _C.onSurfaceVariant))),
          )
        else
          ...exts.asMap().entries.map((e) {
            final i = e.key; final ext = e.value;
            final hasFile = File(ext.scanPath).existsSync();
            return Container(
              margin: const EdgeInsets.only(bottom: 8),
              decoration: BoxDecoration(color: _C.surfaceContainerLow, borderRadius: BorderRadius.circular(8), border: Border.all(color: _C.outlineVariant.withValues(alpha: 0.3))),
              child: ExpansionTile(
                tilePadding: const EdgeInsets.symmetric(horizontal: 12),
                leading: CircleAvatar(radius: 14, backgroundColor: _C.primary.withValues(alpha: 0.1), child: Text("${i + 1}", style: const TextStyle(fontWeight: FontWeight.bold, color: _C.primary, fontSize: 12))),
                title: Text("Extensión ${i + 1}", style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: _C.onSurface)),
                subtitle: Text("Vence: ${df.format(ext.fechaExtension)}", style: const TextStyle(fontSize: 11, color: _C.onSurfaceVariant)),
                children: [
                  if (hasFile)
                    Padding(padding: const EdgeInsets.fromLTRB(12, 0, 12, 12), child: SizedBox(
                      width: double.infinity, height: 140,
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(8),
                        child: ext.scanPath.endsWith(".pdf")
                            ? Container(color: _C.errorContainer.withValues(alpha: 0.3), child: Center(child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                                Icon(Icons.picture_as_pdf, size: 32, color: _C.red),
                                Text("PDF ext. ${i + 1}", style: TextStyle(color: _C.red, fontSize: 12, fontWeight: FontWeight.w600)),
                                const SizedBox(height: 6),
                                OutlinedButton.icon(
                                  icon: const Icon(Icons.open_in_new, size: 14), label: const Text("Abrir PDF", style: TextStyle(fontSize: 12)),
                                  onPressed: () => _abrirPdf(ext.scanPath),
                                  style: OutlinedButton.styleFrom(foregroundColor: _C.red, side: BorderSide(color: _C.red.withValues(alpha: 0.3)), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8))),
                                ),
                              ])))
                            : Image.file(File(ext.scanPath), fit: BoxFit.cover, errorBuilder: (_, __, ___) => Container(color: _C.surfaceContainerLow, child: const Center(child: Icon(Icons.broken_image, color: _C.outline)))),
                      ),
                    ))
                  else
                    const Padding(padding: EdgeInsets.fromLTRB(12, 0, 12, 12), child: Text("Documento no disponible", style: TextStyle(fontSize: 12, color: _C.onSurfaceVariant))),
                ],
              ),
            );
          }),
        const SizedBox(height: 8),
        SizedBox(width: double.infinity, child: OutlinedButton.icon(
          onPressed: _ext, icon: const Icon(Icons.add_circle_outline, size: 18),
          label: Text(exts.isEmpty ? "Agregar extensión" : "Nueva extensión"),
          style: OutlinedButton.styleFrom(foregroundColor: _C.primary, side: const BorderSide(color: _C.surfaceContainerHigh), padding: const EdgeInsets.symmetric(vertical: 14), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8))),
        )),
      ]),
    );
  }
}

// ── Data Card estilo Tailwind ─────────────────────────────────────
class _DataCard extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry? padding;
  const _DataCard({required this.child, this.padding});
  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      decoration: BoxDecoration(
        color: _C.card,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: _C.outlineVariant.withValues(alpha: 0.5)),
        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.04), blurRadius: 2, offset: const Offset(0, 1))],
      ),
      padding: padding ?? const EdgeInsets.all(16),
      child: child,
    );
  }
}
