import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';

import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:connectivity_plus/connectivity_plus.dart';

import '../models/permit_model.dart';
import 'permiso_service.dart';
import 'auth_service.dart';

/// Servicio de almacenamiento offline para permisos escaneados.
///
/// Funcionalidades:
/// - Guardar permisos (con imágenes escaneadas) en almacenamiento local
/// - Sincronización automática cuando se restablece la conexión
/// - Cola de pendientes para garantizar trazabilidad
///
/// Sigue el mismo patrón que [DocumentoOfflineService].
class PermisoOfflineService {
  static const _keyPendientes = 'permisos_pendientes_sync';
  static const _keyIndice = 'permisos_offline_index';

  /// Último mensaje de error/diagnóstico de la sincronización.
  /// Permite mostrar al usuario la causa exacta cuando un permiso no se
  /// sincroniza con el backend.
  static String? ultimoErrorSync;

  // ─── Directorio base ────────────────────────────────────────────
  static Future<Directory> _getDir() async {
    final base = await getApplicationDocumentsDirectory();
    final dir = Directory('${base.path}/permisos_offline');
    if (!await dir.exists()) {
      await dir.create(recursive: true);
    }
    return dir;
  }

  static Future<Directory> _getImagesDir() async {
    final base = await _getDir();
    final imgDir = Directory('${base.path}/images');
    if (!await imgDir.exists()) {
      await imgDir.create(recursive: true);
    }
    return imgDir;
  }

  // ─── Guardar permiso completo (datos + imágenes) ───────────────
  static Future<bool> guardarPermiso(PermitModel permit) async {
    try {
      final dir = await _getDir();
      final imgDir = await _getImagesDir();

      // 1) Copiar imágenes escaneadas al almacenamiento local
      final newPaths = <String>[];
      if (permit.imagePath != null && permit.imagePath!.isNotEmpty) {
        final paths = permit.imagePath!.split('|');
        for (final path in paths) {
          final src = File(path);
          if (await src.exists()) {
            final destPath =
                '${imgDir.path}/${permit.id}_${path.split('/').last}';
            await src.copy(destPath);
            newPaths.add(destPath);
          }
        }
      }

      // 2) Crear copia del permiso con rutas locales
      final localPermit = permit.copyWith(
        imagePath: newPaths.isEmpty ? permit.imagePath : newPaths.join('|'),
      );

      // 3) Guardar como JSON
      final file = File('${dir.path}/${permit.id}.json');
      await file.writeAsString(jsonEncode(_permitToJson(localPermit)));

      // 4) Registrar en el índice
      await _registrarEnIndice(permit.id, permit.title);

      // 5) SIEMPRE agregar a cola de sincronización pendiente.
      //    El mecanismo de sincronización se encargará de enviarlo al backend
      //    cuando haya conexión, evitando pérdida de datos.
      await _agregarAPendientes(permit.id);

      return true;
    } catch (e) {
      debugPrint('PermisoOfflineService.guardarPermiso error: $e');
      return false;
    }
  }

  // ─── Leer permiso desde almacenamiento local ───────────────────
  static Future<PermitModel?> obtenerPermiso(String id) async {
    try {
      final dir = await _getDir();
      final file = File('${dir.path}/$id.json');
      if (!await file.exists()) return null;

      final data = jsonDecode(await file.readAsString());
      return _permitFromJson(data);
    } catch (e) {
      debugPrint('PermisoOfflineService.obtenerPermiso error: $e');
      return null;
    }
  }

  // ─── Listar todos los permisos guardados localmente ────────────
  static Future<List<PermitModel>> listarPermisos() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final idx = _leerIndice(prefs);

      final permisos = <PermitModel>[];
      for (final id in idx.keys) {
        final p = await obtenerPermiso(id);
        if (p != null) permisos.add(p);
      }

