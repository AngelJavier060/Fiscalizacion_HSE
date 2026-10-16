import 'package:flutter/material.dart';
import 'package:edge_detection/edge_detection.dart';
import 'package:google_mlkit_document_scanner/google_mlkit_document_scanner.dart';
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
      // 1. Escáner nativo ML Kit (auto bordes + perspectiva) con fallback a Cunning
      List<String>? pictures;
      try {
        final opts = DocumentScannerOptions(
          documentFormats: const {DocumentFormat.jpeg},
          pageLimit: 1,
          mode: ScannerMode.full,
          isGalleryImport: true,
        );
        final scanner = DocumentScanner(options: opts);
        final result = await scanner.scanDocument();
        await scanner.close();
        pictures = result.images;
      } catch (_) {}
      
      if (pictures == null || pictures.isEmpty) {
        final tempDir = await getTemporaryDirectory();
        final path = "${tempDir.path}/scan_ocr_${DateTime.now().millisecondsSinceEpoch}.jpg";
        bool success = await EdgeDetection.detectEdge(
          path,
          canUseGallery: true,
          androidScanTitle: 'Escaneando',
          androidCropTitle: 'Recortar',
          androidCropBlackWhiteTitle: 'Blanco y Negro',
          androidCropReset: 'Deshacer',
        ) ?? false;
        if (success) {
          pictures = [path];
        }
      }
      
      if (pictures == null || pictures.isEmpty || !mounted) return;

      // 2. Ejecutar OCR sobre la imagen corregida
      final text = await _scanner.runOCROnPath(pictures.first);
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

