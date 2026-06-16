import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_pdfview/flutter_pdfview.dart';
import 'package:path_provider/path_provider.dart';
import '../config/api_config.dart';
import '../services/auth_service.dart';

/// Paleta clara MD3 (consistente con el resto de la app).
class _Pal {
  static const bg = Color(0xFFFAF8FF);
  static const surface = Color(0xFFFFFFFF);
  static const low = Color(0xFFF2F3FF);
  static const high = Color(0xFFE2E7FF);
  static const primary = Color(0xFF003EC7);
  static const onSurface = Color(0xFF131B2E);
  static const onSurfaceVar = Color(0xFF434656);
}

class DocumentoPdfScreen extends StatefulWidget {
  /// ID del documento cuyo PDF se mostrará
  final int documentoId;
  /// Título del documento para mostrar en el AppBar
  final String titulo;

  const DocumentoPdfScreen({
    super.key,
    required this.documentoId,
    required this.titulo,
  });

  @override
  State<DocumentoPdfScreen> createState() => _DocumentoPdfScreenState();
}

class _DocumentoPdfScreenState extends State<DocumentoPdfScreen> {
  String? _pdfPath;
  bool _cargando = true;
  String? _error;
  int _totalPaginas = 0;
  int _paginaActual = 0;
  // Control para PDFView (se mantiene para referencia)
  // ignore: unused_field
  PDFViewController? _pdfController;

  @override
  void initState() {
    super.initState();
    _cargarPdf();
  }

  Future<void> _cargarPdf() async {
    try {
      final token = await AuthService.getToken();
      if (token == null) {
        if (mounted) {
          setState(() {
            _error = 'No hay sesión activa. Inicia sesión de nuevo.';
            _cargando = false;
          });
        }
        return;
      }

      // Construir URL del archivo PDF
      final pdfUrl = '${ApiConfig.baseUrl}${ApiConfig.documentosDetalle}/${widget.documentoId}/archivo';

      // Descargar PDF a archivo temporal
      final dir = await getTemporaryDirectory();
      final archivo = File('${dir.path}/pdf_${widget.documentoId}.pdf');

      // Si ya existe en caché, verificar si está completo
      if (await archivo.exists()) {
        final stat = await archivo.stat();
        if (stat.size > 0) {
          if (mounted) {
            setState(() {
              _pdfPath = archivo.path;
              _cargando = false;
            });
          }
          return;
        }
        // Archivo corrupto, descargar de nuevo
        await archivo.delete();
      }

      // Descargar usando HttpClient con headers de auth
      final client = HttpClient();
      client.connectionTimeout = const Duration(seconds: 30);
      try {
        final request = await client.getUrl(Uri.parse(pdfUrl));
        request.headers.set('Authorization', 'Bearer $token');
        final response = await request.close();

        if (response.statusCode != 200) {
          throw Exception('Error HTTP ${response.statusCode}');
        }

        // Leer los bytes de la respuesta
        final bytes = <int>[];
        await for (final chunk in response) {
          bytes.addAll(chunk);
        }
        await archivo.writeAsBytes(bytes);

        if (mounted) {
          setState(() {
            _pdfPath = archivo.path;
            _cargando = false;
          });
        }
      } finally {
        client.close();
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = 'No se pudo cargar el PDF. Verifica tu conexión.';
          _cargando = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: _Pal.bg,
      appBar: AppBar(
        backgroundColor: _Pal.surface,
        foregroundColor: _Pal.onSurface,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        shape: const Border(
          bottom: BorderSide(color: _Pal.high, width: 1),
        ),
        iconTheme: const IconThemeData(color: _Pal.onSurfaceVar),
        title: Text(
          widget.titulo,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(
            color: _Pal.onSurface,
            fontWeight: FontWeight.w700,
            fontSize: 16,
          ),
        ),
        actions: [
          if (_pdfPath != null) ...[
            // Indicador de página
            Center(
              child: Container(
                margin: const EdgeInsets.only(right: 8),
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: _Pal.low,
                  borderRadius: BorderRadius.circular(99),
                ),
                child: Text(
                  '$_paginaActual / $_totalPaginas',
                  style: const TextStyle(
                    color: _Pal.primary,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ),
          ],
        ],
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_cargando) {
      return const Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            CircularProgressIndicator(color: _Pal.primary),
            SizedBox(height: 16),
            Text(
              'Cargando PDF…',
              style: TextStyle(color: _Pal.onSurfaceVar, fontSize: 15),
            ),
          ],
        ),
      );
    }

    if (_error != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.picture_as_pdf_rounded,
                  size: 64, color: _Pal.onSurfaceVar),
              const SizedBox(height: 16),
              Text(
                _error!,
                textAlign: TextAlign.center,
                style: const TextStyle(
                    color: _Pal.onSurfaceVar, fontSize: 15, height: 1.4),
              ),
              const SizedBox(height: 24),
              ElevatedButton.icon(
                onPressed: () {
                  setState(() {
                    _cargando = true;
                    _error = null;
                  });
                  _cargarPdf();
                },
                icon: const Icon(Icons.refresh_rounded),
                label: const Text('Reintentar'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: _Pal.primary,
                  foregroundColor: Colors.white,
                ),
              ),
            ],
          ),
        ),
      );
    }

    if (_pdfPath == null) {
      return const Center(
        child: Text(
          'No se pudo cargar el PDF',
          style: TextStyle(color: _Pal.onSurfaceVar),
        ),
      );
    }

    // Visor PDF con soporte de zoom (pinch-to-zoom)
    return PDFView(
      filePath: _pdfPath!,
      enableSwipe: true,
      swipeHorizontal: false,
      autoSpacing: true,
      pageFling: true,
      onRender: (paginas) {
        if (mounted) {
          setState(() {
            _totalPaginas = paginas ?? 0;
          });
        }
      },
      onViewCreated: (controller) {
        _pdfController = controller;
      },
      onPageChanged: (pagina, total) {
        if (mounted) {
          setState(() {
            _paginaActual = (pagina ?? 0) + 1;
            _totalPaginas = total ?? 0;
          });
        }
      },
      onError: (error) {
        if (mounted) {
          setState(() {
            _error = 'Error al mostrar el PDF: $error';
          });
        }
      },
    );
  }
}
