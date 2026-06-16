import 'dart:io';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:intl/intl.dart';
import 'package:path_provider/path_provider.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;
import '../models/permit_model.dart';
import '../services/permiso_offline_service.dart';

/// Pantalla para agregar una extensión a un permiso de trabajo.
/// La extensión incluye una nueva fecha de vencimiento y un escaneo.
class ExtensionScreen extends StatefulWidget {
  final PermitModel permit;

  const ExtensionScreen({super.key, required this.permit});

  @override
  State<ExtensionScreen> createState() => _ExtensionScreenState();
}

class _ExtensionScreenState extends State<ExtensionScreen> {
  final ImagePicker _picker = ImagePicker();
  DateTime _fechaExtension = DateTime.now().add(const Duration(days: 30));
  String? _scanPath;
  bool _isSaving = false;

  Future<void> _capturarScan() async {
    try {
      final XFile? photo = await _picker.pickImage(
        source: ImageSource.camera,
        imageQuality: 85,
        maxWidth: 2048,
        maxHeight: 2048,
      );

      if (photo != null && mounted) {
        setState(() => _scanPath = photo.path);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error al capturar: $e'), backgroundColor: Colors.red),
        );
      }
    }
  }

  Future<String?> _generarPdfExtension(String imagePath) async {
    try {
      final pdf = pw.Document();
      final outputDir = await getApplicationDocumentsDirectory();
      final pdfsDir = Directory('${outputDir.path}/pdfs');
      if (!await pdfsDir.exists()) await pdfsDir.create(recursive: true);

      final file = File(imagePath);
      if (!await file.exists()) return null;

      final bytes = await file.readAsBytes();
      final img = pw.MemoryImage(bytes);

      pdf.addPage(
        pw.Page(
          pageFormat: PdfPageFormat.a4,
          build: (pw.Context context) {
            return pw.Center(child: pw.Image(img, fit: pw.BoxFit.contain));
          },
        ),
      );

      final timestamp = DateTime.now().millisecondsSinceEpoch;
      final pdfPath = '${pdfsDir.path}/extension_${widget.permit.id}_$timestamp.pdf';
      final pdfFile = File(pdfPath);
      await pdfFile.writeAsBytes(await pdf.save());
      return pdfPath;
    } catch (e) {
      debugPrint('Error generando PDF extensión: $e');
      return null;
    }
  }

  Future<void> _guardarExtension() async {
    if (_scanPath == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Debe capturar un documento de extensión'),
          backgroundColor: Colors.red,
        ),
      );
      return;
    }

    setState(() => _isSaving = true);

    // Generar PDF desde la imagen capturada
    final pdfPath = await _generarPdfExtension(_scanPath!);
    final scanFinal = pdfPath ?? _scanPath!;

    final extension = ExtensionModel(
      fechaExtension: _fechaExtension,
      scanPath: scanFinal,
      createdAt: DateTime.now(),
    );

    final permitActualizado = widget.permit.copyWith(
      extensiones: [...widget.permit.extensiones, extension],
    );

    try {
      await PermisoOfflineService.guardarPermiso(permitActualizado);

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Extensión guardada correctamente'),
            backgroundColor: Color(0xFF3B6D11),
          ),
        );
        Navigator.pop(context, permitActualizado);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error al guardar: $e'), backgroundColor: Colors.red),
        );
        setState(() => _isSaving = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final dateFmt = DateFormat('dd/MM/yyyy');

    return Scaffold(
      backgroundColor: const Color(0xFFF9F9F9),
      appBar: PreferredSize(
        preferredSize: const Size.fromHeight(64),
        child: Container(
          color: Colors.white,
          child: SafeArea(
            bottom: false,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Row(
                children: [
                  Material(
                    color: Colors.transparent,
                    child: InkWell(
                      borderRadius: BorderRadius.circular(20),
                      onTap: () => Navigator.pop(context),
                      child: const Padding(
                        padding: EdgeInsets.all(8),
                        child: Icon(Icons.arrow_back, color: Color(0xFF003398), size: 24),
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  const Expanded(
                    child: Text(
                      'Extensión de permiso',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w600,
                        color: Color(0xFF003398),
                      ),
                    ),
                  ),
                  Material(
                    color: Colors.transparent,
                    child: InkWell(
                      borderRadius: BorderRadius.circular(8),
                      onTap: _isSaving ? null : _guardarExtension,
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                        child: _isSaving
                            ? const SizedBox(
                                width: 20, height: 20,
                                child: CircularProgressIndicator(strokeWidth: 2, color: Color(0xFF003398)),
                              )
                            : const Text(
                                'Guardar',
                                style: TextStyle(
                                  fontSize: 14, fontWeight: FontWeight.bold, color: Color(0xFF003398),
                                ),
                              ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Información del permiso actual
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0xFFC3C5D7).withOpacity(0.3)),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Icon(Icons.assignment, size: 20, color: Color(0xFF003398)),
                    const SizedBox(width: 8),
                    Text(
                      widget.permit.id,
                      style: const TextStyle(
                        fontSize: 16, fontWeight: FontWeight.w600, color: Color(0xFF1A1C1C),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  widget.permit.title,
                  style: const TextStyle(fontSize: 13, color: Color(0xFF434654)),
                ),
                const SizedBox(height: 8),
                Text(
                  'Vence: ${dateFmt.format(widget.permit.endDate)}',
                  style: const TextStyle(fontSize: 12, color: Color(0xFF747686)),
                ),
                if (widget.permit.tieneExtension)
                  Padding(
                    padding: const EdgeInsets.only(top: 4),
                    child: Text(
                      'Extensiones: ${widget.permit.extensiones.length}',
                      style: const TextStyle(fontSize: 12, color: Color(0xFF3B6D11)),
                    ),
                  ),
              ],
            ),
          ),

          const SizedBox(height: 24),

          // Nueva fecha de extensión
          const Text(
            'Nueva fecha de vencimiento',
            style: TextStyle(
              fontSize: 14, fontWeight: FontWeight.w600, color: Color(0xFF1A1C1C),
            ),
          ),
          const SizedBox(height: 8),
          InkWell(
            onTap: () async {
              final picked = await showDatePicker(
                context: context,
                initialDate: _fechaExtension,
                firstDate: DateTime.now(),
                lastDate: DateTime(2035),
                locale: const Locale('es'),
              );
              if (picked != null) setState(() => _fechaExtension = picked);
            },
            child: Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: const Color(0xFFC3C5D7)),
              ),
              child: Row(
                children: [
                  const Icon(Icons.calendar_today, size: 18, color: Color(0xFF747686)),
                  const SizedBox(width: 8),
                  Text(
                    dateFmt.format(_fechaExtension),
                    style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w500, color: Color(0xFF1A1C1C)),
                  ),
                ],
              ),
            ),
          ),

          const SizedBox(height: 24),

          // Captura de documento de extensión
          const Text(
            'Documento de extensión',
            style: TextStyle(
              fontSize: 14, fontWeight: FontWeight.w600, color: Color(0xFF1A1C1C),
            ),
          ),
          const SizedBox(height: 8),
          if (_scanPath != null && File(_scanPath!).existsSync())
            Stack(
              children: [
                ClipRRect(
                  borderRadius: BorderRadius.circular(12),
                  child: Image.file(
                    File(_scanPath!),
                    fit: BoxFit.cover,
                    width: double.infinity,
                    height: 200,
                  ),
                ),
                Positioned(
                  top: 8, right: 8,
                  child: GestureDetector(
                    onTap: () => setState(() => _scanPath = null),
                    child: Container(
                      padding: const EdgeInsets.all(4),
                      decoration: const BoxDecoration(
                        color: Colors.red,
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.close, size: 16, color: Colors.white),
                    ),
                  ),
                ),
              ],
            )
          else
            Container(
              height: 160,
              decoration: BoxDecoration(
                color: Colors.grey.shade100,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: Colors.grey.shade300),
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.camera_alt_outlined, size: 40, color: Color(0xFF747686)),
                  const SizedBox(height: 8),
                  const Text(
                    'Capture el documento de extensión',
                    style: TextStyle(color: Color(0xFF747686), fontSize: 13),
                  ),
                ],
              ),
            ),
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: _capturarScan,
              icon: const Icon(Icons.camera_alt_outlined, size: 18),
              label: Text(_scanPath != null ? 'Volver a capturar' : 'Capturar documento'),
              style: OutlinedButton.styleFrom(
                foregroundColor: const Color(0xFF003398),
                side: const BorderSide(color: Color(0xFFC3C5D7)),
                padding: const EdgeInsets.symmetric(vertical: 12),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
            ),
          ),

          const SizedBox(height: 32),

          // Resumen
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: const Color(0xFFEAF3DE),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0xFF3B6D11).withOpacity(0.2)),
            ),
            child: Row(
              children: [
                const Icon(Icons.info_outline, size: 20, color: Color(0xFF3B6D11)),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    'Al guardar, el permiso ${widget.permit.id} se extenderá hasta el ${dateFmt.format(_fechaExtension)}.',
                    style: const TextStyle(fontSize: 12, color: Color(0xFF3B6D11)),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
