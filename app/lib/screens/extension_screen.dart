import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:intl/intl.dart';
import 'package:path_provider/path_provider.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;
import '../models/permit_model.dart';
import '../services/permiso_offline_service.dart';

/// Pantalla para gestionar extensiones de un permiso de trabajo.
/// Soporta hasta 7 extensiones (una por jornada) con escaneo múltiple.
class ExtensionScreen extends StatefulWidget {
  final PermitModel permit;

  const ExtensionScreen({super.key, required this.permit});

  @override
  State<ExtensionScreen> createState() => _ExtensionScreenState();
}

class _ExtensionScreenState extends State<ExtensionScreen> {
  static const int _maxExtensiones = 7;
  final ImagePicker _picker = ImagePicker();

  // Lista de extensiones en edición (día a día)
  final List<_ExtensionDraft> _borradores = [];
  bool _isSaving = false;

  @override
  void initState() {
    super.initState();
    _inicializarBorradores();
  }

  void _inicializarBorradores() {
    final existentes = widget.permit.extensiones;

    for (int i = 0; i < _maxExtensiones; i++) {
      if (i < existentes.length) {
        // Restaurar extensión guardada
        _borradores.add(_ExtensionDraft(
          dia: i + 1,
          fechaExtension: existentes[i].fechaExtension,
          scanPaths: existentes[i].scanPath.isNotEmpty
              ? [existentes[i].scanPath]
              : [],
          guardada: true,
        ));
      } else {
        // Slot vacío
        _borradores.add(_ExtensionDraft(
          dia: i + 1,
          fechaExtension: DateTime.now().add(Duration(days: i + 1)),
          scanPaths: [],
          guardada: false,
        ));
      }
    }
  }

