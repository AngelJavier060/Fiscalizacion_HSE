import 'package:flutter/material.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:speech_to_text/speech_to_text.dart';

import '../services/ia_service.dart';

/// Hoja inferior para PREGUNTAR POR VOZ sobre un documento.
/// Flujo: escucha tu voz (gratis, motor del dispositivo) → consulta a la IA
/// acotada al documento → muestra y LEE la respuesta en voz alta.
class PreguntarVozSheet extends StatefulWidget {
  final int documentoId;
  final int empresaId;

  const PreguntarVozSheet({
    super.key,
    required this.documentoId,
    required this.empresaId,
  });

  @override
  State<PreguntarVozSheet> createState() => _PreguntarVozSheetState();
}

enum _Estado { iniciando, escuchando, pensando, respondiendo, listo, error }

class _PreguntarVozSheetState extends State<PreguntarVozSheet> {
  static const _surface = Color(0xFF1E293B);
  static const _bg = Color(0xFF0F172A);
  static const _border = Color(0xFF334155);
  static const _accent = Color(0xFF059669);
  static const _accentLight = Color(0xFF34D399);
  static const _muted = Color(0xFF94A3B8);

  final SpeechToText _speech = SpeechToText();
  final FlutterTts _tts = FlutterTts();

  _Estado _estado = _Estado.iniciando;
  bool _speechDisponible = false;

  String _pregunta = '';
  String _respuesta = '';
  String _mensaje = '';

  @override
  void initState() {
    super.initState();
    _initTts();
    _initSpeech();
  }

  Future<void> _initTts() async {
    await _tts.setLanguage('es-ES');
    await _tts.setSpeechRate(0.5);
    await _tts.setPitch(1.0);
    _tts.setCompletionHandler(() {
      if (mounted && _estado == _Estado.respondiendo) {
        setState(() => _estado = _Estado.listo);
      }
    });
  }

  Future<void> _initSpeech() async {
    try {
      _speechDisponible = await _speech.initialize(
        onStatus: (status) {
          // Cuando el reconocimiento termina (silencio o fin), procesamos.
          if (status == 'done' || status == 'notListening') {
            if (mounted && _estado == _Estado.escuchando) {
              _procesarPregunta();
            }
          }
        },
        onError: (err) {
          if (!mounted) return;
          setState(() {
            _estado = _Estado.error;
            _mensaje =
                'No se pudo escuchar. Revisa el permiso de micrófono e inténtalo de nuevo.';
          });
        },
      );

      if (!mounted) return;

      if (!_speechDisponible) {
        setState(() {
          _estado = _Estado.error;
          _mensaje =
              'Tu dispositivo no tiene reconocimiento de voz disponible o falta el permiso de micrófono.';
        });
        return;
      }

      await _empezarEscucha();
    } catch (_) {
      if (mounted) {
        setState(() {
          _estado = _Estado.error;
          _mensaje = 'No se pudo iniciar el micrófono.';
        });
      }
    }
  }

