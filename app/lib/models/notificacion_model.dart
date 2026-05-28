class NotificacionModel {
  final int id;
  final String titulo;
  final String mensaje;
  final String tipo;
  final bool leida;
  final DateTime? fechaLectura;
  final bool tieneAudio;
  final String? rutaAudio;
  final int? duracionAudio;
  final int usuarioId;
  final String? recordatorioTitulo;
  final int? documentoId;
  final String? documentoTitulo;
  final DateTime createdAt;

  NotificacionModel({
    required this.id,
    required this.titulo,
    required this.mensaje,
    required this.tipo,
    required this.leida,
    this.fechaLectura,
    required this.tieneAudio,
    this.rutaAudio,
    this.duracionAudio,
    required this.usuarioId,
    this.recordatorioTitulo,
    this.documentoId,
    this.documentoTitulo,
    required this.createdAt,
  });

  factory NotificacionModel.fromJson(Map<String, dynamic> json) {
    return NotificacionModel(
      id: json['id'] as int,
      titulo: json['titulo'] as String? ?? 'Notificación',
      mensaje: json['mensaje'] as String? ?? '',
      tipo: json['tipo'] as String? ?? 'RECORDATORIO',
      leida: json['leida'] as bool? ?? false,
      fechaLectura: json['fechaLectura'] != null
          ? DateTime.tryParse(json['fechaLectura'] as String)
          : null,
      tieneAudio: json['tieneAudio'] as bool? ?? false,
      rutaAudio: json['rutaAudio'] as String?,
      duracionAudio: json['duracionAudio'] as int?,
      usuarioId: json['usuarioId'] as int? ?? 0,
      recordatorioTitulo: json['recordatorioTitulo'] as String?,
      documentoId: json['documentoId'] as int?,
      documentoTitulo: json['documentoTitulo'] as String?,
      createdAt: json['createdAt'] != null
          ? DateTime.parse(json['createdAt'] as String)
          : DateTime.now(),
    );
  }

  String get tiempoRelativo {
    final now = DateTime.now();
    final diff = now.difference(createdAt);

    if (diff.inMinutes < 1) return 'Ahora';
    if (diff.inMinutes < 60) return 'Hace ${diff.inMinutes} min';
    if (diff.inHours < 24) return 'Hace ${diff.inHours}h';
    if (diff.inDays < 7) return 'Hace ${diff.inDays}d';
    return '${createdAt.day}/${createdAt.month}';
  }

  String get tipoIcono {
    switch (tipo) {
      case 'RECORDATORIO': return '🔔';
      case 'DOCUMENTO': return '📄';
      case 'SISTEMA': return '⚙️';
      case 'ALERTA': return '⚠️';
      default: return '📬';
    }
  }
}

class ConsultaIaModel {
  final int id;
  final String pregunta;
  final String respuesta;
  final String? documentosReferencia;
  final String tipo;
  final DateTime createdAt;

  ConsultaIaModel({
    required this.id,
    required this.pregunta,
    required this.respuesta,
    this.documentosReferencia,
    required this.tipo,
    required this.createdAt,
  });

  factory ConsultaIaModel.fromJson(Map<String, dynamic> json) {
    return ConsultaIaModel(
      id: json['id'] as int,
      pregunta: json['pregunta'] as String? ?? '',
      respuesta: json['respuesta'] as String? ?? '',
      documentosReferencia: json['documentosReferencia'] as String?,
      tipo: json['tipo'] as String? ?? 'CONSULTA',
      createdAt: json['createdAt'] != null
          ? DateTime.parse(json['createdAt'] as String)
          : DateTime.now(),
    );
  }
}
