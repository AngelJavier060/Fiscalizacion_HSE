import 'package:flutter/material.dart';
import '../services/scanner_service.dart';

// Colores del tema (MD3 adaptado del HTML)
class _ScanPal {
  static const primary = Color(0xFF003398);
  static const error = Color(0xFFBA1A1A);
}

/// Botón de escaneo que abre la cámara, reconoce texto OCR
/// y lo inserta en un [TextEditingController].
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
      final String? text = await _scanner.scanText();

      if (text != null && text.isNotEmpty) {
        // Insertar el texto escaneado al final del contenido existente
        final currentText = widget.controller.text;
        final cursorPos = widget.controller.selection.baseOffset;

        if (currentText.isEmpty) {
          widget.controller.text = text;
          widget.controller.selection = TextSelection.collapsed(offset: text.length);
        } else {
          // Insertar en la posición del cursor o al final
          final insertPos = cursorPos >= 0 ? cursorPos : currentText.length;
          final newText = '${currentText.substring(0, insertPos)}$text${currentText.substring(insertPos)}';
          widget.controller.text = newText;
          widget.controller.selection = TextSelection.collapsed(
            offset: insertPos + text.length,
          );
        }

        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Texto escaneado correctamente'),
              behavior: SnackBarBehavior.floating,
              duration: Duration(seconds: 2),
              backgroundColor: Color(0xFF3B6D11),
            ),
          );
        }
      } else if (text != null && text.isEmpty) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('No se reconoció texto en la imagen'),
              behavior: SnackBarBehavior.floating,
              duration: Duration(seconds: 2),
            ),
          );
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Error al escanear: ${e.toString().replaceAll('Exception: ', '')}'),
            behavior: SnackBarBehavior.floating,
            duration: const Duration(seconds: 3),
            backgroundColor: _ScanPal.error,
          ),
        );
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
                    strokeWidth: 2,
                    color: _ScanPal.primary,
                  ),
                )
              : Icon(
                  Icons.document_scanner_outlined,
                  size: widget.iconSize,
                  color: widget.iconColor ?? _ScanPal.primary,
                ),
        ),
      ),
    );
  }
}
