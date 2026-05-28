import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../config/api_config.dart';
import '../models/documento_model.dart';
import '../models/user_model.dart';
import '../services/documento_service.dart';
import '../services/documento_offline_service.dart';
import '../services/auth_service.dart';

/// Paleta clara MD3 (consistente con FISCALIZA-AI y lector).
class _Pal {
  static const bg = Color(0xFFFAF8FF);
  static const surface = Color(0xFFFFFFFF);
  static const low = Color(0xFFF2F3FF);
  static const high = Color(0xFFE2E7FF);
  static const border = Color(0xFFC3C5D9);
  static const primary = Color(0xFF003EC7);
  static const onSurface = Color(0xFF131B2E);
  static const onSurfaceVar = Color(0xFF434656);
  static const secondary = Color(0xFF006B5B);
  static const error = Color(0xFFBA1A1A);
}

class DocumentosScreen extends StatefulWidget {
  const DocumentosScreen({super.key});

  @override
  State<DocumentosScreen> createState() => _DocumentosScreenState();
}

class _DocumentosScreenState extends State<DocumentosScreen> {
  static const _keyEmpresaSel = 'doc_empresa_seleccionada';

  List<DocumentoModel> _documentos = [];
  List<DocumentoModel> _documentosFiltrados = [];
  bool _isLoading = true;
  String _busqueda = '';
  int _empresaId = 0;
  String? _error;

  // Selector de empresa (solo para SUPER_ADMIN que no pertenece a una empresa)
  bool _esSuperAdmin = false;
  List<EmpresaOpcion> _empresas = [];

  @override
  void initState() {
    super.initState();
    _loadDocumentos();
  }

