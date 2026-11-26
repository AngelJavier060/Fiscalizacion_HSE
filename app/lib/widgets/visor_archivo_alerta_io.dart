import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_pdfview/flutter_pdfview.dart';

class VisorArchivoAlerta extends StatelessWidget {
  final Uint8List bytes;
  final bool imagen;
  final String? ruta;

  const VisorArchivoAlerta({
    super.key,
    required this.bytes,
    required this.imagen,
    this.ruta,
  });

  @override
  Widget build(BuildContext context) {
    if (imagen) {
      return Image.memory(bytes, fit: BoxFit.fill, width: double.infinity, height: double.infinity);
    }
    final archivo = ruta;
    if (archivo == null || archivo.isEmpty) {
      return const Center(child: Text('Sin archivo'));
    }
    return PDFView(filePath: archivo, fitPolicy: FitPolicy.BOTH, pageFling: false);
  }
}
