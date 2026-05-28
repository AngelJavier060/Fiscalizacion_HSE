import 'package:audioplayers/audioplayers.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Una opción de sonido de fondo para acompañar la lectura/escucha.
class AmbienteOpcion {
  final String id;
  final String label;
  final String? asset; // null = "Solo voz" (sin fondo)
  const AmbienteOpcion(this.id, this.label, this.asset);
}

/// Controla el sonido ambiental de fondo (estilo Pocket FM) que acompaña a la
/// voz. La elección y el volumen se guardan en el dispositivo y se comparten
/// entre el lector de documentos y el chat de FISCALIZA-AI.
class FondoSonido {
  static const List<AmbienteOpcion> opciones = [
    AmbienteOpcion('pad', 'Pad calmado', 'audio/ambiente.wav'),
    AmbienteOpcion('lluvia', 'Lluvia suave', 'audio/lluvia.wav'),
    AmbienteOpcion('ninguno', 'Solo voz', null),
  ];

  static const _kId = 'fondo_ambiente_id';
  static const _kVol = 'fondo_volumen';

  final AudioPlayer _player = AudioPlayer();
  String _ambienteId = 'pad';
  double _volumen = 0.18;
  String? _cargado;

  String get ambienteId => _ambienteId;
  double get volumen => _volumen;

  AmbienteOpcion get ambiente => opciones.firstWhere(
        (o) => o.id == _ambienteId,
        orElse: () => opciones.last,
      );

  bool get activo => ambiente.asset != null;

  Future<void> init() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      _ambienteId = prefs.getString(_kId) ?? _ambienteId;
      _volumen = prefs.getDouble(_kVol) ?? _volumen;
      await _player.setReleaseMode(ReleaseMode.loop);
      await _player.setVolume(_volumen);
      await _preparar();
    } catch (_) {}
  }

  Future<void> _preparar() async {
    final asset = ambiente.asset;
    if (asset == null || _cargado == asset) return;
    await _player.setSource(AssetSource(asset));
    _cargado = asset;
  }

  Future<void> iniciar() async {
    if (!activo) return;
    try {
      await _preparar();
      await _player.setVolume(_volumen);
      await _player.resume();
    } catch (_) {}
  }

  Future<void> detener() async {
    try {
      await _player.pause();
    } catch (_) {}
  }

  Future<void> setAmbiente(String id, {bool sonando = false}) async {
    _ambienteId = id;
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_kId, id);
    } catch (_) {}
    if (!activo) {
      await detener();
    } else {
      await _preparar();
      if (sonando) await iniciar();
    }
  }

  Future<void> setVolumen(double v) async {
    _volumen = v;
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setDouble(_kVol, v);
      await _player.setVolume(v);
    } catch (_) {}
  }

  Future<void> dispose() async {
    try {
      await _player.dispose();
    } catch (_) {}
  }
}
