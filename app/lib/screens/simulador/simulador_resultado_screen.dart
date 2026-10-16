import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';

import '../../models/simulador_model.dart';
import '../../services/api_service.dart';
import '../../services/simulador_service.dart';

class _Pal {
  static const bg = Color(0xFFFAF8FF);
  static const surface = Color(0xFFFFFFFF);
  static const high = Color(0xFFE2E7FF);
  static const border = Color(0xFFC3C5D9);
  static const primary = Color(0xFF003EC7);
  static const onSurface = Color(0xFF131B2E);
  static const onSurfaceVar = Color(0xFF434656);
  static const error = Color(0xFFBA1A1A);
  static const ok = Color(0xFF059669);
}

class SimuladorResultadoScreen extends StatefulWidget {
  final int intentoId;
  const SimuladorResultadoScreen({super.key, required this.intentoId});

  @override
  State<SimuladorResultadoScreen> createState() => _SimuladorResultadoScreenState();
}

class _SimuladorResultadoScreenState extends State<SimuladorResultadoScreen> {
  final _api = SimuladorService();
  SsoResultado? _res;
  bool _cargando = true;
  bool _verRevision = false;
  String? _error;
  static const _notaMinima = 70;

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  Future<void> _cargar() async {
    try {
      final res = await _api.resultado(widget.intentoId);
      if (!mounted) return;
      setState(() {
        _res = res;
        _cargando = false;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _cargando = false;
        _error = e.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _cargando = false;
        _error = 'No se pudo cargar el resultado';
      });
    }
  }

  String get _modoLabel {
    final modo = _res?.modo ?? '';
    if (modo == 'falladas') return 'Solo falladas';
    return '$modo reactivos';
  }

  String get _fechaEval {
    final raw = _res?.fechaFin ?? _res?.fechaInicio;
    if (raw == null) return '';
    final d = DateTime.tryParse(raw);
    if (d == null) return raw;
    return DateFormat("d MMMM yyyy · HH:mm", 'es').format(d);
  }

  @override
  Widget build(BuildContext context) {
    final res = _res;
    return Scaffold(
      backgroundColor: _Pal.bg,
      appBar: AppBar(
        backgroundColor: _Pal.surface,
        surfaceTintColor: _Pal.surface,
        elevation: 0,
        systemOverlayStyle: SystemUiOverlayStyle.dark,
        iconTheme: const IconThemeData(color: _Pal.onSurface),
        shape: const Border(bottom: BorderSide(color: _Pal.high)),
        title: const Text(
          'Resultado de evaluación',
          style: TextStyle(color: _Pal.onSurface, fontWeight: FontWeight.w600, fontSize: 16),
        ),
      ),
      body: _cargando
          ? const Center(child: CircularProgressIndicator(color: _Pal.primary))
          : res == null
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Text(_error ?? 'Resultado no disponible', textAlign: TextAlign.center),
                  ),
                )
              : ListView(
                  padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
                  children: [
                    if (_error != null) ...[
                      Text(_error!, style: const TextStyle(color: _Pal.error)),
                      const SizedBox(height: 12),
                    ],
                    _Hero(res: res, modoLabel: _modoLabel, fecha: _fechaEval),
                    const SizedBox(height: 16),
                    _Kpis(res: res),
                    if (res.aciertosPorTema.isNotEmpty) ...[
                      const SizedBox(height: 16),
                      _Temas(temas: res.aciertosPorTema),
                    ],
                    const SizedBox(height: 16),
                    FilledButton.icon(
                      onPressed: () => setState(() => _verRevision = !_verRevision),
                      style: FilledButton.styleFrom(
                        backgroundColor: _Pal.primary,
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 14),
                      ),
                      icon: const Icon(Icons.replay),
                      label: Text(_verRevision ? 'Ocultar revisión' : 'Revisar respuestas'),
                    ),
                    const SizedBox(height: 8),
                    OutlinedButton.icon(
                      onPressed: () => Navigator.pop(context),
                      style: OutlinedButton.styleFrom(
                        foregroundColor: _Pal.onSurface,
                        side: const BorderSide(color: _Pal.border),
                        padding: const EdgeInsets.symmetric(vertical: 14),
                      ),
                      icon: const Icon(Icons.arrow_back),
                      label: const Text('Volver al simulador'),
                    ),
                    if (_verRevision) ...[
                      const SizedBox(height: 20),
                      ...res.revision.map((r) => _RevisionCard(item: r)),
                    ],
                  ],
                ),
    );
  }
}

class _Hero extends StatelessWidget {
  final SsoResultado res;
  final String modoLabel;
  final String fecha;
  const _Hero({required this.res, required this.modoLabel, required this.fecha});

  @override
  Widget build(BuildContext context) {
    final ok = res.aprobado;
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: _Pal.surface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: ok ? const Color(0xFFA7F3D0) : const Color(0xFFFECACA)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                decoration: BoxDecoration(
                  color: ok ? const Color(0xFFECFDF5) : const Color(0xFFFFF1F2),
                  borderRadius: BorderRadius.circular(99),
                ),
                child: Row(
                  children: [
                    Icon(
                      ok ? Icons.check_circle : Icons.cancel,
                      size: 16,
                      color: ok ? _Pal.ok : _Pal.error,
                    ),
                    const SizedBox(width: 6),
                    Text(
                      ok ? 'Aprobado' : 'Reprobado',
                      style: TextStyle(
                        color: ok ? _Pal.ok : _Pal.error,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ],
                ),
              ),
              const Spacer(),
              Text('Evaluación #${res.id}', style: const TextStyle(color: _Pal.onSurfaceVar, fontSize: 12)),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            ok ? 'Habilitación operativa alcanzada' : 'Requiere reentrenamiento técnico',
            style: const TextStyle(color: _Pal.onSurfaceVar, fontSize: 13),
          ),
          const SizedBox(height: 8),
          Text(
            'Simulación de Fiscalización HSE — ${res.totalPreguntas} reactivos',
            style: const TextStyle(
              color: _Pal.onSurface,
              fontSize: 18,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            'Modalidad: ${res.temaNombre} • $modoLabel',
            style: const TextStyle(color: _Pal.onSurfaceVar, fontSize: 13),
          ),
          if (fecha.isNotEmpty) ...[
            const SizedBox(height: 4),
            Text(fecha, style: const TextStyle(color: _Pal.onSurfaceVar, fontSize: 12)),
          ],
        ],
      ),
    );
  }
}

