int _toInt(dynamic v, [int d = 0]) {
  if (v is int) return v;
  if (v is num) return v.toInt();
  if (v is String) return int.tryParse(v) ?? d;
  return d;
}

class UserModel {
  final int id;
  final String nombre;
  final String email;
  final String rol;
  final int empresaId;
  final String empresaNombre;
  final bool activo;
  final List<String> modulos;

  UserModel({
    required this.id,
    required this.nombre,
    required this.email,
    required this.rol,
    required this.empresaId,
    required this.empresaNombre,
    required this.activo,
    this.modulos = const [],
  });

  factory UserModel.fromJson(Map<String, dynamic> json) {
    final src = json['usuario'] is Map
        ? Map<String, dynamic>.from(json['usuario'] as Map)
        : json;
    final mods = json['modulos'] ?? src['modulos'];
    return UserModel(
      id: _toInt(src['id'] ?? json['id']),
      nombre: src['nombre'] as String? ?? json['nombre'] as String? ?? '',
      email: src['email'] as String? ?? json['email'] as String? ?? '',
      rol: src['rol'] as String? ?? json['rol'] as String? ?? 'USUARIO',
      empresaId: _toInt(src['empresaId'] ?? json['empresaId']),
      empresaNombre:
          src['empresaNombre'] as String? ?? json['empresaNombre'] as String? ?? '',
      activo: src['activo'] as bool? ?? json['activo'] as bool? ?? true,
      modulos: mods is List ? mods.map((e) => e.toString()).toList() : const [],
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'nombre': nombre,
    'email': email,
    'rol': rol,
    'empresaId': empresaId,
    'empresaNombre': empresaNombre,
    'activo': activo,
    'modulos': modulos,
  };

  String get iniciales {
    final parts = nombre.split(' ');
    if (parts.length >= 2) {
      return '${parts[0][0]}${parts[1][0]}'.toUpperCase();
    }
    return nombre.isNotEmpty ? nombre[0].toUpperCase() : 'U';
  }

  bool get isAdmin => rol == 'ADMIN_EMPRESA' || rol == 'SUPER_ADMIN';
  bool get isUsuario => rol == 'USUARIO';

  bool tieneModulo(String codigo) {
    if (modulos.isEmpty) return true;
    return modulos.contains(codigo);
  }
}

class LoginResponse {
  final String token;
  final UserModel usuario;
  final String tipo;
  final String? mensaje;

  LoginResponse({
    required this.token,
    required this.usuario,
    required this.tipo,
    this.mensaje,
  });

  factory LoginResponse.fromJson(Map<String, dynamic> json) {
    final usuarioJson = json['usuario'] is Map
        ? Map<String, dynamic>.from(json['usuario'] as Map)
        : json;
    return LoginResponse(
      token: json['token'] as String,
      usuario: UserModel.fromJson(usuarioJson),
      tipo: json['tipoToken'] as String? ?? json['tipo'] as String? ?? 'Bearer',
      mensaje: json['mensaje'] as String?,
    );
  }
}