  Future<void> _capturarScan(int index) async {
    try {
      final XFile? photo = await _picker.pickImage(
        source: ImageSource.camera,
        imageQuality: 80,
        maxWidth: 1920,
        maxHeight: 1920,
      );
      if (photo != null && mounted) {
        setState(() {
          _borradores[index].scanPaths.add(photo.path);
        });
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error al capturar: $e'), backgroundColor: Colors.red),
        );
      }
    }
  }

  Future<String?> _generarPdfMultiPagina(List<String> imagePaths) async {
    if (imagePaths.isEmpty) return null;
    try {
    final pdf = pw.Document();
    for (final path in imagePaths) {
      final file = File(path);
      if (!await file.exists()) continue;
      try {
        final bytes = await file.readAsBytes();
        pdf.addPage(pw.Page(
          pageFormat: PdfPageFormat.a4,
          build: (_) => pw.Center(
            child: pw.Image(pw.MemoryImage(bytes), fit: pw.BoxFit.contain),
          ),
        ));
      } catch (_) {}
    }


      final outputDir = await getApplicationDocumentsDirectory();
      final pdfsDir = Directory('${outputDir.path}/pdfs');
      if (!await pdfsDir.exists()) await pdfsDir.create(recursive: true);

      final timestamp = DateTime.now().millisecondsSinceEpoch;
      final permitId = widget.permit.id.replaceAll(RegExp(r'[\s/\-]'), '_');
      final pdfPath = '${pdfsDir.path}/${permitId}_ext$timestamp.pdf';
      await File(pdfPath).writeAsBytes(await pdf.save());
      return pdfPath;
    } catch (e) {
      debugPrint('Error generando PDF: $e');
      return null;
    }
  }

  Future<void> _guardarTodo() async {
    final conDatos = _borradores.where((b) => b.scanPaths.isNotEmpty).toList();
    if (conDatos.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Debe escanear al menos un día de extensión'),
          backgroundColor: Colors.red,
        ),
      );
      return;
    }

    setState(() => _isSaving = true);

    try {
      final nuevasExtensiones = <ExtensionModel>[];

      for (final borrador in _borradores) {
        if (borrador.scanPaths.isEmpty) continue;

        // Generar PDF multi-página con todas las imágenes del día
        final pdfPath = await _generarPdfMultiPagina(borrador.scanPaths);
        final scanFinal =
            pdfPath ?? (borrador.scanPaths.isNotEmpty ? borrador.scanPaths.first : '');

        if (scanFinal.isNotEmpty) {
          nuevasExtensiones.add(ExtensionModel(
            fechaExtension: borrador.fechaExtension,
            scanPath: scanFinal,
            createdAt: DateTime.now(),
          ));
        }
      }

      if (nuevasExtensiones.isEmpty) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('No se pudo generar ninguna extensión'),
              backgroundColor: Colors.red,
            ),
          );
        }
        setState(() => _isSaving = false);
        return;
      }

      final permitActualizado = widget.permit.copyWith(
        extensiones: nuevasExtensiones,
      );

      await PermisoOfflineService.guardarPermiso(permitActualizado);

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
                '${nuevasExtensiones.length} extensión(es) guardada(s) correctamente'),
            backgroundColor: const Color(0xFF3B6D11),
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
                        child: Icon(Icons.arrow_back,
                            color: Color(0xFF003398), size: 24),
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Extensiones',
                          style: TextStyle(
                              fontSize: 20,
                              fontWeight: FontWeight.w600,
                              color: Color(0xFF003398)),
                        ),
                        Text(
                          '${widget.permit.id} — Máx. $_maxExtensiones jornadas',
                          style: const TextStyle(
                              fontSize: 11, color: Color(0xFF747686)),
                        ),
                      ],
                    ),
                  ),
                  Material(
                    color: Colors.transparent,
                    child: InkWell(
                      borderRadius: BorderRadius.circular(8),
                      onTap: _isSaving ? null : _guardarTodo,
                      child: Padding(
                        padding:
                            const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                        child: _isSaving
                            ? const SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Color(0xFF003398),
                                ),
                              )
                            : const Text(
                                'Guardar',
                                style: TextStyle(
                                  fontSize: 14,
                                  fontWeight: FontWeight.bold,
                                  color: Color(0xFF003398),
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
          // Info del permiso
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                  color: const Color(0xFFC3C5D7).withOpacity(0.3)),
            ),
            child: Row(children: [
              const Icon(Icons.assignment, size: 20, color: Color(0xFF003398)),
              const SizedBox(width: 10),
              Expanded(
                  child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                Text(widget.permit.id,
                    style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w600,
                        color: Color(0xFF1A1C1C))),
                Text(widget.permit.title,
                    style: const TextStyle(
                        fontSize: 12, color: Color(0xFF434654))),
              ])),
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: const Color(0xFFEAF3DE),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  '${_borradores.where((b) => b.scanPaths.isNotEmpty).length}/$_maxExtensiones',
                  style: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      color: Color(0xFF3B6D11)),
                ),
              ),
            ]),
          ),

          const SizedBox(height: 20),

          // Slots diarios
          ...List.generate(_maxExtensiones, (i) => _buildDiaSlot(i, dateFmt)),

          const SizedBox(height: 20),

          // Resumen
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: const Color(0xFFEAF3DE),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                  color: const Color(0xFF3B6D11).withOpacity(0.2)),
            ),
            child: Row(
              children: [
                const Icon(Icons.info_outline,
                    size: 20, color: Color(0xFF3B6D11)),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    'El permiso permite hasta $_maxExtensiones extensiones (1 por jornada). '
                    'Cada extensión puede contener múltiples páginas escaneadas que se consolidan en un solo PDF.',
                    style: const TextStyle(
                        fontSize: 12,
                        color: Color(0xFF3B6D11),
                        height: 1.4),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDiaSlot(int index, DateFormat dateFmt) {
    final borrador = _borradores[index];
    final tieneScan = borrador.scanPaths.isNotEmpty;
    final estaGuardada = borrador.guardada;

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: tieneScan
              ? const Color(0xFF3B6D11).withOpacity(0.3)
              : const Color(0xFFC3C5D7).withOpacity(0.3),
          width: estaGuardada ? 2 : 1,
        ),
      ),
      child: ExpansionTile(
        key: PageStorageKey('ext_$index'),
        initiallyExpanded: index < 2 || !estaGuardada,
        tilePadding:
            const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
        leading: CircleAvatar(
          radius: 16,
          backgroundColor: tieneScan
              ? const Color(0xFF3B6D11)
              : const Color(0xFFE8F0FE),
          child: Text(
            '${index + 1}',
            style: TextStyle(
              fontWeight: FontWeight.bold,
              color: tieneScan ? Colors.white : const Color(0xFF003398),
              fontSize: 14,
            ),
          ),
        ),
        title: Row(
          children: [
            Expanded(
              child: Text(
                'Jornada ${index + 1}',
                style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFF1A1C1C)),
              ),
            ),
            if (tieneScan)
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                decoration: BoxDecoration(
                  color: const Color(0xFFEAF3DE),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  '${borrador.scanPaths.length} pág',
                  style: const TextStyle(
                      fontSize: 10,
                      fontWeight: FontWeight.w600,
                      color: Color(0xFF3B6D11)),
                ),
              ),
            if (estaGuardada) ...[
              const SizedBox(width: 4),
              const Icon(Icons.check_circle,
                  size: 16, color: Color(0xFF3B6D11)),
            ],
          ],
        ),
        subtitle: Text(
          'Vence: ${dateFmt.format(borrador.fechaExtension)}',
          style: const TextStyle(fontSize: 11, color: Color(0xFF747686)),
        ),
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Selector fecha
                Row(
                  children: [
                    const Icon(Icons.calendar_today,
                        size: 16, color: Color(0xFF747686)),
                    const SizedBox(width: 8),
                    InkWell(
                      onTap: estaGuardada
                          ? null
                          : () async {
                              final picked = await showDatePicker(
                                context: context,
                                initialDate: borrador.fechaExtension,
                                firstDate: DateTime.now(),
                                lastDate: DateTime(2035),
                                locale: const Locale('es'),
                              );
                              if (picked != null && mounted) {
                                setState(
                                    () => borrador.fechaExtension = picked);
                              }
                            },
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 12, vertical: 6),
                        decoration: BoxDecoration(
                          color: const Color(0xFFF5F5F7),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Text(
                          dateFmt.format(borrador.fechaExtension),
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w500,
                            color: estaGuardada
                                ? const Color(0xFF747686)
                                : const Color(0xFF1A1C1C),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),

                // Mini galería de páginas escaneadas
                if (borrador.scanPaths.isNotEmpty)
                  SizedBox(
                    height: 80,
                    child: ListView.builder(
                      scrollDirection: Axis.horizontal,
                      itemCount: borrador.scanPaths.length,
                      itemBuilder: (_, pi) {
                        final path = borrador.scanPaths[pi];
                        return Stack(
                          children: [
                            Container(
                              width: 64,
                              height: 80,
                              margin: const EdgeInsets.only(right: 6),
                              child: ClipRRect(
                                borderRadius: BorderRadius.circular(8),
                                child: File(path).existsSync()
                                    ? Image.file(File(path),
                                        fit: BoxFit.cover,
                                        width: 64,
                                        height: 80)
                                    : Container(
                                        color: Colors.grey[200],
                                        child: const Icon(
                                            Icons.broken_image,
                                            size: 24,
                                            color: Colors.grey)),
                              ),
                            ),
                            if (!estaGuardada)
                              Positioned(
                                top: 2,
                                right: 8,
                                child: GestureDetector(
                                  onTap: () => setState(() =>
                                      borrador.scanPaths.removeAt(pi)),
                                  child: Container(
                                    width: 20,
                                    height: 20,
                                    decoration: const BoxDecoration(
                                        color: Colors.red,
                                        shape: BoxShape.circle),
                                    child: const Icon(Icons.close,
                                        size: 14, color: Colors.white),
                                  ),
                                ),
                              ),
                          ],
                        );
                      },
                    ),
                  ),

                const SizedBox(height: 8),

                // Botones escanear
                if (!estaGuardada)
                  Row(
                    children: [
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: () => _capturarScan(index),
                          icon: const Icon(Icons.camera_alt_outlined,
                              size: 16),
                          label: Text(
                            borrador.scanPaths.isEmpty
                                ? 'Escanear día ${index + 1}'
                                : 'Agregar página',
                            style: const TextStyle(fontSize: 12),
                          ),
                          style: OutlinedButton.styleFrom(
                            foregroundColor: const Color(0xFF003398),
                            side: const BorderSide(color: Color(0xFFC3C5D7)),
                            padding: const EdgeInsets.symmetric(vertical: 8),
                            shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(8)),
                          ),
                        ),
                      ),
                    ],
                  ),
                if (estaGuardada)
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: const Color(0xFFEAF3DE),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.check_circle,
                            size: 16, color: Color(0xFF3B6D11)),
                        SizedBox(width: 6),
                        Text('Extensión guardada',
                            style: TextStyle(
                                fontSize: 12,
                                color: Color(0xFF3B6D11),
                                fontWeight: FontWeight.w600)),
                      ],
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

class _ExtensionDraft {
  final int dia;
  DateTime fechaExtension;
  List<String> scanPaths;
  bool guardada;

  _ExtensionDraft({
    required this.dia,
    required this.fechaExtension,
    required this.scanPaths,
    this.guardada = false,
  });
}
