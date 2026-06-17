import 'dart:io';
import 'package:http/http.dart' as http;
import '../config/api_config.dart';
import '../models/permit_model.dart';
import 'api_service.dart';
import 'auth_service.dart';

/// Servicio API para permisos de trabajo HSE.
/// Conecta con [PermisoTrabajoController] del backend.
class PermisoService {
  /// Listar todos los permisos de una empresa
  static Future<List<PermitModel>> listar(int empresaId) async {
    final response = await ApiService.getList(
      '${ApiConfig.permisosTrabajo}/empresa/$empresaId/todos',
    );
    return response
        .map((e) => _fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Obtener un permiso por ID
  static Future<PermitModel> obtener(String id) async {
    final response = await ApiService.get(
      '${ApiConfig.permisosTrabajo}/$id',
    );
    return _fromJson(response);
  }

  /// Crear un nuevo permiso
  static Future<PermitModel> crear(PermitModel permit) async {
    final response = await ApiService.post(
      ApiConfig.permisosTrabajo,
      body: _toJson(permit),
    );
    return _fromJson(response);
  }

  /// Actualizar un permiso existente
  static Future<PermitModel> actualizar(PermitModel permit) async {
    final response = await ApiService.put(
      '${ApiConfig.permisosTrabajo}/${permit.id}',
      body: _toJson(permit),
    );
    return _fromJson(response);
  }

  /// Eliminar un permiso (borrado lógico)
  static Future<void> eliminar(String id) async {
    await ApiService.delete('${ApiConfig.permisosTrabajo}/$id');
  }

  /// Contar permisos por estado
  static Future<Map<String, int>> contar(int empresaId) async {
    final response = await ApiService.get(
      '${ApiConfig.permisosTrabajo}/empresa/$empresaId/contar',
    );
    return {
      'total': (response['total'] as int?) ?? 0,
      'vigentes': (response['vigentes'] as int?) ?? 0,
      'expirados': (response['expirados'] as int?) ?? 0,
    };
  }

  /// Subir archivo (PDF/imagen) a un permiso de trabajo
  static Future<String> subirArchivo(String permitId, String filePath) async {
    final file = File(filePath);
    if (!await file.exists()) {
      throw ApiException('El archivo no existe: $filePath');
    }

    final headers = await AuthService.getAuthHeaders();
    headers.remove('Content-Type'); // MultipartRequest establece su propio Content-Type

    final request = http.MultipartRequest(
      'POST',
      ApiConfig.uri('${ApiConfig.permisosTrabajo}/$permitId/archivo'),
    );
    request.headers.addAll(headers);

    request.files.add(await http.MultipartFile.fromPath(
      'archivo',
      filePath,
    ));

    try {
      final streamedResponse = await request.send();
      final response = await http.Response.fromStream(streamedResponse);
      if (response.statusCode == 201) {
        return response.body; // Ruta del archivo en el servidor
      }
      throw ApiException('Error al subir archivo: ${response.statusCode}');
    } catch (e) {
      if (e is ApiException) rethrow;
      throw ApiException('Error de conexión al subir archivo: $e');
    }
  }

  // ── Serialización ──────────────────────────────────────────

  static PermitModel _fromJson(Map<String, dynamic> json) {
    CriticalTask? task;
    if (json['criticalTask'] != null) {
      task = CriticalTask.values.firstWhere(
        (e) => e.name == json['criticalTask'],
        orElse: () => CriticalTask.hot,
      );
    }

    return PermitModel(
      id: json['id'] as String? ?? '',
      title: json['title'] as String? ?? '',
      area: json['area'] as String? ?? '',
      responsible: json['responsible'] as String? ?? '',
      startDate: _parseDate(json['startDate']),
      endDate: _parseDate(json['endDate']),
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
    );
  }

  static Map<String, dynamic> _toJson(PermitModel p) {
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
    };
  }

  static DateTime _parseDate(String? dateStr) {
    if (dateStr == null) return DateTime.now();
    return DateTime.tryParse(dateStr) ?? DateTime.now();
  }
}
