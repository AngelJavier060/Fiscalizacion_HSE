import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_pdfview/flutter_pdfview.dart';
import 'package:path_provider/path_provider.dart';

import '../../models/simulador_model.dart';

class SustentoVisorScreen extends StatefulWidget {
  final SustentoArchivo archivo;

  const SustentoVisorScreen({super.key, required this.archivo});

  @override
  State<SustentoVisorScreen> createState() => _SustentoVisorScreenState();
}

class _SustentoVisorScreenState extends State<SustentoVisorScreen> {
  String? _pdfPath;
  String? _error;
  bool _cargando = true;
  int _pagina = 1;
  int _total = 0;

  SustentoArchivo get a => widget.archivo;

  @override
  void initState() {
    super.initState();
    _preparar();
  }

  Future<void> _preparar() async {
    if (a.esImagen) {
      setState(() => _cargando = false);
      return;
    }
    if (!a.esPdf) {
      setState(() {
        _cargando = false;
        _error = 'Este archivo no se puede mostrar aquí.';
      });
      return;
    }
    if (kIsWeb) {
      setState(() {
        _cargando = false;
        _error = 'El visor de PDF está disponible en el teléfono.';
      });
      return;
    }
    try {
      final dir = await getTemporaryDirectory();
      final safe = a.nombre.replaceAll(RegExp(r'[\\/]+'), '_');
      final file = File('${dir.path}/sso_$safe');
      await file.writeAsBytes(a.bytes, flush: true);
      if (!mounted) return;
      setState(() {
        _pdfPath = file.path;
        _cargando = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _cargando = false;
        _error = 'No se pudo abrir el PDF.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final tipo = a.esPdf ? 'PDF' : (a.esImagen ? 'Foto' : 'Archivo');
    return Scaffold(
      backgroundColor: const Color(0xFF0F172A),
      appBar: AppBar(
        backgroundColor: const Color(0xFF111827),
        foregroundColor: Colors.white,
        elevation: 0,
        automaticallyImplyLeading: false,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              tipo,
              style: const TextStyle(
                color: Color(0xFF86EFAC),
                fontSize: 11,
                fontWeight: FontWeight.w800,
                letterSpacing: 0.8,
              ),
            ),
            Text(
              a.nombre,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(color: Colors.white, fontSize: 15, fontWeight: FontWeight.w600),
            ),
          ],
        ),
        actions: [
          if (_pdfPath != null && _total > 0)
            Center(
              child: Padding(
                padding: const EdgeInsets.only(right: 8),
                child: Text(
                  '$_pagina / $_total',
                  style: const TextStyle(color: Color(0xFFCBD5E1), fontSize: 12),
                ),
              ),
            ),
          TextButton.icon(
            onPressed: () => Navigator.pop(context),
            icon: const Icon(Icons.close, color: Color(0xFF0F172A), size: 18),
            label: const Text('Cerrar', style: TextStyle(color: Color(0xFF0F172A), fontWeight: FontWeight.w800)),
            style: TextButton.styleFrom(
              backgroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
            ),
          ),
          const SizedBox(width: 12),
        ],
      ),
      body: _cuerpo(),
    );
  }

  Widget _cuerpo() {
    if (_cargando) {
      return const Center(child: CircularProgressIndicator(color: Colors.white));
    }
    if (_error != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(_error!, style: const TextStyle(color: Colors.white70), textAlign: TextAlign.center),
        ),
      );
    }
    if (a.esImagen) {
      return ColoredBox(
        color: const Color(0xFFE2E8F0),
        child: InteractiveViewer(
          minScale: 0.8,
          maxScale: 4,
          child: Center(
            child: Image.memory(
              Uint8List.fromList(a.bytes),
              fit: BoxFit.contain,
            ),
          ),
        ),
      );
    }
    if (_pdfPath == null) {
      return const Center(
        child: Text('No se pudo mostrar el archivo.', style: TextStyle(color: Colors.white70)),
      );
    }
    return ColoredBox(
      color: const Color(0xFFE2E8F0),
      child: PDFView(
        filePath: _pdfPath!,
        enableSwipe: true,
        swipeHorizontal: false,
        autoSpacing: true,
        pageFling: true,
        onRender: (paginas) {
          if (mounted) setState(() => _total = paginas ?? 0);
        },
        onPageChanged: (pagina, total) {
          if (!mounted) return;
          setState(() {
            _pagina = (pagina ?? 0) + 1;
            _total = total ?? 0;
          });
        },
        onError: (error) {
          if (mounted) setState(() => _error = 'Error al mostrar el PDF: $error');
        },
      ),
    );
  }
}
