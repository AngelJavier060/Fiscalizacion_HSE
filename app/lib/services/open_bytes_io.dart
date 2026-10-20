import 'dart:io';

import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';

Future<void> abrirBytes(List<int> bytes, String filename, [String mime = '']) async {
  final dir = await getTemporaryDirectory();
  final safe = filename.replaceAll(RegExp(r'[\\/]+'), '_');
  final file = File('${dir.path}/$safe');
  await file.writeAsBytes(bytes, flush: true);
  await OpenFilex.open(file.path);
}