  Future<void> _loadDocumentos() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });
    try {
      final authService = AuthService();
      final userDataMap = await authService.getUserData();
      final user = UserModel.fromJson(userDataMap);
      _esSuperAdmin = user.rol == 'SUPER_ADMIN';

      // Caso normal: el usuario pertenece a una empresa.
      if (user.empresaId > 0) {
        _empresaId = user.empresaId;
      } else if (_esSuperAdmin) {
        // Super admin: no tiene empresa fija -> debe elegir una.
        if (_empresas.isEmpty) {
          _empresas = await DocumentoService.getEmpresas();
        }
        if (_empresas.isEmpty) {
          if (mounted) {
            setState(() {
              _isLoading = false;
              _error = 'No hay empresas registradas todavía.';
            });
          }
          return;
        }
        // Recuperar la última empresa elegida o usar la primera.
        if (_empresaId <= 0) {
          final prefs = await SharedPreferences.getInstance();
          final guardada = prefs.getInt(_keyEmpresaSel);
          final existe = _empresas.any((e) => e.id == guardada);
          _empresaId = existe ? guardada! : _empresas.first.id;
        }
      } else {
        // Usuario normal sin empresa asignada.
        if (mounted) {
          setState(() {
            _isLoading = false;
            _error =
                'Tu usuario no tiene una empresa asignada, por eso no se pueden mostrar documentos. '
                'Pide al administrador que te asigne a una empresa.';
          });
        }
        return;
      }

      final docs = await DocumentoService.getDocumentos(_empresaId);
      if (mounted) {
        setState(() {
          _documentos = docs;
          _documentosFiltrados = docs;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _error = e.toString();
        });
      }
    }
  }

  Future<void> _cambiarEmpresa(int empresaId) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_keyEmpresaSel, empresaId);
    setState(() {
      _empresaId = empresaId;
      _busqueda = '';
    });
    await _loadDocumentos();
  }

  void _filtrar(String query) {
    setState(() {
      _busqueda = query;
      if (query.isEmpty) {
        _documentosFiltrados = _documentos;
      } else {
        _documentosFiltrados = _documentos
            .where((d) =>
                d.titulo.toLowerCase().contains(query.toLowerCase()) ||
                (d.descripcion?.toLowerCase().contains(query.toLowerCase()) ?? false))
            .toList();
      }
    });
  }

  Widget _buildError() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.cloud_off_rounded, size: 56, color: _Pal.error),
            const SizedBox(height: 16),
            const Text(
              'No se pudieron cargar los documentos',
              style: TextStyle(
                color: _Pal.onSurface,
                fontSize: 17,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              _error ?? '',
              textAlign: TextAlign.center,
              style: const TextStyle(
                  color: _Pal.onSurfaceVar, fontSize: 14, height: 1.4),
            ),
            const SizedBox(height: 20),
            ElevatedButton.icon(
              onPressed: _loadDocumentos,
              icon: const Icon(Icons.refresh_rounded),
              label: const Text('Reintentar'),
              style: ElevatedButton.styleFrom(
                backgroundColor: _Pal.primary,
                foregroundColor: Colors.white,
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: _Pal.bg,
      appBar: AppBar(
        backgroundColor: _Pal.surface,
        foregroundColor: _Pal.onSurface,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        shape: const Border(
          bottom: BorderSide(color: _Pal.high, width: 1),
        ),
        iconTheme: const IconThemeData(color: _Pal.onSurfaceVar),
        title: const Text(
          'Documentos Normativos',
          style: TextStyle(
              color: _Pal.onSurface, fontWeight: FontWeight.w700, fontSize: 16),
        ),
      ),
      body: Column(
        children: [
          // Selector de empresa (solo SUPER_ADMIN)
          if (_esSuperAdmin && _empresas.isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 14),
                decoration: BoxDecoration(
                  color: _Pal.surface,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: _Pal.border),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.domain, color: _Pal.onSurfaceVar, size: 20),
                    const SizedBox(width: 10),
                    Expanded(
                      child: DropdownButtonHideUnderline(
                        child: DropdownButton<int>(
                          value: _empresaId > 0 ? _empresaId : null,
                          isExpanded: true,
                          dropdownColor: _Pal.surface,
                          hint: const Text('Selecciona una empresa',
                              style: TextStyle(color: _Pal.onSurfaceVar)),
                          icon: const Icon(Icons.expand_more,
                              color: _Pal.onSurfaceVar),
                          style: const TextStyle(
                              color: _Pal.onSurface, fontSize: 15),
                          items: _empresas
                              .map((e) => DropdownMenuItem<int>(
                                    value: e.id,
                                    child: Text(e.nombre, overflow: TextOverflow.ellipsis),
                                  ))
                              .toList(),
                          onChanged: (id) {
                            if (id != null && id != _empresaId) _cambiarEmpresa(id);
                          },
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),

          // Barra de búsqueda
          Padding(
            padding: const EdgeInsets.all(16),
            child: TextField(
              cursorColor: _Pal.primary,
              style: const TextStyle(color: _Pal.onSurface),
              decoration: InputDecoration(
                hintText: 'Buscar documentos...',
                hintStyle: TextStyle(
                    color: _Pal.onSurfaceVar.withValues(alpha: 0.7)),
                filled: true,
                fillColor: _Pal.surface,
                prefixIcon: const Icon(Icons.search, color: _Pal.onSurfaceVar),
                suffixIcon: _busqueda.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear, color: _Pal.onSurfaceVar),
                        onPressed: () => _filtrar(''),
                      )
                    : null,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: const BorderSide(color: _Pal.border),
                ),
                enabledBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: const BorderSide(color: _Pal.border),
                ),
                focusedBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: const BorderSide(color: _Pal.primary, width: 1.5),
                ),
              ),
              onChanged: _filtrar,
            ),
          ),

          // Lista
          Expanded(
            child: _isLoading
                ? const Center(
                    child: CircularProgressIndicator(color: _Pal.primary),
                  )
                : _error != null
                    ? _buildError()
                    : _documentosFiltrados.isEmpty
                        ? Center(
                            child: Column(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                Icon(
                                  _busqueda.isNotEmpty
                                      ? Icons.search_off
                                      : Icons.description_outlined,
                                  size: 64,
                                  color: _Pal.onSurfaceVar.withValues(alpha: 0.5),
                                ),
                                const SizedBox(height: 16),
                                Text(
                                  _busqueda.isNotEmpty
                                      ? 'No se encontraron documentos'
                                      : 'No hay documentos disponibles',
                                  style: const TextStyle(
                                      color: _Pal.onSurfaceVar, fontSize: 16),
                                ),
                              ],
                            ),
                          )
                        : RefreshIndicator(
                            onRefresh: _loadDocumentos,
                            color: _Pal.primary,
                        child: ListView.builder(
                          padding: const EdgeInsets.symmetric(horizontal: 16),
                          itemCount: _documentosFiltrados.length,
                          itemBuilder: (context, index) {
                            final doc = _documentosFiltrados[index];
                            return _DocumentoCard(
                              documento: doc,
                              onTap: () => Navigator.pushNamed(
                                context,
                                '/documento-detalle',
                                arguments: {'id': doc.id, 'titulo': doc.titulo},
                              ),
                              onEscuchar: () => Navigator.pushNamed(
                                context,
                                '/documento-lector',
                                arguments: {'id': doc.id, 'titulo': doc.titulo},
                              ),
                            );
                          },
                        ),
                      ),
          ),
        ],
      ),
    );
  }
}

class _DocumentoCard extends StatefulWidget {
  final DocumentoModel documento;
  final VoidCallback onTap;
  final VoidCallback onEscuchar;

  const _DocumentoCard({
    required this.documento,
    required this.onTap,
    required this.onEscuchar,
  });

  @override
  State<_DocumentoCard> createState() => _DocumentoCardState();
}

class _DocumentoCardState extends State<_DocumentoCard> {
  bool _descargado = false;
  bool _descargando = false;
  String? _token;

  @override
  void initState() {
    super.initState();
    _verificarDescarga();
    _cargarToken();
  }

  Future<void> _cargarToken() async {
    final t = await AuthService.getToken();
    if (mounted) setState(() => _token = t);
  }

  Future<void> _verificarDescarga() async {
    final ok = await DocumentoOfflineService.estaDescargado(widget.documento.id);
    if (mounted) setState(() => _descargado = ok);
  }

