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
  final String? updatedAt;
  final String estadoProcesamiento;
  final String? errorProcesamiento;

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
    this.updatedAt,
    this.estadoProcesamiento = 'COMPLETADO',
    this.errorProcesamiento,
  });

  bool get isProcesando => estadoProcesamiento == 'PROCESANDO';
  bool get isError => estadoProcesamiento == 'ERROR';
  bool get isCompletado => estadoProcesamiento == 'COMPLETADO';

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
      updatedAt: json['updatedAt'] as String?,
      estadoProcesamiento:
          json['estadoProcesamiento'] as String? ?? 'COMPLETADO',
      errorProcesamiento: json['errorProcesamiento'] as String?,
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
  final String? textoEstructurado;
  final String? textoEditor;
  final String? idioma;

  DocumentoTexto({
    required this.id,
    required this.titulo,
    required this.textoCompleto,
    this.textoEstructurado,
    this.textoEditor,
    this.idioma,
  });

  factory DocumentoTexto.fromJson(Map<String, dynamic> json) {
    return DocumentoTexto(
      id: json['id'] as int? ?? 0,
      titulo: json['titulo'] as String? ?? '',
      textoCompleto: json['textoCompleto'] as String? ?? '',
      textoEstructurado: json['textoEstructurado'] as String?,
      textoEditor: json['textoEditor'] as String?,
      idioma: json['idioma'] as String?,
    );
  }

  /// Mejor texto disponible para TTS (plano, sin HTML).
  String get textoParaLectura {
    for (final raw in [textoCompleto, textoEstructurado, textoEditor]) {
      if (raw == null || raw.trim().isEmpty) continue;
      final plain = raw.contains('<') ? _htmlAPlano(raw) : raw;
      if (plain.trim().isNotEmpty) return plain.trim();
    }
    return '';
  }

  bool get tieneTexto => textoParaLectura.isNotEmpty;

  /// Convierte texto plano editado en HTML simple compatible con la web.
  static String planoAHtmlParaGuardar(String plain) {
    final t = plain.trim();
    if (t.isEmpty) return '';
    if (t.contains('<p>') || t.contains('<h1') || t.contains('<h2')) {
      return t;
    }
    final parrafos = t.split(RegExp(r'\n\n+'));
    final sb = StringBuffer();
    for (final raw in parrafos) {
      final p = raw.trim().replaceAll('\n', ' ');
      if (p.isEmpty) continue;
      if (p.length < 120 &&
          (p.toUpperCase() == p ||
              RegExp(r'^(?:\d+\.|\d+\.\d+|\d+\.)').hasMatch(p))) {
        sb.write('<h2>${_escapeHtml(p)}</h2>\n');
      } else {
        sb.write('<p>${_escapeHtml(p)}</p>\n');
      }
    }
    return sb.toString().trim();
  }

  static String _escapeHtml(String s) {
    return s
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;');
  }

  static String _htmlAPlano(String html) {
    var t = html
        .replaceAll(RegExp(r'</h[1-6]>', caseSensitive: false), '\n\n')
        .replaceAll(RegExp(r'</p>', caseSensitive: false), '\n\n')
        .replaceAll(RegExp(r'</li>', caseSensitive: false), '\n')
        .replaceAll(RegExp(r'<br\s*/?>', caseSensitive: false), '\n')
        .replaceAll(RegExp(r'<[^>]+>'), '')
        .replaceAll('&nbsp;', ' ')
        .replaceAll('&amp;', '&')
        .replaceAll('&lt;', '<')
        .replaceAll('&gt;', '>')
        .replaceAll('&quot;', '"');
    t = t.replaceAll(RegExp(r'\n{3,}'), '\n\n');
    return t.trim();
  }
}
