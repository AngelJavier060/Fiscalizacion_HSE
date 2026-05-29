import 'package:flutter/material.dart';
import '../models/documento_model.dart';
import '../services/api_service.dart';
import '../services/documento_service.dart';
import '../services/documento_sync_service.dart';
import '../services/documento_offline_service.dart';

/// Pantalla dedicada solo a ver y editar el texto del procedimiento.
class DocumentoEditorScreen extends StatefulWidget {
  final int documentoId;
  final String titulo;

  const DocumentoEditorScreen({
    super.key,
    required this.documentoId,
    required this.titulo,
  });

  @override
  State<DocumentoEditorScreen> createState() => _DocumentoEditorScreenState();
}

class _DocumentoEditorScreenState extends State<DocumentoEditorScreen> {
  static const _primary = Color(0xFF003EC7);
  static const _bg = Color(0xFFFAF8FF);
  static const _surface = Color(0xFFFFFFFF);
  static const _onSurface = Color(0xFF131B2E);
  static const _onSurfaceVar = Color(0xFF434656);
  static const _border = Color(0xFFC3C5D9);

  final _controller = TextEditingController();
  final _focusNode = FocusNode();

  bool _cargando = true;
  bool _guardando = false;
  String? _error;
  bool _huboCambios = false;

  @override
  void initState() {
    super.initState();
    _controller.addListener(() {
      if (!_huboCambios && _controller.text.isNotEmpty) {
        setState(() => _huboCambios = true);
      }
    });
    _cargarTexto();
  }

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  Future<void> _cargarTexto() async {
    setState(() {
      _cargando = true;
      _error = null;
    });
    try {
      final texto =
          await DocumentoService.getTextoCompleto(widget.documentoId);
      _controller.text = texto.textoParaLectura;
      _huboCambios = false;
    } catch (e) {
      _error = e is ApiException
          ? e.message
          : 'No se pudo cargar el texto. Verifique su conexión.';
    }
    if (mounted) {
      setState(() => _cargando = false);
      if (_error == null) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) _focusNode.requestFocus();
        });
      }
    }
  }

  Future<void> _guardar() async {
    final plain = _controller.text.trim();
    if (plain.isEmpty || _guardando) return;

    setState(() => _guardando = true);
    try {
      final html = DocumentoTexto.planoAHtmlParaGuardar(plain);
      final doc = await DocumentoService.guardarTextoExtraido(
        widget.documentoId,
        html,
      );
      await DocumentoSyncService.registrarDocumento(doc);

      final textoResp =
          await DocumentoService.getTextoCompleto(widget.documentoId);
      if (textoResp.tieneTexto) {
        await DocumentoOfflineService.guardarTexto(
          textoResp,
          widget.titulo,
          updatedAt: doc.updatedAt ?? doc.createdAt,
        );
      }

      if (!mounted) return;
      setState(() {
        _guardando = false;
        _huboCambios = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Texto guardado. También se verá en la web.'),
        ),
      );
      Navigator.pop(context, true);
    } catch (e) {
      if (!mounted) return;
      setState(() => _guardando = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            e is ApiException
                ? e.message
                : 'No se pudo guardar. Intente de nuevo.',
          ),
        ),
      );
    }
  }

  Future<bool> _confirmarSalir() async {
    if (!_huboCambios) return true;
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('¿Descartar cambios?'),
        content: const Text('Hay cambios sin guardar en el texto.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Seguir editando'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Salir sin guardar'),
          ),
        ],
      ),
    );
    return ok ?? false;
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: !_huboCambios,
      onPopInvokedWithResult: (didPop, result) async {
        if (didPop) return;
        if (await _confirmarSalir() && context.mounted) {
          Navigator.pop(context);
        }
      },
      child: Scaffold(
        backgroundColor: _bg,
        appBar: AppBar(
          backgroundColor: _surface,
          foregroundColor: _onSurface,
          surfaceTintColor: Colors.transparent,
          elevation: 0,
          title: const Text(
            'Editar texto',
            style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16),
          ),
          bottom: PreferredSize(
            preferredSize: const Size.fromHeight(1),
            child: Container(color: _border, height: 1),
          ),
        ),
        body: _cargando
            ? const Center(
                child: CircularProgressIndicator(color: _primary),
              )
            : _error != null
                ? Center(
                    child: Padding(
                      padding: const EdgeInsets.all(24),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(_error!,
                              textAlign: TextAlign.center,
                              style: const TextStyle(color: _onSurfaceVar)),
                          const SizedBox(height: 16),
                          ElevatedButton(
                            onPressed: _cargarTexto,
                            child: const Text('Reintentar'),
                          ),
                        ],
                      ),
                    ),
                  )
                : Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.fromLTRB(16, 12, 16, 10),
                        color: const Color(0xFFEAEDFF),
                        child: const Row(
                          children: [
                            Icon(Icons.touch_app_rounded,
                                color: _primary, size: 20),
                            SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                'Toque el cuadro de abajo, edite los párrafos '
                                'y pulse GUARDAR CAMBIOS.',
                                style: TextStyle(
                                  color: _onSurfaceVar,
                                  fontSize: 13,
                                  height: 1.35,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      Expanded(
                        child: Padding(
                          padding: const EdgeInsets.all(12),
                          child: TextField(
                            controller: _controller,
                            focusNode: _focusNode,
                            maxLines: null,
                            expands: true,
                            textAlignVertical: TextAlignVertical.top,
                            keyboardType: TextInputType.multiline,
                            style: const TextStyle(
                              color: _onSurface,
                              fontSize: 16,
                              height: 1.55,
                            ),
                            decoration: InputDecoration(
                              filled: true,
                              fillColor: _surface,
                              hintText:
                                  'Escriba o pegue aquí el texto del procedimiento…',
                              hintStyle: TextStyle(
                                color: _onSurfaceVar.withValues(alpha: 0.65),
                              ),
                              border: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(12),
                                borderSide:
                                    const BorderSide(color: _primary, width: 2),
                              ),
                              enabledBorder: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(12),
                                borderSide:
                                    const BorderSide(color: _primary, width: 2),
                              ),
                              focusedBorder: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(12),
                                borderSide: const BorderSide(
                                    color: _primary, width: 2.5),
                              ),
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
        bottomNavigationBar: _cargando || _error != null
            ? null
            : SafeArea(
                child: Container(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
                  decoration: const BoxDecoration(
                    color: _surface,
                    border: Border(top: BorderSide(color: _border)),
                  ),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      SizedBox(
                        width: double.infinity,
                        height: 52,
                        child: ElevatedButton.icon(
                          onPressed: _guardando ? null : _guardar,
                          icon: _guardando
                              ? const SizedBox(
                                  width: 22,
                                  height: 22,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    color: Colors.white,
                                  ),
                                )
                              : const Icon(Icons.save_rounded, size: 24),
                          label: Text(
                            _guardando ? 'Guardando…' : 'GUARDAR CAMBIOS',
                            style: const TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: _primary,
                            foregroundColor: Colors.white,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(height: 8),
                      TextButton(
                        onPressed: _guardando
                            ? null
                            : () async {
                                if (await _confirmarSalir() && context.mounted) {
                                  Navigator.pop(context);
                                }
                              },
                        child: const Text('Cancelar'),
                      ),
                    ],
                  ),
                ),
              ),
      ),
    );
  }
}
