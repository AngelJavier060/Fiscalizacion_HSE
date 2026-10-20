import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';

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
}

class SimuladorInicioScreen extends StatefulWidget {
  const SimuladorInicioScreen({super.key});

  @override
  State<SimuladorInicioScreen> createState() => _SimuladorInicioScreenState();
}

class _SimuladorInicioScreenState extends State<SimuladorInicioScreen> {
  final _api = SimuladorService();
  SsoInicio? _data;
  bool _cargando = true;
  bool _iniciando = false;
  String? _error;
  String _alcance = 'general';
  int? _temaId;
  int _banco = 20;

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  Future<void> _cargar() async {
    setState(() {
      _cargando = true;
      _error = null;
    });
    try {
      final data = await _api.inicio();
      if (!mounted) return;
      setState(() {
        _data = data;
        _cargando = false;
        if (_alcance == 'falladas' && data.falladasPendientes == 0) {
          _alcance = 'general';
        }
        if (_temaId != null && data.temas.every((t) => t.id != _temaId)) {
          _temaId = data.temas.isEmpty ? null : data.temas.first.id;
        }
        _temaId ??= data.temas.isEmpty ? null : data.temas.first.id;
        final n = data.preguntasPorBanco;
        _banco = (n == 0 || n == 20 || n == 40 || n == 60) ? n : 20;
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
        _error = 'No se pudo cargar el simulador';
      });
    }
  }

  Future<void> _empezar({bool nuevoSet = true}) async {
    setState(() {
      _iniciando = true;
      _error = null;
    });
    try {
      final ex = await _api.iniciarExamen(
        _alcance == 'falladas' ? 'falladas' : 'banco',
        temaId: _alcance == 'tema' ? _temaId : null,
        preguntasPorBanco: _banco,
        nuevoSet: nuevoSet,
      );
      if (!mounted) return;
      await Navigator.pushNamed(
        context,
        '/simulador-examen',
        arguments: {'id': ex.id},
      );
      if (mounted) _cargar();
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _iniciando = false;
        _error = e.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _iniciando = false;
        _error = 'No se pudo iniciar el examen';
      });
    }
    if (mounted) setState(() => _iniciando = false);
  }

  Future<void> _continuar(int id) async {
    await Navigator.pushNamed(
      context,
      '/simulador-examen',
      arguments: {'id': id},
    );
    if (mounted) _cargar();
  }

  Future<void> _verResultado(int id) async {
    await Navigator.pushNamed(
      context,
      '/simulador-resultado',
      arguments: {'id': id},
    );
    if (mounted) _cargar();
  }

  String _fmtFecha(String? raw) {
    if (raw == null || raw.isEmpty) return '—';
    final d = DateTime.tryParse(raw);
    if (d == null) return raw;
    return DateFormat("d MMM yyyy, HH:mm", 'es').format(d);
  }

  @override
  Widget build(BuildContext context) {
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
          'Simulador SSO',
          style: TextStyle(
            color: _Pal.onSurface,
            fontWeight: FontWeight.w600,
            fontSize: 18,
          ),
        ),
      ),
      body: _cargando
          ? const Center(child: CircularProgressIndicator(color: _Pal.primary))
          : RefreshIndicator(
              onRefresh: _cargar,
              color: _Pal.primary,
              child: ListView(
                padding: const EdgeInsets.fromLTRB(16, 20, 16, 32),
                children: [
                  if (_error != null) ...[
                    _Alert(text: _error!),
                    const SizedBox(height: 16),
                  ],
                  if (_data != null) ...[
                    _Welcome(data: _data!),
                    const SizedBox(height: 16),
                    _Kpis(data: _data!),
                    if (_data!.intentoPendienteId != null) ...[
                      const SizedBox(height: 16),
                      _Pendiente(
                        modo: _data!.intentoPendienteModo ?? '',
                        onContinuar: () => _continuar(_data!.intentoPendienteId!),
                      ),
                    ],
                    const SizedBox(height: 20),
                    _ElegirExamen(
                      data: _data!,
                      alcance: _alcance,
                      temaId: _temaId,
                      banco: _banco,
                      iniciando: _iniciando,
                      onAlcance: (m) => setState(() => _alcance = m),
                      onTema: (id) => setState(() => _temaId = id),
                      onBanco: (n) => setState(() => _banco = n),
                      onEmpezar: _iniciando ? null : (nuevo) => _empezar(nuevoSet: nuevo),
                    ),
                    const SizedBox(height: 20),
                    _Historial(
                      items: _data!.historial,
                      fmtFecha: _fmtFecha,
                      onResultado: _verResultado,
                    ),
                  ],
                ],
              ),
            ),
    );
  }
}

