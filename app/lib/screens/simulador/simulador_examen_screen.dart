import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../models/simulador_model.dart';
import '../../services/api_service.dart';
import '../../services/simulador_service.dart';

class _Pal {
  static const bg = Color(0xFFFAF8FF);
  static const surface = Color(0xFFFFFFFF);
  static const low = Color(0xFFF2F3FF);
  static const high = Color(0xFFE2E7FF);
  static const border = Color(0xFFC3C5D9);
  static const primary = Color(0xFF003EC7);
  static const onSurface = Color(0xFF131B2E);
  static const onSurfaceVar = Color(0xFF434656);
  static const error = Color(0xFFBA1A1A);
  static const ok = Color(0xFF059669);
  static const warn = Color(0xFFD97706);
}

class SimuladorExamenScreen extends StatefulWidget {
  final int examenId;
  const SimuladorExamenScreen({super.key, required this.examenId});

  @override
  State<SimuladorExamenScreen> createState() => _SimuladorExamenScreenState();
}

class _SimuladorExamenScreenState extends State<SimuladorExamenScreen> {
  final _api = SimuladorService();
  SsoExamen? _examen;
  int _indice = 0;
  String? _error;
  bool _cargando = true;
  bool _finalizando = false;
  int _restanteSeg = 0;
  final Set<int> _marcadas = {};
  Timer? _timer;

  SsoExamenPregunta? get _actual {
    final ex = _examen;
    if (ex == null || ex.preguntas.isEmpty) return null;
    if (_indice < 0 || _indice >= ex.preguntas.length) return null;
    return ex.preguntas[_indice];
  }

  bool get _esUltima =>
      _examen != null && _indice == _examen!.preguntas.length - 1;

