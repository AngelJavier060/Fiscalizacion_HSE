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

  /// Abre la cámara y retorna la ruta de la foto tomada, sin ejecutar OCR.
  /// Retorna null si el usuario cancela.
  Future<String?> pickImagePath() async {
    try {
      final XFile? photo = await _picker.pickImage(
        source: ImageSource.camera,
        maxWidth: 2560,
        maxHeight: 2560,
        imageQuality: 90,
        preferredCameraDevice: CameraDevice.rear,
      );
      return photo?.path;
    } catch (e) {
      throw Exception('Error al abrir la cámara: $e');
    }
  }

  /// Ejecuta OCR sobre una imagen ya existente en disco.
  /// Retorna el texto reconocido, cadena vacía si no hay texto, o null si hay error.
  Future<String?> runOCROnPath(String imagePath) async {
    try {
      final inputImage = InputImage.fromFilePath(imagePath);
      final recognized = await _recognizer.processImage(inputImage);
      final text = recognized.text.trim();
      return text.isEmpty ? '' : text;
    } catch (e) {
      throw Exception('Error al reconocer texto: $e');
    }
  }

  /// Libera los recursos del recognizer
  void dispose() {
    _recognizer.close();
  }
}
