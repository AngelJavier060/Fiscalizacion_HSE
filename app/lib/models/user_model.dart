class UserModel {
  final int id;
  final String nombre;
  final String email;
  final String rol;
  final int empresaId;
  final String empresaNombre;
  final bool activo;

  UserModel({
    required this.id,
    required this.nombre,
    required this.email,
    required this.rol,
    required this.empresaId,
    required this.empresaNombre,
    required this.activo,
  });

  factory UserModel.fromJson(Map<String, dynamic> json) {
    return UserModel(
      id: json['id'] as int,
      nombre: json['nombre'] as String? ?? '',
      email: json['email'] as String? ?? '',
      rol: json['rol'] as String? ?? 'USUARIO',
      empresaId: json['empresaId'] as int? ?? 0,
      empresaNombre: json['empresaNombre'] as String? ?? '',
      activo: json['activo'] as bool? ?? true,
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
    return LoginResponse(
      token: json['token'] as String,
      usuario: UserModel.fromJson(json['usuario'] as Map<String, dynamic>),
      tipo: json['tipo'] as String? ?? 'Bearer',
      mensaje: json['mensaje'] as String?,
    );
  }
}