class _Alert extends StatelessWidget {
  final String text;
  const _Alert({required this.text});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFFFFF1F2),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFFFECACA)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.error_outline, color: _Pal.error, size: 20),
          const SizedBox(width: 10),
          Expanded(
            child: Text(text, style: const TextStyle(color: _Pal.error, fontSize: 13)),
          ),
        ],
      ),
    );
  }
}

class _Welcome extends StatelessWidget {
  final SsoInicio data;
  const _Welcome({required this.data});

  @override
  Widget build(BuildContext context) {
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
          const Text(
            'Simulador de evaluaciones SSO',
            style: TextStyle(
              color: _Pal.onSurface,
              fontSize: 20,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'Elija 20 (15 min), 40 (30 min), 60 (50 min) o Todas (1 h 30 min). Si salen menos de 10 preguntas, el tiempo es 10 min. Las preguntas y las opciones se mezclan al azar. Con ${data.notaMinima}% o más avanza; si no, puede repetir las mismas o continuar con otras.',
            style: const TextStyle(color: _Pal.onSurfaceVar, fontSize: 14, height: 1.4),
          ),
        ],
      ),
    );
  }
}

class _Kpis extends StatelessWidget {
  final SsoInicio data;
  const _Kpis({required this.data});

  @override
  Widget build(BuildContext context) {
    final nota = data.ultimaNota;
    final notaColor = data.ultimoAprobado == true
        ? _Pal.ok
        : data.ultimoAprobado == false
            ? _Pal.error
            : _Pal.onSurface;

    return Column(
      children: [
        _KpiCard(
          label: 'Avance de la vuelta ${data.vueltaActual}',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '${data.preguntasVistasVuelta} de ${data.preguntasBanco}',
                style: const TextStyle(
                  color: _Pal.onSurface,
                  fontSize: 22,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 10),
              ClipRRect(
                borderRadius: BorderRadius.circular(99),
                child: LinearProgressIndicator(
                  value: data.pctVuelta / 100,
                  minHeight: 8,
                  backgroundColor: _Pal.high,
                  color: _Pal.primary,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: _KpiCard(
                label: 'Última nota',
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      nota == null ? '—' : nota.toStringAsFixed(0),
                      style: TextStyle(
                        color: notaColor,
                        fontSize: 22,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      data.ultimoAprobado == null
                          ? 'Sin exámenes finalizados'
                          : (data.ultimoAprobado! ? 'Avanzó de banco' : 'Puede repetir o continuar'),
                      style: const TextStyle(color: _Pal.onSurfaceVar, fontSize: 12),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _KpiCard(
                label: 'Falladas por repasar',
                child: Text(
                  '${data.falladasPendientes}',
                  style: const TextStyle(
                    color: _Pal.onSurface,
                    fontSize: 22,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ),
          ],
        ),
      ],
    );
  }
}

class _KpiCard extends StatelessWidget {
  final String label;
  final Widget child;
  const _KpiCard({required this.label, required this.child});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: _Pal.surface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: _Pal.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: const TextStyle(color: _Pal.onSurfaceVar, fontSize: 12, fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 8),
          child,
        ],
      ),
    );
  }
}

class _Pendiente extends StatelessWidget {
  final String modo;
  final VoidCallback onContinuar;
  const _Pendiente({required this.modo, required this.onContinuar});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFFFFFBEB),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: const Color(0xFFFDE68A)),
      ),
      child: Row(
        children: [
          const Icon(Icons.timer_outlined, color: Color(0xFFD97706)),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              'Tienes un examen $modo en curso.',
              style: const TextStyle(color: _Pal.onSurface, fontWeight: FontWeight.w600),
            ),
          ),
          FilledButton(
            onPressed: onContinuar,
            style: FilledButton.styleFrom(
              backgroundColor: _Pal.primary,
              foregroundColor: Colors.white,
            ),
            child: const Text('Continuar'),
          ),
        ],
      ),
    );
  }
}

class _BancoOpt {
  final int n;
  final String label;
  final String tiempo;
  const _BancoOpt(this.n, this.label, this.tiempo);
}

const _bancos = [
  _BancoOpt(20, '20 preguntas', '15 min'),
  _BancoOpt(40, '40 preguntas', '30 min'),
  _BancoOpt(60, '60 preguntas', '50 min'),
  _BancoOpt(0, 'Todas', '1 h 30 min'),
];

int _tiempoSegunCantidad(int n) {
  if (n < 10) return 10;
  if (n <= 20) return 15;
  if (n <= 40) return 30;
  if (n <= 60) return 50;
  return 90;
}

String _labelTiempo(int min) {
  if (min >= 60) {
    final h = min ~/ 60;
    final m = min % 60;
    return m == 0 ? '$h h' : '$h h $m min';
  }
  return '$min min';
}

