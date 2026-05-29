import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../models/documento_model.dart';
import 'documento_offline_service.dart';

/// Detecta cambios hechos en la web y limpia caché local obsoleta en el móvil.
class DocumentoSyncService {
  static const _keyVersiones = 'docs_sync_updated_at';

  /// Tras obtener la lista del servidor, invalida textos descargados que cambiaron.
  static Future<void> aplicarListaServidor(List<DocumentoModel> docs) async {
    final prefs = await SharedPreferences.getInstance();
    final prev = _leerVersiones(prefs);
    final idsServidor = <String>{};

    for (final doc in docs) {
      final key = doc.id.toString();
      idsServidor.add(key);
      final prevUpdated = prev[key] as String?;
      final serverUpdated = doc.updatedAt ?? doc.createdAt ?? '';

      if (prevUpdated != null &&
          serverUpdated.isNotEmpty &&
          prevUpdated != serverUpdated) {
        await DocumentoOfflineService.eliminar(doc.id);
      }
      if (doc.isProcesando) {
        await DocumentoOfflineService.eliminar(doc.id);
      }

      prev[key] = serverUpdated;
    }

    // Documentos eliminados en servidor: limpiar entradas huérfanas
    for (final key in prev.keys.toList()) {
      if (!idsServidor.contains(key)) {
        final id = int.tryParse(key);
        if (id != null) await DocumentoOfflineService.eliminar(id);
        prev.remove(key);
      }
    }

    await prefs.setString(_keyVersiones, jsonEncode(prev));
  }

  /// True si el documento cambió en el servidor respecto a la caché local.
  static Future<bool> documentoCambioEnServidor(DocumentoModel doc) async {
    if (doc.isProcesando) return true;

    final prefs = await SharedPreferences.getInstance();
    final prev = _leerVersiones(prefs);
    final prevUpdated = prev[doc.id.toString()] as String?;
    final serverUpdated = doc.updatedAt ?? doc.createdAt ?? '';

    if (serverUpdated.isEmpty) return false;
    if (prevUpdated == null) return false;
    return prevUpdated != serverUpdated;
  }

  /// Registra la versión conocida tras cargar un documento individual.
  static Future<void> registrarDocumento(DocumentoModel doc) async {
    final prefs = await SharedPreferences.getInstance();
    final prev = _leerVersiones(prefs);
    prev[doc.id.toString()] = doc.updatedAt ?? doc.createdAt ?? '';
    await prefs.setString(_keyVersiones, jsonEncode(prev));
  }

  static Map<String, dynamic> _leerVersiones(SharedPreferences prefs) {
    final raw = prefs.getString(_keyVersiones);
    if (raw == null || raw.isEmpty) return {};
    try {
      return Map<String, dynamic>.from(jsonDecode(raw) as Map);
    } catch (_) {
      return {};
    }
  }
}
