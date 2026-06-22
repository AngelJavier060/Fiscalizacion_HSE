import 'dart:io';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:image/image.dart' as img;
import 'package:path_provider/path_provider.dart';
import '../services/scanner_service.dart';

// Colores del tema (MD3 adaptado del HTML)
class _ScanPal {
  static const primary = Color(0xFF003398);
  static const error = Color(0xFFBA1A1A);
}

/// Botón de escaneo que abre la cámara, muestra un selector de área
/// de recorte y luego reconoce el texto OCR para insertarlo en el controlador.
class ScanButton extends StatefulWidget {
  final TextEditingController controller;
  final double iconSize;
  final Color? iconColor;

  const ScanButton({
    super.key,
    required this.controller,
    this.iconSize = 22,
    this.iconColor,
  });

  @override
  State<ScanButton> createState() => _ScanButtonState();
}

class _ScanButtonState extends State<ScanButton> {
  bool _isScanning = false;
  final ScannerService _scanner = ScannerService();

  @override
  void dispose() {
    _scanner.dispose();
    super.dispose();
  }

  Future<void> _scan() async {
    if (_isScanning) return;
    setState(() => _isScanning = true);

    try {
      // 1. Tomar foto con la cámara
      final imagePath = await _scanner.pickImagePath();
      if (imagePath == null || !mounted) return;

      // 2. Mostrar pantalla de recorte para seleccionar el área de texto
      final croppedPath = await Navigator.push<String?>(
        context,
        MaterialPageRoute(
          builder: (_) => _OcrCropScreen(imagePath: imagePath),
          fullscreenDialog: true,
        ),
      );
      if (croppedPath == null || !mounted) return;

      // 3. Ejecutar OCR sobre la imagen recortada/seleccionada
      final text = await _scanner.runOCROnPath(croppedPath);
      if (!mounted) return;

      if (text != null && text.isNotEmpty) {
        final current = widget.controller.text;
        final cursor = widget.controller.selection.baseOffset;
        final pos = cursor >= 0 ? cursor : current.length;
        widget.controller.text =
            '${current.substring(0, pos)}$text${current.substring(pos)}';
        widget.controller.selection =
            TextSelection.collapsed(offset: pos + text.length);
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Texto transcrito correctamente'),
          backgroundColor: Color(0xFF3B6D11),
          behavior: SnackBarBehavior.floating,
          duration: Duration(seconds: 2),
        ));
      } else if (text != null) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('No se reconoció texto en el área seleccionada'),
          behavior: SnackBarBehavior.floating,
          duration: Duration(seconds: 2),
        ));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Error: ${e.toString().replaceAll('Exception: ', '')}'),
          backgroundColor: _ScanPal.error,
          behavior: SnackBarBehavior.floating,
          duration: const Duration(seconds: 3),
        ));
      }
    } finally {
      if (mounted) setState(() => _isScanning = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        borderRadius: BorderRadius.circular(20),
        onTap: _scan,
        child: Padding(
          padding: const EdgeInsets.all(6),
          child: _isScanning
              ? SizedBox(
                  width: widget.iconSize,
                  height: widget.iconSize,
                  child: const CircularProgressIndicator(
                      strokeWidth: 2, color: _ScanPal.primary),
                )
              : Icon(Icons.document_scanner_outlined,
                  size: widget.iconSize,
                  color: widget.iconColor ?? _ScanPal.primary),
        ),
      ),
    );
  }
}

// ═══════════════════════════════════════════════════════════════════
// Pantalla de recorte para OCR
// ═══════════════════════════════════════════════════════════════════

class _OcrCropScreen extends StatefulWidget {
  final String imagePath;
  const _OcrCropScreen({required this.imagePath});

  @override
  State<_OcrCropScreen> createState() => _OcrCropScreenState();
}

class _OcrCropScreenState extends State<_OcrCropScreen> {
  // Selección en coordenadas normalizadas 0-1 relativas al contenedor
  double _left = 0.05;
  double _top = 0.10;
  double _right = 0.95;
  double _bottom = 0.90;

  bool _processing = false;
  Size? _imageNaturalSize;