  String get _reloj {
    final tope = (_examen?.tiempoLimiteMin ?? 0) * 60;
    var seg = _restanteSeg;
    if (tope > 0 && seg > tope) seg = tope;
    if (seg < 0) seg = 0;
    final m = (seg ~/ 60).toString().padLeft(2, '0');
    final s = (seg % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  String get _modoLabel {
    final modo = _examen?.modo ?? '';
    if (modo == 'falladas') return 'Solo falladas';
    return 'Modo $modo reactivos';
  }

  @override
  void initState() {
    super.initState();
    _abrir();
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _abrir() async {
    try {
      final ex = await _api.obtenerExamen(widget.examenId);
      if (!mounted) return;
      final firstUnanswered = ex.preguntas.indexWhere((p) => p.opcionElegidaId == null);
      setState(() {
        _examen = ex;
        _cargando = false;
        _indice = firstUnanswered >= 0 ? firstUnanswered : 0;
      });
      _arrancarTimer(ex);
    } on ApiException catch (e) {
      if (_agotado(e.message)) {
        _irAResultado();
        return;
      }
      if (!mounted) return;
      setState(() {
        _cargando = false;
        _error = e.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _cargando = false;
        _error = 'No se pudo abrir el examen';
      });
    }
  }

  void _arrancarTimer(SsoExamen ex) {
    _timer?.cancel();
    final tope = ex.tiempoLimiteMin * 60;
    final inicial = ex.segundosRestantes >= 0 ? ex.segundosRestantes : tope;
    _restanteSeg = inicial > tope ? tope : (inicial < 0 ? 0 : inicial);
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      final next = _restanteSeg - 1;
      setState(() => _restanteSeg = next < 0 ? 0 : next);
      if (next <= 0) {
        _timer?.cancel();
        _finalizar();
      }
    });
  }

  bool _agotado(String msg) =>
      RegExp(r'agotó|ya no está en curso', caseSensitive: false).hasMatch(msg);

  Future<void> _elegir(int opcionId) async {
    final ex = _examen;
    final actual = _actual;
    if (ex == null || actual == null || _finalizando) return;
    setState(() => actual.opcionElegidaId = opcionId);
    try {
      final actualizado = await _api.responder(ex.id, actual.id, opcionId);
      if (!mounted) return;
      setState(() => _examen = actualizado);
    } on ApiException catch (e) {
      if (_agotado(e.message)) {
        _irAResultado();
        return;
      }
      if (!mounted) return;
      setState(() => _error = e.message);
    }
  }

  Future<void> _pedirFinalizar() async {
    final sin = _examen?.preguntas.where((p) => p.opcionElegidaId == null).length ?? 0;
    if (sin > 0) {
      final ok = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          backgroundColor: _Pal.surface,
          surfaceTintColor: Colors.transparent,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          title: const Text(
            'Hay preguntas sin responder',
            style: TextStyle(color: _Pal.onSurface, fontWeight: FontWeight.w700),
          ),
          content: Text(
            'Tienes $sin pregunta${sin == 1 ? '' : 's'} sin responder. ¿Finalizar de todos modos?',
            style: const TextStyle(color: _Pal.onSurfaceVar),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Seguir respondiendo', style: TextStyle(color: _Pal.onSurfaceVar)),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              style: FilledButton.styleFrom(backgroundColor: _Pal.primary),
              child: const Text('Finalizar'),
            ),
          ],
        ),
      );
      if (ok != true) return;
    }
    await _finalizar();
  }

  Future<void> _finalizar() async {
    final ex = _examen;
    if (ex == null || _finalizando) return;
    setState(() => _finalizando = true);
    try {
      final r = await _api.finalizar(ex.id);
      if (!mounted) return;
      Navigator.pushReplacementNamed(
        context,
        '/simulador-resultado',
        arguments: {'id': r.id},
      );
    } on ApiException catch (e) {
      if (_agotado(e.message)) {
        _irAResultado();
        return;
      }
      if (!mounted) return;
      setState(() {
        _finalizando = false;
        _error = e.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _finalizando = false;
        _error = 'No se pudo calificar';
      });
    }
  }

  void _irAResultado() {
    if (!mounted) return;
    Navigator.pushReplacementNamed(
      context,
      '/simulador-resultado',
      arguments: {'id': widget.examenId},
    );
  }

  Future<bool> _confirmarSalida() async {
    if (_finalizando) return false;
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: _Pal.surface,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const Text(
          'Salir del examen',
          style: TextStyle(color: _Pal.onSurface, fontWeight: FontWeight.w700),
        ),
        content: const Text(
          'Tu progreso queda guardado. Puedes continuar más tarde mientras el tiempo no se agote.',
          style: TextStyle(color: _Pal.onSurfaceVar),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Seguir', style: TextStyle(color: _Pal.onSurfaceVar)),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Salir', style: TextStyle(color: _Pal.error)),
          ),
        ],
      ),
    );
    return ok == true;
  }

  String _letra(int i) => String.fromCharCode(65 + i);

  String _criticidad(String? tema) {
    final t = (tema ?? '').toLowerCase();
    if (RegExp(r'izaje|asme|altura|ansi|incendio|extintor|el[eé]ctric').hasMatch(t)) {
      return 'Alta';
    }
    if (RegExp(r'legislaci|decreto|epp|clasificad|atex').hasMatch(t)) return 'Alta';
    return 'Media';
  }

  Color _estadoColor(int i) {
    if (i == _indice) return _Pal.primary;
    final p = _examen?.preguntas[i];
    if (p == null) return _Pal.border;
    if (_marcadas.contains(p.id)) return _Pal.warn;
    if (p.opcionElegidaId != null) return _Pal.ok;
    return _Pal.border;
  }

  @override
  Widget build(BuildContext context) {
    final urgente = _restanteSeg <= 60 && _restanteSeg > 0;
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) async {
        if (didPop) return;
        if (await _confirmarSalida() && context.mounted) {
          Navigator.pop(context);
        }
      },
      child: Scaffold(
        backgroundColor: _Pal.bg,
        appBar: AppBar(
          backgroundColor: _Pal.surface,
          surfaceTintColor: _Pal.surface,
          elevation: 0,
          systemOverlayStyle: SystemUiOverlayStyle.dark,
          iconTheme: const IconThemeData(color: _Pal.onSurface),
          shape: const Border(bottom: BorderSide(color: _Pal.high)),
          title: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Examen SSO',
                style: TextStyle(color: _Pal.onSurface, fontWeight: FontWeight.w600, fontSize: 16),
              ),
              if (_examen != null)
                Text(
                  _modoLabel,
                  style: const TextStyle(color: _Pal.onSurfaceVar, fontSize: 12),
                ),
            ],
          ),
          actions: [
            if (_examen != null)
              Padding(
                padding: const EdgeInsets.only(right: 12),
                child: Center(
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                    decoration: BoxDecoration(
                      color: urgente ? const Color(0xFFFFF1F2) : _Pal.low,
                      borderRadius: BorderRadius.circular(99),
                      border: Border.all(color: urgente ? const Color(0xFFFECACA) : _Pal.border),
                    ),
                    child: Row(
                      children: [
                        Icon(
                          Icons.timer_outlined,
                          size: 16,
                          color: urgente ? _Pal.error : _Pal.primary,
                        ),
                        const SizedBox(width: 6),
                        Text(
                          _reloj,
                          style: TextStyle(
                            color: urgente ? _Pal.error : _Pal.onSurface,
                            fontWeight: FontWeight.w800,
                            fontFeatures: const [FontFeature.tabularFigures()],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
          ],
        ),
        body: _cargando
            ? const Center(child: CircularProgressIndicator(color: _Pal.primary))
            : _examen == null
                ? Center(
                    child: Padding(
                      padding: const EdgeInsets.all(24),
                      child: Text(_error ?? 'Examen no disponible', textAlign: TextAlign.center),
                    ),
                  )
                : Column(
                    children: [
                      LinearProgressIndicator(
                        value: _examen!.progreso / 100,
                        minHeight: 4,
                        backgroundColor: _Pal.high,
                        color: _Pal.primary,
                      ),
                      if (_error != null)
                        Padding(
                          padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
                          child: Text(_error!, style: const TextStyle(color: _Pal.error, fontSize: 13)),
                        ),
                      if (_examen!.aviso != null)
                        Padding(
                          padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
                          child: Text(
                            _examen!.aviso!,
                            style: const TextStyle(color: _Pal.warn, fontSize: 13),
                          ),
                        ),
                      Expanded(
                        child: ListView(
                          padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
                          children: [
                            _buildPregunta(),
                            const SizedBox(height: 20),
                            _buildNavegador(),
                          ],
                        ),
                      ),
                      _buildFooter(),
                    ],
                  ),
      ),
    );
  }

  Widget _buildPregunta() {
    final p = _actual;
    if (p == null) return const SizedBox.shrink();
    final crit = _criticidad(p.temaNombre);
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: _Pal.surface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: _Pal.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(
                'Pregunta ${_indice + 1} de ${_examen!.preguntas.length}',
                style: const TextStyle(
                  color: _Pal.primary,
                  fontWeight: FontWeight.w700,
                  fontSize: 12,
                ),
              ),
              const Spacer(),
              if ((p.temaNombre ?? '').isNotEmpty)
                Flexible(
                  child: Text(
                    p.temaNombre!,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: _Pal.onSurfaceVar, fontSize: 12),
                  ),
                ),
              const SizedBox(width: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: crit == 'Alta' ? const Color(0xFFFFF1F2) : _Pal.low,
                  borderRadius: BorderRadius.circular(99),
                ),
                child: Text(
                  crit,
                  style: TextStyle(
                    color: crit == 'Alta' ? _Pal.error : _Pal.primary,
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            p.enunciado,
            style: const TextStyle(
              color: _Pal.onSurface,
              fontSize: 16,
              fontWeight: FontWeight.w600,
              height: 1.4,
            ),
          ),
          const SizedBox(height: 16),
          ...List.generate(p.opciones.length, (i) {
            final op = p.opciones[i];
            final sel = p.opcionElegidaId == op.id;
            return Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: InkWell(
                onTap: _finalizando ? null : () => _elegir(op.id),
                borderRadius: BorderRadius.circular(14),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 150),
                  width: double.infinity,
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: sel ? _Pal.low : _Pal.surface,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(
                      color: sel ? _Pal.primary : _Pal.border,
                      width: sel ? 1.8 : 1,
                    ),
                  ),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Container(
                        width: 28,
                        height: 28,
                        alignment: Alignment.center,
                        decoration: BoxDecoration(
                          color: sel ? _Pal.primary : _Pal.high,
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Text(
                          _letra(i),
                          style: TextStyle(
                            color: sel ? Colors.white : _Pal.onSurface,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          op.texto,
                          style: const TextStyle(color: _Pal.onSurface, height: 1.35),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            );
          }),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              onPressed: () {
                final id = p.id;
                setState(() {
                  if (_marcadas.contains(id)) {
                    _marcadas.remove(id);
                  } else {
                    _marcadas.add(id);
                  }
                });
              },
              icon: Icon(
                _marcadas.contains(p.id) ? Icons.flag : Icons.flag_outlined,
                color: _marcadas.contains(p.id) ? _Pal.warn : _Pal.onSurfaceVar,
              ),
              label: Text(
                _marcadas.contains(p.id) ? 'Marcada para revisar' : 'Marcar para revisar',
                style: TextStyle(
                  color: _marcadas.contains(p.id) ? _Pal.warn : _Pal.onSurfaceVar,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildNavegador() {
    final preguntas = _examen?.preguntas ?? [];
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: List.generate(preguntas.length, (i) {
        final color = _estadoColor(i);
        final actual = i == _indice;
        return InkWell(
          onTap: () => setState(() => _indice = i),
          borderRadius: BorderRadius.circular(8),
          child: Container(
            width: 36,
            height: 36,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: actual ? _Pal.primary : _Pal.surface,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: actual ? _Pal.primary : color, width: 1.6),
            ),
            child: Text(
              '${i + 1}',
              style: TextStyle(
                color: actual ? Colors.white : _Pal.onSurface,
                fontWeight: FontWeight.w700,
                fontSize: 12,
              ),
            ),
          ),
        );
      }),
    );
  }

  Widget _buildFooter() {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 20),
      decoration: const BoxDecoration(
        color: _Pal.surface,
        border: Border(top: BorderSide(color: _Pal.high)),
      ),
      child: Row(
        children: [
          Expanded(
            child: OutlinedButton(
              onPressed: _indice == 0 ? null : () => setState(() => _indice--),
              style: OutlinedButton.styleFrom(
                foregroundColor: _Pal.onSurface,
                side: const BorderSide(color: _Pal.border),
                padding: const EdgeInsets.symmetric(vertical: 14),
              ),
              child: const Text('Anterior'),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            flex: 2,
            child: FilledButton(
              onPressed: _finalizando
                  ? null
                  : _esUltima
                      ? _pedirFinalizar
                      : () => setState(() => _indice++),
              style: FilledButton.styleFrom(
                backgroundColor: _Pal.primary,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 14),
              ),
              child: _finalizando
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                    )
                  : Text(_esUltima ? 'Finalizar' : 'Siguiente'),
            ),
          ),
        ],
      ),
    );
  }
}
