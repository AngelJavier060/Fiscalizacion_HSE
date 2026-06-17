import "dart:async";
import "dart:io";
import "package:flutter/material.dart";
import "package:flutter/services.dart";
import "package:image/image.dart" as img;
import "package:image_picker/image_picker.dart";
import "package:intl/intl.dart";
import "package:path_provider/path_provider.dart";
import "package:pdf/pdf.dart";
import "package:pdf/widgets.dart" as pw;
import "../models/permit_model.dart";
import "../services/permiso_offline_service.dart";

class _ScanPal {
  static const bg = Color(0xFF0D0D0D);
  static const surface = Color(0xFF1A1A1A);
  static const surfaceLight = Color(0xFF2A2A2A);
  static const accent = Color(0xFF4A90E2);
  static const green = Color(0xFF34C759);
  static const amber = Color(0xFFFFCC00);
  static const red = Color(0xFFFF3B30);
  static const onSurface = Color(0xFFF5F5F7);
  static const onSurfaceDim = Color(0xFF8E8E93);
  static const border = Color(0xFF38383A);
}

class DocumentScannerScreen extends StatefulWidget {
  final PermitModel? permit;
  const DocumentScannerScreen({super.key, this.permit});
  @override
  State<DocumentScannerScreen> createState() => _DocumentScannerScreenState();
}

