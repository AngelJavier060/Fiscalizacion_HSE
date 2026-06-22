import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:uuid/uuid.dart';
import '../models/permit_model.dart';
import '../services/permiso_offline_service.dart';
import '../services/auth_service.dart';
import '../services/api_service.dart';
import '../widgets/scan_button.dart';

// ── Colores del tema (MD3 adaptado del HTML) ────────────────────────
class _Pal {
  static const primary = Color(0xFF003398);
  static const onPrimary = Color(0xFFFFFFFF);
  static const surface = Color(0xFFF9F9F9);
  static const surfaceLowest = Color(0xFFFFFFFF);
  static const onSurface = Color(0xFF1A1C1C);
  static const onSurfaceVariant = Color(0xFF434654);
  static const outline = Color(0xFF747686);
  static const outlineVariant = Color(0xFFC3C5D7);
  static const tertiary = Color(0xFF810004);
  static const error = Color(0xFFBA1A1A);
  static const primaryFixed = Color(0xFFDCE1FF);
}

/// Pantalla de creación de un nuevo permiso de trabajo.
/// Diseño adaptado fielmente del HTML proporcionado.
class NuevoPermisoScreen extends StatefulWidget {
  final PermitModel? permit;

  const NuevoPermisoScreen({super.key, this.permit});

  @override
  State<NuevoPermisoScreen> createState() => _NuevoPermisoScreenState();
}

class _NuevoPermisoScreenState extends State<NuevoPermisoScreen> {
  final _formKey = GlobalKey<FormState>();

  // ── Controladores ──────────────────────────────────────────────
  // Vacío por defecto: el usuario puede escribir su número de permiso o dejarlo
  // en blanco para que el backend genere un ID único automáticamente.
  final _idController = TextEditingController();
  final _descripcionController = TextEditingController();
  final _emisorController = TextEditingController();
  final _ejecutanteController = TextEditingController();
  final _empresaController = TextEditingController();
  final _areaController = TextEditingController();
  final _notaController = TextEditingController();
  final _responsableController = TextEditingController();

  CriticalTask? _selectedTask;
  DateTime _fechaInicio = DateTime.now();
  DateTime _fechaFin = DateTime.now().add(const Duration(days: 30));
  TimeOfDay _horaInicio = const TimeOfDay(hour: 8, minute: 0);
  TimeOfDay _horaFin = const TimeOfDay(hour: 17, minute: 0);

  bool _isSaving = false;
  bool _isEditing = false;

  // null = personalizado, 'diurna', 'nocturna'
  String? _tipoJornada;

  int _userEmpresaId = 0;
  int? _selectedEmpresaId;
  List<Map<String, dynamic>> _empresasDisponibles = [];
  bool _cargandoEmpresas = false;

  @override
  void initState() {
    super.initState();
    final permit = widget.permit;
    if (permit != null) {
      _isEditing = true;
      _idController.text = permit.id;
      _descripcionController.text = permit.description ?? '';
      _emisorController.text = permit.emisor ?? '';
      _ejecutanteController.text = permit.ejecutante ?? '';
      _empresaController.text = permit.empresaEjecutante ?? '';
      _areaController.text = permit.area;
      _notaController.text = permit.nota ?? '';
      _responsableController.text = permit.responsible;
      _selectedTask = permit.criticalTask;
      _fechaInicio = permit.startDate;
      _fechaFin = permit.endDate;
      if (permit.startTime != null) {
        _horaInicio = TimeOfDay.fromDateTime(permit.startTime!);
      }
      if (permit.endTime != null) {
        _horaFin = TimeOfDay.fromDateTime(permit.endTime!);
      }
    }
    // Para permisos nuevos el ID se deja vacío: si el usuario no escribe uno,
    // se generará un TEMP-{uuid} al guardar y el backend asignará el ID real.
    _loadUserEmpresa();
  }

