class DocumentoModel {
  final int id;
  final String titulo;
  final String? descripcion;
  final String archivoNombre;
  final int archivoTamano;
  final String? idiomaDetectado;
  final bool requiereTraduccion;
  final bool traducido;
  final bool puntosGeneradosIa;
  final int cantidadPuntos;
  final int cantidadPuntosRevisados;
  final bool activo;
  final int empresaId;
  final String empresaNombre;
  final String? createdAt;

  DocumentoModel({
    required this.id,
    required this.titulo,
    this.descripcion,
    required this.archivoNombre,
    required this.archivoTamano,
    this.idiomaDetectado,
    required this.requiereTraduccion,
    required this.traducido,
    required this.puntosGeneradosIa,
    required this.cantidadPuntos,
    required this.cantidadPuntosRevisados,
    required this.activo,
    required this.empresaId,
    required this.empresaNombre,
    this.createdAt,
  });

  factory DocumentoModel.fromJson(Map<String, dynamic> json) {
    return DocumentoModel(
      id: json['id'] as int,
      titulo: json['titulo'] as String? ?? 'Sin título',
      descripcion: json['descripcion'] as String?,
      archivoNombre: json['archivoNombre'] as String? ?? '',
      archivoTamano: json['archivoTamano'] as int? ?? 0,
      idiomaDetectado: json['idiomaDetectado'] as String?,
      requiereTraduccion: json['requiereTraduccion'] as bool? ?? false,
      traducido: json['traducido'] as bool? ?? false,
      puntosGeneradosIa: json['puntosGeneradosIa'] as bool? ?? false,
      cantidadPuntos: json['cantidadPuntos'] as int? ?? 0,
      cantidadPuntosRevisados: json['cantidadPuntosRevisados'] as int? ?? 0,
      activo: json['activo'] as bool? ?? true,
      empresaId: json['empresaId'] as int? ?? 0,
      empresaNombre: json['empresaNombre'] as String? ?? '',
      createdAt: json['createdAt'] as String?,
    );
  }

  String get tamanoFormateado {
    if (archivoTamano < 1024) return '$archivoTamano B';
    if (archivoTamano < 1048576) {
      return '${(archivoTamano / 1024).toStringAsFixed(1)} KB';
    }
    return '${(archivoTamano / 1048576).toStringAsFixed(1)} MB';
  }

  String get iconoIdioma {
    switch (idiomaDetectado) {
      case 'es': return '🇪🇸';
      case 'en': return '🇬🇧';
      case 'pt': return '🇵🇹';
      default: return '🌐';
    }
  }

  String get fechaFormateada {
    if (createdAt == null) return '';
    final date = DateTime.tryParse(createdAt!);
    if (date == null) return '';
    return '${date.day}/${date.month}/${date.year}';
  }
}

class PuntoClaveModel {
  final int id;
  final String contenido;
  final bool esIa;
  final double confianzaIa;
  final bool revisado;
  final int documentoId;

  PuntoClaveModel({
    required this.id,
    required this.contenido,
    required this.esIa,
    required this.confianzaIa,
    required this.revisado,
    required this.documentoId,
  });

  factory PuntoClaveModel.fromJson(Map<String, dynamic> json) {
    return PuntoClaveModel(
      id: json['id'] as int,
      contenido: json['contenido'] as String? ?? '',
      esIa: json['esIa'] as bool? ?? false,
      confianzaIa: (json['confianzaIa'] as num?)?.toDouble() ?? 0.0,
      revisado: json['revisado'] as bool? ?? false,
      documentoId: json['documentoId'] as int? ?? 0,
    );
  }
}

class DocumentoDetalle {
  final DocumentoModel documento;
  final List<PuntoClaveModel> puntosClave;

  DocumentoDetalle({
    required this.documento,
    required this.puntosClave,
  });
}

/// Texto extraído del PDF para el modo lectura/escucha.
class DocumentoTexto {
  final int id;
  final String titulo;
  final String textoCompleto;
  final String? idioma;

  DocumentoTexto({
    required this.id,
    required this.titulo,
    required this.textoCompleto,
    this.idioma,
  });

  factory DocumentoTexto.fromJson(Map<String, dynamic> json) {
    return DocumentoTexto(
      id: json['id'] as int? ?? 0,
      titulo: json['titulo'] as String? ?? '',
      textoCompleto: json['textoCompleto'] as String? ?? '',
      idioma: json['idioma'] as String?,
    );
  }

  bool get tieneTexto => textoCompleto.trim().isNotEmpty;
}