  Widget _iconoPdf() {
    return Container(
      color: _Pal.low,
      alignment: Alignment.center,
      child: const Icon(Icons.picture_as_pdf, color: _Pal.error, size: 26),
    );
  }

  /// Portada del documento: primera página del PDF (con respaldo al ícono).
  Widget _portada() {
    final url =
        '${ApiConfig.baseUrl}${ApiConfig.documentosDetalle}/${widget.documento.id}/paginas/1/preview';
    return SizedBox(
      width: 48,
      height: 62,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(8),
        child: _token == null
            ? _iconoPdf()
            : CachedNetworkImage(
                imageUrl: url,
                httpHeaders: {'Authorization': 'Bearer $_token'},
                fit: BoxFit.cover,
                fadeInDuration: const Duration(milliseconds: 200),
                placeholder: (c, u) => _iconoPdf(),
                errorWidget: (c, u, e) => _iconoPdf(),
              ),
      ),
    );
  }

  Future<void> _descargar() async {
    setState(() => _descargando = true);
    try {
      await DocumentoOfflineService.descargar(
          widget.documento.id, widget.documento.titulo);
      if (!mounted) return;
      setState(() {
        _descargado = true;
        _descargando = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          backgroundColor: _Pal.surface,
          content: Text(
            'Guardado en el teléfono. Ya se puede escuchar sin internet.',
            style: TextStyle(color: _Pal.onSurface),
          ),
        ),
      );
    } catch (_) {
      if (!mounted) return;
      setState(() => _descargando = false);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          backgroundColor: _Pal.surface,
          content: Text(
            'No se pudo descargar. Verifica tu conexión.',
            style: TextStyle(color: _Pal.onSurface),
          ),
        ),
      );
    }
  }

  Future<void> _quitarDescarga() async {
    await DocumentoOfflineService.eliminar(widget.documento.id);
    if (mounted) setState(() => _descargado = false);
  }

  Widget _botonDescarga() {
    if (_descargando) {
      return const SizedBox(
        width: 48,
        height: 48,
        child: Center(
          child: SizedBox(
            width: 20,
            height: 20,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              color: _Pal.primary,
            ),
          ),
        ),
      );
    }
    if (_descargado) {
      return IconButton(
        visualDensity: VisualDensity.compact,
        constraints: const BoxConstraints(minWidth: 40, minHeight: 40),
        padding: EdgeInsets.zero,
        onPressed: _quitarDescarga,
        icon: const Icon(Icons.offline_pin_rounded),
        color: _Pal.secondary,
        tooltip: 'Guardado sin internet (toca para eliminar)',
      );
    }
    return IconButton(
      visualDensity: VisualDensity.compact,
      constraints: const BoxConstraints(minWidth: 40, minHeight: 40),
      padding: EdgeInsets.zero,
      onPressed: _descargar,
      icon: const Icon(Icons.download_for_offline_outlined),
      color: _Pal.onSurfaceVar,
      tooltip: 'Descargar para escuchar sin internet',
    );
  }

  @override
  Widget build(BuildContext context) {
    final documento = widget.documento;
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      color: _Pal.surface,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: const BorderSide(color: _Pal.border),
      ),
      child: InkWell(
        onTap: widget.onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              _portada(),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      documento.titulo,
                      style: const TextStyle(
                        color: _Pal.onSurface,
                        fontWeight: FontWeight.w600,
                        fontSize: 14,
                      ),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 4),
                    Wrap(
                      spacing: 8,
                      runSpacing: 4,
                      crossAxisAlignment: WrapCrossAlignment.center,
                      children: [
                        Text(
                          '${documento.iconoIdioma} ${documento.idiomaDetectado?.toUpperCase() ?? 'ES'}',
                          style: const TextStyle(
                              color: _Pal.onSurfaceVar, fontSize: 12),
                        ),
                        Text(
                          '${documento.cantidadPuntos} pts',
                          style: const TextStyle(
                              color: _Pal.onSurfaceVar, fontSize: 12),
                        ),
                        if (documento.cantidadPuntos > 0)
                          Text(
                            '${documento.cantidadPuntosRevisados}/${documento.cantidadPuntos} rev.',
                            style: TextStyle(
                              color: documento.cantidadPuntos ==
                                      documento.cantidadPuntosRevisados
                                  ? _Pal.secondary
                                  : const Color(0xFFB45309),
                              fontSize: 11,
                            ),
                          ),
                      ],
                    ),
                    const SizedBox(height: 2),
                    Text(
                      documento.tamanoFormateado,
                      style: const TextStyle(
                          color: _Pal.onSurfaceVar, fontSize: 11),
                    ),
                  ],
                ),
              ),
              _botonDescarga(),
              IconButton(
                visualDensity: VisualDensity.compact,
                constraints: const BoxConstraints(minWidth: 40, minHeight: 40),
                padding: EdgeInsets.zero,
                onPressed: widget.onEscuchar,
                icon: const Icon(Icons.headphones_rounded),
                color: _Pal.primary,
                tooltip: 'Leer y escuchar',
              ),
            ],
          ),
        ),
      ),
    );
  }
}