  Future<void> _empezarEscucha() async {
    await _tts.stop();
    setState(() {
      _estado = _Estado.escuchando;
      _pregunta = '';
      _respuesta = '';
      _mensaje = '';
    });

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
        setState(() => _pregunta = result.recognizedWords);
      },
    );
  }

  Future<void> _detenerEscucha() async {
    await _speech.stop();
    _procesarPregunta();
  }

  Future<void> _procesarPregunta() async {
    final pregunta = _pregunta.trim();
    if (pregunta.isEmpty) {
      setState(() {
        _estado = _Estado.listo;
        _mensaje = 'No te escuché bien. Pulsa el micrófono e inténtalo otra vez.';
      });
      return;
    }

    setState(() => _estado = _Estado.pensando);

    try {
      final respuesta = await IaService.consultar(
        pregunta: pregunta,
        empresaId: widget.empresaId,
        documentoId: widget.documentoId,
      );
      if (!mounted) return;

      final texto = (respuesta['respuesta'] as String?)?.trim();
      final advertencia = (respuesta['advertencia'] as String?)?.trim();
      final contenido = (texto != null && texto.isNotEmpty)
          ? texto
          : (advertencia != null && advertencia.isNotEmpty)
              ? advertencia
              : 'No encontré información sobre eso en este documento.';

      setState(() {
        _respuesta = contenido;
        _estado = _Estado.respondiendo;
      });
      await _leerRespuesta(contenido);
    } catch (_) {
      if (mounted) {
        setState(() {
          _estado = _Estado.error;
          _mensaje = 'No se pudo consultar. Verifica tu conexión con el servidor.';
        });
      }
    }
  }

  Future<void> _leerRespuesta(String texto) async {
    await _tts.stop();
    // El motor TTS tiene un límite por enunciado; troceamos respuestas largas.
    final trozos = _trocear(texto, 3500);
    for (final t in trozos) {
      await _tts.speak(t);
    }
  }

  List<String> _trocear(String texto, int max) {
    if (texto.length <= max) return [texto];
    final trozos = <String>[];
    final oraciones = texto.split(RegExp(r'(?<=[\.\!\?])\s+'));
    final sb = StringBuffer();
    for (final o in oraciones) {
      if (sb.isNotEmpty && sb.length + o.length > max) {
        trozos.add(sb.toString());
        sb.clear();
      }
      if (sb.isNotEmpty) sb.write(' ');
      sb.write(o);
    }
    if (sb.isNotEmpty) trozos.add(sb.toString());
    return trozos;
  }

  Future<void> _repetir() async {
    if (_respuesta.isEmpty) return;
    setState(() => _estado = _Estado.respondiendo);
    await _leerRespuesta(_respuesta);
  }

  Future<void> _detenerLectura() async {
    await _tts.stop();
    if (mounted) setState(() => _estado = _Estado.listo);
  }

  @override
  void dispose() {
    _speech.stop();
    _tts.stop();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: _surface,
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      padding: EdgeInsets.fromLTRB(
        20,
        12,
        20,
        16 + MediaQuery.of(context).viewInsets.bottom,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 40,
            height: 4,
            margin: const EdgeInsets.only(bottom: 16),
            decoration: BoxDecoration(
              color: _border,
              borderRadius: BorderRadius.circular(99),
            ),
          ),
          Row(
            children: [
              const Icon(Icons.mic_rounded, color: _accentLight, size: 22),
              const SizedBox(width: 8),
              const Expanded(
                child: Text(
                  'Pregunta por voz',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 18,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              IconButton(
                onPressed: () => Navigator.pop(context),
                icon: const Icon(Icons.close_rounded, color: _muted),
              ),
            ],
          ),
          const SizedBox(height: 8),
          _buildEstado(),
          const SizedBox(height: 16),
          _buildAcciones(),
        ],
      ),
    );
  }

  Widget _buildEstado() {
    switch (_estado) {
      case _Estado.iniciando:
        return _texto('Preparando el micrófono…');
      case _Estado.escuchando:
        return Column(
          children: [
            const _OndaMic(),
            const SizedBox(height: 12),
            Text(
              _pregunta.isEmpty ? 'Escuchando… habla ahora' : _pregunta,
              textAlign: TextAlign.center,
              style: TextStyle(
                color: _pregunta.isEmpty ? _muted : Colors.white,
                fontSize: 16,
                height: 1.4,
              ),
            ),
          ],
        );
      case _Estado.pensando:
        return Column(
          children: [
            if (_pregunta.isNotEmpty) _burbujaPregunta(_pregunta),
            const SizedBox(height: 16),
            const CircularProgressIndicator(color: _accent),
            const SizedBox(height: 12),
            _texto('Buscando la respuesta en el documento…'),
          ],
        );
      case _Estado.respondiendo:
      case _Estado.listo:
        return Column(
          children: [
            if (_pregunta.isNotEmpty) _burbujaPregunta(_pregunta),
            if (_respuesta.isNotEmpty) ...[
              const SizedBox(height: 12),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: _bg,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: _border),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(
                      _estado == _Estado.respondiendo
                          ? Icons.graphic_eq_rounded
                          : Icons.volume_up_rounded,
                      color: _accentLight,
                      size: 20,
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        _respuesta,
                        style: const TextStyle(
                          color: Color(0xFFE2E8F0),
                          fontSize: 15,
                          height: 1.5,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
            if (_mensaje.isNotEmpty) ...[
              const SizedBox(height: 12),
              _texto(_mensaje),
            ],
          ],
        );
      case _Estado.error:
        return Column(
          children: [
            const Icon(Icons.error_outline_rounded,
                color: Color(0xFFF87171), size: 36),
            const SizedBox(height: 8),
            _texto(_mensaje),
          ],
        );
    }
  }

  Widget _buildAcciones() {
    final escuchando = _estado == _Estado.escuchando;
    final leyendo = _estado == _Estado.respondiendo;
    final puedeRepetir = _respuesta.isNotEmpty &&
        (_estado == _Estado.listo || _estado == _Estado.error);

    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        if (puedeRepetir) ...[
          _BotonSecundario(
            icon: Icons.replay_rounded,
            label: 'Repetir',
            onTap: _repetir,
          ),
          const SizedBox(width: 16),
        ],
        if (leyendo)
          _BotonSecundario(
            icon: Icons.stop_rounded,
            label: 'Detener',
            onTap: _detenerLectura,
          )
        else
          _BotonMicGrande(
            escuchando: escuchando,
            onTap: escuchando
                ? _detenerEscucha
                : (_estado == _Estado.pensando ? null : _empezarEscucha),
          ),
      ],
    );
  }

  Widget _burbujaPregunta(String texto) {
    return Align(
      alignment: Alignment.centerRight,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        decoration: BoxDecoration(
          color: _accent.withOpacity(0.18),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: _accent.withOpacity(0.4)),
        ),
        child: Text(
          texto,
          style: const TextStyle(color: Colors.white, fontSize: 15),
        ),
      ),
    );
  }

  Widget _texto(String t) => Text(
        t,
        textAlign: TextAlign.center,
        style: const TextStyle(color: _muted, fontSize: 14, height: 1.4),
      );
}

