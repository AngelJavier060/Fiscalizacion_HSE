import 'dart:convert';
import 'dart:io';

import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/documento_model.dart';
import 'documento_service.dart';

/// Guarda el texto de los documentos en el teléfono para leerlos SIN internet
/// (igual que la música descargada). El texto se guarda en un archivo local y
/// un índice en SharedPreferences lleva el registro de lo descargado.
class DocumentoOfflineService {
  static const _keyIndice = 'docs_offline_index';

  static Future<Directory> _dir() async {
    final base = await getApplicationDocumentsDirectory();
    final dir = Directory('${base.path}/docs_offline');
    if (!await dir.exists()) {
      await dir.create(recursive: true);
    }
    return dir;
  }

  static Future<File> _archivo(int id) async {
    final dir = await _dir();
    return File('${dir.path}/doc_$id.json');
  }

  /// Descarga el texto del backend y lo guarda en el teléfono.
  static Future<void> descargar(int id, String titulo) async {
    final texto = await DocumentoService.getTextoCompleto(id);
    await guardarTexto(texto, titulo);
  }

  /// Guarda en el teléfono un texto ya obtenido (para cachear automáticamente).
  static Future<void> guardarTexto(DocumentoTexto texto, String titulo) async {
    final file = await _archivo(texto.id);
    await file.writeAsString(jsonEncode({
      'id': texto.id,
      'titulo': texto.titulo,
      'textoCompleto': texto.textoCompleto,
      'idioma': texto.idioma,
    }));
    await _registrar(texto.id, titulo, texto.textoCompleto.length);
  }

  /// Lee el texto guardado en el teléfono (o null si no está descargado).
  static Future<DocumentoTexto?> obtener(int id) async {
    try {
      final file = await _archivo(id);
      if (!await file.exists()) return null;
      final data = jsonDecode(await file.readAsString()) as Map<String, dynamic>;
      return DocumentoTexto.fromJson(data);
    } catch (_) {
      return null;
    }
  }

  static Future<bool> estaDescargado(int id) async {
    final file = await _archivo(id);
    return file.exists();
  }

  static Future<void> eliminar(int id) async {
    final file = await _archivo(id);
    if (await file.exists()) await file.delete();
    final prefs = await SharedPreferences.getInstance();
    final idx = _leerIndice(prefs);
    idx.remove(id.toString());
    await prefs.setString(_keyIndice, jsonEncode(idx));
  }

  /// IDs de documentos descargados en este teléfono.
  static Future<Set<int>> idsDescargados() async {
    final prefs = await SharedPreferences.getInstance();
    return _leerIndice(prefs).keys.map(int.parse).toSet();
  }

  static Future<void> _registrar(int id, String titulo, int chars) async {
    final prefs = await SharedPreferences.getInstance();
    final idx = _leerIndice(prefs);
    idx[id.toString()] = {
      'titulo': titulo,
      'chars': chars,
      'fecha': DateTime.now().toIso8601String(),
    };
    await prefs.setString(_keyIndice, jsonEncode(idx));
  }

  static Map<String, dynamic> _leerIndice(SharedPreferences prefs) {
    final raw = prefs.getString(_keyIndice);
    if (raw == null || raw.isEmpty) return {};
    try {
      return Map<String, dynamic>.from(jsonDecode(raw) as Map);
    } catch (_) {
      return {};
    }
  }
}
