import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';

import '../models/alerta_incidente.dart';
import '../models/user_model.dart';
import '../services/alerta_archivo.dart';
import '../services/alerta_incidente_service.dart';
import '../services/api_service.dart';
import '../services/auth_service.dart';
import '../services/open_bytes.dart';
import '../widgets/visor_archivo_alerta.dart';

class _Pal {
  static const bg = Color(0xFFF8FAFC);
  static const surface = Color(0xFFFFFFFF);
  static const ink = Color(0xFF0F172A);
  static const muted = Color(0xFF64748B);
  static const line = Color(0xFFE2E8F0);
  static const blue = Color(0xFF2563EB);
}

const _eorpvOpciones = ['No aplica', 'Sí aplica'];

class AlertasIncidentesScreen extends StatefulWidget {
  const AlertasIncidentesScreen({super.key});

  @override
  State<AlertasIncidentesScreen> createState() => _AlertasIncidentesScreenState();
}

class _AlertasIncidentesScreenState extends State<AlertasIncidentesScreen> {
  List<AlertaIncidente> _alertas = [];
  bool _cargando = true;
  String? _error;
  String _busqueda = '';
  String _ambito = '';
  String _area = '';
  bool _puedeRegistrar = false;

  @override
  void initState() {
    super.initState();
    _cargar();
    _leerRol();
  }

  Future<void> _leerRol() async {
    try {
      final datos = await AuthService().getUserData();
      final rol = UserModel.fromJson(datos).rol;
      if (!mounted) return;
      setState(() => _puedeRegistrar = rol == 'SUPER_ADMIN');
    } catch (_) {}
  }

  Future<void> _cargar() async {
    setState(() {
      _cargando = true;
      _error = null;
    });
    try {
      final lista = await AlertaIncidenteService.listar();
      if (!mounted) return;
      setState(() {
        _alertas = lista;
        _cargando = false;
        if (_ambito.isNotEmpty && !_opciones('ambito').contains(_ambito)) _ambito = '';
        if (_area.isNotEmpty && !_opciones('area').contains(_area)) _area = '';
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.message;
        _cargando = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _error = 'No se pudo leer las alertas.';
        _cargando = false;
      });
    }
  }

  List<String> _opciones(String campo) {
    final vistos = <String>{};
    final lista = <String>[];
    for (final a in _alertas) {
      final nombre = (campo == 'ambito' ? a.ambito : a.area).trim();
      final clave = nombre.toLowerCase();
      if (nombre.isEmpty || vistos.contains(clave)) continue;
      vistos.add(clave);
      lista.add(nombre);
    }
    return lista;
  }

  List<AlertaIncidente> get _visibles {
    final q = _busqueda.trim().toLowerCase();
    return _alertas.where((a) {
      if (_ambito.isNotEmpty && a.ambito.trim().toLowerCase() != _ambito.trim().toLowerCase()) return false;
      if (_area.isNotEmpty && a.area.trim().toLowerCase() != _area.trim().toLowerCase()) return false;
      if (q.isEmpty) return true;
      return '${a.codigo} ${a.titulo} ${a.detalle} ${a.area} ${a.ambito} ${a.empresa}'.toLowerCase().contains(q);
    }).toList();
  }

  String _fecha(String iso) {
    final p = iso.split('-');
    if (p.length != 3) return iso;
    return '${p[2]}/${p[1]}/${p[0]}';
  }

