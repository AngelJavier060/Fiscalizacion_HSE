import 'package:flutter/material.dart';
import '../models/documento_model.dart';
import '../services/documento_service.dart';

/// Paleta clara MD3 (consistente con FISCALIZA-AI y lector).
class _Pal {
  static const bg = Color(0xFFFAF8FF);
  static const surface = Color(0xFFFFFFFF);
  static const low = Color(0xFFF2F3FF);
  static const high = Color(0xFFE2E7FF);
  static const border = Color(0xFFC3C5D9);
  static const primary = Color(0xFF003EC7);
  static const container = Color(0xFFEAEDFF);
  static const onSurface = Color(0xFF131B2E);
  static const onSurfaceVar = Color(0xFF434656);
  static const secondary = Color(0xFF006B5B);
  static const error = Color(0xFFBA1A1A);
}

class DocumentoDetalleScreen extends StatefulWidget {
  final int documentoId;
  final String titulo;

  const DocumentoDetalleScreen({
    super.key,
    required this.documentoId,
    required this.titulo,
  });

  @override
  State<DocumentoDetalleScreen> createState() => _DocumentoDetalleScreenState();
}

class _DocumentoDetalleScreenState extends State<DocumentoDetalleScreen> {
  DocumentoDetalle? _detalle;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadDetalle();
  }

  Future<void> _loadDetalle() async {
    try {
      final detalle = await DocumentoService.getDocumentoDetalle(widget.documentoId);
      if (mounted) {
        setState(() {
          _detalle = detalle;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) setState(() => _isLoading = false);
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
              color: _Pal.onSurface, fontWeight: FontWeight.w700, fontSize: 16),
        ),
      ),
      body: _isLoading
          ? const Center(
              child: CircularProgressIndicator(color: _Pal.primary),
            )
          : _detalle == null
              ? const Center(
                  child: Text('Error al cargar',
                      style: TextStyle(color: _Pal.error)))
              : RefreshIndicator(
                  onRefresh: _loadDetalle,
                  color: _Pal.primary,
                  child: ListView(
                    padding: const EdgeInsets.all(16),
                    children: [
                      // Info del documento
                      Card(
                        color: _Pal.surface,
                        elevation: 0,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                          side: const BorderSide(color: _Pal.border),
                        ),
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  Text(
                                    _detalle!.documento.iconoIdioma,
                                    style: const TextStyle(fontSize: 20),
                                  ),
                                  const SizedBox(width: 8),
                                  Text(
                                    _detalle!.documento.idiomaDetectado
                                            ?.toUpperCase() ??
                                        'ES',
                                    style: const TextStyle(
                                        color: _Pal.onSurfaceVar),
                                  ),
                                  const Spacer(),
                                  _Badge(
                                    text: _detalle!.documento.traducido
                                        ? 'Traducido'
                                        : 'Original',
                                    color: _detalle!.documento.traducido
                                        ? _Pal.secondary
                                        : const Color(0xFFB45309),
                                  ),
                                ],
                              ),
                              if (_detalle!.documento.descripcion != null) ...[
                                const SizedBox(height: 12),
                                Text(
                                  _detalle!.documento.descripcion!,
                                  style: const TextStyle(
                                      color: _Pal.onSurfaceVar, fontSize: 13),
                                ),
                              ],
                              const SizedBox(height: 12),
                              Row(
                                children: [
                                  const Icon(Icons.insert_drive_file_outlined,
                                      size: 16, color: _Pal.onSurfaceVar),
                                  const SizedBox(width: 4),
                                  Text(
                                    _detalle!.documento.tamanoFormateado,
                                    style: const TextStyle(
                                        color: _Pal.onSurfaceVar, fontSize: 12),
                                  ),
                                  const SizedBox(width: 16),
                                  const Icon(Icons.calendar_today,
                                      size: 14, color: _Pal.onSurfaceVar),
                                  const SizedBox(width: 4),
                                  Text(
                                    _detalle!.documento.fechaFormateada,
                                    style: const TextStyle(
                                        color: _Pal.onSurfaceVar, fontSize: 12),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ),
                      const SizedBox(height: 16),

                      // Acción principal: leer y escuchar
                      SizedBox(
                        width: double.infinity,
                        child: ElevatedButton.icon(
                          onPressed: () => Navigator.pushNamed(
                            context,
                            '/documento-lector',
                            arguments: {
                              'id': widget.documentoId,
                              'titulo': widget.titulo,
                            },
                          ),
                          icon: const Icon(Icons.headphones_rounded),
                          label: const Text('Leer y escuchar'),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: _Pal.primary,
                            foregroundColor: Colors.white,
                            padding: const EdgeInsets.symmetric(vertical: 16),
                            textStyle: const TextStyle(
                              fontSize: 15,
                              fontWeight: FontWeight.w600,
                            ),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(height: 20),

                      // Puntos Clave
                      Row(
                        children: [
                          const Icon(Icons.checklist_rounded,
                              color: _Pal.onSurface, size: 20),
                          const SizedBox(width: 8),
                          const Text(
                            'Puntos Clave',
                            style: TextStyle(
                              color: _Pal.onSurface,
                              fontSize: 16,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          const SizedBox(width: 8),
                          Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 8, vertical: 2),
                            decoration: BoxDecoration(
                              color: _Pal.container,
                              borderRadius: BorderRadius.circular(99),
                            ),
                            child: Text(
                              '${_detalle!.puntosClave.length}',
                              style: const TextStyle(
                                  color: _Pal.primary,
                                  fontSize: 12,
                                  fontWeight: FontWeight.w600),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),

                      if (_detalle!.puntosClave.isEmpty)
                        Card(
                          color: _Pal.surface,
                          elevation: 0,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12),
                            side: const BorderSide(color: _Pal.border),
                          ),
                          child: const Padding(
                            padding: EdgeInsets.all(24),
                            child: Center(
                              child: Column(
                                children: [
                                  Icon(Icons.lightbulb_outline,
                                      size: 40, color: _Pal.onSurfaceVar),
                                  SizedBox(height: 8),
                                  Text(
                                    'Aún no hay puntos clave para este documento',
                                    style: TextStyle(color: _Pal.onSurfaceVar),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        )
                      else
                        ..._detalle!.puntosClave.map((punto) => Card(
                              margin: const EdgeInsets.only(bottom: 8),
                              color: _Pal.surface,
                              elevation: 0,
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                                side: const BorderSide(color: _Pal.border),
                              ),
                              child: Padding(
                                padding: const EdgeInsets.all(14),
                                child: Row(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      punto.esIa ? '🤖' : '✍️',
                                      style: const TextStyle(fontSize: 16),
                                    ),
                                    const SizedBox(width: 8),
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment:
                                            CrossAxisAlignment.start,
                                        children: [
                                          Text(
                                            punto.contenido,
                                            style: const TextStyle(
                                              color: _Pal.onSurface,
                                              fontSize: 13,
                                              height: 1.45,
                                            ),
                                          ),
                                          if (punto.esIa) ...[
                                            const SizedBox(height: 6),
                                            Row(
                                              children: [
                                                Container(
                                                  padding: const EdgeInsets
                                                      .symmetric(
                                                      horizontal: 6,
                                                      vertical: 2),
                                                  decoration: BoxDecoration(
                                                    color: _Pal.low,
                                                    borderRadius:
                                                        BorderRadius.circular(4),
                                                  ),
                                                  child: Text(
                                                    '${(punto.confianzaIa * 100).toStringAsFixed(0)}%',
                                                    style: const TextStyle(
                                                      color: _Pal.primary,
                                                      fontSize: 11,
                                                      fontWeight:
                                                          FontWeight.w600,
                                                    ),
                                                  ),
                                                ),
                                                const SizedBox(width: 8),
                                                Text(
                                                  punto.revisado
                                                      ? 'Revisado'
                                                      : 'Pendiente',
                                                  style: TextStyle(
                                                    color: punto.revisado
                                                        ? _Pal.secondary
                                                        : const Color(
                                                            0xFFB45309),
                                                    fontSize: 11,
                                                    fontWeight: FontWeight.w500,
                                                  ),
                                                ),
                                              ],
                                            ),
                                          ],
                                        ],
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            )),
                    ],
                  ),
                ),
    );
  }
}

class _Badge extends StatelessWidget {
  final String text;
  final Color color;
  const _Badge({required this.text, required this.color});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: color.withValues(alpha: 0.3)),
      ),
      child: Text(text,
          style: TextStyle(
              color: color, fontSize: 11, fontWeight: FontWeight.w600)),
    );
  }
}