class _Kpis extends StatelessWidget {
  final SsoResultado res;
  const _Kpis({required this.res});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: _Metric(
            label: 'Puntuación',
            value: res.nota.toStringAsFixed(0),
            hint: '${res.correctas}/${res.totalPreguntas} aciertos',
            color: res.aprobado ? _Pal.ok : _Pal.error,
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: _Metric(
            label: 'Correctas',
            value: '${res.correctas}',
            hint: 'Umbral ${_SimuladorResultadoScreenState._notaMinima}',
            color: _Pal.primary,
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: _Metric(
            label: 'Tasa de error',
            value: '${res.tasaError.toStringAsFixed(0)}%',
            hint: '${res.incorrectas} incorrectas',
            color: _Pal.error,
          ),
        ),
      ],
    );
  }
}

class _Metric extends StatelessWidget {
  final String label;
  final String value;
  final String hint;
  final Color color;
  const _Metric({
    required this.label,
    required this.value,
    required this.hint,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: _Pal.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: _Pal.border),
      ),
      child: Column(
        children: [
          Text(label, style: const TextStyle(color: _Pal.onSurfaceVar, fontSize: 11)),
          const SizedBox(height: 6),
          Text(
            value,
            style: TextStyle(color: color, fontSize: 20, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 4),
          Text(hint, textAlign: TextAlign.center, style: const TextStyle(color: _Pal.onSurfaceVar, fontSize: 10)),
        ],
      ),
    );
  }
}

class _Temas extends StatelessWidget {
  final List<SsoAciertosTema> temas;
  const _Temas({required this.temas});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: _Pal.surface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: _Pal.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Desempeño por tema',
            style: TextStyle(color: _Pal.onSurface, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 12),
          ...temas.map((t) {
            final pct = t.pct / 100;
            return Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(t.tema, style: const TextStyle(color: _Pal.onSurface, fontSize: 13)),
                      ),
                      Text(
                        '${t.correctas}/${t.total}',
                        style: const TextStyle(
                          color: _Pal.onSurfaceVar,
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(99),
                    child: LinearProgressIndicator(
                      value: pct,
                      minHeight: 7,
                      backgroundColor: _Pal.high,
                      color: t.pct >= 70 ? _Pal.ok : _Pal.error,
                    ),
                  ),
                ],
              ),
            );
          }),
        ],
      ),
    );
  }
}

class _RevisionCard extends StatelessWidget {
  final SsoRevision item;
  const _RevisionCard({required this.item});

  @override
  Widget build(BuildContext context) {
    final ok = item.esCorrecta == true;
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: _Pal.surface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: ok ? const Color(0xFFA7F3D0) : const Color(0xFFFECACA)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(
                'Pregunta ${item.orden}',
                style: const TextStyle(color: _Pal.onSurfaceVar, fontSize: 12, fontWeight: FontWeight.w700),
              ),
              const Spacer(),
              Text(
                ok ? 'Correcta' : 'Incorrecta',
                style: TextStyle(
                  color: ok ? _Pal.ok : _Pal.error,
                  fontWeight: FontWeight.w800,
                  fontSize: 12,
                ),
              ),
            ],
          ),
          if (item.temaNombre.isNotEmpty) ...[
            const SizedBox(height: 4),
            Text(item.temaNombre, style: const TextStyle(color: _Pal.onSurfaceVar, fontSize: 12)),
          ],
          const SizedBox(height: 8),
          Text(
            item.enunciado,
            style: const TextStyle(color: _Pal.onSurface, fontWeight: FontWeight.w600, height: 1.35),
          ),
          const SizedBox(height: 10),
          ...item.opciones.map((op) {
            Color border = _Pal.border;
            Color bg = _Pal.surface;
            if (op.correcta) {
              border = const Color(0xFFA7F3D0);
              bg = const Color(0xFFECFDF5);
            } else if (op.elegida) {
              border = const Color(0xFFFECACA);
              bg = const Color(0xFFFFF1F2);
            }
            return Container(
              width: double.infinity,
              margin: const EdgeInsets.only(bottom: 6),
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: bg,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: border),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(op.texto, style: const TextStyle(color: _Pal.onSurface, fontSize: 13)),
                  if (!op.correcta && op.elegida && (op.argumento ?? '').isNotEmpty) ...[
                    const SizedBox(height: 6),
                    Text(
                      op.argumento!,
                      style: const TextStyle(color: _Pal.error, fontSize: 12, height: 1.35),
                    ),
                  ],
                ],
              ),
            );
          }),
          if ((item.explicacion ?? '').isNotEmpty) ...[
            const SizedBox(height: 8),
            Text(item.explicacion!, style: const TextStyle(color: _Pal.onSurfaceVar, fontSize: 13, height: 1.4)),
          ],
          if ((item.fuente ?? '').isNotEmpty) ...[
            const SizedBox(height: 6),
            Text('Fuente: ${item.fuente}', style: const TextStyle(color: _Pal.onSurfaceVar, fontSize: 11)),
          ],
        ],
      ),
    );
  }
}