  Future<void> _ingresar() async {
    final creada = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => const _IngresoAlertaSheet(),
    );
    if (creada == true) _cargar();
  }

  void _verPdf(AlertaIncidente alerta) {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => _PdfAlertaPage(alerta: alerta)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final visibles = _visibles;
    return Scaffold(
      backgroundColor: _Pal.bg,
      appBar: AppBar(
        backgroundColor: Colors.white.withValues(alpha: 0.96),
        foregroundColor: _Pal.ink,
        elevation: 0,
        surfaceTintColor: Colors.white,
        titleSpacing: 0,
        title: Row(
          children: [
            const Flexible(
              child: Text('Alertas de incidentes', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 17, letterSpacing: -0.2)),
            ),
            if (_alertas.isNotEmpty) ...[
              const SizedBox(width: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                decoration: BoxDecoration(color: const Color(0xFFFEE2E2), borderRadius: BorderRadius.circular(99)),
                child: Text('${_alertas.length}', style: const TextStyle(color: Color(0xFFB91C1C), fontWeight: FontWeight.w800, fontSize: 12)),
              ),
            ],
          ],
        ),
      ),
      floatingActionButton: _puedeRegistrar
          ? FloatingActionButton.extended(
              backgroundColor: _Pal.blue,
              foregroundColor: Colors.white,
              onPressed: _ingresar,
              icon: const Icon(Icons.add),
              label: const Text('Ingreso de alerta'),
            )
          : null,
      body: _cargando
          ? const Center(child: CircularProgressIndicator(color: _Pal.blue))
          : RefreshIndicator(
              color: _Pal.blue,
              onRefresh: _cargar,
              child: ListView(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
                children: [
                  if (_error != null)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: Text(_error!, style: const TextStyle(color: Color(0xFFB91C1C), fontWeight: FontWeight.w600)),
                    ),
                  _Stats(alertas: _alertas),
                  const SizedBox(height: 14),
                  TextField(
                    onChanged: (v) => setState(() => _busqueda = v),
                    decoration: InputDecoration(
                      hintText: 'Buscar por código, empresa o detalle...',
                      hintStyle: const TextStyle(color: Color(0xFF94A3B8), fontSize: 14),
                      prefixIcon: const Icon(Icons.search, color: Color(0xFF94A3B8), size: 20),
                      filled: true,
                      fillColor: Colors.white,
                      contentPadding: const EdgeInsets.symmetric(vertical: 12),
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: _Pal.line)),
                      enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: _Pal.line)),
                    ),
                  ),
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      Expanded(child: _selector('Ámbito: Todos', _ambito, _opciones('ambito'), (v) => setState(() => _ambito = v))),
                      const SizedBox(width: 10),
                      Expanded(child: _selector('Área: Todas', _area, _opciones('area'), (v) => setState(() => _area = v))),
                    ],
                  ),
                  const SizedBox(height: 14),
                  Row(
                    children: [
                      Text('RESULTADOS ENCONTRADOS ', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w800, letterSpacing: 0.6, color: _Pal.muted)),
                      Text('(${visibles.length})', style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w800, color: _Pal.blue)),
                    ],
                  ),
                  const SizedBox(height: 10),
                  if (visibles.isEmpty)
                    const Padding(
                      padding: EdgeInsets.only(top: 24),
                      child: Text('No hay incidentes con ese filtro.', textAlign: TextAlign.center, style: TextStyle(color: _Pal.muted)),
                    ),
                  ...visibles.map((a) => _Tarjeta(
                        alerta: a,
                        fecha: _fecha(a.fecha),
                        onPdf: () => _verPdf(a),
                      )),
                ],
              ),
            ),
    );
  }

  Widget _selector(String vacio, String valor, List<String> opciones, ValueChanged<String> onChanged) {
    return DropdownButtonFormField<String>(
      key: ValueKey('$vacio|$valor|${opciones.join('|')}'),
      initialValue: valor.isEmpty ? '' : valor,
      isExpanded: true,
      decoration: InputDecoration(
        filled: true,
        fillColor: _Pal.surface,
        contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: _Pal.line)),
      ),
      items: [
        DropdownMenuItem(value: '', child: Text(vacio, overflow: TextOverflow.ellipsis)),
        ...opciones.map((op) => DropdownMenuItem(value: op, child: Text(op, overflow: TextOverflow.ellipsis))),
      ],
      onChanged: (v) => onChanged(v ?? ''),
    );
  }
}

class _Stats extends StatelessWidget {
  final List<AlertaIncidente> alertas;
  const _Stats({required this.alertas});

