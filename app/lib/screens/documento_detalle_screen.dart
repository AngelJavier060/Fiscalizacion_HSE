import 'dart:async';

import 'package:flutter/material.dart';
import '../models/documento_model.dart';
import '../services/documento_service.dart';
import '../services/documento_sync_service.dart';
import '../services/documento_offline_service.dart';

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
  static const warning = Color(0xFFB45309);
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

class _DocumentoDetalleScreenState extends State<DocumentoDetalleScreen>
    with WidgetsBindingObserver {
  DocumentoDetalle? _detalle;
  DocumentoTexto? _texto;
  bool _isLoading = true;
  bool _cargandoTexto = true;
  bool _reprocesando = false;
  Timer? _pollTimer;
  static const _pollInterval = Duration(seconds: 10);

  // ─── Edición de puntos clave ──────────────────
  int? _editandoPuntoId;
  String _editandoPuntoTexto = '';
  bool _agregandoPunto = false;
  final _nuevoPuntoController = TextEditingController();
  bool _accionCargando = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _loadDetalle();
    _cargarTexto();
    _iniciarPollContinuo();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _pollTimer?.cancel();
    _nuevoPuntoController.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _loadDetalle();
    }
  }

  void _iniciarPollContinuo() {
    _pollTimer?.cancel();
    _pollTimer =
        Timer.periodic(_pollInterval, (_) => _loadDetalle(silencioso: true));
  }

  Future<void> _cargarTexto() async {
    try {
      final texto =
          await DocumentoService.getTextoCompleto(widget.documentoId);
      if (mounted) {
        setState(() {
          _texto = texto;
          _cargandoTexto = false;
        });
      }
    } catch (_) {
      if (mounted) setState(() => _cargandoTexto = false);
    }
  }

  Future<void> _loadDetalle({bool silencioso = false}) async {
    try {
      final detalle =
          await DocumentoService.getDocumentoDetalle(widget.documentoId);
      final cambio = _detalle != null &&
          await DocumentoSyncService.documentoCambioEnServidor(
              detalle.documento);
      if (cambio) {
        await DocumentoOfflineService.eliminar(widget.documentoId);
      }
      await DocumentoSyncService.registrarDocumento(detalle.documento);
      if (mounted) {
        setState(() {
          _detalle = detalle;
          if (!silencioso) _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted && !silencioso) setState(() => _isLoading = false);
    }
  }

  Future<void> _reprocesar() async {
    if (_reprocesando) return;
    setState(() => _reprocesando = true);
    try {
      await DocumentoService.reprocesar(widget.documentoId);
      if (mounted) await _loadDetalle();
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('No se pudo reprocesar el documento')),
        );
      }
    } finally {
      if (mounted) setState(() => _reprocesando = false);
    }
  }

  void _abrirLector() {
    if (_detalle?.documento.isProcesando ?? false) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content:
              Text('El PDF aún se está procesando. Espere unos minutos.'),
        ),
      );
      return;
    }
    Navigator.pushNamed(
      context,
      '/documento-lector',
      arguments: {'id': widget.documentoId, 'titulo': widget.titulo},
    );
  }

  void _abrirEditor() async {
    if (_detalle?.documento.isProcesando ?? false) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Espere a que termine el procesamiento del PDF.'),
        ),
      );
      return;
    }
    final guardado = await Navigator.pushNamed<bool>(
      context,
      '/documento-editor',
      arguments: {
        'id': widget.documentoId,
        'titulo': widget.titulo,
      },
    );
    if (guardado == true) {
      await _cargarTexto();
      await _loadDetalle(silencioso: true);
    }
  }

  // ─── Puntos Clave CRUD ────────────────────────

  Future<void> _agregarPuntoManual() async {
    final contenido = _nuevoPuntoController.text.trim();
    if (contenido.isEmpty || _accionCargando) return;

    setState(() => _accionCargando = true);
    try {
      await DocumentoService.crearPuntoClave(
        widget.documentoId,
        contenido,
      );
      _nuevoPuntoController.clear();
      await _loadDetalle(silencioso: true);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Punto clave agregado')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Error al agregar punto clave'),
            backgroundColor: _Pal.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _accionCargando = false);
    }
  }

  void _iniciarEdicion(PuntoClaveModel punto) {
    setState(() {
      _editandoPuntoId = punto.id;
      _editandoPuntoTexto = punto.contenido;
    });
  }

  void _cancelarEdicion() {
    setState(() {
      _editandoPuntoId = null;
      _editandoPuntoTexto = '';
    });
  }

  Future<void> _guardarEdicion(PuntoClaveModel punto) async {
    final contenido = _editandoPuntoTexto.trim();
    if (contenido.isEmpty || _accionCargando) return;

    setState(() => _accionCargando = true);
    try {
      await DocumentoService.editarPuntoClave(
        punto.id,
        widget.documentoId,
        contenido,
      );
      _cancelarEdicion();
      await _loadDetalle(silencioso: true);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Punto clave actualizado')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Error al guardar cambios'),
            backgroundColor: _Pal.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _accionCargando = false);
    }
  }

  Future<void> _confirmarEliminar(PuntoClaveModel punto) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: _Pal.surface,
        title: const Text(
          'Eliminar punto clave',
          style: TextStyle(color: _Pal.onSurface),
        ),
        content: Text(
          '¿Estás seguro de eliminar este punto clave?\n\n"${punto.contenido.length > 80 ? '${punto.contenido.substring(0, 80)}…' : punto.contenido}"',
          style: const TextStyle(color: _Pal.onSurfaceVar),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancelar'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text(
              'Eliminar',
              style: TextStyle(color: _Pal.error),
            ),
          ),
        ],
      ),
    );

    if (ok == true) {
      await _eliminarPunto(punto.id);
    }
  }

  Future<void> _eliminarPunto(int puntoId) async {
    if (_accionCargando) return;
    setState(() => _accionCargando = true);
    try {
      await DocumentoService.eliminarPuntoClave(puntoId);
      await _loadDetalle(silencioso: true);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Punto clave eliminado')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Error al eliminar punto clave'),
            backgroundColor: _Pal.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _accionCargando = false);
    }
  }

  Future<void> _marcarRevisado(PuntoClaveModel punto) async {
    if (_accionCargando) return;
    setState(() => _accionCargando = true);
    try {
      await DocumentoService.marcarPuntoRevisado(punto.id);
      await _loadDetalle(silencioso: true);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Error al marcar como revisado'),
            backgroundColor: _Pal.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _accionCargando = false);
    }
  }

  Future<void> _marcarTodosRevisados() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: _Pal.surface,
        title: const Text(
          'Marcar todos como revisados',
          style: TextStyle(color: _Pal.onSurface),
        ),
        content: const Text(
          '¿Confirmas que todos los puntos clave han sido validados?',
          style: TextStyle(color: _Pal.onSurfaceVar),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancelar'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text(
              'Sí, marcar todos',
              style: TextStyle(color: _Pal.secondary),
            ),
          ),
        ],
      ),
    );

    if (ok == true) {
      setState(() => _accionCargando = true);
      try {
        await DocumentoService.marcarTodosRevisados(widget.documentoId);
        await _loadDetalle(silencioso: true);
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Todos marcados como revisados')),
          );
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Error al marcar todos'),
              backgroundColor: _Pal.error,
            ),
          );
        }
      } finally {
        if (mounted) setState(() => _accionCargando = false);
      }
    }
  }

  // ─── UI ──────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    final doc = _detalle?.documento;

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
              fontSize: 16),
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
                      // ── Banners de estado ──
                      if (doc != null && doc.isProcesando)
                        _EstadoBanner(
                          icon: Icons.sync_rounded,
                          color: _Pal.primary,
                          titulo: 'Procesando PDF…',
                          subtitulo:
                              'Extrayendo texto. La lista se actualiza sola.',
                        ),
                      if (doc != null && doc.isError)
                        _EstadoBanner(
                          icon: Icons.error_outline_rounded,
                          color: _Pal.error,
                          titulo: 'Error al procesar',
                          subtitulo: doc.errorProcesamiento ??
                              'No se pudo extraer el texto del PDF.',
                          accion: _reprocesando
                              ? null
                              : TextButton(
                                  onPressed: _reprocesar,
                                  child: const Text('Reprocesar PDF'),
                                ),
                        ),

                      // ── Info del documento ──
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
                      _buildSeccionTexto(doc),

                      const SizedBox(height: 16),

                      // ── Botón Leer y escuchar ──
                      SizedBox(
                        width: double.infinity,
                        child: ElevatedButton.icon(
                          onPressed: _abrirLector,
                          icon: const Icon(Icons.headphones_rounded),
                          label: Text(doc?.isProcesando == true
                              ? 'Esperando procesamiento…'
                              : 'Leer y escuchar'),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: _Pal.primary,
                            foregroundColor: Colors.white,
                            padding:
                                const EdgeInsets.symmetric(vertical: 16),
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

                      // ── Sección Puntos Clave ──
                      _buildSeccionPuntosClave(),
                    ],
                  ),
                ),
    );
  }

  Widget _buildSeccionTexto(DocumentoModel? doc) {
    final procesando = doc?.isProcesando ?? false;
    final tieneTexto = _texto?.tieneTexto ?? false;
    final preview = _texto?.textoParaLectura ?? '';

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Icon(Icons.article_outlined,
                color: _Pal.onSurface, size: 22),
            const SizedBox(width: 8),
            const Expanded(
              child: Text(
                'Texto del procedimiento',
                style: TextStyle(
                  color: _Pal.onSurface,
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
            if (!procesando && !_cargandoTexto)
              TextButton.icon(
                onPressed: _abrirEditor,
                icon: const Icon(Icons.edit_rounded, size: 18),
                label: const Text('Editar'),
              ),
          ],
        ),
        const SizedBox(height: 8),
        Material(
          color: _Pal.surface,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
            side: const BorderSide(color: _Pal.primary, width: 2),
          ),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: procesando ? null : _abrirEditor,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  if (_cargandoTexto)
                    const Center(
                      child: Padding(
                        padding: EdgeInsets.symmetric(vertical: 24),
                        child:
                            CircularProgressIndicator(color: _Pal.primary),
                      ),
                    )
                  else if (procesando)
                    const Text(
                      'El PDF se está procesando. Espere para ver y editar el texto.',
                      style:
                          TextStyle(color: _Pal.onSurfaceVar, height: 1.45),
                    )
                  else if (tieneTexto)
                    Text(
                      preview,
                      maxLines: 12,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: _Pal.onSurface,
                        fontSize: 15,
                        height: 1.55,
                      ),
                    )
                  else
                    const Text(
                      'Aún no hay texto. Toque aquí para escribir o pegar '
                      'el contenido del procedimiento.',
                      style: TextStyle(
                        color: _Pal.onSurfaceVar,
                        fontSize: 14,
                        height: 1.45,
                      ),
                    ),
                  if (!procesando && !_cargandoTexto) ...[
                    const SizedBox(height: 14),
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 12, vertical: 10),
                      decoration: BoxDecoration(
                        color: _Pal.container,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: const Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.touch_app_rounded,
                              color: _Pal.primary, size: 20),
                          SizedBox(width: 8),
                          Text(
                            'Toque para editar y guardar',
                            style: TextStyle(
                              color: _Pal.primary,
                              fontWeight: FontWeight.w700,
                              fontSize: 14,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }

  // ─── PUNTOS CLAVE ────────────────────────────

  Widget _buildSeccionPuntosClave() {
    final puntos = _detalle!.puntosClave;
    final pendientes = puntos.where((p) => p.esIa && !p.revisado).length;
    final revisados = puntos.where((p) => p.revisado).length;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Header
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
              padding:
                  const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              decoration: BoxDecoration(
                color: _Pal.container,
                borderRadius: BorderRadius.circular(99),
              ),
              child: Text(
                '${puntos.length}',
                style: const TextStyle(
                    color: _Pal.primary,
                    fontSize: 12,
                    fontWeight: FontWeight.w600),
              ),
            ),
            if (revisados > 0) ...[
              const SizedBox(width: 6),
              Text(
                '$revisados rev.',
                style: const TextStyle(
                    color: _Pal.secondary,
                    fontSize: 11,
                    fontWeight: FontWeight.w500),
              ),
            ],
            const Spacer(),
            if (pendientes > 0)
              TextButton(
                onPressed: _marcarTodosRevisados,
                child: Text(
                  '$pendientes pendientes',
                  style: const TextStyle(
                    color: _Pal.warning,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
          ],
        ),

        const SizedBox(height: 8),

        // Input para agregar punto manual
        Card(
          color: _Pal.surface,
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
            side: BorderSide(
              color: _agregandoPunto ? _Pal.primary : _Pal.border,
            ),
          ),
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Expanded(
                  child: TextField(
                    controller: _nuevoPuntoController,
                    onChanged: (_) => setState(() {
                      _agregandoPunto =
                          _nuevoPuntoController.text.trim().isNotEmpty;
                    }),
                    minLines: 1,
                    maxLines: 3,
                    style: const TextStyle(
                      color: _Pal.onSurface,
                      fontSize: 13,
                    ),
                    decoration: InputDecoration(
                      hintText: 'Agregar punto clave manual…',
                      hintStyle: TextStyle(
                        color: _Pal.onSurfaceVar.withValues(alpha: 0.7),
                      ),
                      border: InputBorder.none,
                      contentPadding: const EdgeInsets.symmetric(
                          horizontal: 4, vertical: 4),
                      isDense: true,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                _accionCargando
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: _Pal.primary,
                        ),
                      )
                    : IconButton(
                        onPressed:
                            _agregandoPunto ? _agregarPuntoManual : null,
                        icon: const Icon(Icons.add_circle_rounded),
                        color: _agregandoPunto
                            ? _Pal.primary
                            : _Pal.onSurfaceVar,
                        tooltip: 'Agregar punto',
                      ),
              ],
            ),
          ),
        ),

        const SizedBox(height: 8),

        // Lista de puntos clave
        if (puntos.isEmpty)
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
          ...puntos.map((punto) => _buildPuntoCard(punto)),
      ],
    );
  }

  Widget _buildPuntoCard(PuntoClaveModel punto) {
    final editando = _editandoPuntoId == punto.id;

    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      color: _Pal.surface,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(
          color: editando ? _Pal.primary : _Pal.border,
          width: editando ? 1.5 : 1,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (editando)
              _buildEditMode(punto)
            else
              _buildViewMode(punto),
          ],
        ),
      ),
    );
  }

  Widget _buildEditMode(PuntoClaveModel punto) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text(punto.esIa ? '🤖' : '✍️',
                style: const TextStyle(fontSize: 16)),
            const SizedBox(width: 8),
            const Text(
              'Editando punto clave',
              style: TextStyle(
                color: _Pal.primary,
                fontSize: 13,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
        const SizedBox(height: 10),
        TextField(
          controller: TextEditingController.fromValue(
            TextEditingValue(
              text: _editandoPuntoTexto,
              selection: TextSelection.fromPosition(
                TextPosition(offset: _editandoPuntoTexto.length),
              ),
            ),
          ),
          onChanged: (v) => _editandoPuntoTexto = v,
          maxLines: 4,
          minLines: 2,
          style: const TextStyle(
            color: _Pal.onSurface,
            fontSize: 13,
            height: 1.45,
          ),
          decoration: InputDecoration(
            filled: true,
            fillColor: _Pal.low,
            hintText: 'Editar contenido…',
            hintStyle:
                TextStyle(color: _Pal.onSurfaceVar.withValues(alpha: 0.7)),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(8),
              borderSide: const BorderSide(color: _Pal.primary),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(8),
              borderSide: const BorderSide(color: _Pal.primary),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(8),
              borderSide:
                  const BorderSide(color: _Pal.primary, width: 1.5),
            ),
            contentPadding: const EdgeInsets.all(12),
          ),
        ),
        const SizedBox(height: 10),
        Row(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            TextButton(
              onPressed: _accionCargando ? null : _cancelarEdicion,
              child: const Text('Cancelar'),
            ),
            const SizedBox(width: 8),
            ElevatedButton.icon(
              onPressed: _editandoPuntoTexto.trim().isNotEmpty &&
                      !_accionCargando
                  ? () => _guardarEdicion(punto)
                  : null,
              icon: _accionCargando
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: Colors.white,
                      ),
                    )
                  : const Icon(Icons.save_rounded, size: 18),
              label: const Text('Guardar'),
              style: ElevatedButton.styleFrom(
                backgroundColor: _Pal.primary,
                foregroundColor: Colors.white,
                padding:
                    const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                textStyle:
                    const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildViewMode(PuntoClaveModel punto) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(punto.esIa ? '🤖' : '✍️',
                style: const TextStyle(fontSize: 16)),
            const SizedBox(width: 8),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    punto.contenido,
                    style: const TextStyle(
                      color: _Pal.onSurface,
                      fontSize: 13,
                      height: 1.45,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Wrap(
                    spacing: 6,
                    runSpacing: 4,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    children: [
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: punto.esIa
                              ? _Pal.container
                              : _Pal.low,
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Text(
                          punto.esIa ? 'IA' : 'Manual',
                          style: TextStyle(
                            color: punto.esIa
                                ? _Pal.primary
                                : _Pal.onSurfaceVar,
                            fontSize: 10,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                      if (punto.esIa)
                        Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 6, vertical: 2),
                          decoration: BoxDecoration(
                            color: _Pal.low,
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: Text(
                            '${(punto.confianzaIa * 100).toStringAsFixed(0)}%',
                            style: const TextStyle(
                              color: _Pal.primary,
                              fontSize: 10,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: punto.revisado
                              ? _Pal.secondary.withValues(alpha: 0.1)
                              : const Color(0xFFB45309)
                                  .withValues(alpha: 0.1),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              punto.revisado
                                  ? Icons.verified_rounded
                                  : Icons.pending_rounded,
                              size: 12,
                              color: punto.revisado
                                  ? _Pal.secondary
                                  : const Color(0xFFB45309),
                            ),
                            const SizedBox(width: 3),
                            Text(
                              punto.revisado ? 'Revisado' : 'Pendiente',
                              style: TextStyle(
                                color: punto.revisado
                                    ? _Pal.secondary
                                    : const Color(0xFFB45309),
                                fontSize: 10,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),

        const SizedBox(height: 10),
        Row(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            if (punto.esIa && !punto.revisado)
              _ActionButton(
                icon: Icons.check_circle_outline_rounded,
                color: _Pal.secondary,
                tooltip: 'Marcar como revisado',
                onPressed:
                    _accionCargando ? null : () => _marcarRevisado(punto),
              ),
            _ActionButton(
              icon: Icons.edit_note_rounded,
              color: _Pal.primary,
              tooltip: 'Editar punto clave',
              onPressed: _accionCargando
                  ? null
                  : () => _iniciarEdicion(punto),
            ),
            _ActionButton(
              icon: Icons.delete_outline_rounded,
              color: _Pal.error,
              tooltip: 'Eliminar punto clave',
              onPressed: _accionCargando
                  ? null
                  : () => _confirmarEliminar(punto),
            ),
          ],
        ),
      ],
    );
  }
}

// ─── Widget auxiliar: botón de acción ────────

class _ActionButton extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String tooltip;
  final VoidCallback? onPressed;

  const _ActionButton({
    required this.icon,
    required this.color,
    required this.tooltip,
    this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(left: 4),
      child: SizedBox(
        width: 36,
        height: 36,
        child: IconButton(
          visualDensity: VisualDensity.compact,
          padding: EdgeInsets.zero,
          constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
          onPressed: onPressed,
          icon: Icon(icon, size: 20),
          color: onPressed != null ? color : color.withValues(alpha: 0.4),
          tooltip: tooltip,
        ),
      ),
    );
  }
}

// ─── Widgets existentes ──────────────────────

class _EstadoBanner extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String titulo;
  final String subtitulo;
  final Widget? accion;

  const _EstadoBanner({
    required this.icon,
    required this.color,
    required this.titulo,
    required this.subtitulo,
    this.accion,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      color: color.withValues(alpha: 0.08),
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: color.withValues(alpha: 0.35)),
      ),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, color: color, size: 22),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(titulo,
                      style: TextStyle(
                          color: color,
                          fontWeight: FontWeight.w700,
                          fontSize: 14)),
                  const SizedBox(height: 4),
                  Text(subtitulo,
                      style: const TextStyle(
                          color: _Pal.onSurfaceVar, fontSize: 13)),
                  if (accion != null) accion!,
                ],
              ),
            ),
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
