import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:speech_to_text/speech_to_text.dart';

import '../models/notificacion_model.dart';
import '../models/user_model.dart';
import '../services/auth_service.dart';
import '../services/documento_service.dart';
import '../services/fondo_sonido.dart';
import '../services/ia_service.dart';

/// Paleta clara (Material Design 3) compartida por la pantalla del chat.
class _Pal {
  static const bg = Color(0xFFFAF8FF); // background / surface
  static const surface = Color(0xFFFFFFFF); // surface-container-lowest
  static const container = Color(0xFFEAEDFF); // surface-container (burbuja IA)
  static const low = Color(0xFFF2F3FF); // surface-container-low
  static const high = Color(0xFFE2E7FF); // surface-container-high
  static const border = Color(0xFFC3C5D9); // outline-variant
  static const primary = Color(0xFF003EC7);
  static const primaryContainer = Color(0xFF0052FF);
  static const onPrimaryContainer = Color(0xFFDFE3FF);
  static const onSurface = Color(0xFF131B2E);
  static const onSurfaceVar = Color(0xFF434656);
  static const secondary = Color(0xFF006B5B); // verde "En línea"
  static const tertiary = Color(0xFF3E5600);
  static const error = Color(0xFFBA1A1A);
  static const errorContainer = Color(0xFFFFDAD6);
  static const onErrorContainer = Color(0xFF93000A);
}

/// Chat de FISCALIZA-AI: preguntas y respuestas en Markdown, respondiendo
/// SOLO con los documentos cargados en el sistema. Diseño claro (MD3).
class IaChatScreen extends StatefulWidget {
  const IaChatScreen({super.key});

  @override
  State<IaChatScreen> createState() => _IaChatScreenState();
}

class _IaChatScreenState extends State<IaChatScreen> {
  static const _empresaKey = 'doc_empresa_seleccionada';

  final TextEditingController _controller = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  final FlutterTts _tts = FlutterTts();
  final SpeechToText _speech = SpeechToText();
  final FondoSonido _fondo = FondoSonido();

  final List<_ChatMessage> _messages = [];
  bool _isLoading = false;
  int _segundos = 0;
  Timer? _timer;

  bool _speechInit = false;
  bool _escuchando = false;

  int _empresaId = 0;
  bool _esSuperAdmin = false;
  List<EmpresaOpcion> _empresas = [];
  int _docsSistema = 0;

  int? _hablandoMsg; // índice del mensaje que se está leyendo

  final List<String> _sugerencias = const [
    '¿Qué documentos hay cargados en el sistema?',
    'Resume los estándares que salvan vidas',
    '¿Qué dice sobre trabajo en altura?',
    '¿Cuáles son los controles críticos?',
  ];

  @override
  void initState() {
    super.initState();
    _tts.setLanguage('es-ES');
    _tts.setSpeechRate(0.5);
    _tts.setCompletionHandler(() {
      _fondo.detener();
      if (mounted) setState(() => _hablandoMsg = null);
    });
    _fondo.init();
    _initChat();
    _resolverEmpresa();
  }

  @override
  void dispose() {
    _timer?.cancel();
    _controller.dispose();
    _scrollController.dispose();
    _tts.stop();
    _speech.stop();
    _fondo.dispose();
    super.dispose();
  }

  void _initChat() {
    _messages.add(_ChatMessage(
      tipo: 'ia',
      contenido: '¡Hola! Soy **FISCALIZA-AI**, tu asistente experto en '
          'normativas HSE. 📋\n\nRespondo usando **solo los documentos '
          'cargados en el sistema**. ¿Qué te gustaría consultar?',
    ));
  }

  Future<void> _resolverEmpresa() async {
    try {
      final user = UserModel.fromJson(await AuthService().getUserData());
      _esSuperAdmin = user.rol == 'SUPER_ADMIN';

      if (user.empresaId > 0) {
        _empresaId = user.empresaId;
      } else if (_esSuperAdmin) {
        _empresas = await DocumentoService.getEmpresas();
        final prefs = await SharedPreferences.getInstance();
        final guardada = prefs.getInt(_empresaKey);
        if (_empresas.isNotEmpty) {
          final existe = _empresas.any((e) => e.id == guardada);
          _empresaId = existe ? guardada! : _empresas.first.id;
        }
      }
      if (mounted) setState(() {});
      await _cargarEstado();
    } catch (_) {}
  }

