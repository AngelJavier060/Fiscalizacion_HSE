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
  String _modo = '20';
  int? _temaId;

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
        if (_modo == 'falladas' && data.falladasPendientes == 0) {
          _modo = '20';
        }
        if (_temaId != null && data.temas.every((t) => t.id != _temaId)) {
          _temaId = null;
        }
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

  Future<void> _empezar() async {
    setState(() {
      _iniciando = true;
      _error = null;
    });
    try {
      final ex = await _api.iniciarExamen(
        _modo,
        temaId: _modo == 'falladas' ? null : _temaId,
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
                      modo: _modo,
                      temaId: _temaId,
                      iniciando: _iniciando,
                      onModo: (m) => setState(() {
                        _modo = m;
                        if (m == 'falladas') _temaId = null;
                      }),
                      onTema: (id) => setState(() => _temaId = id),
                      onEmpezar: _iniciando ? null : _empezar,
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
            'Exámenes aleatorios de 20 o 40 preguntas. Apruebas con ${data.notaMinima} o más. Tiempo: ${data.tiempo20Min} min.',
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
                          : (data.ultimoAprobado! ? 'Aprobado' : 'Reprobado'),
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

class _ElegirExamen extends StatelessWidget {
  final SsoInicio data;
  final String modo;
  final int? temaId;
  final bool iniciando;
  final ValueChanged<String> onModo;
  final ValueChanged<int?> onTema;
  final VoidCallback? onEmpezar;

  const _ElegirExamen({
    required this.data,
    required this.modo,
    required this.temaId,
    required this.iniciando,
    required this.onModo,
    required this.onTema,
    required this.onEmpezar,
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
            'Elegir examen',
            style: TextStyle(color: _Pal.onSurface, fontSize: 16, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 12),
          _ModoChip(
            selected: modo == '20',
            label: '20 preguntas (${data.tiempo20Min} min)',
            onTap: () => onModo('20'),
          ),
          const SizedBox(height: 8),
          _ModoChip(
            selected: modo == '40',
            label: '40 preguntas (${data.tiempo40Min} min)',
            onTap: () => onModo('40'),
          ),
          const SizedBox(height: 8),
          _ModoChip(
            selected: modo == 'falladas',
            enabled: data.falladasPendientes > 0,
            label: 'Falladas (${data.falladasPendientes})',
            onTap: data.falladasPendientes > 0 ? () => onModo('falladas') : null,
          ),
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
              enabled: modo != 'falladas',
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
                value: modo == 'falladas' ? null : temaId,
                isExpanded: true,
                dropdownColor: _Pal.surface,
                style: const TextStyle(color: _Pal.onSurface, fontSize: 14),
                items: [
                  const DropdownMenuItem<int?>(
                    value: null,
                    child: Text('Todos los temas'),
                  ),
                  ...data.temas.map(
                    (t) => DropdownMenuItem<int?>(
                      value: t.id,
                      child: Text('${t.nombre} (${t.preguntasActivas})'),
                    ),
                  ),
                ],
                onChanged: modo == 'falladas' ? null : onTema,
              ),
            ),
          ),
          const SizedBox(height: 16),
          SizedBox(
            width: double.infinity,
            child: FilledButton(
              onPressed: onEmpezar,
              style: FilledButton.styleFrom(
                backgroundColor: _Pal.primary,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
              child: iniciando
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                    )
                  : const Text('Comenzar examen', style: TextStyle(fontWeight: FontWeight.w700)),
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
