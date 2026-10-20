int _asInt(dynamic v, [int d = 0]) {
  if (v is int) return v;
  if (v is num) return v.toInt();
  if (v is String) return int.tryParse(v) ?? d;
  return d;
}

double _asDouble(dynamic v, [double d = 0]) {
  if (v is num) return v.toDouble();
  if (v is String) return double.tryParse(v) ?? d;
  return d;
}

class SsoTema {
  final int id;
  final String nombre;
  final bool activo;
  final int totalPreguntas;
  final int preguntasActivas;
  final int cumplidasVuelta;

  SsoTema({
    required this.id,
    required this.nombre,
    required this.activo,
    required this.totalPreguntas,
    required this.preguntasActivas,
    this.cumplidasVuelta = 0,
  });

  factory SsoTema.fromJson(Map<String, dynamic> json) => SsoTema(
        id: _asInt(json['id']),
        nombre: json['nombre'] as String? ?? '',
        activo: json['activo'] as bool? ?? true,
        totalPreguntas: _asInt(json['totalPreguntas']),
        preguntasActivas: _asInt(json['preguntasActivas']),
        cumplidasVuelta: _asInt(json['cumplidasVuelta']),
      );
}

class SsoHistorialItem {
  final int id;
  final String? fechaInicio;
  final String? fechaFin;
  final String modo;
  final String temaNombre;
  final double? nota;
  final bool? aprobado;
  final String estado;
  final int totalPreguntas;
  final int? correctas;

  SsoHistorialItem({
    required this.id,
    this.fechaInicio,
    this.fechaFin,
    required this.modo,
    required this.temaNombre,
    this.nota,
    this.aprobado,
    required this.estado,
    required this.totalPreguntas,
    this.correctas,
  });

  factory SsoHistorialItem.fromJson(Map<String, dynamic> json) => SsoHistorialItem(
        id: _asInt(json['id']),
        fechaInicio: json['fechaInicio'] as String?,
        fechaFin: json['fechaFin'] as String?,
        modo: json['modo'] as String? ?? '',
        temaNombre: json['temaNombre'] as String? ?? '',
        nota: json['nota'] == null ? null : _asDouble(json['nota']),
        aprobado: json['aprobado'] as bool?,
        estado: json['estado'] as String? ?? '',
        totalPreguntas: _asInt(json['totalPreguntas']),
        correctas: json['correctas'] == null ? null : _asInt(json['correctas']),
      );
}

class SsoInicio {
  final int vueltaActual;
  final int preguntasVistasVuelta;
  final int preguntasBanco;
  final int falladasPendientes;
  final double? ultimaNota;
  final bool? ultimoAprobado;
  final String? ultimaFecha;
  final int? intentoPendienteId;
  final String? intentoPendienteModo;
  final List<SsoTema> temas;
  final int notaMinima;
  final int tiempo20Min;
  final int tiempo40Min;
  final int tiempoExamenMin;
  final int preguntasPorBanco;
  final bool bloqueRepetir;
  final double? bloqueRepetirNota;
  final int bloqueRepetirTotal;
  final int? bloqueRepetirTemaId;
  final String? bloqueRepetirTemaNombre;
  final List<SsoHistorialItem> historial;

  SsoInicio({
    required this.vueltaActual,
    required this.preguntasVistasVuelta,
    required this.preguntasBanco,
    required this.falladasPendientes,
    this.ultimaNota,
    this.ultimoAprobado,
    this.ultimaFecha,
    this.intentoPendienteId,
    this.intentoPendienteModo,
    required this.temas,
    required this.notaMinima,
    required this.tiempo20Min,
    required this.tiempo40Min,
    this.tiempoExamenMin = 0,
    this.preguntasPorBanco = 20,
    this.bloqueRepetir = false,
    this.bloqueRepetirNota,
    this.bloqueRepetirTotal = 0,
    this.bloqueRepetirTemaId,
    this.bloqueRepetirTemaNombre,
    required this.historial,
  });