  static const double _hr = 18.0; // handle radius

  @override
  void initState() {
    super.initState();
    _loadImageSize();
  }

  Future<void> _loadImageSize() async {
    try {
      final bytes = await File(widget.imagePath).readAsBytes();
      final codec = await ui.instantiateImageCodec(bytes);
      final frame = await codec.getNextFrame();
      if (mounted) {
        setState(() => _imageNaturalSize =
            Size(frame.image.width.toDouble(), frame.image.height.toDouble()));
      }
    } catch (_) {}
  }

  // Rect real de la imagen dentro del contenedor (BoxFit.contain)
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
      (container.width - dw) / 2,
      (container.height - dh) / 2,
      dw,
      dh,
    );
  }

  void _moveHandle(String corner, double dx, double dy, double w, double h) {
    const minSel = 0.06;
    setState(() {
      final ndx = dx / w;
      final ndy = dy / h;
      switch (corner) {
        case 'tl':
          _left = (_left + ndx).clamp(0.0, _right - minSel);
          _top = (_top + ndy).clamp(0.0, _bottom - minSel);
        case 'tr':
          _right = (_right + ndx).clamp(_left + minSel, 1.0);
          _top = (_top + ndy).clamp(0.0, _bottom - minSel);
        case 'bl':
          _left = (_left + ndx).clamp(0.0, _right - minSel);
          _bottom = (_bottom + ndy).clamp(_top + minSel, 1.0);
        case 'br':
          _right = (_right + ndx).clamp(_left + minSel, 1.0);
          _bottom = (_bottom + ndy).clamp(_top + minSel, 1.0);
      }
    });
  }

  Future<void> _confirmar(Size containerSize) async {
    setState(() => _processing = true);
    try {
      final imgRect = _displayRect(containerSize);
      final bytes = await File(widget.imagePath).readAsBytes();
      final original = img.decodeImage(bytes);
      if (original == null) {
        if (mounted) Navigator.pop(context, widget.imagePath);
        return;
      }

      // Convertir selección (coord. container) a coord. imagen
      final selL = (_left * containerSize.width - imgRect.left) / imgRect.width;
      final selT = (_top * containerSize.height - imgRect.top) / imgRect.height;
      final selR = (_right * containerSize.width - imgRect.left) / imgRect.width;
      final selB = (_bottom * containerSize.height - imgRect.top) / imgRect.height;

      final cL = selL.clamp(0.0, 1.0);
      final cT = selT.clamp(0.0, 1.0);
      final cR = selR.clamp(cL + 0.01, 1.0);
      final cB = selB.clamp(cT + 0.01, 1.0);

      final px = (cL * original.width).round();
      final py = (cT * original.height).round();
      final pw = ((cR - cL) * original.width).round().clamp(1, original.width - px);
      final ph = ((cB - cT) * original.height).round().clamp(1, original.height - py);

      final cropped = img.copyCrop(original, x: px, y: py, width: pw, height: ph);
      final dir = await getTemporaryDirectory();
      final outPath =
          '${dir.path}/ocr_crop_${DateTime.now().millisecondsSinceEpoch}.jpg';
      await File(outPath).writeAsBytes(img.encodeJpg(cropped, quality: 92));
      if (mounted) Navigator.pop(context, outPath);
    } catch (_) {
      if (mounted) Navigator.pop(context, widget.imagePath);
    }
  }

  Widget _buildHandle(String corner, double w, double h) {
    double x, y;
    switch (corner) {
      case 'tl':
        x = _left * w;
        y = _top * h;
      case 'tr':
        x = _right * w;
        y = _top * h;
      case 'bl':
        x = _left * w;
        y = _bottom * h;
      default: // br
        x = _right * w;
        y = _bottom * h;
    }
    return Positioned(
      left: x - _hr,
      top: y - _hr,
      child: GestureDetector(
        onPanUpdate: (d) =>
            _moveHandle(corner, d.delta.dx, d.delta.dy, w, h),
        child: Container(
          width: _hr * 2,
          height: _hr * 2,
          decoration: BoxDecoration(
            color: const Color(0xFF003398),
            shape: BoxShape.circle,
            border: Border.all(color: Colors.white, width: 2.5),
            boxShadow: [
              BoxShadow(
                  color: Colors.black.withValues(alpha: 0.45), blurRadius: 6)
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: const Color(0xFF0A0A1A),
        iconTheme: const IconThemeData(color: Colors.white),
        title: const Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Seleccionar área de texto',
                style: TextStyle(
                    color: Colors.white,
                    fontSize: 15,
                    fontWeight: FontWeight.w600)),
            Text('Arrastra las esquinas azules para ajustar',
                style: TextStyle(color: Colors.white54, fontSize: 11)),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, widget.imagePath),
            child: const Text('Usar completa',
                style:
                    TextStyle(color: Colors.white60, fontSize: 13)),
          ),
        ],
      ),
      body: LayoutBuilder(
        builder: (context, constraints) {
          final w = constraints.maxWidth;
          final h = constraints.maxHeight - 76;
          return Column(
            children: [
              SizedBox(
                width: w,
                height: h,
                child: Stack(children: [
                  Positioned.fill(
                    child: Image.file(File(widget.imagePath),
                        fit: BoxFit.contain),
                  ),
                  CustomPaint(
                    size: Size(w, h),
                    painter: _CropOverlayPainter(
                      left: _left * w,
                      top: _top * h,
                      right: _right * w,
                      bottom: _bottom * h,
                    ),
                  ),
                  _buildHandle('tl', w, h),
                  _buildHandle('tr', w, h),
                  _buildHandle('bl', w, h),
                  _buildHandle('br', w, h),
                ]),
              ),
              // Barra inferior
              Container(
                height: 76,
                color: const Color(0xFF0A0A1A),
                padding:
                    const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                child: _processing
                    ? const Center(
                        child: CircularProgressIndicator(
                            color: Color(0xFF003398)))
                    : Row(children: [
                        Expanded(
                          child: OutlinedButton(
                            onPressed: () => Navigator.pop(context, null),
                            style: OutlinedButton.styleFrom(
                              foregroundColor: Colors.white,
                              side: const BorderSide(color: Colors.white30),
                              shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(10)),
                            ),
                            child: const Text('Cancelar'),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          flex: 2,
                          child: ElevatedButton.icon(
                            onPressed: () => _confirmar(Size(w, h)),
                            icon: const Icon(Icons.crop, size: 18),
                            label: const Text('Recortar y transcribir'),
                            style: ElevatedButton.styleFrom(
                              backgroundColor: const Color(0xFF003398),
                              foregroundColor: Colors.white,
                              shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(10)),
                            ),
                          ),
                        ),
                      ]),
              ),
            ],
          );
        },
      ),
    );
  }
}