  List<MapEntry<String, int>> _contar(String Function(AlertaIncidente) leer) {
    final filas = <MapEntry<String, int>>[];
    for (final a in alertas) {
      final nombre = leer(a).trim();
      if (nombre.isEmpty) continue;
      final i = filas.indexWhere((e) => e.key.toLowerCase() == nombre.toLowerCase());
      if (i >= 0) {
        filas[i] = MapEntry(filas[i].key, filas[i].value + 1);
      } else {
        filas.add(MapEntry(nombre, 1));
      }
    }
    return filas;
  }

  Widget _barraCard(String titulo, String etiqueta, Color etiquetaColor, Color etiquetaFondo, List<MapEntry<String, int>> filas, Color color) {
    final maximo = filas.fold<int>(1, (m, e) => e.value > m ? e.value : m);
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE2E8F0)),
        boxShadow: const [BoxShadow(color: Color(0x0A0F172A), blurRadius: 3, offset: Offset(0, 1))],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(child: Text(titulo, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w800, letterSpacing: 0.6, color: Color(0xFF94A3B8)))),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                decoration: BoxDecoration(color: etiquetaFondo, borderRadius: BorderRadius.circular(6), border: Border.all(color: etiquetaColor.withValues(alpha: 0.25))),
                child: Text(etiqueta, style: TextStyle(color: etiquetaColor, fontSize: 11, fontWeight: FontWeight.w700)),
              ),
            ],
          ),
          const SizedBox(height: 8),
          if (filas.isEmpty) const Text('Sin incidentes registrados', style: TextStyle(color: _Pal.muted, fontSize: 12)),
          ...filas.map((e) => Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Column(
                  children: [
                    Row(
                      children: [
                        Expanded(child: Text(e.key, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14, color: _Pal.ink))),
                        Text('${e.value}', style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
                        const SizedBox(width: 4),
                        Text(e.value == 1 ? 'caso' : 'casos', style: const TextStyle(color: _Pal.muted, fontSize: 12)),
                      ],
                    ),
                    const SizedBox(height: 6),
                    ClipRRect(
                      borderRadius: BorderRadius.circular(99),
                      child: LinearProgressIndicator(
                        value: e.value / maximo,
                        minHeight: 8,
                        backgroundColor: const Color(0xFFF1F5F9),
                        color: color,
                      ),
                    ),
                  ],
                ),
              )),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final empresas = _contar((a) => a.empresa);
    final ambitos = _contar((a) => a.ambito);
    return Column(
      children: [
        _barraCard('INCIDENTES POR EMPRESA', 'Seguimiento', const Color(0xFFB45309), const Color(0xFFFFFBEB), empresas, const Color(0xFFF59E0B)),
        _barraCard('ÁMBITO DE CONSECUENCIAS', 'Prioridad HSE', const Color(0xFF047857), const Color(0xFFECFDF5), ambitos, const Color(0xFF10B981)),
      ],
    );
  }
}

class _Tarjeta extends StatelessWidget {
  final AlertaIncidente alerta;
  final String fecha;
  final VoidCallback onPdf;
  const _Tarjeta({required this.alerta, required this.fecha, required this.onPdf});