class _OndaMic extends StatefulWidget {
  const _OndaMic();

  @override
  State<_OndaMic> createState() => _OndaMicState();
}

class _OndaMicState extends State<_OndaMic>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c;

  @override
  void initState() {
    super.initState();
    _c = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1100),
    )..repeat();
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _c,
      builder: (context, child) {
        final scale = 1 + _c.value * 0.6;
        return SizedBox(
          width: 90,
          height: 90,
          child: Stack(
            alignment: Alignment.center,
            children: [
              Container(
                width: 70 * scale,
                height: 70 * scale,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: const Color(0xFF059669).withOpacity(0.18 * (1 - _c.value)),
                ),
              ),
              Container(
                width: 64,
                height: 64,
                decoration: const BoxDecoration(
                  shape: BoxShape.circle,
                  color: Color(0xFF059669),
                ),
                child: const Icon(Icons.mic_rounded, color: Colors.white, size: 30),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _BotonMicGrande extends StatelessWidget {
  final bool escuchando;
  final VoidCallback? onTap;
  const _BotonMicGrande({required this.escuchando, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final habilitado = onTap != null;
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 14),
        decoration: BoxDecoration(
          color: escuchando
              ? const Color(0xFFB91C1C)
              : (habilitado ? const Color(0xFF059669) : const Color(0xFF334155)),
          borderRadius: BorderRadius.circular(99),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(escuchando ? Icons.stop_rounded : Icons.mic_rounded,
                color: Colors.white, size: 22),
            const SizedBox(width: 8),
            Text(
              escuchando ? 'Detener y preguntar' : 'Hablar',
              style: const TextStyle(
                color: Colors.white,
                fontSize: 15,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _BotonSecundario extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  const _BotonSecundario({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
        decoration: BoxDecoration(
          color: const Color(0xFF0F172A),
          borderRadius: BorderRadius.circular(99),
          border: Border.all(color: const Color(0xFF334155)),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, color: const Color(0xFF94A3B8), size: 20),
            const SizedBox(width: 6),
            Text(
              label,
              style: const TextStyle(color: Color(0xFFE2E8F0), fontSize: 14),
            ),
          ],
        ),
      ),
    );
  }
}