      // Ordenar por fecha descendente
      permisos.sort((a, b) => b.startDate.compareTo(a.startDate));
      return permisos;
    } catch (e) {
      debugPrint('PermisoOfflineService.listarPermisos error: $e');
      return [];
    }
  }

  // ─── Sincronizar permisos pendientes ───────────────────────────
  static Future<int> sincronizarPendientes() async {
    ultimoErrorSync = null;
    final conn = await Connectivity().checkConnectivity();
    final isOnline = conn.any((c) => c != ConnectivityResult.none);

    if (!isOnline) {
      ultimoErrorSync = 'Sin conexión a internet.';
      return 0;
    }

    final prefs = await SharedPreferences.getInstance();
    final pendientes = _leerPendientes(prefs);

    if (pendientes.isEmpty) return 0;

    debugPrint('🔄 Iniciando sincronización de ${pendientes.length} permiso(s) pendiente(s)...');
    int sincronizados = 0;
    int empresaId = 0;

    try {
      final userData = await AuthService().getUserData();
      empresaId = (userData['empresaId'] as num?)?.toInt() ?? 0;
    } catch (_) {}

    for (final id in pendientes) {
      try {
        final permit = await obtenerPermiso(id);
        if (permit != null) {
          // Usar el empresaId guardado localmente (pudo elegirse al crear)
          int empresaParaSync = empresaId;
          
          // 1. Si el permiso ya tiene empresaId válido guardado localmente, usarlo
          if (permit.empresaId != null && permit.empresaId! > 0) {
            empresaParaSync = permit.empresaId!;
          }
          final permitParaBackend = permit.copyWith(empresaId: empresaParaSync);

          try {
            // Intentar crear en el backend
            final syncedPermit = await PermisoService.crear(permitParaBackend);
            
            // Si el ID era TEMP-, actualizar el almacenamiento local con el ID real
            if (id.startsWith('TEMP-') && syncedPermit.id != id) {
              await actualizarIdLocal(id, syncedPermit);
            }
          } catch (e) {
            final errorMsg = '$e';
            
            // Si el token expiró, informar al usuario inmediatamente
            if (errorMsg.contains('expirada') || errorMsg.contains('401') ||
                errorMsg.contains('Unauthorized')) {
              ultimoErrorSync =
                  'Sesión expirada. Cierra sesión e inicia de nuevo para sincronizar.';
              // No reintentar: todos los pendientes fallarán con 401
              break;
            }
            
            // Si el permiso ya existe en el backend, intentar actualizar
            if (errorMsg.contains('Ya existe') || errorMsg.contains('409') ||
                errorMsg.contains('conflict')) {
              try {
                await PermisoService.actualizar(permitParaBackend);
              } catch (e2) {
                ultimoErrorSync = errorMsg;
                continue;
              }
            } else {
              // Otro error (empresa no encontrada, validación, etc.)
              ultimoErrorSync = errorMsg;
              continue;
            }
          }

          // Subir los archivos (PDFs/imágenes) al servidor
          await _subirArchivosPermiso(permit);

          await _removerDePendientes(id, prefs);
          sincronizados++;
        } else {
          // Si no existe el archivo, remover de pendientes
          debugPrint('⚠️ Permiso $id no encontrado localmente, removiendo de pendientes');
          await _removerDePendientes(id, prefs);
        }
      } catch (e) {
        debugPrint(
            'PermisoOfflineService.sincronizarPendientes error ($id): $e');
      }
    }

    debugPrint('🔄 Sincronización completada: $sincronizados/${pendientes.length} sincronizados');
    return sincronizados;
  }

  /// Sube todos los archivos (PDFs e imágenes) de un permiso al servidor
  static Future<void> _subirArchivosPermiso(PermitModel permit) async {
    if (permit.imagePath == null || permit.imagePath!.isEmpty) return;

    final rutas = permit.imagePath!.split('|');
    for (final ruta in rutas) {
      final trimmed = ruta.trim();
      if (trimmed.isEmpty) continue;

      final file = File(trimmed);
      if (!await file.exists()) continue;

      try {
        // Solo subir archivos que parezcan locales (no rutas del servidor)
        if (!trimmed.contains('uploads/')) {
          await PermisoService.subirArchivo(permit.id, trimmed);
          debugPrint('✅ Archivo subido: $trimmed');
        }
      } catch (e) {
        debugPrint('⚠️ Error subiendo archivo $trimmed: $e');
        // No detener la sincronización por un archivo
      }
    }
  }

  // ─── Verificar si hay pendientes por sincronizar ──────────────
  static Future<int> pendientesCount() async {
    final prefs = await SharedPreferences.getInstance();
    return _leerPendientes(prefs).length;
  }

  // ─── Actualizar ID local después de sincronización ─────────────
  static Future<void> actualizarIdLocal(String oldId, PermitModel newPermit) async {
    try {
      final dir = await _getDir();
      final oldFile = File('${dir.path}/$oldId.json');
      
      if (await oldFile.exists()) {
        // Leer el archivo viejo
        final content = await oldFile.readAsString();
        final json = jsonDecode(content) as Map<String, dynamic>;
        
        // Actualizar con el nuevo ID y datos del backend
        json['id'] = newPermit.id;
        json['empresaId'] = newPermit.empresaId;
        
        // Guardar con el nuevo ID
        final newFile = File('${dir.path}/${newPermit.id}.json');
        await newFile.writeAsString(jsonEncode(json));
        
        // Eliminar el archivo viejo
        await oldFile.delete();
        
        // Actualizar el índice
        final prefs = await SharedPreferences.getInstance();
        final idx = _leerIndice(prefs);
        idx.remove(oldId);
        idx[newPermit.id] = newPermit.title;
        await prefs.setString(_keyIndice, jsonEncode(idx));
        
        // Actualizar la cola de pendientes
        await _removerDePendientes(oldId, prefs);
        
        debugPrint('✅ ID local actualizado: $oldId -> ${newPermit.id}');
      }
    } catch (e) {
      debugPrint('PermisoOfflineService.actualizarIdLocal error: $e');
    }
  }

  // ─── Eliminar permiso local ────────────────────────────────────
  static Future<void> eliminarPermiso(String id) async {
    try {
      final dir = await _getDir();
      final file = File('${dir.path}/$id.json');
      if (await file.exists()) await file.delete();

      final prefs = await SharedPreferences.getInstance();
      final idx = _leerIndice(prefs);
      idx.remove(id);
      await prefs.setString(_keyIndice, jsonEncode(idx));

      await _removerDePendientes(id, prefs);
    } catch (e) {
      debugPrint('PermisoOfflineService.eliminarPermiso error: $e');
    }
  }

  // ═══════════════════════════════════════════════════════════════
  // MÉTODOS PRIVADOS
  // ═══════════════════════════════════════════════════════════════

  static Future<void> _registrarEnIndice(String id, String titulo) async {
    final prefs = await SharedPreferences.getInstance();
    final idx = _leerIndice(prefs);
    idx[id] = {
      'titulo': titulo,
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

  static Future<void> _agregarAPendientes(String id) async {
    final prefs = await SharedPreferences.getInstance();
    final pendientes = _leerPendientes(prefs);
    if (!pendientes.contains(id)) {
      pendientes.add(id);
      await prefs.setString(_keyPendientes, jsonEncode(pendientes));
    }
  }

  static Future<void> _removerDePendientes(
      String id, SharedPreferences prefs) async {
    final pendientes = _leerPendientes(prefs);
    pendientes.remove(id);
    await prefs.setString(_keyPendientes, jsonEncode(pendientes));
  }

  static List<String> _leerPendientes(SharedPreferences prefs) {
    final raw = prefs.getString(_keyPendientes);
    if (raw == null || raw.isEmpty) return [];
    try {
      return List<String>.from(jsonDecode(raw) as List);
    } catch (_) {
      return [];
    }
  }

  // ─── Serialización ────────────────────────────────────────────
  static Map<String, dynamic> _permitToJson(PermitModel p) {
    return {
      'id': p.id,
      'title': p.title,
      'area': p.area,
      'responsible': p.responsible,
      'startDate': p.startDate.toIso8601String(),
      'endDate': p.endDate.toIso8601String(),
      'imagePath': p.imagePath,
      'criticalTask': p.criticalTask?.name,
      'description': p.description,
      'emisor': p.emisor,
      'ejecutante': p.ejecutante,
      'empresaEjecutante': p.empresaEjecutante,
      'nota': p.nota,
      'startTime': p.startTime?.toIso8601String(),
      'endTime': p.endTime?.toIso8601String(),
      'empresaId': p.empresaId ?? 0,
      'extensiones': p.extensiones.map((e) => e.toJson()).toList(),
    };
  }

  static PermitModel _permitFromJson(Map<String, dynamic> json) {
    CriticalTask? task;
    if (json['criticalTask'] != null) {
      task = CriticalTask.values.firstWhere(
        (e) => e.name == json['criticalTask'],
        orElse: () => CriticalTask.hot,
      );
    }

    List<ExtensionModel> extensiones = [];
    if (json['extensiones'] != null && json['extensiones'] is List) {
      extensiones = (json['extensiones'] as List)
          .map((e) => ExtensionModel.fromJson(e as Map<String, dynamic>))
          .toList();
    }

    return PermitModel(
      id: json['id'] as String? ?? '',
      title: json['title'] as String? ?? '',
      area: json['area'] as String? ?? '',
      responsible: json['responsible'] as String? ?? '',
      startDate: DateTime.tryParse(json['startDate'] as String? ?? '') ??
          DateTime.now(),
      endDate: DateTime.tryParse(json['endDate'] as String? ?? '') ??
          DateTime.now(),
      imagePath: json['imagePath'] as String?,
      criticalTask: task,
      description: json['description'] as String?,
      emisor: json['emisor'] as String?,
      ejecutante: json['ejecutante'] as String?,
      empresaEjecutante: json['empresaEjecutante'] as String?,
      nota: json['nota'] as String?,
      startTime: json['startTime'] != null
          ? DateTime.tryParse(json['startTime'] as String)
          : null,
      endTime: json['endTime'] != null
          ? DateTime.tryParse(json['endTime'] as String)
          : null,
      empresaId: json['empresaId'] as int?,
      extensiones: extensiones,
    );
  }
}