  @override
  Widget build(BuildContext context) {
    final alta = alerta.prioridad == 'alta';
    final franja = alta ? const Color(0xFFF59E0B) : const Color(0xFF3B82F6);
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE2E8F0)),
        boxShadow: const [BoxShadow(color: Color(0x0A0F172A), blurRadius: 3, offset: Offset(0, 1))],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(16),
        child: IntrinsicHeight(
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Container(width: 6, color: franja),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(12, 12, 8, 12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Expanded(
                            child: Wrap(
                              spacing: 6,
                              runSpacing: 4,
                              children: [
                                _chip(alerta.codigo, const Color(0xFFF1F5F9), _Pal.muted),
                                if (alerta.impacto.isNotEmpty) _chip(alerta.impacto, const Color(0xFFECFDF5), const Color(0xFF047857)),
                                if (alerta.ambito.isNotEmpty) _chip(alerta.ambito, const Color(0xFFFFFBEB), const Color(0xFFB45309)),
                              ],
                            ),
                          ),
                          IconButton(
                            tooltip: 'Ver PDF',
                            onPressed: onPdf,
                            icon: const Icon(Icons.remove_red_eye_outlined, color: Color(0xFF94A3B8)),
                          ),
                        ],
                      ),
                      Text(alerta.titulo, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 14, height: 1.3, color: _Pal.ink)),
                      const SizedBox(height: 10),
                      const Divider(height: 1, color: Color(0xFFF1F5F9)),
                      const SizedBox(height: 8),
                      Row(
                        children: [
                          const Icon(Icons.apartment_outlined, size: 16, color: Color(0xFF94A3B8)),
                          const SizedBox(width: 6),
                          Expanded(child: Text(alerta.empresa, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13))),
                          Text(alerta.area, style: const TextStyle(color: Color(0xFF94A3B8), fontSize: 12)),
                        ],
                      ),
                      const SizedBox(height: 6),
                      Row(
                        children: [
                          const Icon(Icons.calendar_today_outlined, size: 14, color: Color(0xFF94A3B8)),
                          const SizedBox(width: 6),
                          Text('$fecha • ${alerta.hora}', style: const TextStyle(color: Color(0xFF94A3B8), fontSize: 11)),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _chip(String texto, Color fondo, Color tinta) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(color: fondo, borderRadius: BorderRadius.circular(6)),
      child: Text(texto, style: TextStyle(color: tinta, fontSize: 11, fontWeight: FontWeight.w700)),
    );
  }
}

class _IngresoAlertaSheet extends StatefulWidget {
  const _IngresoAlertaSheet();

  @override
  State<_IngresoAlertaSheet> createState() => _IngresoAlertaSheetState();
}

class _IngresoAlertaSheetState extends State<_IngresoAlertaSheet> {
  final _codigo = TextEditingController();
  final _titulo = TextEditingController();
  final _unidad = TextEditingController();
  final _ambito = TextEditingController();
  final _area = TextEditingController();
  final _dueno = TextEditingController();
  final _empresa = TextEditingController();
  DateTime? _fecha;
  TimeOfDay? _hora;
  String _impacto = '';
  String _grave = '';
  String _iap = '';
  String _eorpv = '';
  Uint8List? _archivo;
  String? _nombreArchivo;
  String _aviso = '';
  bool _guardando = false;

  @override
  void dispose() {
    _codigo.dispose();
    _titulo.dispose();
    _unidad.dispose();
    _ambito.dispose();
    _area.dispose();
    _dueno.dispose();
    _empresa.dispose();
    super.dispose();
  }

