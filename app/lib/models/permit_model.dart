enum PermitStatus { active, warning, expired }

enum CriticalTask {
  hot,
  height,
  confined,
  electrical,
  excavation,
}

extension CriticalTaskX on CriticalTask {
  String get label {
    switch (this) {
      case CriticalTask.hot:
        return 'Trabajo en caliente';
      case CriticalTask.height:
        return 'Trabajo en altura';
      case CriticalTask.confined:
        return 'Espacio confinado';
      case CriticalTask.electrical:
        return 'Eléctrico';
      case CriticalTask.excavation:
        return 'Excavación';
    }
  }
}

class ExtensionModel {
  final DateTime fechaExtension;
  final String scanPath;
  final DateTime createdAt;

  const ExtensionModel({
    required this.fechaExtension,
    required this.scanPath,
    required this.createdAt,
  });

  Map<String, dynamic> toJson() => {
        'fechaExtension': fechaExtension.toIso8601String(),
        'scanPath': scanPath,
        'createdAt': createdAt.toIso8601String(),
      };

  factory ExtensionModel.fromJson(Map<String, dynamic> json) =>
      ExtensionModel(
        fechaExtension: DateTime.parse(json['fechaExtension'] as String),
        scanPath: json['scanPath'] as String? ?? '',
        createdAt: DateTime.parse(json['createdAt'] as String),
      );
}

class PermitModel {
  final String id;
  final String title;
  final String area;
  final String responsible;
  final DateTime startDate;
  final DateTime endDate;
  final String? imagePath;
  // ── Nuevos campos del formulario ──
  final CriticalTask? criticalTask;
  final String? description;
  final String? emisor;
  final String? ejecutante;
  final String? empresaEjecutante;
  final String? nota;
  final DateTime? startTime;
  final DateTime? endTime;
  final int? empresaId;
  // ── Extensión ──
  final List<ExtensionModel> extensiones;

  PermitModel({
    required this.id,
    required this.title,
    required this.area,
    required this.responsible,
    required this.startDate,
    required this.endDate,
    this.imagePath,
    this.criticalTask,
    this.description,
    this.emisor,
    this.ejecutante,
    this.empresaEjecutante,
    this.nota,
    this.startTime,
    this.endTime,
    this.empresaId,
    this.extensiones = const [],
  });

  /// Estado calculado automáticamente por fechas.
  PermitStatus get status {
    final now = DateTime.now();
    if (now.isAfter(endDate)) return PermitStatus.expired;
    final total = endDate.difference(startDate).inDays;
    final remaining = endDate.difference(now).inDays;
    if (total > 0 && remaining / total < 0.30) return PermitStatus.warning;
    return PermitStatus.active;
  }

  /// Fracción de vigencia transcurrida (0.0 a 1.0).
  double get progressFraction {
    final total = endDate.difference(startDate).inMilliseconds;
    final elapsed = DateTime.now().difference(startDate).inMilliseconds;
    return (elapsed / total).clamp(0.0, 1.0);
  }

  /// Días restantes (negativo si ya venció).
  int get remainingDays => endDate.difference(DateTime.now()).inDays;

  /// Porcentaje restante de vigencia.
  double get remainingPercent {
    final total = endDate.difference(startDate).inDays;
    if (total <= 0) return 0;
    final remaining = remainingDays;
    return ((remaining / total) * 100).clamp(0, 100);
  }

  /// True si tiene al menos un PDF escaneado
  bool get tieneScan => imagePath != null && imagePath!.isNotEmpty;

  /// True si tiene extensiones registradas
  bool get tieneExtension => extensiones.isNotEmpty;

  /// Fecha final considerando la última extensión
  DateTime get fechaFinalEfectiva =>
      extensiones.isNotEmpty ? extensiones.last.fechaExtension : endDate;

  /// Días restantes considerando extensiones
  int get remainingDaysEfectivo => fechaFinalEfectiva.difference(DateTime.now()).inDays;