  factory SsoInicio.fromJson(Map<String, dynamic> json) => SsoInicio(
        vueltaActual: _asInt(json['vueltaActual'], 1),
        preguntasVistasVuelta: _asInt(json['preguntasVistasVuelta']),
        preguntasBanco: _asInt(json['preguntasBanco']),
        falladasPendientes: _asInt(json['falladasPendientes']),
        ultimaNota: json['ultimaNota'] == null ? null : _asDouble(json['ultimaNota']),
        ultimoAprobado: json['ultimoAprobado'] as bool?,
        ultimaFecha: json['ultimaFecha'] as String?,
        intentoPendienteId: json['intentoPendienteId'] == null
            ? null
            : _asInt(json['intentoPendienteId']),
        intentoPendienteModo: json['intentoPendienteModo'] as String?,
        temas: ((json['temas'] as List?) ?? [])
            .whereType<Map>()
            .map((e) => SsoTema.fromJson(Map<String, dynamic>.from(e)))
            .toList(),
        notaMinima: _asInt(json['notaMinima'], 90),
        tiempo20Min: _asInt(json['tiempo20Min'], 15),
        tiempo40Min: _asInt(json['tiempo40Min'], 30),
        tiempoExamenMin: _asInt(json['tiempoExamenMin'], 0),
        preguntasPorBanco: _asInt(json['preguntasPorBanco'], 20),
        bloqueRepetir: json['bloqueRepetir'] as bool? ?? false,
        bloqueRepetirNota: json['bloqueRepetirNota'] == null ? null : _asDouble(json['bloqueRepetirNota']),
        bloqueRepetirTotal: _asInt(json['bloqueRepetirTotal']),
        bloqueRepetirTemaId: json['bloqueRepetirTemaId'] == null ? null : _asInt(json['bloqueRepetirTemaId']),
        bloqueRepetirTemaNombre: json['bloqueRepetirTemaNombre'] as String?,
        historial: ((json['historial'] as List?) ?? [])
            .whereType<Map>()
            .map((e) => SsoHistorialItem.fromJson(Map<String, dynamic>.from(e)))
            .toList(),
      );

  int get pctVuelta {
    if (preguntasBanco <= 0) return 0;
    final p = ((preguntasVistasVuelta / preguntasBanco) * 100).round();
    return p.clamp(0, 100);
  }

  int get minutosExamen => tiempoExamenMin > 0 ? tiempoExamenMin : tiempo20Min;

  String get tiempoLabel {
    final min = minutosExamen;
    if (min >= 60) {
      final h = min ~/ 60;
      final m = min % 60;
      return m == 0 ? '$h h' : '$h h $m min';
    }
    return '$min min';
  }

  String get bancoLabel =>
      preguntasPorBanco == 0 ? 'todas las preguntas' : '$preguntasPorBanco preguntas';
}

class SsoExamenOpcion {
  final int id;
  final String texto;

  SsoExamenOpcion({required this.id, required this.texto});

  factory SsoExamenOpcion.fromJson(Map<String, dynamic> json) => SsoExamenOpcion(
        id: _asInt(json['id']),
        texto: json['texto'] as String? ?? '',
      );
}

class SsoExamenPregunta {
  final int id;
  final int orden;
  final String enunciado;
  final String? temaNombre;
  int? opcionElegidaId;
  final List<SsoExamenOpcion> opciones;

  SsoExamenPregunta({
    required this.id,
    required this.orden,
    required this.enunciado,
    this.temaNombre,
    this.opcionElegidaId,
    required this.opciones,
  });

  factory SsoExamenPregunta.fromJson(Map<String, dynamic> json) => SsoExamenPregunta(
        id: _asInt(json['id']),
        orden: _asInt(json['orden']),
        enunciado: json['enunciado'] as String? ?? '',
        temaNombre: json['temaNombre'] as String?,
        opcionElegidaId:
            json['opcionElegidaId'] == null ? null : _asInt(json['opcionElegidaId']),
        opciones: ((json['opciones'] as List?) ?? [])
            .whereType<Map>()
            .map((e) => SsoExamenOpcion.fromJson(Map<String, dynamic>.from(e)))
            .toList(),
      );
}

