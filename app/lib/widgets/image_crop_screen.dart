import 'dart:io';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:image/image.dart' as img;
import 'package:path_provider/path_provider.dart';
import '../services/document_processor.dart';

/// Pantalla de recorte estilo CamScanner.
/// Muestra la imagen con un rectángulo de selección ajustable mediante 4 esquinas.
/// Retorna la ruta del archivo recortado, la imagen original si se elige
/// "Usar completa", o null si se cancela.
class ImageCropScreen extends StatefulWidget {
  final String imagePath;
  final String title;
  final String confirmLabel;
  /// Si true, aplica procesamiento tipo CamScanner tras el recorte.
  final bool autoProcessDoc;

  const ImageCropScreen({
    super.key,
    required this.imagePath,
    this.title = 'Recortar imagen',
    this.confirmLabel = 'Recortar',
    this.autoProcessDoc = false,
  });

  @override
  State<ImageCropScreen> createState() => _ImageCropScreenState();
}

class _ImageCropScreenState extends State<ImageCropScreen> {
  // Selección en coordenadas normalizadas 0-1 del contenedor
  double _left   = 0.06;
  double _top    = 0.06;
  double _right  = 0.94;
  double _bottom = 0.94;

  bool   _processing = false;
  bool   _showHint   = true;
  Size?  _imageNaturalSize;

  // Tamaño de la zona táctil de cada esquina
  static const double _hitArea = 56.0;
  static const double _minSel  = 0.07;

  @override
  void initState() {
    super.initState();
    _loadImageSize();
    // Ocultar pista después de 2.5 s
    Future.delayed(const Duration(milliseconds: 2500), () {
      if (mounted) setState(() => _showHint = false);
    });
  }

  // ── Carga dimensiones reales de la imagen ──────────────────────
  Future<void> _loadImageSize() async {
    try {
      final bytes = await File(widget.imagePath).readAsBytes();
      final codec = await ui.instantiateImageCodec(bytes);
      final frame = await codec.getNextFrame();
      if (mounted) {
        setState(() => _imageNaturalSize = Size(
          frame.image.width.toDouble(),
          frame.image.height.toDouble(),
        ));
      }
    } catch (_) {}
  }

  // Rect donde se muestra la imagen dentro del contenedor (BoxFit.contain)
  Rect _displayRect(Size container) {
    if (_imageNaturalSize == null) {
      return Rect.fromLTWH(0, 0, container.width, container.height);
    }
    final nw = _imageNaturalSize!.width;
    final nh = _imageNaturalSize!.height;
    final scale = (container.width / nw) < (container.height / nh)
        ? container.width / nw
        : container.height / nh;
    final dw = nw * scale;
    final dh = nh * scale;
    return Rect.fromLTWH(
      (container.width  - dw) / 2,
      (container.height - dh) / 2,
      dw,
      dh,
    );
  }

  // ── Mover esquina ──────────────────────────────────────────────
  void _move(String corner, double dx, double dy, double w, double h) {
    setState(() {
      if (_showHint) _showHint = false;
      final ndx = dx / w;
      final ndy = dy / h;
      switch (corner) {
        case 'tl':
          _left  = (_left  + ndx).clamp(0.0, _right  - _minSel);
          _top   = (_top   + ndy).clamp(0.0, _bottom - _minSel);
        case 'tr':
          _right = (_right + ndx).clamp(_left  + _minSel, 1.0);
          _top   = (_top   + ndy).clamp(0.0, _bottom - _minSel);
        case 'bl':
          _left   = (_left   + ndx).clamp(0.0, _right  - _minSel);
          _bottom = (_bottom + ndy).clamp(_top + _minSel, 1.0);
        case 'br':
          _right  = (_right  + ndx).clamp(_left + _minSel, 1.0);
          _bottom = (_bottom + ndy).clamp(_top  + _minSel, 1.0);
      }
    });
  }

