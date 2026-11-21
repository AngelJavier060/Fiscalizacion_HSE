import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_pdfview/flutter_pdfview.dart';

import '../models/alerta_incidente.dart';
import '../services/alerta_archivo.dart';
import '../services/alerta_incidente_service.dart';
import '../services/api_service.dart';
import '../services/open_bytes.dart';

class _Pal {
  static const bg = Color(0xFFF8FAFC);
  static const surface = Color(0xFFFFFFFF);
  static const ink = Color(0xFF0F172A);
  static const muted = Color(0xFF64748B);
  static const line = Color(0xFFE2E8F0);
  static const blue = Color(0xFF2563EB);
}

const _eorpvOpciones = [
  'No aplica / Evento operacional menor',
  'Trabajo en altura / Caídas',
  'Izaje y maniobras de carga',
  'LOTO / Bloqueo de energía',
  'Ingreso a espacios confinados',
  'Vehículos y equipos móviles',
  'Manejo de sustancias químicas peligrosas',
  'Excavaciones y zanjas',
];

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
        backgroundColor: _Pal.surface,
        foregroundColor: _Pal.ink,
        elevation: 0,
        title: const Text('Alertas de incidentes', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 18)),
      ),
      floatingActionButton: FloatingActionButton.extended(
        backgroundColor: _Pal.blue,
        foregroundColor: Colors.white,
        onPressed: _ingresar,
        icon: const Icon(Icons.add),
        label: const Text('Ingreso de alerta'),
      ),
      body: _cargando
          ? const Center(child: CircularProgressIndicator(color: _Pal.blue))
          : RefreshIndicator(
              color: _Pal.blue,
              onRefresh: _cargar,
              child: ListView(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 96),
                children: [
                  if (_error != null)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: Text(_error!, style: const TextStyle(color: Color(0xFFB91C1C), fontWeight: FontWeight.w600)),
                    ),
                  _Stats(alertas: _alertas),
                  const SizedBox(height: 12),
                  TextField(
                    onChanged: (v) => setState(() => _busqueda = v),
                    decoration: InputDecoration(
                      hintText: 'Buscar por código, título o área',
                      prefixIcon: const Icon(Icons.search),
                      filled: true,
                      fillColor: _Pal.surface,
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: _Pal.line)),
                    ),
                  ),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Expanded(child: _selector('Ámbito: Todos', _ambito, _opciones('ambito'), (v) => setState(() => _ambito = v))),
                      const SizedBox(width: 8),
                      Expanded(child: _selector('Área: Todas', _area, _opciones('area'), (v) => setState(() => _area = v))),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Text('${visibles.length} incidentes', style: const TextStyle(color: _Pal.muted, fontWeight: FontWeight.w600)),
                  const SizedBox(height: 8),
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

  @override
  Widget build(BuildContext context) {
    final empresas = _contar((a) => a.empresa);
    final ambitos = _contar((a) => a.ambito);
    return Column(
      children: [
        _barraCard('Incidentes por empresa', empresas, const Color(0xFFF59E0B)),
        const SizedBox(height: 8),
        _barraCard('Ámbito de consecuencias', ambitos, const Color(0xFF10B981)),
      ],
    );
  }

  Widget _barraCard(String titulo, List<MapEntry<String, int>> filas, Color color) {
    final maximo = filas.fold<int>(1, (m, e) => e.value > m ? e.value : m);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: _Pal.surface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: _Pal.line),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(titulo.toUpperCase(), style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w800, color: _Pal.muted, letterSpacing: 0.4)),
          const SizedBox(height: 8),
          if (filas.isEmpty) const Text('Sin incidentes registrados', style: TextStyle(color: _Pal.muted, fontSize: 12)),
          ...filas.map((e) => Padding(
                padding: const EdgeInsets.only(bottom: 6),
                child: Row(
                  children: [
                    Expanded(flex: 4, child: Text(e.key, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 12))),
                    Expanded(
                      flex: 5,
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(99),
                        child: LinearProgressIndicator(
                          value: e.value / maximo,
                          minHeight: 8,
                          backgroundColor: const Color(0xFFF1F5F9),
                          color: color,
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text('${e.value}', style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 12)),
                  ],
                ),
              )),
        ],
      ),
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
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.fromLTRB(12, 12, 4, 12),
      decoration: BoxDecoration(
        color: _Pal.surface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: _Pal.line),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(alerta.codigo, style: const TextStyle(fontFamily: 'monospace', fontWeight: FontWeight.w800, fontSize: 12)),
                const SizedBox(height: 4),
                Text(alerta.titulo, style: const TextStyle(fontWeight: FontWeight.w700, color: _Pal.ink)),
                if (alerta.detalle.isNotEmpty)
                  Text(alerta.detalle, style: const TextStyle(color: _Pal.muted, fontSize: 12)),
                const SizedBox(height: 6),
                Text('$fecha · ${alerta.ambito} · ${alerta.area}', style: const TextStyle(color: _Pal.muted, fontSize: 12)),
              ],
            ),
          ),
          IconButton(
            tooltip: 'Ver PDF',
            onPressed: onPdf,
            icon: const Icon(Icons.remove_red_eye_outlined, color: _Pal.blue),
          ),
        ],
      ),
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
                _campo('Título del incidente', _titulo, 'Describa el incidente', lineas: 2),
                _campo('Unidad de negocio', _unidad, 'Escriba la unidad de negocio', lineas: 2),
                _campo('Ámbito de consecuencias', _ambito, 'Escriba el ámbito', lineas: 2),
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
                _campo('Área afectada', _area, 'Escriba el área afectada', lineas: 2),
                _campo('Dueño del área', _dueno, 'Nombre o cargo', lineas: 2),
                _campo('Empresa involucrada', _empresa, 'Escriba la empresa', lineas: 2),
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

  Widget _campo(String etiqueta, TextEditingController c, String hint, {int lineas = 1}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: TextField(
        controller: c,
        minLines: lineas,
        maxLines: lineas,
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
    return Scaffold(
      backgroundColor: const Color(0xFF0F172A),
      appBar: AppBar(
        backgroundColor: const Color(0xFF0F172A),
        foregroundColor: Colors.white,
        title: Text(widget.alerta.codigo),
        actions: [
          TextButton(onPressed: _bytes == null ? null : _ver, child: const Text('Ver', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w800))),
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cerrar', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w800))),
        ],
      ),
      body: Center(
        child: AspectRatio(
          aspectRatio: 210 / 297,
          child: Container(
            color: Colors.white,
            margin: const EdgeInsets.all(16),
            child: _cargando
                ? const Center(child: CircularProgressIndicator(color: _Pal.blue))
                : _error != null
                    ? Center(child: Padding(padding: const EdgeInsets.all(24), child: Text(_error!, textAlign: TextAlign.center)))
                    : _esImagen && _bytes != null
                        ? Image.memory(_bytes!, fit: BoxFit.contain)
                        : _ruta != null
                            ? PDFView(filePath: _ruta!)
                            : const Center(child: Text('Pulse Ver para abrir el PDF.')),
          ),
        ),
      ),
    );
  }
}