// Dibuja el overlay oscuro alrededor de la selección + borde + cuadrícula
class _CropOverlayPainter extends CustomPainter {
  final double left, top, right, bottom;
  const _CropOverlayPainter(
      {required this.left,
      required this.top,
      required this.right,
      required this.bottom});

  @override
  void paint(Canvas canvas, Size size) {
    final shadow = Paint()..color = Colors.black.withValues(alpha: 0.55);
    canvas.drawRect(Rect.fromLTWH(0, 0, size.width, top), shadow);
    canvas.drawRect(
        Rect.fromLTWH(0, bottom, size.width, size.height - bottom), shadow);
    canvas.drawRect(Rect.fromLTWH(0, top, left, bottom - top), shadow);
    canvas.drawRect(
        Rect.fromLTWH(right, top, size.width - right, bottom - top), shadow);

    // Borde de selección
    final border = Paint()
      ..color = const Color(0xFF4FC3F7)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.0;
    canvas.drawRect(Rect.fromLTRB(left, top, right, bottom), border);

    // Cuadrícula de tercios
    final grid = Paint()
      ..color = Colors.white.withValues(alpha: 0.22)
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
  bool shouldRepaint(_CropOverlayPainter old) =>
      left != old.left ||
      top != old.top ||
      right != old.right ||
      bottom != old.bottom;
}