  Future<void> _cargarEstado() async {
    if (_empresaId <= 0) return;
    try {
      final estado = await IaService.estado(_empresaId);
      if (!mounted) return;
      setState(() {
        _docsSistema = (estado['documentosActivos'] as num?)?.toInt() ?? 0;
      });
    } catch (_) {}
  }

  Future<void> _cambiarEmpresa(int id) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_empresaKey, id);
    setState(() {
      _empresaId = id;
      _docsSistema = 0;
    });
    await _cargarEstado();
  }

  void _iniciarTimer() {
    _segundos = 0;
    _timer?.cancel();
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) setState(() => _segundos++);
    });
  }

  Future<void> _enviarMensaje([String? preset]) async {
    final texto = (preset ?? _controller.text).trim();
    if (texto.isEmpty || _isLoading) return;

    if (_empresaId <= 0) {
      _mostrarAviso(_esSuperAdmin
          ? 'Selecciona una empresa para consultar sus documentos.'
          : 'Tu usuario no tiene una empresa asignada.');
      return;
    }

    if (_escuchando) {
      await _speech.stop();
      _escuchando = false;
    }

    setState(() {
      _messages.add(_ChatMessage(tipo: 'usuario', contenido: texto));
      _isLoading = true;
    });
    _controller.clear();
    _iniciarTimer();
    _scrollToBottom();

    try {
      final response = await IaService.consultar(
        pregunta: texto,
        empresaId: _empresaId,
      );

      var contenido = response['respuesta'] as String? ??
          'No pude procesar tu consulta.';
      final advertencia = (response['advertencia'] as String?)?.trim();
      if (advertencia != null && advertencia.isNotEmpty) {
        contenido += '\n\n---\n*$advertencia*';
      }

      // Documentos citados (si el backend marcó mostrarReferencias).
      final fuentes = <String>{};
      final resultados = response['resultados'] as List<dynamic>?;
      if (resultados != null) {
        for (final r in resultados) {
          final t = (r as Map)['documentoTitulo'] as String?;
          if (t != null && t.trim().isNotEmpty) fuentes.add(t.trim());
        }
      }

      setState(() {
        _messages.add(_ChatMessage(
          tipo: 'ia',
          contenido: contenido,
          fuentes: fuentes.toList(),
        ));
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _messages.add(_ChatMessage(
          tipo: 'ia',
          contenido:
              'Error de conexión. Verifica tu internet e intenta de nuevo.',
          esError: true,
        ));
        _isLoading = false;
      });
    } finally {
      _timer?.cancel();
    }
    _scrollToBottom();
  }

  Future<void> _leerMensaje(int index) async {
    if (_hablandoMsg == index) {
      await _tts.stop();
      await _fondo.detener();
      setState(() => _hablandoMsg = null);
      return;
    }
    await _tts.stop();
    setState(() => _hablandoMsg = index);
    await _fondo.iniciar();
    await _tts.speak(_limpiarMarkdown(_messages[index].contenido));
  }

  String _limpiarMarkdown(String t) {
    return t
        .replaceAll(RegExp(r'[#*_`>]'), '')
        .replaceAll(RegExp(r'\n{2,}'), '. ')
        .replaceAll('\n', ' ')
        .trim();
  }

  // ---- Preguntar por voz (STT gratis, motor del dispositivo) ----
  Future<void> _toggleMic() async {
    if (_isLoading) return;
    if (_escuchando) {
      await _speech.stop();
      if (mounted) setState(() => _escuchando = false);
      return;
    }

    if (!_speechInit) {
      _speechInit = await _speech.initialize(
        onStatus: (status) {
          if ((status == 'done' || status == 'notListening') && mounted) {
            setState(() => _escuchando = false);
          }
        },
        onError: (_) {
          if (mounted) {
            setState(() => _escuchando = false);
            _mostrarAviso(
                'No se pudo escuchar. Revisa el permiso de micrófono.');
          }
        },
      );
    }

    if (!_speechInit) {
      _mostrarAviso(
          'Tu dispositivo no tiene reconocimiento de voz o falta el permiso de micrófono.');
      return;
    }

    await _tts.stop();
    setState(() => _escuchando = true);
    await _speech.listen(
      listenOptions: SpeechListenOptions(
        partialResults: true,
        cancelOnError: true,
        listenMode: ListenMode.dictation,
        localeId: 'es_ES',
        listenFor: const Duration(seconds: 30),
        pauseFor: const Duration(seconds: 3),
      ),
      onResult: (result) {
        if (!mounted) return;
        setState(() {
          _controller.text = result.recognizedWords;
          _controller.selection = TextSelection.fromPosition(
            TextPosition(offset: _controller.text.length),
          );
        });
      },
    );
  }

  // ---- Historial de conversaciones (backend) ----
  Future<void> _abrirHistorial() async {
    showModalBottomSheet(
      context: context,
      backgroundColor: _Pal.surface,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) {
        return DraggableScrollableSheet(
          expand: false,
          initialChildSize: 0.7,
          maxChildSize: 0.92,
          minChildSize: 0.4,
          builder: (ctx, scrollCtrl) {
            return Column(
              children: [
                Container(
                  width: 40,
                  height: 4,
                  margin: const EdgeInsets.symmetric(vertical: 12),
                  decoration: BoxDecoration(
                    color: _Pal.border,
                    borderRadius: BorderRadius.circular(99),
                  ),
                ),
                const Padding(
                  padding: EdgeInsets.fromLTRB(20, 0, 20, 8),
                  child: Row(
                    children: [
                      Icon(Icons.history, color: _Pal.primary, size: 20),
                      SizedBox(width: 8),
                      Text('Historial de consultas',
                          style: TextStyle(
                              color: _Pal.onSurface,
                              fontSize: 16,
                              fontWeight: FontWeight.w700)),
                    ],
                  ),
                ),
                Expanded(
                  child: FutureBuilder<List<ConsultaIaModel>>(
                    future: IaService.getHistorial(),
                    builder: (context, snap) {
                      if (snap.connectionState == ConnectionState.waiting) {
                        return const Center(
                            child:
                                CircularProgressIndicator(color: _Pal.primary));
                      }
                      if (snap.hasError) {
                        return const Center(
                          child: Padding(
                            padding: EdgeInsets.all(24),
                            child: Text(
                              'No se pudo cargar el historial.',
                              style: TextStyle(color: _Pal.onSurfaceVar),
                              textAlign: TextAlign.center,
                            ),
                          ),
                        );
                      }
                      final items = snap.data ?? [];
                      if (items.isEmpty) {
                        return const Center(
                          child: Padding(
                            padding: EdgeInsets.all(24),
                            child: Text(
                              'Aún no tienes consultas guardadas.',
                              style: TextStyle(color: _Pal.onSurfaceVar),
                              textAlign: TextAlign.center,
                            ),
                          ),
                        );
                      }
                      return ListView.separated(
                        controller: scrollCtrl,
                        padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
                        itemCount: items.length,
                        separatorBuilder: (_, __) =>
                            const SizedBox(height: 10),
                        itemBuilder: (context, i) {
                          final c = items[i];
                          return InkWell(
                            borderRadius: BorderRadius.circular(12),
                            onTap: () {
                              Navigator.pop(ctx);
                              _cargarConsulta(c);
                            },
                            child: Container(
                              padding: const EdgeInsets.all(14),
                              decoration: BoxDecoration(
                                color: _Pal.low,
                                borderRadius: BorderRadius.circular(12),
                                border: Border.all(color: _Pal.border),
                              ),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(
                                    children: [
                                      const Icon(Icons.chat_outlined,
                                          size: 15, color: _Pal.primary),
                                      const SizedBox(width: 6),
                                      Expanded(
                                        child: Text(
                                          c.pregunta,
                                          maxLines: 2,
                                          overflow: TextOverflow.ellipsis,
                                          style: const TextStyle(
                                              color: _Pal.onSurface,
                                              fontSize: 14,
                                              fontWeight: FontWeight.w600),
                                        ),
                                      ),
                                    ],
                                  ),
                                  const SizedBox(height: 6),
                                  Text(
                                    c.respuesta,
                                    maxLines: 2,
                                    overflow: TextOverflow.ellipsis,
                                    style: const TextStyle(
                                        color: _Pal.onSurfaceVar,
                                        fontSize: 12.5,
                                        height: 1.4),
                                  ),
                                  const SizedBox(height: 6),
                                  Text(
                                    _fechaCorta(c.createdAt),
                                    style: TextStyle(
                                        color: _Pal.onSurfaceVar
                                            .withValues(alpha: 0.7),
                                        fontSize: 11),
                                  ),
                                ],
                              ),
                            ),
                          );
                        },
                      );
                    },
                  ),
                ),
              ],
            );
          },
        );
      },
    );
  }

  void _cargarConsulta(ConsultaIaModel c) {
    _tts.stop();
    setState(() {
      _messages
        ..add(_ChatMessage(tipo: 'usuario', contenido: c.pregunta))
        ..add(_ChatMessage(tipo: 'ia', contenido: c.respuesta));
    });
    _scrollToBottom();
  }

  String _fechaCorta(DateTime d) {
    final dd = d.day.toString().padLeft(2, '0');
    final mm = d.month.toString().padLeft(2, '0');
    final hh = d.hour.toString().padLeft(2, '0');
    final mi = d.minute.toString().padLeft(2, '0');
    return '$dd/$mm/${d.year} · $hh:$mi';
  }

  void _mostrarAviso(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(msg, style: const TextStyle(color: Colors.white)),
        backgroundColor: _Pal.onSurface,
      ),
    );
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });
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
        titleSpacing: 0,
        title: Row(
          children: [
            Stack(
              children: [
                Container(
                  width: 38,
                  height: 38,
                  decoration: BoxDecoration(
                    color: _Pal.primaryContainer,
                    borderRadius: BorderRadius.circular(99),
                  ),
                  child: const Icon(Icons.smart_toy_rounded,
                      color: _Pal.onPrimaryContainer, size: 20),
                ),
                Positioned(
                  bottom: 0,
                  right: 0,
                  child: Container(
                    width: 11,
                    height: 11,
                    decoration: BoxDecoration(
                      color: _Pal.secondary,
                      shape: BoxShape.circle,
                      border: Border.all(color: _Pal.surface, width: 2),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(width: 10),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                const Text('FISCALIZA-AI',
                    style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w800,
                        color: _Pal.onSurface)),
                Text(
                  _docsSistema > 0 ? 'En línea · $_docsSistema PDF' : 'En línea',
                  style: const TextStyle(
                      fontSize: 11,
                      color: _Pal.secondary,
                      fontWeight: FontWeight.w600),
                ),
              ],
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: 'Historial',
            icon: const Icon(Icons.history),
            onPressed: _abrirHistorial,
          ),
          if (_messages.length > 1)
            IconButton(
              tooltip: 'Nuevo chat',
              icon: const Icon(Icons.add_comment_outlined),
              onPressed: () {
                _tts.stop();
                setState(() {
                  _messages.clear();
                  _hablandoMsg = null;
                  _initChat();
                });
              },
            ),
        ],
      ),
      body: Column(
        children: [
          if (_esSuperAdmin && _empresas.isNotEmpty) _selectorEmpresa(),
          Expanded(
            child: ListView(
              controller: _scrollController,
              padding: const EdgeInsets.all(16),
              children: [
                ...List.generate(_messages.length, (index) {
                  return _MessageBubble(
                    message: _messages[index],
                    hablando: _hablandoMsg == index,
                    onLeer: _messages[index].tipo == 'ia' && index > 0
                        ? () => _leerMensaje(index)
                        : null,
                  );
                }),
                if (_isLoading) _PensandoIndicador(segundos: _segundos),
                if (_messages.length <= 1 && !_isLoading) ...[
                  const SizedBox(height: 8),
                  const _CapacidadesCard(),
                  const SizedBox(height: 14),
                  _sugerenciasWidget(),
                ],
              ],
            ),
          ),
          _composer(),
        ],
      ),
    );
  }

  Widget _selectorEmpresa() {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 12, 16, 0),
      padding: const EdgeInsets.symmetric(horizontal: 14),
      decoration: BoxDecoration(
        color: _Pal.surface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: _Pal.border),
      ),
      child: Row(
        children: [
          const Icon(Icons.domain, color: _Pal.onSurfaceVar, size: 20),
          const SizedBox(width: 10),
          Expanded(
            child: DropdownButtonHideUnderline(
              child: DropdownButton<int>(
                value: _empresaId > 0 ? _empresaId : null,
                isExpanded: true,
                dropdownColor: _Pal.surface,
                hint: const Text('Selecciona una empresa',
                    style: TextStyle(color: _Pal.onSurfaceVar)),
                icon: const Icon(Icons.expand_more, color: _Pal.onSurfaceVar),
                style: const TextStyle(color: _Pal.onSurface, fontSize: 15),
                items: _empresas
                    .map((e) => DropdownMenuItem<int>(
                          value: e.id,
                          child: Text(e.nombre,
                              overflow: TextOverflow.ellipsis),
                        ))
                    .toList(),
                onChanged: (id) {
                  if (id != null && id != _empresaId) _cambiarEmpresa(id);
                },
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _sugerenciasWidget() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('Prueba preguntar:',
            style: TextStyle(
                color: _Pal.onSurfaceVar,
                fontSize: 13,
                fontWeight: FontWeight.w600)),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: _sugerencias
              .map((s) => _ChipSugerencia(
                    texto: s,
                    onTap: () => _enviarMensaje(s),
                  ))
              .toList(),
        ),
        const SizedBox(height: 8),
      ],
    );
  }

  Widget _composer() {
    return Container(
      padding: EdgeInsets.fromLTRB(
          12, 10, 12, 10 + MediaQuery.of(context).padding.bottom),
      decoration: const BoxDecoration(
        color: _Pal.surface,
        border: Border(top: BorderSide(color: _Pal.high)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Container(
            decoration: BoxDecoration(
              color: _escuchando ? _Pal.error : _Pal.low,
              shape: BoxShape.circle,
              border: Border.all(
                  color: _escuchando ? _Pal.error : _Pal.border),
            ),
            child: IconButton(
              tooltip: _escuchando ? 'Detener' : 'Preguntar por voz',
              icon: Icon(
                _escuchando ? Icons.stop_rounded : Icons.mic_none_rounded,
                color: _escuchando ? Colors.white : _Pal.primary,
              ),
              onPressed: _isLoading ? null : _toggleMic,
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: TextField(
              controller: _controller,
              enabled: !_isLoading,
              minLines: 1,
              maxLines: 5,
              textInputAction: TextInputAction.newline,
              cursorColor: _Pal.primary,
              style: const TextStyle(color: _Pal.onSurface),
              decoration: InputDecoration(
                hintText: _escuchando
                    ? 'Escuchando… habla ahora'
                    : 'Pregunta sobre normativas…',
                hintStyle: TextStyle(
                    color: _escuchando
                        ? _Pal.primary
                        : _Pal.onSurfaceVar.withValues(alpha: 0.7)),
                filled: true,
                fillColor: _Pal.low,
                contentPadding:
                    const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(24),
                  borderSide: const BorderSide(color: _Pal.border),
                ),
                enabledBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(24),
                  borderSide: const BorderSide(color: _Pal.border),
                ),
                focusedBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(24),
                  borderSide: const BorderSide(color: _Pal.primary, width: 1.5),
                ),
              ),
            ),
          ),
          const SizedBox(width: 8),
          Container(
            decoration: const BoxDecoration(
              color: _Pal.primary,
              shape: BoxShape.circle,
            ),
            child: IconButton(
              icon: _isLoading
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                          color: Colors.white, strokeWidth: 2),
                    )
                  : const Icon(Icons.send_rounded, color: Colors.white),
              onPressed: _isLoading ? null : () => _enviarMensaje(),
            ),
          ),
        ],
      ),
    );
  }
}