class SsoExamen {
  final int id;
  final String modo;
  final int? temaId;
  final String temaNombre;
  final String estado;
  final String fechaInicio;
  final int tiempoLimiteMin;
  final int segundosRestantes;
  final int totalPreguntas;
  final int respondidas;
  final bool bancoInsuficiente;
  final String? aviso;
  final List<SsoExamenPregunta> preguntas;

  SsoExamen({
    required this.id,
    required this.modo,
    this.temaId,
    required this.temaNombre,
    required this.estado,
    required this.fechaInicio,
    required this.tiempoLimiteMin,
    this.segundosRestantes = -1,
    required this.totalPreguntas,
    required this.respondidas,
    required this.bancoInsuficiente,
    this.aviso,
    required this.preguntas,
  });

  factory SsoExamen.fromJson(Map<String, dynamic> json) => SsoExamen(
        id: _asInt(json['id']),
        modo: json['modo'] as String? ?? '',
        temaId: json['temaId'] == null ? null : _asInt(json['temaId']),
        temaNombre: json['temaNombre'] as String? ?? 'Todos los temas',
        estado: json['estado'] as String? ?? '',
        fechaInicio: json['fechaInicio'] as String? ?? '',
        tiempoLimiteMin: _asInt(json['tiempoLimiteMin'], 30),
        segundosRestantes: json['segundosRestantes'] == null ? -1 : _asInt(json['segundosRestantes']),
        totalPreguntas: _asInt(json['totalPreguntas']),
        respondidas: _asInt(json['respondidas']),
        bancoInsuficiente: json['bancoInsuficiente'] as bool? ?? false,
        aviso: json['aviso'] as String?,
        preguntas: ((json['preguntas'] as List?) ?? [])
            .whereType<Map>()
            .map((e) => SsoExamenPregunta.fromJson(Map<String, dynamic>.from(e)))
            .toList(),
      );

  int get progreso {
    if (totalPreguntas <= 0) return 0;
    return ((respondidas / totalPreguntas) * 100).round().clamp(0, 100);
  }
}

class SsoAciertosTema {
  final String tema;
  final int total;
  final int correctas;

  SsoAciertosTema({required this.tema, required this.total, required this.correctas});

  factory SsoAciertosTema.fromJson(Map<String, dynamic> json) => SsoAciertosTema(
        tema: json['tema'] as String? ?? '',
        total: _asInt(json['total']),
        correctas: _asInt(json['correctas']),
      );

  double get pct => total == 0 ? 0 : (correctas / total) * 100;
}

class SsoOpcionRev {
  final int id;
  final String texto;
  final bool correcta;
  final bool elegida;
  final String? argumento;

  SsoOpcionRev({
    required this.id,
    required this.texto,
    required this.correcta,
    required this.elegida,
    this.argumento,
  });

  factory SsoOpcionRev.fromJson(Map<String, dynamic> json) => SsoOpcionRev(
        id: _asInt(json['id']),
        texto: json['texto'] as String? ?? '',
        correcta: json['correcta'] as bool? ?? false,
        elegida: json['elegida'] as bool? ?? false,
        argumento: json['argumento'] as String?,
      );
}

class SsoRevision {
  final int preguntaId;
  final int orden;
  final String enunciado;
  final String? explicacion;
  final String? fuente;
  final bool tieneSustento;
  final String? sustentoNombre;
  final String? sustentoTipo;
  final String temaNombre;
  final bool? esCorrecta;
  final List<SsoOpcionRev> opciones;

  SsoRevision({
    required this.preguntaId,
    required this.orden,
    required this.enunciado,
    this.explicacion,
    this.fuente,
    this.tieneSustento = false,
    this.sustentoNombre,
    this.sustentoTipo,
    required this.temaNombre,
    this.esCorrecta,
    required this.opciones,
  });