  Future<void> _loadUserEmpresa() async {
    try {
      final userData = await AuthService().getUserData();
      final eid = (userData['empresaId'] as num?)?.toInt() ?? 0;
      if (mounted) setState(() => _userEmpresaId = eid);
      if (eid == 0) {
        if (mounted) setState(() => _cargandoEmpresas = true);
        try {
          final response = await ApiService.get(
            '/api/empresas',
            params: {'size': '100', 'sort': 'nombre'},
          );
          final content = (response['content'] as List<dynamic>? ?? [])
              .map((e) => e as Map<String, dynamic>)
              .toList();
          if (mounted) {
            setState(() {
              _empresasDisponibles = content;
              _cargandoEmpresas = false;
            });
          }
        } catch (_) {
          if (mounted) setState(() => _cargandoEmpresas = false);
        }
      }
    } catch (_) {}
  }

  @override
  void dispose() {
    _idController.dispose();
    _descripcionController.dispose();
    _emisorController.dispose();
    _ejecutanteController.dispose();
    _empresaController.dispose();
    _areaController.dispose();
    _notaController.dispose();
    _responsableController.dispose();
    super.dispose();
  }

  // ── Construir el modelo final ─────────────────────────────────
  PermitModel _buildPermit() {
    final startDT = DateTime(
      _fechaInicio.year,
      _fechaInicio.month,
      _fechaInicio.day,
      _horaInicio.hour,
      _horaInicio.minute,
    );
    final endDT = DateTime(
      _fechaFin.year,
      _fechaFin.month,
      _fechaFin.day,
      _horaFin.hour,
      _horaFin.minute,
    );

    return PermitModel(
      id: _idController.text.trim().isEmpty
          ? 'TEMP-${const Uuid().v4()}'
          : _idController.text.trim(),
      title: _selectedTask?.label ?? 'Permiso de trabajo',
      area: _areaController.text.trim().isEmpty
          ? 'Sin asignar'
          : _areaController.text.trim(),
      responsible: _responsableController.text.trim().isEmpty
          ? 'Sin asignar'
          : _responsableController.text.trim(),
      startDate: startDT,
      endDate: endDT,
      criticalTask: _selectedTask,
      description: _descripcionController.text.trim(),
      emisor: _emisorController.text.trim(),
      ejecutante: _ejecutanteController.text.trim(),
      empresaEjecutante: _empresaController.text.trim(),
      nota: _notaController.text.trim(),
      startTime: startDT,
      endTime: endDT,
    );
  }