class _DocumentScannerScreenState extends State<DocumentScannerScreen>
    with WidgetsBindingObserver {
  final ImagePicker _picker = ImagePicker();
  final List<_ScannedPage> _pages = [];
  final Set<int> _selectedPages = {};
  int _currentPageIndex = -1;
  bool _isCapturing = false;
  bool _isProcessing = false;
  String _statusMessage = "Listo para escanear";

  @override
  void initState() { super.initState(); WidgetsBinding.instance.addObserver(this); SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge); }
  @override
  void dispose() { WidgetsBinding.instance.removeObserver(this); SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge); super.dispose(); }

  Future<void> _capturePage() async {
    if (_isCapturing) return;
    setState(() => _isCapturing = true);
    try {
      final XFile? photo = await _picker.pickImage(source: ImageSource.camera, preferredCameraDevice: CameraDevice.rear, imageQuality: 80, maxWidth: 1920, maxHeight: 1920);
      if (photo != null && mounted) await _processCapturedPage(photo.path);
    } catch (e) { if (mounted) _showStatus("Error al capturar: $e", isError: true); }
    finally { if (mounted) setState(() => _isCapturing = false); }
  }

  Future<void> _processCapturedPage(String originalPath) async {
    setState(() { _isProcessing = true; _statusMessage = "Procesando imagen..."; });
        try {
      final bytes = await File(originalPath).readAsBytes();
      img.Image? image = img.decodeImage(bytes);
      if (image == null) throw Exception("No se pudo decodificar");
      image = img.bakeOrientation(image);
      // Escalar a resolución máxima para agilizar
      final maxDim = 1200;
      if (image.width > maxDim || image.height > maxDim) {
        image = img.copyResize(image, width: image.width > image.height ? maxDim : null, height: image.height >= image.width ? maxDim : null);
      }
      final timestamp = DateTime.now().millisecondsSinceEpoch;
      final outputDir = await getApplicationDocumentsDirectory();
      final scansDir = Directory("${outputDir.path}/scans");
            if (!await scansDir.exists()) await scansDir.create(recursive: true);
            final idBase = (widget.permit?.id ?? 'temp').replaceAll(RegExp(r'[\s/\-]'), '_');
            final processedPath = "${scansDir.path}/${idBase}_pag${_pages.length + 1}.jpg";
      final img2 = image!;
      await File(processedPath).writeAsBytes(img.encodeJpg(img2, quality: 80));
      if (!mounted) return;
      final page = _ScannedPage(id: timestamp, path: processedPath, originalPath: originalPath, capturedAt: DateTime.now(), width: img2.width, height: img2.height);
      setState(() { _pages.add(page); _selectedPages.add(page.id); _currentPageIndex = _pages.length - 1; _isProcessing = false; _statusMessage = "Pag ${_pages.length} - ${img2.width}x${img2.height} - OK"; });
      HapticFeedback.mediumImpact();
    } catch (e) {
      debugPrint("Error: $e");
      try {
        final ts = DateTime.now().millisecondsSinceEpoch;
        final outDir = await getApplicationDocumentsDirectory();
        final sDir = Directory("${outDir.path}/scans");
                if (!await sDir.exists()) await sDir.create(recursive: true);
                final idBase = (widget.permit?.id ?? 'temp').replaceAll(RegExp(r'[\s/\-]'), '_');
                final fb = "${sDir.path}/${idBase}_pag${_pages.length + 1}.jpg";
        await File(originalPath).copy(fb);
        if (mounted) {
          final p = _ScannedPage(id: ts, path: fb, originalPath: originalPath, capturedAt: DateTime.now());
          setState(() { _pages.add(p); _selectedPages.add(p.id); _currentPageIndex = _pages.length - 1; _isProcessing = false; _statusMessage = "Pag ${_pages.length} (original)"; });
        }
      } catch (_) { if (mounted) { setState(() => _isProcessing = false); _showStatus("Error al procesar", isError: true); } }
    }
  }

  img.Image _ajustarContraste(img.Image src) {
    final gray = img.grayscale(src);
    int minVal = 255, maxVal = 0;
    for (var y = 0; y < gray.height; y++) { for (var x = 0; x < gray.width; x++) { final l = img.getLuminance(gray.getPixel(x, y)).toInt(); if (l < minVal) minVal = l; if (l > maxVal) maxVal = l; } }
    if (maxVal - minVal < 30) return src;
    return img.adjustColor(src, contrast: (255.0 / (maxVal - minVal)).clamp(0.5, 3.0));
  }

  img.Image _sharpenImage(img.Image src) {
    final blurred = img.gaussianBlur(src, radius: 2);
    final result = img.Image.from(src);
    for (var y = 0; y < src.height; y++) {
      for (var x = 0; x < src.width; x++) {
        final sp = src.getPixel(x, y);
        final bp = blurred.getPixel(x, y);
        final r = (sp.r - bp.r) ~/ 4 + sp.r;
        final g = (sp.g - bp.g) ~/ 4 + sp.g;
        final b = (sp.b - bp.b) ~/ 4 + sp.b;
        result.setPixel(x, y, result.getColor(r.clamp(0,255), g.clamp(0,255), b.clamp(0,255)));
      }
    }
    return result;
  }

  void _removePage(int index) {
    if (index < 0 || index >= _pages.length) return;
    final page = _pages[index];
    setState(() { _selectedPages.remove(page.id); _pages.removeAt(index); if (_pages.isEmpty) _currentPageIndex = -1; else if (_currentPageIndex >= _pages.length) _currentPageIndex = _pages.length - 1; _statusMessage = _pages.isEmpty ? "Listo para escanear" : "${_pages.length} pag(s)"; });
    try { File(page.path).deleteSync(); File(page.originalPath).deleteSync(); } catch (_) {}
  }

  void _toggleSelection(int pid) { setState(() { if (_selectedPages.contains(pid)) _selectedPages.remove(pid); else _selectedPages.add(pid); }); }

  Future<String> _generarPdf(List<String> paths) async {
    final pdf = pw.Document();
    final out = await getApplicationDocumentsDirectory();
    final d = Directory("${out.path}/pdfs");
    if (!await d.exists()) await d.create(recursive: true);
    int cnt = 0;
    for (final p in paths) {
      final f = File(p);
      if (!await f.exists()) continue;
      try {
        final bytes = await f.readAsBytes();
        pdf.addPage(pw.Page(
          pageFormat: PdfPageFormat.a4,
          build: (_) => pw.Center(
            child: pw.Image(pw.MemoryImage(bytes), fit: pw.BoxFit.contain),
          ),
        ));
        cnt++;
      } catch (_) {}
    }
        if (cnt == 0) return "";
          final idBase = (widget.permit?.id ?? 'documento').replaceAll(RegExp(r'[\s/\-]'), '_');
          final fp = "${d.path}/${idBase}.pdf";
    await File(fp).writeAsBytes(await pdf.save());
    return fp;
  }

  Future<void> _finalizar() async {
    if (_selectedPages.isEmpty) { _showStatus("Seleccione al menos una pagina", isError: true); return; }
    setState(() { _isProcessing = true; _statusMessage = "Generando PDF..."; });
    final imgs = _pages.where((p) => _selectedPages.contains(p.id)).map((p) => p.path).toList();
    final pdfPath = await _generarPdf(imgs);
    if (pdfPath.isEmpty) { if (mounted) _showStatus("Error al generar PDF", isError: true); return; }
    setState(() => _statusMessage = "Guardando...");
    final paths = "$pdfPath|${imgs.join("|")}";
    if (widget.permit != null) {
      final upd = widget.permit!.copyWith(imagePath: paths);
      try { await PermisoOfflineService.guardarPermiso(upd); if (!mounted) return; _showStatus("PDF guardado correctamente"); Navigator.pop(context, upd); }
      catch (e) { if (mounted) _showStatus("Error al guardar", isError: true); }
    } else {
      final perm = PermitModel(id: "PT-${DateTime.now().year}-${DateTime.now().millisecond.toString().padLeft(4, "0")}", title: "Permiso escaneado (${imgs.length} pag)", area: "Por asignar", responsible: "Usuario", startDate: DateTime.now(), endDate: DateTime.now().add(const Duration(days: 30)), imagePath: paths);
      try { await PermisoOfflineService.guardarPermiso(perm); if (!mounted) return; _showStatus("PDF guardado correctamente"); Navigator.pop(context, perm); }
      catch (e) { if (mounted) _showStatus("Error al guardar", isError: true); }
    }
  }

  void _showStatus(String msg, {bool isError = false}) {
    setState(() => _statusMessage = msg);
    if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg), behavior: SnackBarBehavior.floating, backgroundColor: isError ? _ScanPal.red : _ScanPal.green, duration: const Duration(seconds: 2)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(backgroundColor: _ScanPal.bg, appBar: _buildAppBar(), body: Column(children: [
      Expanded(child: _pages.isEmpty ? _buildCameraGuide() : _buildPreviewGallery()),
      if (_isProcessing || _isCapturing) const LinearProgressIndicator(backgroundColor: _ScanPal.surfaceLight, color: _ScanPal.accent, minHeight: 3),
      _buildStatusBar(), _buildBottomBar(),
    ]));
  }

  PreferredSizeWidget _buildAppBar() {
    return PreferredSize(preferredSize: const Size.fromHeight(56), child: Container(color: _ScanPal.surface, child: SafeArea(bottom: false, child: Row(children: [
      const SizedBox(width: 8),
      IconButton(icon: const Icon(Icons.close, color: _ScanPal.onSurface), onPressed: () => Navigator.pop(context)),
      const SizedBox(width: 4),
      Expanded(child: Column(mainAxisAlignment: MainAxisAlignment.center, crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text("Escaner de documentos", style: TextStyle(color: _ScanPal.onSurface, fontSize: 16, fontWeight: FontWeight.w600)),
        Text(_pages.isEmpty ? "Sin paginas" : "${_selectedPages.length}/${_pages.length} selec | ${_pages.length} pag(s)", style: const TextStyle(color: _ScanPal.onSurfaceDim, fontSize: 12)),
      ])),
    ]))));
  }

  Widget _buildCameraGuide() {
    return Stack(fit: StackFit.expand, children: [
      Container(color: _ScanPal.bg),
      Center(child: Container(margin: const EdgeInsets.all(24), decoration: BoxDecoration(borderRadius: BorderRadius.circular(12), border: Border.all(color: Colors.white.withValues(alpha: 0.15), width: 1)), child: Stack(children: [
        Positioned(top: -2, left: -2, child: _corner(Alignment.topLeft)),
        Positioned(top: -2, right: -2, child: _corner(Alignment.topRight)),
        Positioned(bottom: -2, left: -2, child: _corner(Alignment.bottomLeft)),
        Positioned(bottom: -2, right: -2, child: _corner(Alignment.bottomRight)),
      ]))),
      Positioned(bottom: 32, left: 0, right: 0, child: Column(children: [
        const Icon(Icons.document_scanner_outlined, color: _ScanPal.onSurfaceDim, size: 32),
        const SizedBox(height: 8),
        Text("Coloca el documento dentro del marco", textAlign: TextAlign.center, style: TextStyle(color: _ScanPal.onSurfaceDim, fontSize: 14, fontWeight: FontWeight.w400)),
        const SizedBox(height: 4),
        Text("Procesamiento profesional automatico", textAlign: TextAlign.center, style: TextStyle(color: _ScanPal.onSurfaceDim, fontSize: 12)),
      ])),
    ]);
  }

  Widget _corner(Alignment align) {
    return Container(width: 24, height: 24, decoration: BoxDecoration(border: Border(
      top: (align == Alignment.topLeft || align == Alignment.topRight) ? const BorderSide(color: _ScanPal.accent, width: 3) : BorderSide.none,
      bottom: (align == Alignment.bottomLeft || align == Alignment.bottomRight) ? const BorderSide(color: _ScanPal.accent, width: 3) : BorderSide.none,
      left: (align == Alignment.topLeft || align == Alignment.bottomLeft) ? const BorderSide(color: _ScanPal.accent, width: 3) : BorderSide.none,
      right: (align == Alignment.topRight || align == Alignment.bottomRight) ? const BorderSide(color: _ScanPal.accent, width: 3) : BorderSide.none,
    )));
  }

  Widget _buildPreviewGallery() {
    if (_pages.isEmpty) return const SizedBox();
    final page = _pages[_currentPageIndex.clamp(0, _pages.length - 1)];
    final sel = _selectedPages.contains(page.id);
    return Column(children: [
      Expanded(child: Padding(padding: const EdgeInsets.all(8), child: ClipRRect(borderRadius: BorderRadius.circular(12), child: Stack(fit: StackFit.expand, children: [
        _buildPageImage(page.path),
        Positioned(top: 12, right: 12, child: GestureDetector(onTap: () => _toggleSelection(page.id), child: Container(padding: const EdgeInsets.all(6), decoration: BoxDecoration(color: sel ? _ScanPal.green : _ScanPal.onSurfaceDim, shape: BoxShape.circle), child: Icon(sel ? Icons.check : Icons.close, color: Colors.white, size: 18)))),
        Positioned(bottom: 12, left: 12, child: Container(padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4), decoration: BoxDecoration(color: Colors.black54, borderRadius: BorderRadius.circular(8)), child: Text("${page.width}x${page.height} px", style: const TextStyle(color: Colors.white, fontSize: 10)))),
      ])))),
      Container(height: 110, color: _ScanPal.surface, child: ListView.builder(scrollDirection: Axis.horizontal, padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8), itemCount: _pages.length, itemBuilder: (_, i) => _buildThumbnail(i))),
    ]);
  }

  Widget _buildThumbnail(int index) {
    final sel = index == _currentPageIndex;
    final page = _pages[index];
    final pageSel = _selectedPages.contains(page.id);
    return GestureDetector(onTap: () => setState(() => _currentPageIndex = index), child: Container(width: 72, margin: const EdgeInsets.symmetric(horizontal: 4), decoration: BoxDecoration(borderRadius: BorderRadius.circular(8), border: Border.all(color: sel ? _ScanPal.accent : _ScanPal.border, width: sel ? 2 : 1)), child: Stack(children: [
      ClipRRect(borderRadius: BorderRadius.circular(7), child: _buildPageImage(page.path, fit: BoxFit.cover)),
      Positioned(top: 4, left: 4, child: Container(padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2), decoration: BoxDecoration(color: Colors.black54, borderRadius: BorderRadius.circular(4)), child: Text("${index + 1}", style: const TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.bold)))),
      Positioned(top: 4, right: 4, child: GestureDetector(onTap: () => _removePage(index), child: Container(width: 20, height: 20, decoration: const BoxDecoration(color: _ScanPal.red, shape: BoxShape.circle), child: const Icon(Icons.close, size: 14, color: Colors.white)))),
      if (!pageSel) Positioned.fill(child: Container(decoration: BoxDecoration(color: Colors.black54, borderRadius: BorderRadius.circular(7)), child: const Center(child: Icon(Icons.visibility_off, color: Colors.white38, size: 20)))),
    ])));
  }

  Widget _buildPageImage(String path, {BoxFit fit = BoxFit.contain}) {
    final f = File(path);
    if (!f.existsSync()) return Container(color: _ScanPal.surfaceLight, child: const Center(child: Icon(Icons.broken_image_outlined, color: _ScanPal.onSurfaceDim, size: 40)));
    return Image.file(f, fit: fit, errorBuilder: (_, __, ___) => Container(color: _ScanPal.surfaceLight, child: const Center(child: Icon(Icons.broken_image_outlined, color: _ScanPal.onSurfaceDim, size: 40))));
  }

  Widget _buildStatusBar() {
    return Container(width: double.infinity, padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8), color: _ScanPal.surface, child: Text(_statusMessage, style: const TextStyle(color: _ScanPal.onSurfaceDim, fontSize: 12)));
  }

  Widget _buildBottomBar() {
    return Container(color: _ScanPal.surface, padding: EdgeInsets.only(bottom: MediaQuery.of(context).padding.bottom + 8, top: 8, left: 16, right: 16), child: Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [
      _ScanButton(icon: Icons.add_a_photo_rounded, label: "Capturar", onTap: _isCapturing || _isProcessing ? null : _capturePage, isLoading: _isCapturing, primary: true),
      if (_pages.isNotEmpty) ...[const SizedBox(width: 12), _ScanButton(icon: Icons.check_circle_outline, label: "Guardar (${_selectedPages.length}/${_pages.length})", onTap: _isProcessing ? null : _finalizar, primary: false)],
    ]));
  }
}