class _ChipSugerencia extends StatelessWidget {
  final String texto;
  final VoidCallback onTap;
  const _ChipSugerencia({required this.texto, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(99),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        decoration: BoxDecoration(
          color: _Pal.surface,
          borderRadius: BorderRadius.circular(99),
          border: Border.all(color: _Pal.border),
        ),
        child: Text(
          texto,
          style: const TextStyle(
            fontSize: 13,
            color: _Pal.onSurface,
            fontWeight: FontWeight.w500,
            height: 1.3,
          ),
        ),
      ),
    );
  }
}

class _ChatMessage {
  final String tipo; // 'usuario' | 'ia'
  final String contenido;
  final List<String>? fuentes;
  final bool esError;

  _ChatMessage({
    required this.tipo,
    required this.contenido,
    this.fuentes,
    this.esError = false,
  });
}

/// Tarjeta de capacidades (estilo "bento") mostrada al inicio del chat.
class _CapacidadesCard extends StatelessWidget {
  const _CapacidadesCard();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: _Pal.surface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: _Pal.border.withValues(alpha: 0.5)),
        boxShadow: [
          BoxShadow(
            color: _Pal.primary.withValues(alpha: 0.06),
            blurRadius: 18,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: const Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: EdgeInsets.only(bottom: 8, left: 2),
            child: Text('Mis capacidades principales:',
                style: TextStyle(
                    color: _Pal.onSurfaceVar,
                    fontSize: 13,
                    fontWeight: FontWeight.w600)),
          ),
          _CapacidadFila(
            icon: Icons.search_rounded,
            color: _Pal.secondary,
            destacado: 'Buscar',
            texto: ' información en documentos',
          ),
          SizedBox(height: 8),
          _CapacidadFila(
            icon: Icons.description_outlined,
            color: _Pal.primary,
            destacado: 'Resumir',
            texto: ' normativas extensas',
          ),
          SizedBox(height: 8),
          _CapacidadFila(
            icon: Icons.forum_outlined,
            color: _Pal.tertiary,
            destacado: 'Responder',
            texto: ' preguntas sobre seguridad',
          ),
        ],
      ),
    );
  }
}

