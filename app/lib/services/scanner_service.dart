import 'package:image_picker/image_picker.dart';
import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';

/// Servicio para escanear texto usando la cámara del dispositivo + OCR.
/// Toma una foto, la procesa con Google ML Kit y devuelve el texto reconocido.
class ScannerService {
  final ImagePicker _picker = ImagePicker();
  late final TextRecognizer _recognizer;

  ScannerService() {
    _recognizer = TextRecognizer(script: TextRecognitionScript.latin);
  }

  /// Abre la cámara, toma una foto y reconoce el texto.
  /// Retorna el texto extraído o null si el usuario cancela o hay error.
  Future<String?> scanText() async {
    try {
      // 1. Tomar foto con la cámara
      final XFile? photo = await _picker.pickImage(
        source: ImageSource.camera,
        maxWidth: 1920,
        maxHeight: 1920,
        imageQuality: 85,
        preferredCameraDevice: CameraDevice.rear,
      );

      if (photo == null) return null; // Usuario canceló

      // 2. Procesar la imagen con ML Kit
      final InputImage inputImage = InputImage.fromFilePath(photo.path);
      final RecognizedText recognizedText =
          await _recognizer.processImage(inputImage);

      // 3. Extraer todo el texto
      final String text = recognizedText.text;

      if (text.trim().isEmpty) {
        return ''; // No se reconoció texto
      }

      return text;
    } catch (e) {
      // Si hay un error (permiso denegado, etc.), relanzamos para manejarlo arriba
      throw Exception('Error al escanear texto: $e');
    } finally {
      // Liberar recursos del recognizer
      // No lo cerramos aquí porque es reutilizable; se cierra al dispose del servicio
    }
  }

  /// Libera los recursos del recognizer
  void dispose() {
    _recognizer.close();
  }
}
