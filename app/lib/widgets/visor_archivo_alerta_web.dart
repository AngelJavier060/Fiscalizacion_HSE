import 'dart:html' as html;
import 'dart:typed_data';
import 'dart:ui_web' as ui_web;

import 'package:flutter/material.dart';

class VisorArchivoAlerta extends StatefulWidget {
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
  State<VisorArchivoAlerta> createState() => _VisorArchivoAlertaState();
}

class _VisorArchivoAlertaState extends State<VisorArchivoAlerta> {
  late final String _tipo;
  late final String _url;

  @override
  void initState() {
    super.initState();
    final mime = widget.imagen ? 'image/jpeg' : 'application/pdf';
    final blob = html.Blob([widget.bytes], mime);
    _url = html.Url.createObjectUrlFromBlob(blob);
    _tipo = 'alerta-archivo-${identityHashCode(this)}';
    final src = widget.imagen ? _url : '$_url#toolbar=0&navpanes=0&view=FitH';
    ui_web.platformViewRegistry.registerViewFactory(_tipo, (int viewId) {
      return html.IFrameElement()
        ..src = src
        ..style.border = '0'
        ..style.width = '100%'
        ..style.height = '100%'
        ..style.backgroundColor = 'white';
    });
  }

  @override
  void dispose() {
    html.Url.revokeObjectUrl(_url);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return HtmlElementView(viewType: _tipo);
  }
}