  Future<void> _elegirArchivo() async {
    final resultado = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: const ['pdf', 'png', 'jpg', 'jpeg'],
      withData: true,
    );
    final file = resultado?.files.single;
    if (file == null) return;
    final bytes = file.bytes ?? await leerRutaAlerta(file.path);
    if (bytes == null) return;
    setState(() {
      _archivo = bytes;
      _nombreArchivo = file.name;
    });
  }

  String _isoFecha(DateTime f) =>
      '${f.year.toString().padLeft(4, '0')}-${f.month.toString().padLeft(2, '0')}-${f.day.toString().padLeft(2, '0')}';

  String _isoHora(TimeOfDay h) => '${h.hour.toString().padLeft(2, '0')}:${h.minute.toString().padLeft(2, '0')}';

  Future<void> _registrar() async {
    if (_codigo.text.trim().isEmpty ||
        _titulo.text.trim().isEmpty ||
        _unidad.text.trim().isEmpty ||
        _ambito.text.trim().isEmpty ||
        _area.text.trim().isEmpty ||
        _dueno.text.trim().isEmpty ||
        _empresa.text.trim().isEmpty ||
        _fecha == null ||
        _hora == null ||
        _impacto.isEmpty ||
        _grave.isEmpty ||
        _iap.isEmpty ||
        _eorpv.isEmpty) {
      setState(() => _aviso = 'Complete los campos obligatorios.');
      return;
    }
    setState(() {
      _guardando = true;
      _aviso = '';
    });
    try {
      await AlertaIncidenteService.crear(
        datos: {
          'codigo': _codigo.text.trim(),
          'titulo': _titulo.text.trim(),
          'unidad': _unidad.text.trim(),
          'ambito': _ambito.text.trim(),
          'fecha': _isoFecha(_fecha!),
          'area': _area.text.trim(),
          'dueno': _dueno.text.trim(),
          'hora': _isoHora(_hora!),
          'empresa': _empresa.text.trim(),
          'impacto': _impacto,
          'accidenteGrave': _grave == 'Sí',
          'iap': _iap == 'Sí',
          'eorpv': _eorpv,
        },
        archivo: _archivo,
        nombreArchivo: _nombreArchivo,
      );
      if (mounted) Navigator.pop(context, true);
    } on ApiException catch (e) {
      if (mounted) setState(() => _aviso = e.message);
    } catch (_) {
      if (mounted) setState(() => _aviso = 'No se pudo guardar la alerta.');
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final alto = MediaQuery.of(context).size.height * 0.92;
    return Container(
      height: alto,
      decoration: const BoxDecoration(
        color: _Pal.surface,
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: Column(
        children: [
          const SizedBox(height: 10),
          Container(width: 42, height: 4, decoration: BoxDecoration(color: _Pal.line, borderRadius: BorderRadius.circular(99))),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 8, 8),
            child: Row(
              children: [
                const Expanded(
                  child: Text('Ingreso de alerta de incidente', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
                ),
                IconButton(onPressed: () => Navigator.pop(context), icon: const Icon(Icons.close)),
              ],
            ),
          ),
          Expanded(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
              children: [
                _campo('Código SGI', _codigo, 'Ej. SGI-2026-HSE-089'),
                _campo('Título del incidente', _titulo, 'Escriba el título. El recuadro muestra todo el texto.', lineas: 1, maximo: 6),
                _campo('Unidad de negocio', _unidad, 'Escriba la unidad de negocio'),
                _campo('Ámbito de consecuencias', _ambito, 'Escriba el ámbito'),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Fecha del incidente'),
                  subtitle: Text(_fecha == null ? 'Seleccione' : '${_fecha!.day}/${_fecha!.month}/${_fecha!.year}'),
                  trailing: const Icon(Icons.calendar_today_outlined),
                  onTap: () async {
                    final f = await showDatePicker(context: context, firstDate: DateTime(2020), lastDate: DateTime(2035), initialDate: _fecha ?? DateTime.now());
                    if (f != null) setState(() => _fecha = f);
                  },
                ),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Hora del incidente'),
                  subtitle: Text(_hora == null ? 'Seleccione' : _hora!.format(context)),
                  trailing: const Icon(Icons.schedule),
                  onTap: () async {
                    final h = await showTimePicker(context: context, initialTime: _hora ?? TimeOfDay.now());
                    if (h != null) setState(() => _hora = h);
                  },
                ),
                _campo('Área afectada', _area, 'Escriba el área afectada'),
                _campo('Dueño del área', _dueno, 'Nombre o cargo'),
                _campo('Empresa involucrada', _empresa, 'Escriba la empresa'),
                const Text('Impacto a las personas', style: TextStyle(fontWeight: FontWeight.w700)),
                const SizedBox(height: 6),
                Row(
                  children: [
                    Expanded(child: _opcion('Sin lesión', _impacto, (v) => setState(() => _impacto = v))),
                    const SizedBox(width: 8),
                    Expanded(child: _opcion('Con lesión', _impacto, (v) => setState(() => _impacto = v))),
                  ],
                ),
                const SizedBox(height: 12),
                _combo('Accidente grave / crítico', _grave, const ['Sí', 'No'], (v) => setState(() => _grave = v)),
                _combo('IAP (incidente de alto potencial)', _iap, const ['Sí', 'No'], (v) => setState(() => _iap = v)),
                _combo('EORpV / Regla que salva vidas', _eorpv, _eorpvOpciones, (v) => setState(() => _eorpv = v)),
                OutlinedButton.icon(
                  onPressed: _elegirArchivo,
                  icon: const Icon(Icons.attach_file),
                  label: Text(_nombreArchivo ?? 'Adjuntar PDF, JPG o PNG'),
                ),
                if (_aviso.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Text(_aviso, style: const TextStyle(color: Color(0xFFB91C1C), fontWeight: FontWeight.w600)),
                  ),
              ],
            ),
          ),
          Padding(
            padding: EdgeInsets.fromLTRB(16, 8, 16, 12 + MediaQuery.of(context).viewInsets.bottom),
            child: SizedBox(
              width: double.infinity,
              child: FilledButton(
                style: FilledButton.styleFrom(backgroundColor: _Pal.blue, padding: const EdgeInsets.symmetric(vertical: 14)),
                onPressed: _guardando ? null : _registrar,
                child: Text(_guardando ? 'Guardando...' : 'Registrar alerta'),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _campo(String etiqueta, TextEditingController c, String hint, {int lineas = 1, int? maximo}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: TextField(
        controller: c,
        minLines: lineas,
        maxLines: maximo ?? lineas,
        decoration: InputDecoration(
          labelText: etiqueta,
          hintText: hint,
          alignLabelWithHint: lineas > 1,
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
        ),
      ),
    );
  }

  Widget _opcion(String texto, String actual, ValueChanged<String> onTap) {
    final activo = actual == texto;
    return InkWell(
      onTap: () => onTap(texto),
      borderRadius: BorderRadius.circular(10),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 12),
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: activo ? const Color(0xFFEFF6FF) : _Pal.surface,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: activo ? _Pal.blue : _Pal.line),
        ),
        child: Text(texto, style: TextStyle(fontWeight: FontWeight.w700, color: activo ? _Pal.blue : _Pal.ink)),
      ),
    );
  }

  Widget _combo(String etiqueta, String valor, List<String> opciones, ValueChanged<String> onChanged) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: DropdownButtonFormField<String>(
        key: ValueKey('$etiqueta|$valor'),
        initialValue: valor.isEmpty ? null : valor,
        isExpanded: true,
        decoration: InputDecoration(labelText: etiqueta, border: OutlineInputBorder(borderRadius: BorderRadius.circular(10))),
        items: opciones.map((op) => DropdownMenuItem(value: op, child: Text(op, overflow: TextOverflow.ellipsis))).toList(),
        onChanged: (v) { if (v != null) onChanged(v); },
      ),
    );
  }
}