  factory SsoRevision.fromJson(Map<String, dynamic> json) => SsoRevision(
        preguntaId: _asInt(json['preguntaId']),
        orden: _asInt(json['orden']),
        enunciado: json['enunciado'] as String? ?? '',
        explicacion: json['explicacion'] as String?,
        fuente: json['fuente'] as String?,
        tieneSustento: json['tieneSustento'] as bool? ?? false,
        sustentoNombre: json['sustentoNombre'] as String?,
        sustentoTipo: json['sustentoTipo'] as String?,
        temaNombre: json['temaNombre'] as String? ?? '',
        esCorrecta: json['esCorrecta'] as bool?,
        opciones: ((json['opciones'] as List?) ?? [])
            .whereType<Map>()
            .map((e) => SsoOpcionRev.fromJson(Map<String, dynamic>.from(e)))
            .toList(),
      );

  bool get fallada => esCorrecta != true;

  bool get esImagen {
    final t = (sustentoTipo ?? '').toLowerCase();
    final n = (sustentoNombre ?? '').toLowerCase();
    return t.startsWith('image/') || RegExp(r'\.(jpg|jpeg|png|webp|gif)$').hasMatch(n);
  }

  bool get esPdf {
    final t = (sustentoTipo ?? '').toLowerCase();
    final n = (sustentoNombre ?? '').toLowerCase();
    return t.contains('pdf') || n.endsWith('.pdf');
  }
}

class SustentoArchivo {
  final List<int> bytes;
  final String nombre;
  final String mime;

  SustentoArchivo({
    required this.bytes,
    required this.nombre,
    required this.mime,
  });

  bool get esImagen {
    final t = mime.toLowerCase();
    final n = nombre.toLowerCase();
    return t.startsWith('image/') || RegExp(r'\.(jpg|jpeg|png|webp|gif)$').hasMatch(n);
  }

  bool get esPdf {
    final t = mime.toLowerCase();
    final n = nombre.toLowerCase();
    return t.contains('pdf') || n.endsWith('.pdf');
  }
}

class SsoResultado {
  final int id;
  final String modo;
  final String temaNombre;
  final String estado;
  final String? fechaInicio;
  final String? fechaFin;
  final int totalPreguntas;
  final int correctas;
  final double nota;
  final bool aprobado;
  final List<SsoAciertosTema> aciertosPorTema;
  final List<SsoRevision> revision;

  SsoResultado({
    required this.id,
    required this.modo,
    required this.temaNombre,
    required this.estado,
    this.fechaInicio,
    this.fechaFin,
    required this.totalPreguntas,
    required this.correctas,
    required this.nota,
    required this.aprobado,
    required this.aciertosPorTema,
    required this.revision,
  });

  factory SsoResultado.fromJson(Map<String, dynamic> json) => SsoResultado(
        id: _asInt(json['id']),
        modo: json['modo'] as String? ?? '',
        temaNombre: json['temaNombre'] as String? ?? '',
        estado: json['estado'] as String? ?? '',
        fechaInicio: json['fechaInicio'] as String?,
        fechaFin: json['fechaFin'] as String?,
        totalPreguntas: _asInt(json['totalPreguntas']),
        correctas: _asInt(json['correctas']),
        nota: _asDouble(json['nota']),
        aprobado: json['aprobado'] as bool? ?? false,
        aciertosPorTema: ((json['aciertosPorTema'] as List?) ?? [])
            .whereType<Map>()
            .map((e) => SsoAciertosTema.fromJson(Map<String, dynamic>.from(e)))
            .toList(),
        revision: ((json['revision'] as List?) ?? [])
            .whereType<Map>()
            .map((e) => SsoRevision.fromJson(Map<String, dynamic>.from(e)))
            .toList(),
      );

  int get incorrectas => (totalPreguntas - correctas).clamp(0, totalPreguntas);
  double get tasaError =>
      totalPreguntas == 0 ? 0 : (incorrectas / totalPreguntas) * 100;
}