class _ElegirExamen extends StatelessWidget {
  final SsoInicio data;
  final String alcance;
  final int? temaId;
  final int banco;
  final bool iniciando;
  final ValueChanged<String> onAlcance;
  final ValueChanged<int?> onTema;
  final ValueChanged<int> onBanco;
  final ValueChanged<bool>? onEmpezar;

  const _ElegirExamen({
    required this.data,
    required this.alcance,
    required this.temaId,
    required this.banco,
    required this.iniciando,
    required this.onAlcance,
    required this.onTema,
    required this.onBanco,
    required this.onEmpezar,
  });

  int get _totalAlcance {
    if (alcance == 'tema') {
      for (final t in data.temas) {
        if (t.id == temaId) return t.preguntasActivas;
      }
      return 0;
    }
    return data.preguntasBanco;
  }

  int get _cumplidas {
    if (alcance == 'tema') {
      for (final t in data.temas) {
        if (t.id == temaId) return t.cumplidasVuelta;
      }
      return 0;
    }
    return data.preguntasVistasVuelta;
  }

  int get _preguntasEste {
    if (alcance == 'falladas') {
      final n = data.falladasPendientes;
      return banco == 0 ? n : (n < banco ? n : banco);
    }
    final pendientes = _totalAlcance - _cumplidas;
    final pool = pendientes > 0 ? pendientes : (alcance == 'general' ? _totalAlcance : pendientes);
    return banco == 0 ? pool : (pool < banco ? pool : banco);
  }

  bool get _puedeRepetir {
    if (!data.bloqueRepetir || alcance == 'falladas') return false;
    return alcance == 'general'
        ? data.bloqueRepetirTemaId == null
        : data.bloqueRepetirTemaId == temaId;
  }