  /// Crea una copia con campos actualizados.
  PermitModel copyWith({
    String? id,
    String? title,
    String? area,
    String? responsible,
    DateTime? startDate,
    DateTime? endDate,
    String? imagePath,
    CriticalTask? criticalTask,
    String? description,
    String? emisor,
    String? ejecutante,
    String? empresaEjecutante,
    String? nota,
    DateTime? startTime,
    DateTime? endTime,
    int? empresaId,
    List<ExtensionModel>? extensiones,
  }) =>
      PermitModel(
        id: id ?? this.id,
        title: title ?? this.title,
        area: area ?? this.area,
        responsible: responsible ?? this.responsible,
        startDate: startDate ?? this.startDate,
        endDate: endDate ?? this.endDate,
        imagePath: imagePath ?? this.imagePath,
        criticalTask: criticalTask ?? this.criticalTask,
        description: description ?? this.description,
        emisor: emisor ?? this.emisor,
        ejecutante: ejecutante ?? this.ejecutante,
        empresaEjecutante: empresaEjecutante ?? this.empresaEjecutante,
        nota: nota ?? this.nota,
        startTime: startTime ?? this.startTime,
        endTime: endTime ?? this.endTime,
        empresaId: empresaId ?? this.empresaId,
        extensiones: extensiones ?? this.extensiones,
      );

  // ── Datos de demostración ──────────────────────────────────────────

  static List<PermitModel> demoPermits() => [
    PermitModel(
      id: 'PT-2026-0842',
      title: 'Trabajo en caliente — soldadura',
      area: 'Planta 3 — Nivel 2',
      responsible: 'J. Morales',
      startDate: DateTime(2026, 1, 10),
      endDate: DateTime(2026, 6, 15),
      criticalTask: CriticalTask.hot,
      description: 'Soldadura de estructuras metálicas en área de producción.',
      emisor: 'Carlos Mendoza',
      ejecutante: 'Pedro Ramírez',
      empresaEjecutante: 'Soldaduras Industriales S.A.',
      nota: 'Usar careta de soldadura y guantes dieléctricos.',
      startTime: DateTime(2026, 1, 10, 8, 0),
      endTime: DateTime(2026, 6, 15, 17, 0),
    ),
    PermitModel(
      id: 'PT-2026-0810',
      title: 'Trabajo en altura — andamios',
      area: 'Edificio B — Torre externa',
      responsible: 'L. Gutiérrez',
      startDate: DateTime(2025, 11, 1),
      endDate: DateTime(2026, 2, 28),
      criticalTask: CriticalTask.height,
      description: 'Montaje y desmontaje de andamios en fachada exterior.',
      emisor: 'Ana Torres',
      ejecutante: 'Luis Gutiérrez',
      empresaEjecutante: 'Andamios Seguros S.A.',
      nota: 'Obligatorio arnés de seguridad y línea de vida.',
      startTime: DateTime(2025, 11, 1, 7, 0),
      endTime: DateTime(2026, 2, 28, 16, 0),
    ),
    PermitModel(
      id: 'PT-2025-1205',
      title: 'Espacio confinado — tanque TK-04',
      area: 'Planta 1 — Sector tanques',
      responsible: 'R. Díaz',
      startDate: DateTime(2025, 8, 1),
      endDate: DateTime(2025, 12, 20),
      criticalTask: CriticalTask.confined,
      description: 'Limpieza y mantenimiento interno del tanque de almacenamiento.',
      emisor: 'Mónica Rivas',
      ejecutante: 'Roberto Díaz',
      empresaEjecutante: 'Mantenimientos Petro S.A.C.',
      nota: 'Monitoreo continuo de gases. Equipo de respiración autónomo requerido.',
      startTime: DateTime(2025, 8, 1, 6, 0),
      endTime: DateTime(2025, 12, 20, 18, 0),
    ),
    PermitModel(
      id: 'PT-2026-0901',
      title: 'Eléctrico — tableros BT',
      area: 'Sala eléctrica — Piso 1',
      responsible: 'M. Soto',
      startDate: DateTime(2026, 3, 1),
      endDate: DateTime(2026, 5, 30),
      criticalTask: CriticalTask.electrical,
      description: 'Mantenimiento de tableros de baja tensión.',
      emisor: 'Jorge Paredes',
      ejecutante: 'Miguel Soto',
      empresaEjecutante: 'ElectroServicios Perú',
      nota: 'Bloqueo y etiquetado (LOTO) obligatorio. Verificar ausencia de tensión.',
      startTime: DateTime(2026, 3, 1, 8, 30),
      endTime: DateTime(2026, 5, 30, 17, 30),
    ),
  ];

  @override
  String toString() => 'PermitModel(id: $id, title: $title, status: $status)';
}
