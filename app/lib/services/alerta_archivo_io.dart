import 'dart:io';
import 'dart:typed_data';

import 'package:path_provider/path_provider.dart';

Future<String?> guardarTemporalAlerta(Uint8List bytes, String nombre) async {
  final dir = await getTemporaryDirectory();
  final seguro = nombre.replaceAll(RegExp(r'[\\/]+'), '_');
  final archivo = File('${dir.path}/alerta_$seguro');
  await archivo.writeAsBytes(bytes, flush: true);
  return archivo.path;
}

Future<Uint8List?> leerRutaAlerta(String? ruta) async {
  if (ruta == null || ruta.isEmpty) return null;
  final archivo = File(ruta);
  if (!await archivo.exists()) return null;
  return archivo.readAsBytes();
}