  // ── Recortar y retornar ────────────────────────────────────────
  Future<void> _doCrop(Size containerSize) async {
    setState(() => _processing = true);
    try {
      final imgRect = _displayRect(containerSize);
      final bytes   = await File(widget.imagePath).readAsBytes();
      final original = img.decodeImage(bytes);
      if (original == null) {
        if (mounted) Navigator.pop(context, widget.imagePath);
        return;
      }

      // Container px → imagen normalizada
      final selL = (_left   * containerSize.width  - imgRect.left) / imgRect.width;
      final selT = (_top    * containerSize.height - imgRect.top)  / imgRect.height;
      final selR = (_right  * containerSize.width  - imgRect.left) / imgRect.width;
      final selB = (_bottom * containerSize.height - imgRect.top)  / imgRect.height;

      final cL = selL.clamp(0.0, 1.0);
      final cT = selT.clamp(0.0, 1.0);
      final cR = selR.clamp(cL + 0.01, 1.0);
      final cB = selB.clamp(cT + 0.01, 1.0);

      final px = (cL * original.width ).round();
      final py = (cT * original.height).round();
      final pw = ((cR - cL) * original.width ).round().clamp(1, original.width  - px);
      final ph = ((cB - cT) * original.height).round().clamp(1, original.height - py);

      final cropped = img.copyCrop(original, x: px, y: py, width: pw, height: ph);
      final dir = await getTemporaryDirectory();
      final croppedPath =
          '${dir.path}/crop_${DateTime.now().millisecondsSinceEpoch}.jpg';
      await File(croppedPath).writeAsBytes(img.encodeJpg(cropped, quality: 93));

      // Opcionalmente aplicar procesamiento de documento (CamScanner effect)
      String finalPath = croppedPath;
      if (widget.autoProcessDoc) {
        finalPath =
            await DocumentProcessor.processForDocument(croppedPath) ??
            croppedPath;
      }

      if (mounted) Navigator.pop(context, finalPath);
    } catch (_) {
      if (mounted) Navigator.pop(context, widget.imagePath);
    }
  }

  // ── Construir esquina draggable ────────────────────────────────
  Widget _handle(String corner, double w, double h) {
    final double cx = (corner == 'tl' || corner == 'bl') ? _left * w : _right * w;
    final double cy = (corner == 'tl' || corner == 'tr') ? _top  * h : _bottom * h;
    return Positioned(
      left: cx - _hitArea / 2,
      top:  cy - _hitArea / 2,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onPanUpdate: (d) => _move(corner, d.delta.dx, d.delta.dy, w, h),
        child: SizedBox(
          width: _hitArea,
          height: _hitArea,
          child: CustomPaint(painter: _CornerHandlePainter(corner: corner)),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        elevation: 0,
        iconTheme: const IconThemeData(color: Colors.white),
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(widget.title,
                style: const TextStyle(
                    color: Colors.white,
                    fontSize: 16,
                    fontWeight: FontWeight.w700)),
            const Text('Arrastra las esquinas azules para ajustar el área',
                style: TextStyle(color: Colors.white54, fontSize: 11)),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, widget.imagePath),
            child: const Text('Usar completa',
                style: TextStyle(color: Colors.white54, fontSize: 12)),
          ),
        ],
      ),
      body: LayoutBuilder(builder: (context, cs) {
        final w = cs.maxWidth;
        final h = cs.maxHeight - 84;
        return Column(
          children: [
            // ── Vista de recorte ──────────────────────────────────
            SizedBox(
              width: w,
              height: h,
              child: Stack(clipBehavior: Clip.hardEdge, children: [
                // Imagen
                Positioned.fill(
                  child: Image.file(File(widget.imagePath),
                      fit: BoxFit.contain),
                ),
                // Overlay + borde + cuadrícula
                CustomPaint(
                  size: Size(w, h),
                  painter: _SelectionPainter(
                    left:   _left   * w,
                    top:    _top    * h,
                    right:  _right  * w,
                    bottom: _bottom * h,
                  ),
                ),
                // Esquinas draggables
                _handle('tl', w, h),
                _handle('tr', w, h),
                _handle('bl', w, h),
                _handle('br', w, h),
                // Pista de uso (fade automático)
                if (_showHint)
                  Positioned(
                    left: 0, right: 0,
                    top: _top * h + (_bottom - _top) * h / 2 - 22,
                    child: Center(
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 18, vertical: 9),
                        decoration: BoxDecoration(
                          color: Colors.black.withValues(alpha: 0.75),
                          borderRadius: BorderRadius.circular(24),
                        ),
                        child: const Row(mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(Icons.open_with, color: Colors.white, size: 18),
                            SizedBox(width: 8),
                            Text('Arrastra las esquinas azules',
                                style: TextStyle(
                                    color: Colors.white,
                                    fontSize: 13,
                                    fontWeight: FontWeight.w500)),
                          ],
                        ),
                      ),
                    ),
                  ),
              ]),
            ),
            // ── Barra inferior ────────────────────────────────────
            Container(
              height: 84,
              color: const Color(0xFF0D0D1A),
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              child: _processing
                  ? const Center(
                      child: CircularProgressIndicator(
                          color: Color(0xFF4FC3F7)))
                  : Row(children: [
                      Expanded(child: OutlinedButton(
                        onPressed: () => Navigator.pop(context, null),
                        style: OutlinedButton.styleFrom(
                          foregroundColor: Colors.white70,
                          side: const BorderSide(color: Colors.white24),
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(10)),
                        ),
                        child: const Text('Cancelar'),
                      )),
                      const SizedBox(width: 12),
                      Expanded(flex: 2, child: ElevatedButton.icon(
                        onPressed: () => _doCrop(Size(w, h)),
                        icon: const Icon(Icons.crop, size: 18),
                        label: Text(widget.confirmLabel),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xFF003398),
                          foregroundColor: Colors.white,
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(10)),
                        ),
                      )),
                    ]),
            ),
          ],
        );
      }),
    );
  }
}