  @override
  Widget build(BuildContext context) {
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
          const Text(
            'Elegir alcance',
            style: TextStyle(color: _Pal.onSurface, fontSize: 16, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 12),
          if (_puedeRepetir) ...[
            Text(
              'El último intento de ${data.bloqueRepetirTemaNombre ?? 'General'} no alcanzó ${data.notaMinima}% (nota ${data.bloqueRepetirNota?.toStringAsFixed(0) ?? '—'}%). Puede repetir las mismas preguntas (otro orden) o continuar con otras al azar.',
              style: const TextStyle(color: Color(0xFF9A3412), fontSize: 13, height: 1.35),
            ),
            const SizedBox(height: 12),
          ],
          _ModoChip(
            selected: alcance == 'general',
            label: 'General (todos los temas)',
            onTap: () => onAlcance('general'),
          ),
          const SizedBox(height: 8),
          _ModoChip(
            selected: alcance == 'tema',
            label: 'Por tema',
            onTap: () => onAlcance('tema'),
          ),
          const SizedBox(height: 8),
          _ModoChip(
            selected: alcance == 'falladas',
            enabled: data.falladasPendientes > 0,
            label: 'Falladas (${data.falladasPendientes})',
            onTap: data.falladasPendientes > 0 ? () => onAlcance('falladas') : null,
          ),
          if (alcance == 'tema') ...[
            const SizedBox(height: 16),
            const Text(
              'Tema',
              style: TextStyle(color: _Pal.onSurfaceVar, fontSize: 12, fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 6),
            InputDecorator(
              decoration: InputDecoration(
                filled: true,
                fillColor: _Pal.low,
                contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: const BorderSide(color: _Pal.border),
                ),
                enabledBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: const BorderSide(color: _Pal.border),
                ),
              ),
              child: DropdownButtonHideUnderline(
                child: DropdownButton<int?>(
                  value: temaId,
                  isExpanded: true,
                  dropdownColor: _Pal.surface,
                  style: const TextStyle(color: _Pal.onSurface, fontSize: 14),
                  items: data.temas
                      .map(
                        (t) => DropdownMenuItem<int?>(
                          value: t.id,
                          child: Text('${t.nombre} · ${t.cumplidasVuelta}/${t.preguntasActivas}'),
                        ),
                      )
                      .toList(),
                  onChanged: onTema,
                ),
              ),
            ),
          ],
          const SizedBox(height: 16),
          const Text(
            'Tamaño del examen',
            style: TextStyle(color: _Pal.onSurface, fontSize: 14, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: _bancos
                .map(
                  (b) => ChoiceChip(
                    label: Text('${b.label}\n${b.tiempo}', textAlign: TextAlign.center),
                    selected: banco == b.n,
                    onSelected: (_) => onBanco(b.n),
                    selectedColor: _Pal.high,
                    labelStyle: TextStyle(
                      color: banco == b.n ? _Pal.primary : _Pal.onSurface,
                      fontWeight: FontWeight.w700,
                      fontSize: 12,
                      height: 1.25,
                    ),
                    backgroundColor: _Pal.surface,
                    side: BorderSide(color: banco == b.n ? _Pal.primary : _Pal.border),
                  ),
                )
                .toList(),
          ),
          const SizedBox(height: 12),
          Text(
            'Este examen: $_preguntasEste preguntas · ${_labelTiempo(_tiempoSegunCantidad(_preguntasEste))}',
            style: const TextStyle(color: _Pal.onSurfaceVar, fontSize: 13, fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 16),
          if (iniciando)
            const Center(
              child: SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(strokeWidth: 2, color: _Pal.primary),
              ),
            )
          else if (_puedeRepetir)
            Column(
              children: [
                SizedBox(
                  width: double.infinity,
                  child: OutlinedButton(
                    onPressed: onEmpezar == null ? null : () => onEmpezar!(false),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: _Pal.primary,
                      side: const BorderSide(color: _Pal.primary),
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                    child: Text(
                      'Repetir las mismas (${data.bloqueRepetirTotal})',
                      style: const TextStyle(fontWeight: FontWeight.w700),
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton(
                    onPressed: onEmpezar == null ? null : () => onEmpezar!(true),
                    style: FilledButton.styleFrom(
                      backgroundColor: _Pal.primary,
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                    child: Text(
                      'Continuar con otras ($_preguntasEste)',
                      style: const TextStyle(fontWeight: FontWeight.w700),
                    ),
                  ),
                ),
              ],
            )
          else
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: onEmpezar == null ? null : () => onEmpezar!(true),
                style: FilledButton.styleFrom(
                  backgroundColor: _Pal.primary,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
                child: Text(
                  'Comenzar $_preguntasEste ${_preguntasEste == 1 ? 'pregunta' : 'preguntas'}',
                  style: const TextStyle(fontWeight: FontWeight.w700),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _ModoChip extends StatelessWidget {
  final bool selected;
  final bool enabled;
  final String label;
  final VoidCallback? onTap;

  const _ModoChip({
    required this.selected,
    required this.label,
    this.enabled = true,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: enabled ? onTap : null,
      borderRadius: BorderRadius.circular(12),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        decoration: BoxDecoration(
          color: selected ? _Pal.low : _Pal.surface,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: selected ? _Pal.primary : _Pal.border,
            width: selected ? 1.6 : 1,
          ),
        ),
        child: Row(
          children: [
            Icon(
              selected ? Icons.radio_button_checked : Icons.radio_button_off,
              size: 20,
              color: !enabled
                  ? _Pal.border
                  : selected
                      ? _Pal.primary
                      : _Pal.onSurfaceVar,
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                label,
                style: TextStyle(
                  color: enabled ? _Pal.onSurface : _Pal.onSurfaceVar,
                  fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Historial extends StatelessWidget {
  final List<SsoHistorialItem> items;
  final String Function(String?) fmtFecha;
  final ValueChanged<int> onResultado;

  const _Historial({
    required this.items,
    required this.fmtFecha,
    required this.onResultado,
  });

  @override
  Widget build(BuildContext context) {
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
          const Text(
            'Historial',
            style: TextStyle(color: _Pal.onSurface, fontSize: 16, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 12),
          if (items.isEmpty)
            const Text('Aún no hay intentos.', style: TextStyle(color: _Pal.onSurfaceVar))
          else
            ...items.map((h) {
              final finalizado = h.estado == 'finalizado' && h.nota != null;
              return InkWell(
                onTap: finalizado ? () => onResultado(h.id) : null,
                borderRadius: BorderRadius.circular(12),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 10),
                  child: Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              '${h.modo} · ${h.temaNombre.isEmpty ? 'Todos los temas' : h.temaNombre}',
                              style: const TextStyle(
                                color: _Pal.onSurface,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              '${fmtFecha(h.fechaInicio)} · ${h.estado}',
                              style: const TextStyle(color: _Pal.onSurfaceVar, fontSize: 12),
                            ),
                          ],
                        ),
                      ),
                      Text(
                        finalizado ? h.nota!.toStringAsFixed(0) : '—',
                        style: TextStyle(
                          color: h.aprobado == true
                              ? _Pal.ok
                              : h.aprobado == false
                                  ? _Pal.error
                                  : _Pal.onSurfaceVar,
                          fontWeight: FontWeight.w800,
                          fontSize: 16,
                        ),
                      ),
                      if (finalizado)
                        const Padding(
                          padding: EdgeInsets.only(left: 4),
                          child: Icon(Icons.chevron_right, color: _Pal.onSurfaceVar),
                        ),
                    ],
                  ),
                ),
              );
            }),
        ],
      ),
    );
  }
}