  // ── Guardar ──────────────────────────────────────────────────
  Future<void> _guardar() async {
    if (!_formKey.currentState!.validate()) return;
    if (_selectedTask == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Seleccione un tipo de tarea crítica'),
          behavior: SnackBarBehavior.floating,
          backgroundColor: _Pal.error,
        ),
      );
      return;
    }

    try {
      // Usar el empresaId del usuario o el seleccionado en el dropdown
      final empresaIdFinal = _userEmpresaId > 0
          ? _userEmpresaId
          : (_selectedEmpresaId ?? 0);

      if (empresaIdFinal == 0) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Selecciona la empresa a la que pertenece este permiso'),
            backgroundColor: _Pal.error,
            behavior: SnackBarBehavior.floating,
          ),
        );
        setState(() => _isSaving = false);
        return;
      }

      // Construir el permiso con el empresaId
      final permit = _buildPermit().copyWith(empresaId: empresaIdFinal);

      // 1) SIEMPRE guardar localmente primero: garantiza que los datos
      //    (incluidas las imágenes escaneadas) nunca se pierdan, incluso
      //    sin conexión.
      final guardadoLocal = await PermisoOfflineService.guardarPermiso(permit);
      if (!guardadoLocal) {
        throw Exception('No se pudo guardar el permiso en el dispositivo');
      }

      // 2) Intentar sincronizar de inmediato con el backend. Si no hay
      //    conexión, quedará en la cola y se sincronizará automáticamente
      //    cuando se restablezca el internet.
      final sincronizados = await PermisoOfflineService.sincronizarPendientes();

      if (!mounted) return;

      if (sincronizados > 0) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Permiso guardado y sincronizado con el servidor.'),
            behavior: SnackBarBehavior.floating,
            backgroundColor: Color(0xFF3B6D11),
          ),
        );
      } else {
        final motivo = PermisoOfflineService.ultimoErrorSync;
        final mensaje = motivo == null
            ? 'Permiso guardado en el dispositivo. Se sincronizará automáticamente cuando haya conexión.'
            : 'Permiso guardado en el dispositivo. No se sincronizó: $motivo';
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(mensaje),
            behavior: SnackBarBehavior.floating,
            backgroundColor: const Color(0xFFE65100),
            duration: const Duration(seconds: 7),
          ),
        );
      }
      Navigator.pop(context, permit);
    } catch (e) {
      debugPrint('Error al guardar permiso: $e');
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Error al guardar: $e'),
          behavior: SnackBarBehavior.floating,
          backgroundColor: _Pal.error,
        ),
      );
    } finally {
      if (mounted) setState(() => _isSaving = false);
    }
  }

  // ── Selector de fecha ─────────────────────────────────────────
  Future<DateTime?> _pickDate(DateTime current) async {
    final picked = await showDatePicker(
      context: context,
      initialDate: current,
      firstDate: DateTime(2020),
      lastDate: DateTime(2035),
      locale: const Locale('es'),
      builder: (context, child) {
        return Theme(
          data: Theme.of(context).copyWith(
            colorScheme: Theme.of(context).colorScheme.copyWith(
                  primary: _Pal.primary,
                ),
          ),
          child: child!,
        );
      },
    );
    return picked;
  }

  Future<TimeOfDay?> _pickTime(TimeOfDay current) async {
    final picked = await showTimePicker(
      context: context,
      initialTime: current,
      builder: (context, child) {
        return Theme(
          data: Theme.of(context).copyWith(
            colorScheme: Theme.of(context).colorScheme.copyWith(
                  primary: _Pal.primary,
                ),
          ),
          child: child!,
        );
      },
    );
    return picked;
  }

  @override
  Widget build(BuildContext context) {
    final dateFmt = DateFormat('dd/MM/yyyy');

    return Scaffold(
      backgroundColor: _Pal.surface,
      // ── TopAppBar ────────────────────────────────────────────
      appBar: PreferredSize(
        preferredSize: const Size.fromHeight(64),
        child: Container(
          color: _Pal.surfaceLowest,
          child: SafeArea(
            bottom: false,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Row(
                children: [
                  // Botón atrás
                  Material(
                    color: Colors.transparent,
                    child: InkWell(
                      borderRadius: BorderRadius.circular(20),
                      onTap: () => Navigator.pop(context),
                      child: const Padding(
                        padding: EdgeInsets.all(8),
                        child: Icon(Icons.arrow_back, color: _Pal.primary, size: 24),
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  // Título
                  Expanded(
                    child: Text(
                      _isEditing ? 'Editar permiso' : 'Nuevo permiso de trabajo',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w600,
                        color: _Pal.primary,
                      ),
                    ),
                  ),
                  // Botón Guardar
                  Material(
                    color: Colors.transparent,
                    child: InkWell(
                      borderRadius: BorderRadius.circular(8),
                      onTap: _isSaving ? null : _guardar,
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                        child: _isSaving
                            ? const SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: _Pal.primary,
                                ),
                              )
                            : const Text(
                                'Guardar',
                                style: TextStyle(
                                  fontSize: 14,
                                  fontWeight: FontWeight.bold,
                                  color: _Pal.primary,
                                ),
                              ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
      // ── Cuerpo ──────────────────────────────────────────────
      body: Theme(
        data: Theme.of(context).copyWith(
          brightness: Brightness.light,
          scaffoldBackgroundColor: _Pal.surface,
          textTheme: const TextTheme(
            bodyLarge: TextStyle(color: _Pal.onSurface, fontSize: 14),
            bodyMedium: TextStyle(color: _Pal.onSurface, fontSize: 14),
            bodySmall: TextStyle(color: _Pal.onSurfaceVariant, fontSize: 12),
            titleMedium: TextStyle(color: _Pal.onSurface, fontSize: 16, fontWeight: FontWeight.w600),
          ),
          inputDecorationTheme: InputDecorationTheme(
            filled: true,
            fillColor: _Pal.surfaceLowest,
            contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
            hintStyle: TextStyle(color: _Pal.onSurfaceVariant.withValues(alpha: 0.7), fontSize: 14),
            labelStyle: const TextStyle(color: _Pal.onSurface, fontSize: 14),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: const BorderSide(color: _Pal.outlineVariant),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: const BorderSide(color: _Pal.outlineVariant),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: const BorderSide(color: _Pal.primary, width: 2),
            ),
            errorBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: const BorderSide(color: _Pal.error),
            ),
            focusedErrorBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: const BorderSide(color: _Pal.error, width: 2),
            ),
          ),
        ),
        child: Form(
          key: _formKey,
          child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 100),
          children: [
            // ═══════════════════════════════════════════════════
            // INFORMACIÓN GENERAL
            // ═══════════════════════════════════════════════════
            _SectionCard(
              icon: Icons.info_outline,
              title: 'Información General',
              children: [
                // ID del Permiso
                _FieldLabel(label: 'ID del Permiso'),
                const SizedBox(height: 6),
                TextFormField(
                  controller: _idController,
                  style: const TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.w500,
                    color: _Pal.onSurface,
                  ),
                  decoration: _inputDecoration().copyWith(
                    hintText: 'Opcional (se genera automáticamente)',
                  ),
                ),
                const SizedBox(height: 20),
                // Empresa (solo visible cuando el usuario no tiene empresa asignada)
                if (_userEmpresaId == 0) ...[                  
                  _FieldLabel(label: 'Empresa del permiso *'),
                  const SizedBox(height: 6),
                  if (_cargandoEmpresas)
                    const Center(
                      child: Padding(
                        padding: EdgeInsets.symmetric(vertical: 12),
                        child: CircularProgressIndicator(
                          color: _Pal.primary, strokeWidth: 2,
                        ),
                      ),
                    )
                  else if (_empresasDisponibles.isEmpty)
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: const Color(0xFFFFF3E0),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: const Color(0xFFFF9800)),
                      ),
                      child: const Text(
                        '⚠️ No se cargaron empresas. Verifica conexión a internet.',
                        style: TextStyle(color: Color(0xFFE65100), fontSize: 13),
                      ),
                    )
                  else
                    Theme(
                      data: Theme.of(context).copyWith(canvasColor: Colors.white),
                      child: DropdownButtonFormField<int>(
                        value: _selectedEmpresaId,
                        decoration: _inputDecoration().copyWith(
                          hintText: 'Selecciona la empresa...',
                          suffixIcon: const Icon(Icons.expand_more, color: _Pal.outline),
                        ),
                        isExpanded: true,
                        menuMaxHeight: 300,
                        borderRadius: BorderRadius.circular(12),
                        items: _empresasDisponibles.map((e) {
                          return DropdownMenuItem<int>(
                            value: (e['id'] as num).toInt(),
                            child: Text(
                              e['nombre'] as String? ?? 'Empresa ${e['id']}',
                              style: const TextStyle(
                                  fontSize: 14, color: _Pal.onSurface),
                            ),
                          );
                        }).toList(),
                        onChanged: (val) => setState(() => _selectedEmpresaId = val),
                        validator: (val) =>
                            val == null ? 'Selecciona una empresa' : null,
                      ),
                    ),
                  const SizedBox(height: 20),
                ],
                // Tareas Críticas
                _FieldLabel(label: 'Tareas Críticas'),
                const SizedBox(height: 6),
                Theme(
                  data: Theme.of(context).copyWith(
                    brightness: Brightness.light,
                    canvasColor: Colors.white,
                    cardTheme: CardThemeData(
                      color: Colors.white,
                      surfaceTintColor: Colors.transparent,
                      elevation: 0,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                        side: const BorderSide(color: _Pal.outlineVariant),
                      ),
                    ),
                  ),
                  child: DropdownButtonFormField<CriticalTask>(
                    value: _selectedTask,
                    decoration: _inputDecoration().copyWith(
                      hintText: 'Seleccionar tipo...',
                      suffixIcon: const Icon(
                        Icons.expand_more,
                        color: _Pal.outline,
                      ),
                    ),
                    isExpanded: true,
                    menuMaxHeight: 300,
                    borderRadius: BorderRadius.circular(12),
                    items: CriticalTask.values.map((task) {
                      return DropdownMenuItem(
                        value: task,
                        child: Text(
                          task.label,
                          style: const TextStyle(
                            fontSize: 14,
                            color: _Pal.onSurface,
                          ),
                        ),
                      );
                    }).toList(),
                    onChanged: (val) => setState(() => _selectedTask = val),
                    validator: (val) => val == null ? 'Seleccione una tarea' : null,
                  ),
                ),
                const SizedBox(height: 20),
                // Tipo y Descripción
                _FieldLabel(label: 'Tipo y Descripción de la actividad'),
                const SizedBox(height: 6),
                TextFormField(
                  controller: _descripcionController,
                  maxLines: 4,
                  style: const TextStyle(color: _Pal.onSurface, fontSize: 14),
                  decoration: _inputDecoration().copyWith(
                    hintText: 'Describa detalladamente la actividad a realizar...',
                    suffixIcon: ScanButton(controller: _descripcionController),
                  ),
                  validator: (val) {
                    if (val == null || val.trim().isEmpty) {
                      return 'Describa la actividad';
                    }
                    return null;
                  },
                ),
              ],
            ),

            const SizedBox(height: 16),

            // ═══════════════════════════════════════════════════
            // EMISIÓN Y EJECUCIÓN
            // ═══════════════════════════════════════════════════
            _SectionCard(
              icon: Icons.assignment_outlined,
              title: 'Emisión y Ejecución',
              children: [
                _FieldLabel(label: 'Nombre del Emisor'),
                const SizedBox(height: 6),
                TextFormField(
                  controller: _emisorController,
                  style: const TextStyle(color: _Pal.onSurface, fontSize: 14),
                  decoration: _inputDecoration().copyWith(
                    hintText: 'Ej: Nombre del Emisor',
                  ),
                ),
                const SizedBox(height: 16),
                _FieldLabel(label: 'Nombre del Ejecutante'),
                const SizedBox(height: 6),
                TextFormField(
                  controller: _ejecutanteController,
                  style: const TextStyle(color: _Pal.onSurface, fontSize: 14),
                  decoration: _inputDecoration().copyWith(
                    hintText: 'Ej: Nombre del Ejecutante',
                  ),
                ),
                const SizedBox(height: 16),
                _FieldLabel(label: 'Empresa Ejecutante'),
                const SizedBox(height: 6),
                TextFormField(
                  controller: _empresaController,
                  style: const TextStyle(color: _Pal.onSurface, fontSize: 14),
                  decoration: _inputDecoration().copyWith(
                    hintText: 'Ej: Constructora XYZ',
                  ),
                ),
              ],
            ),

            const SizedBox(height: 16),

            // ═══════════════════════════════════════════════════
            // UBICACIÓN
            // ═══════════════════════════════════════════════════
            _SectionCard(
              icon: Icons.location_on_outlined,
              title: 'Ubicación',
              children: [
                _FieldLabel(label: 'Área / Planta'),
                const SizedBox(height: 6),
                TextFormField(
                  controller: _areaController,
                  style: const TextStyle(color: _Pal.onSurface, fontSize: 14),
                  decoration: _inputDecoration().copyWith(
                    hintText: 'Ej: Planta 3, Edificio B',
                  ),
                ),
                const SizedBox(height: 16),
                _FieldLabel(label: 'Nota'),
                const SizedBox(height: 6),
                TextFormField(
                  controller: _notaController,
                  maxLines: 4,
                  style: const TextStyle(color: _Pal.onSurface, fontSize: 14),
                  decoration: _inputDecoration().copyWith(
                    hintText: 'Escriba aquí cualquier observación o detalle adicional...',
                    suffixIcon: ScanButton(controller: _notaController),
                  ),
                ),
              ],
            ),

            const SizedBox(height: 16),

            // ═══════════════════════════════════════════════════
            // PROGRAMACIÓN
            // ═══════════════════════════════════════════════════
            _SectionCard(
              icon: Icons.schedule_outlined,
              title: 'Programación',
              children: [
                // Selector de jornada
                _FieldLabel(label: 'Tipo de Jornada'),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: _JornadaChip(
                        icon: Icons.wb_sunny_outlined,
                        label: 'Diurna',
                        subtitle: '06:00 – 18:00',
                        selected: _tipoJornada == 'diurna',
                        onTap: () => setState(() {
                          _tipoJornada = 'diurna';
                          _horaInicio = const TimeOfDay(hour: 6, minute: 0);
                          _horaFin = const TimeOfDay(hour: 18, minute: 0);
                        }),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: _JornadaChip(
                        icon: Icons.nightlight_outlined,
                        label: 'Nocturna',
                        subtitle: '18:00 – 06:00',
                        selected: _tipoJornada == 'nocturna',
                        onTap: () => setState(() {
                          _tipoJornada = 'nocturna';
                          _horaInicio = const TimeOfDay(hour: 18, minute: 0);
                          _horaFin = const TimeOfDay(hour: 6, minute: 0);
                        }),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                // Fila: Fecha Inicio - Hora Inicio
                Row(
                  children: [
                    Expanded(
                      child: _DateField(
                        label: 'Fecha Inicio',
                        date: _fechaInicio,
                        onTap: () async {
                          final picked = await _pickDate(_fechaInicio);
                          if (picked != null) {
                            setState(() {
                              _fechaInicio = picked;
                              // Auto-calcular fecha fin: inicio + 7 días
                              _fechaFin = picked.add(const Duration(days: 7));
                            });
                          }
                        },
                        displayText: dateFmt.format(_fechaInicio),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: _TimeField(
                        label: 'Hora Inicio',
                        time: _horaInicio,
                        onTap: () async {
                          final picked = await _pickTime(_horaInicio);
                          if (picked != null) setState(() => _horaInicio = picked);
                        },
                        displayText: _horaInicio.format(context),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                // Fila: Fecha Fin - Hora Fin
                Row(
                  children: [
                    Expanded(
                      child: _DateField(
                        label: 'Fecha Fin',
                        date: _fechaFin,
                        onTap: () async {
                          final picked = await _pickDate(_fechaFin);
                          if (picked != null) setState(() => _fechaFin = picked);
                        },
                        displayText: dateFmt.format(_fechaFin),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: _TimeField(
                        label: 'Hora Fin',
                        time: _horaFin,
                        onTap: () async {
                          final picked = await _pickTime(_horaFin);
                          if (picked != null) setState(() => _horaFin = picked);
                        },
                        displayText: _horaFin.format(context),
                      ),
                    ),
                  ],
                ),
              ],
            ),

            const SizedBox(height: 16),

            // ═══════════════════════════════════════════════════
            // RESPONSABLE / SUPERVISOR
            // ═══════════════════════════════════════════════════
            _SectionCard(
              icon: Icons.badge_outlined,
              title: 'Responsable / Supervisor',
              children: [
                _FieldLabel(label: 'Nombre Completo'),
                const SizedBox(height: 6),
                TextFormField(
                  controller: _responsableController,
                  style: const TextStyle(color: _Pal.onSurface, fontSize: 14),
                  decoration: _inputDecoration().copyWith(
                    hintText: 'Buscar responsable...',
                    prefixIcon: const Icon(
                      Icons.person_outline,
                      color: _Pal.outline,
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                // Chip de validación
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: _Pal.primaryFixed.withValues(alpha: 0.3),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                      color: _Pal.primary.withValues(alpha: 0.1),
                    ),
                  ),
                  child: Row(
                    children: [
                      Container(
                        width: 40,
                        height: 40,
                        decoration: BoxDecoration(
                          color: _Pal.primary,
                          borderRadius: BorderRadius.circular(20),
                        ),
                        child: const Icon(
                          Icons.shield_outlined,
                          color: _Pal.onPrimary,
                          size: 20,
                        ),
                      ),
                      const SizedBox(width: 12),
                      const Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Validación Requerida',
                              style: TextStyle(
                                fontSize: 14,
                                fontWeight: FontWeight.w600,
                                color: _Pal.primary,
                              ),
                            ),
                            SizedBox(height: 2),
                            Text(
                              'El supervisor recibirá una notificación para firmar.',
                              style: TextStyle(
                                fontSize: 12,
                                color: _Pal.onSurfaceVariant,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),

            const SizedBox(height: 16),

            // ═══════════════════════════════════════════════════
            // SAFETY GUIDELINES HINT
            // ═══════════════════════════════════════════════════
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: _Pal.tertiary.withValues(alpha: 0.06),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                  color: _Pal.tertiary.withValues(alpha: 0.2),
                ),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Padding(
                    padding: EdgeInsets.only(top: 2),
                    child: Icon(
                      Icons.warning_amber_rounded,
                      color: _Pal.tertiary,
                      size: 22,
                    ),
                  ),
                  const SizedBox(width: 12),
                  const Expanded(
                    child: Text(
                      'Asegúrese de haber completado la evaluación de riesgos en el sitio antes de guardar este permiso. El incumplimiento puede resultar en la invalidación inmediata.',
                      style: TextStyle(
                        fontSize: 12,
                        color: _Pal.tertiary,
                        height: 1.4,
                      ),
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 24),
          ],
        ),
      ),
      ), // Cierra el Theme
      // ── FAB: Nuevo permiso (similar al HTML) ────────────────
      floatingActionButton: FloatingActionButton(
        onPressed: _guardar,
        backgroundColor: _Pal.primary,
        foregroundColor: _Pal.onPrimary,
        child: _isSaving
            ? const SizedBox(
                width: 24,
                height: 24,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  color: Colors.white,
                ),
              )
            : const Icon(Icons.check),
      ),
      // ── BottomNavBar ─────────────────────────────────────────
      bottomNavigationBar: Container(
        decoration: const BoxDecoration(
          color: _Pal.surfaceLowest,
          border: Border(top: BorderSide(color: _Pal.outlineVariant)),
        ),
        child: SafeArea(
          top: false,
          child: SizedBox(
            height: 64,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _navItem(Icons.home_outlined, 'Inicio', active: false),
                _navItem(Icons.assignment, 'Permisos', active: true),
                _navItem(Icons.qr_code_scanner_outlined, 'Escaneo', active: false),
                _navItem(Icons.person_outline, 'Perfil', active: false),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _navItem(IconData icon, String label, {required bool active}) {
    final color = active ? _Pal.primary : _Pal.onSurfaceVariant;
    final weight = active ? FontWeight.w700 : FontWeight.w500;
    return Expanded(
      child: InkWell(
        onTap: () {
          if (!active) {
            if (label == 'Inicio') {
              Navigator.pushReplacementNamed(context, '/home');
            } else if (label == 'Perfil') {
              Navigator.pushNamed(context, '/perfil');
            } else {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text('"$label" próximamente'),
                  behavior: SnackBarBehavior.floating,
                ),
              );
            }
          }
        },
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, color: color, size: 24),
            const SizedBox(height: 4),
            Text(
              label,
              style: TextStyle(
                color: color,
                fontSize: 12,
                fontWeight: weight,
                letterSpacing: 0.5,
              ),
            ),
          ],
        ),
      ),
    );
  }

  InputDecoration _inputDecoration() {
    return InputDecoration(
      filled: true,
      fillColor: _Pal.surfaceLowest,
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      // Color del texto escrito: negro/oscuro para contraste con fondo blanco
      labelStyle: const TextStyle(color: _Pal.onSurface, fontSize: 14),
      hintStyle: TextStyle(color: _Pal.onSurfaceVariant.withValues(alpha: 0.9), fontSize: 14),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: _Pal.outlineVariant),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: BorderSide(color: _Pal.outlineVariant),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: _Pal.primary, width: 2),
      ),
      errorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: _Pal.error),
      ),
      focusedErrorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: _Pal.error, width: 2),
      ),
    );
  }
}

// ── Widgets auxiliares ──────────────────────────────────────────────

/// Tarjeta de sección con barra lateral de color primario.
class _SectionCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final List<Widget> children;

  const _SectionCard({
    required this.icon,
    required this.title,
    required this.children,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: _Pal.surfaceLowest,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: _Pal.outlineVariant.withValues(alpha: 0.3)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 6,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Stack(
        children: [
          // Barra lateral izquierda
          Positioned(
            left: 0,
            top: 0,
            bottom: 0,
            child: Container(
              width: 4,
              decoration: BoxDecoration(
                color: _Pal.primary,
                borderRadius: const BorderRadius.only(
                  topLeft: Radius.circular(11),
                  bottomLeft: Radius.circular(11),
                ),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Título de la sección
                Row(
                  children: [
                    Icon(icon, size: 20, color: _Pal.primary),
                    const SizedBox(width: 8),
                    Text(
                      title,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                        color: _Pal.onSurface,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                ...children,
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Label de campo de formulario.
class _FieldLabel extends StatelessWidget {
  final String label;
  const _FieldLabel({required this.label});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(left: 4),
      child: Text(
        label,
        style: const TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w500,
          letterSpacing: 0.5,
          color: _Pal.onSurfaceVariant,
        ),
      ),
    );
  }
}

/// Campo de selección de fecha.
class _DateField extends StatelessWidget {
  final String label;
  final DateTime date;
  final VoidCallback onTap;
  final String displayText;

  const _DateField({
    required this.label,
    required this.date,
    required this.onTap,
    required this.displayText,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _FieldLabel(label: label),
        const SizedBox(height: 6),
        InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(12),
          child: Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
            decoration: BoxDecoration(
              color: _Pal.surfaceLowest,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: _Pal.outlineVariant),
            ),
            child: Row(
              children: [
                const Icon(
                  Icons.calendar_today_outlined,
                  size: 18,
                  color: _Pal.outline,
                ),
                const SizedBox(width: 8),
                Text(
                  displayText,
                  style: const TextStyle(
                    fontSize: 14,
                    color: _Pal.onSurface,
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

/// Chip de selección de jornada (Diurna/Nocturna).
class _JornadaChip extends StatelessWidget {
  final IconData icon;
  final String label;
  final String subtitle;
  final bool selected;
  final VoidCallback onTap;

  const _JornadaChip({
    required this.icon,
    required this.label,
    required this.subtitle,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 10),
        decoration: BoxDecoration(
          color: selected ? _Pal.primary.withValues(alpha: 0.08) : _Pal.surfaceLowest,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: selected ? _Pal.primary : _Pal.outlineVariant,
            width: selected ? 2 : 1,
          ),
        ),
        child: Column(
          children: [
            Icon(icon, color: selected ? _Pal.primary : _Pal.outline, size: 22),
            const SizedBox(height: 4),
            Text(
              label,
              style: TextStyle(
                fontSize: 13,
                fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
                color: selected ? _Pal.primary : _Pal.onSurface,
              ),
            ),
            Text(
              subtitle,
              style: TextStyle(
                fontSize: 11,
                color: selected ? _Pal.primary.withValues(alpha: 0.7) : _Pal.onSurfaceVariant,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Campo de selección de hora.
class _TimeField extends StatelessWidget {
  final String label;
  final TimeOfDay time;
  final VoidCallback onTap;
  final String displayText;

  const _TimeField({
    required this.label,
    required this.time,
    required this.onTap,
    required this.displayText,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _FieldLabel(label: label),
        const SizedBox(height: 6),
        InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(12),
          child: Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
            decoration: BoxDecoration(
              color: _Pal.surfaceLowest,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: _Pal.outlineVariant),
            ),
            child: Row(
              children: [
                const Icon(
                  Icons.access_time_outlined,
                  size: 18,
                  color: _Pal.outline,
                ),
                const SizedBox(width: 8),
                Text(
                  displayText,
                  style: const TextStyle(
                    fontSize: 14,
                    color: _Pal.onSurface,
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}