class _ScanButton extends StatelessWidget {
  final IconData icon; final String label; final VoidCallback? onTap; final bool isLoading; final bool primary;
  const _ScanButton({required this.icon, required this.label, this.onTap, this.isLoading = false, this.primary = false});
  @override
  Widget build(BuildContext context) {
    if (primary) return GestureDetector(onTap: onTap, child: AbsorbPointer(absorbing: isLoading, child: AnimatedContainer(duration: const Duration(milliseconds: 200), padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14), decoration: BoxDecoration(color: onTap != null ? _ScanPal.accent : _ScanPal.accent.withValues(alpha: 0.3), borderRadius: BorderRadius.circular(16), boxShadow: onTap != null ? [BoxShadow(color: _ScanPal.accent.withValues(alpha: 0.3), blurRadius: 12, offset: const Offset(0, 4))] : []), child: Row(mainAxisSize: MainAxisSize.min, children: [
      if (isLoading) const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white)) else Icon(icon, color: Colors.white, size: 22), const SizedBox(width: 8), Text(label, style: const TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.w600)),
    ]))));
    return GestureDetector(onTap: onTap, child: AnimatedContainer(duration: const Duration(milliseconds: 200), padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14), decoration: BoxDecoration(color: onTap != null ? _ScanPal.green.withValues(alpha: 0.15) : _ScanPal.surfaceLight, borderRadius: BorderRadius.circular(16), border: Border.all(color: onTap != null ? _ScanPal.green.withValues(alpha: 0.3) : _ScanPal.border)), child: Row(mainAxisSize: MainAxisSize.min, children: [
      Icon(icon, color: onTap != null ? _ScanPal.green : _ScanPal.onSurfaceDim, size: 20), const SizedBox(width: 6), Text(label, style: TextStyle(color: onTap != null ? _ScanPal.green : _ScanPal.onSurfaceDim, fontSize: 14, fontWeight: FontWeight.w600)),
    ])));
  }
}

class _ScannedPage {
  final int id; final String path; final String originalPath; final DateTime capturedAt; final int width; final int height;
  const _ScannedPage({required this.id, required this.path, required this.originalPath, required this.capturedAt, this.width = 0, this.height = 0});
}

