class AlertaIncidente {
  final int id;
  final String codigo;
  final String titulo;
  final String detalle;
  final String fecha;
  final String ambito;
  final String area;
  final String unidad;
  final String dueno;
  final String hora;
  final String empresa;
  final String impacto;
  final bool accidenteGrave;
  final bool iap;
  final String eorpv;
  final String? archivoNombre;
  final bool tieneArchivo;
  final String prioridad;

  const AlertaIncidente({
    required this.id,
    required this.codigo,
    required this.titulo,
    required this.detalle,
    required this.fecha,
    required this.ambito,
    required this.area,
    required this.unidad,
    required this.dueno,
    required this.hora,
    required this.empresa,
    required this.impacto,
    required this.accidenteGrave,
    required this.iap,
    required this.eorpv,
    required this.archivoNombre,
    required this.tieneArchivo,
    required this.prioridad,
  });

  factory AlertaIncidente.fromJson(Map<String, dynamic> json) {
    return AlertaIncidente(
      id: (json['id'] as num?)?.toInt() ?? 0,
      codigo: (json['codigo'] ?? '').toString(),
      titulo: (json['titulo'] ?? '').toString(),
      detalle: (json['detalle'] ?? '').toString(),
      fecha: (json['fecha'] ?? '').toString(),
      ambito: (json['ambito'] ?? '').toString(),
      area: (json['area'] ?? '').toString(),
      unidad: (json['unidad'] ?? '').toString(),
      dueno: (json['dueno'] ?? '').toString(),
      hora: (json['hora'] ?? '').toString(),
      empresa: (json['empresa'] ?? '').toString(),
      impacto: (json['impacto'] ?? '').toString(),
      accidenteGrave: json['accidenteGrave'] == true,
      iap: json['iap'] == true,
      eorpv: (json['eorpv'] ?? '').toString(),
      archivoNombre: json['archivoNombre']?.toString(),
      tieneArchivo: json['tieneArchivo'] == true,
      prioridad: (json['prioridad'] ?? 'media').toString(),
    );
  }
}
