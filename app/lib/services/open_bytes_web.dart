import 'dart:html' as html;

Future<void> abrirBytes(List<int> bytes, String filename, String mime) async {
  final blob = html.Blob([bytes], mime.isEmpty ? 'application/octet-stream' : mime);
  final url = html.Url.createObjectUrlFromBlob(blob);
  html.window.open(url, '_blank');
}