class _PdfAlertaPage extends StatefulWidget {
  final AlertaIncidente alerta;
  const _PdfAlertaPage({required this.alerta});

  @override
  State<_PdfAlertaPage> createState() => _PdfAlertaPageState();
}

class _PdfAlertaPageState extends State<_PdfAlertaPage> {
  Uint8List? _bytes;
  String? _ruta;
  bool _cargando = true;
  String? _error;

  bool get _esImagen {
    final n = (widget.alerta.archivoNombre ?? '').toLowerCase();
    return n.endsWith('.png') || n.endsWith('.jpg') || n.endsWith('.jpeg');
  }

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  Future<void> _cargar() async {
    if (!widget.alerta.tieneArchivo) {
      setState(() {
        _cargando = false;
        _error = 'Esta alerta no tiene PDF adjunto.';
      });
      return;
    }
    try {
      final bytes = await AlertaIncidenteService.descargar(widget.alerta.id);
      final ruta = await guardarTemporalAlerta(bytes, widget.alerta.archivoNombre ?? 'alerta.pdf');
      if (!mounted) return;
      setState(() {
        _bytes = bytes;
        _ruta = ruta;
        _cargando = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _cargando = false;
        _error = 'No se pudo abrir el archivo.';
      });
    }
  }

  Future<void> _ver() async {
    final bytes = _bytes;
    if (bytes == null) return;
    final nombre = widget.alerta.archivoNombre ?? 'alerta.pdf';
    final mime = _esImagen ? 'image/jpeg' : 'application/pdf';
    await abrirBytes(bytes, nombre, mime);
  }