// ── Pinta el overlay oscuro, borde y cuadrícula de tercios ─────────
class _SelectionPainter extends CustomPainter {
  final double left, top, right, bottom;
  const _SelectionPainter(
      {required this.left,
      required this.top,
      required this.right,
      required this.bottom});

  @override
  void paint(Canvas canvas, Size size) {
    final dark = Paint()..color = Colors.black.withValues(alpha: 0.6);
    canvas.drawRect(Rect.fromLTWH(0, 0, size.width, top), dark);
    canvas.drawRect(
        Rect.fromLTWH(0, bottom, size.width, size.height - bottom), dark);
    canvas.drawRect(Rect.fromLTWH(0, top, left, bottom - top), dark);
    canvas.drawRect(
        Rect.fromLTWH(right, top, size.width - right, bottom - top), dark);

    // Borde de selección
    canvas.drawRect(
      Rect.fromLTRB(left, top, right, bottom),
      Paint()
        ..color = const Color(0xFF4FC3F7)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2.5,
    );

    // Cuadrícula de tercios
    final grid = Paint()
      ..color = Colors.white.withValues(alpha: 0.20)
      ..strokeWidth = 0.8;
    final gw = (right - left) / 3;
    final gh = (bottom - top) / 3;
    for (int i = 1; i <= 2; i++) {
      canvas.drawLine(
          Offset(left + gw * i, top), Offset(left + gw * i, bottom), grid);
      canvas.drawLine(
          Offset(left, top + gh * i), Offset(right, top + gh * i), grid);
    }
  }

  @override
  bool shouldRepaint(_SelectionPainter old) =>
      left != old.left ||
      top != old.top ||
      right != old.right ||
      bottom != old.bottom;
}

// ── Dibuja el handle de esquina: brazos en L + punto azul ──────────
class _CornerHandlePainter extends CustomPainter {
  final String corner;
  const _CornerHandlePainter({required this.corner});

  @override
  void paint(Canvas canvas, Size size) {
    const armLen  = 18.0;
    const thick   = 4.0;
    const dotR    = 7.0;

    final cx = size.width  / 2;
    final cy = size.height / 2;

    final arm = Paint()
      ..color = Colors.white
      ..strokeWidth = thick
      ..strokeCap = StrokeCap.round
      ..style = PaintingStyle.stroke;

    // Brazos en L dependiendo de la esquina
    switch (corner) {
      case 'tl':
        canvas.drawLine(Offset(cx, cy), Offset(cx + armLen, cy), arm);
        canvas.drawLine(Offset(cx, cy), Offset(cx, cy + armLen), arm);
      case 'tr':
        canvas.drawLine(Offset(cx, cy), Offset(cx - armLen, cy), arm);
        canvas.drawLine(Offset(cx, cy), Offset(cx, cy + armLen), arm);
      case 'bl':
        canvas.drawLine(Offset(cx, cy), Offset(cx + armLen, cy), arm);
        canvas.drawLine(Offset(cx, cy), Offset(cx, cy - armLen), arm);
      case 'br':
        canvas.drawLine(Offset(cx, cy), Offset(cx - armLen, cy), arm);
        canvas.drawLine(Offset(cx, cy), Offset(cx, cy - armLen), arm);
    }

    // Punto de anclaje azul
    canvas.drawCircle(
        Offset(cx, cy), dotR, Paint()..color = const Color(0xFF003398));
    canvas.drawCircle(
        Offset(cx, cy),
        dotR,
        Paint()
          ..color = const Color(0xFF4FC3F7)
          ..style = PaintingStyle.stroke
          ..strokeWidth = 2.5);
  }

  @override
  bool shouldRepaint(_CornerHandlePainter old) => corner != old.corner;
}