class _CapacidadFila extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String destacado;
  final String texto;
  const _CapacidadFila({
    required this.icon,
    required this.color,
    required this.destacado,
    required this.texto,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: _Pal.low,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Container(
            width: 32,
            height: 32,
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.12),
              shape: BoxShape.circle,
            ),
            child: Icon(icon, size: 18, color: color),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text.rich(
              TextSpan(
                children: [
                  TextSpan(
                    text: destacado,
                    style: const TextStyle(
                        color: _Pal.onSurface,
                        fontWeight: FontWeight.w700,
                        fontSize: 14),
                  ),
                  TextSpan(
                    text: texto,
                    style: const TextStyle(
                        color: _Pal.onSurface, fontSize: 14),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _MessageBubble extends StatelessWidget {
  final _ChatMessage message;
  final bool hablando;
  final VoidCallback? onLeer;

  const _MessageBubble({
    required this.message,
    this.hablando = false,
    this.onLeer,
  });

  @override
  Widget build(BuildContext context) {
    final isUsuario = message.tipo == 'usuario';
    final esError = message.esError;

    if (isUsuario) {
      return Padding(
        padding: const EdgeInsets.only(bottom: 14),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            Flexible(
              child: Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                decoration: BoxDecoration(
                  color: _Pal.primary,
                  borderRadius: BorderRadius.circular(18)
                      .copyWith(bottomRight: const Radius.circular(4)),
                ),
                child: Text(
                  message.contenido,
                  style: const TextStyle(
                      color: Colors.white, fontSize: 14.5, height: 1.45),
                ),
              ),
            ),
          ],
        ),
      );
    }

    final burbujaColor = esError ? _Pal.errorContainer : _Pal.container;
    final textoColor = esError ? _Pal.onErrorContainer : _Pal.onSurface;

    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 32,
            height: 32,
            margin: const EdgeInsets.only(right: 8, top: 2),
            decoration: BoxDecoration(
              color: esError
                  ? _Pal.error.withValues(alpha: 0.12)
                  : _Pal.primaryContainer,
              borderRadius: BorderRadius.circular(99),
            ),
            child: Icon(
              esError ? Icons.warning_amber_rounded : Icons.smart_toy_rounded,
              color: esError ? _Pal.error : _Pal.onPrimaryContainer,
              size: 18,
            ),
          ),
          Flexible(
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              decoration: BoxDecoration(
                color: burbujaColor,
                borderRadius: BorderRadius.circular(18)
                    .copyWith(bottomLeft: const Radius.circular(4)),
                border: Border.all(
                  color: esError
                      ? _Pal.error.withValues(alpha: 0.25)
                      : _Pal.border.withValues(alpha: 0.4),
                ),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  MarkdownBody(
                    data: message.contenido,
                    selectable: true,
                    styleSheet: MarkdownStyleSheet(
                      p: TextStyle(
                          color: textoColor, fontSize: 14.5, height: 1.5),
                      strong: TextStyle(
                          color: esError ? _Pal.onErrorContainer : _Pal.primary,
                          fontWeight: FontWeight.w700),
                      listBullet: TextStyle(color: textoColor),
                      h1: const TextStyle(
                          color: _Pal.onSurface,
                          fontSize: 18,
                          fontWeight: FontWeight.w700),
                      h2: const TextStyle(
                          color: _Pal.onSurface,
                          fontSize: 16,
                          fontWeight: FontWeight.w700),
                      h3: const TextStyle(
                          color: _Pal.onSurface,
                          fontSize: 15,
                          fontWeight: FontWeight.w700),
                      code: const TextStyle(
                          color: _Pal.primary,
                          backgroundColor: _Pal.high),
                      blockquote: const TextStyle(color: _Pal.onSurfaceVar),
                      a: const TextStyle(color: _Pal.primary),
                    ),
                  ),
                  if (message.fuentes != null &&
                      message.fuentes!.isNotEmpty) ...[
                    const SizedBox(height: 10),
                    Divider(
                        color: _Pal.border.withValues(alpha: 0.6), height: 1),
                    const SizedBox(height: 8),
                    const Text('Documentos consultados:',
                        style: TextStyle(
                            color: _Pal.onSurfaceVar, fontSize: 12)),
                    const SizedBox(height: 4),
                    ...message.fuentes!.map((f) => Padding(
                          padding: const EdgeInsets.only(top: 3),
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Icon(Icons.description_outlined,
                                  size: 14, color: _Pal.primary),
                              const SizedBox(width: 6),
                              Expanded(
                                child: Text(f,
                                    style: const TextStyle(
                                        color: _Pal.primary, fontSize: 12)),
                              ),
                            ],
                          ),
                        )),
                  ],
                  if (onLeer != null) ...[
                    const SizedBox(height: 6),
                    InkWell(
                      onTap: onLeer,
                      borderRadius: BorderRadius.circular(8),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 4, vertical: 4),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              hablando
                                  ? Icons.stop_circle_outlined
                                  : Icons.volume_up_outlined,
                              size: 16,
                              color: _Pal.onSurfaceVar,
                            ),
                            const SizedBox(width: 5),
                            Text(
                              hablando ? 'Detener' : 'Escuchar',
                              style: const TextStyle(
                                  color: _Pal.onSurfaceVar, fontSize: 12),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _PensandoIndicador extends StatelessWidget {
  final int segundos;
  const _PensandoIndicador({required this.segundos});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 32,
            height: 32,
            margin: const EdgeInsets.only(right: 8),
            decoration: BoxDecoration(
              color: _Pal.primaryContainer,
              borderRadius: BorderRadius.circular(99),
            ),
            child: const Icon(Icons.smart_toy_rounded,
                color: _Pal.onPrimaryContainer, size: 18),
          ),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            decoration: BoxDecoration(
              color: _Pal.container,
              border: Border.all(color: _Pal.border.withValues(alpha: 0.4)),
              borderRadius: BorderRadius.circular(18)
                  .copyWith(bottomLeft: const Radius.circular(4)),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const SizedBox(
                  width: 14,
                  height: 14,
                  child: CircularProgressIndicator(
                      strokeWidth: 2, color: _Pal.primary),
                ),
                const SizedBox(width: 10),
                Text(
                  segundos > 0
                      ? 'Consultando el libro… ${segundos}s'
                      : 'Consultando el libro…',
                  style:
                      const TextStyle(color: _Pal.onSurfaceVar, fontSize: 13),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