  @override
  Widget build(BuildContext context) {
    final a = widget.alerta;
    return Scaffold(
      backgroundColor: const Color(0xFF0F172A),
      endDrawer: Drawer(
        backgroundColor: const Color(0xFF0F172A),
        child: SafeArea(
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              const Text('FICHA TÉCNICA', style: TextStyle(color: Color(0xFF94A3B8), fontWeight: FontWeight.w800, fontSize: 12, letterSpacing: 0.6)),
              const SizedBox(height: 12),
              _dato('Código', a.codigo, destaque: true),
              _dato('Título', a.titulo),
              _dato('Unidad de negocio', a.unidad),
              _dato('Área afectada', a.area),
              _dato('Dueño del área', a.dueno),
              _dato('Fecha y hora', '${_fecha(a.fecha)} · ${a.hora}'),
              _dato('Impacto', a.impacto),
              _dato('Empresa', a.empresa),
              _dato('EORpV', a.eorpv),
              const SizedBox(height: 8),
              const Text('Consulta de solo lectura.', style: TextStyle(color: Color(0xFF94A3B8), fontSize: 12)),
            ],
          ),
        ),
      ),
      appBar: AppBar(
        backgroundColor: const Color(0xFF0F172A),
        foregroundColor: Colors.white,
        title: Text(a.archivoNombre ?? a.codigo, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 14)),
        actions: [
          Builder(
            builder: (ctx) => IconButton(
              tooltip: 'Ficha',
              onPressed: () => Scaffold.of(ctx).openEndDrawer(),
              icon: const Icon(Icons.info_outline),
            ),
          ),
          IconButton(
            tooltip: 'Descargar',
            onPressed: _bytes == null ? null : _ver,
            icon: const Icon(Icons.download_outlined),
          ),
        ],
      ),
      body: LayoutBuilder(
        builder: (context, caja) {
          final altoLibre = caja.maxHeight - 16;
          final anchoLibre = caja.maxWidth - 16;
          final ancho = anchoLibre < altoLibre * 210 / 297 ? anchoLibre : altoLibre * 210 / 297;
          final alto = ancho * 297 / 210;
          return Center(
            child: Container(
              width: ancho,
              height: alto,
              color: Colors.white,
              child: _cargando
                  ? const Center(child: CircularProgressIndicator(color: _Pal.blue))
                  : _error != null
                      ? Center(child: Padding(padding: const EdgeInsets.all(24), child: Text(_error!, textAlign: TextAlign.center)))
                      : _bytes != null
                          ? VisorArchivoAlerta(bytes: _bytes!, imagen: _esImagen, ruta: _ruta)
                          : const Center(child: Text('Sin archivo')),
            ),
          );
        },
      ),
    );
  }

  Widget _dato(String etiqueta, String valor, {bool destaque = false}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(etiqueta.toUpperCase(), style: const TextStyle(color: Color(0xFF64748B), fontSize: 10, fontWeight: FontWeight.w700, letterSpacing: 0.4)),
          const SizedBox(height: 2),
          Text(valor.isEmpty ? '—' : valor, style: TextStyle(color: destaque ? const Color(0xFF38BDF8) : Colors.white, fontWeight: FontWeight.w700, fontSize: 14)),
        ],
      ),
    );
  }

  String _fecha(String iso) {
    final p = iso.split('-');
    if (p.length != 3) return iso;
    return '${p[2]}/${p[1]}/${p[0]}';
  }
}
