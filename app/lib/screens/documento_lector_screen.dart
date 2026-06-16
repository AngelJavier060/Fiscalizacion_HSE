import 'dart:async';
import 'dart:io' show Platform;

import 'package:flutter/material.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:android_intent_plus/android_intent.dart';

import '../models/documento_model.dart';
import '../models/user_model.dart';
import '../services/auth_service.dart';
import '../services/documento_service.dart';
import '../services/documento_offline_service.dart';
import '../services/documento_sync_service.dart';
import '../services/api_service.dart';
import '../services/fondo_sonido.dart';
import '../services/lectura_progreso_service.dart';
import 'preguntar_voz_sheet.dart';

/// Lector de documentos estilo "Pocket FM":
/// muestra el texto y lo lee en voz alta de forma FLUIDA (por bloques continuos)
/// resaltando el fragmento actual mientras habla, con controles simples y
/// guardado del avance para continuar después.
class DocumentoLectorScreen extends StatefulWidget {
  final int documentoId;
  final String titulo;
  /// Si true, abre el editor en cuanto cargue el texto.
  final bool abrirEnEdicion;

  const DocumentoLectorScreen({
    super.key,
    required this.documentoId,
    required this.titulo,
    this.abrirEnEdicion = false,
  });

  @override
  State<DocumentoLectorScreen> createState() => _DocumentoLectorScreenState();
}

class _DocumentoLectorScreenState extends State<DocumentoLectorScreen>
    with WidgetsBindingObserver {
  // Paleta clara MD3 (igual que FISCALIZA-AI)
  static const _bg = Color(0xFFFAF8FF);
  static const _surface = Color(0xFFFFFFFF);
  static const _low = Color(0xFFF2F3FF);
  static const _high = Color(0xFFE2E7FF);
  static const _border = Color(0xFFC3C5D9);
  static const _primary = Color(0xFF003EC7);
  static const _onSurface = Color(0xFF131B2E);
  static const _onSurfaceVar = Color(0xFF434656);
  static const _secondary = Color(0xFF006B5B);
  static const _highlight = Color(0xFFB7C4FF); // resaltado karaoke

  /// Máximo de caracteres por bloque leído de corrido. Un tamaño grande hace
  /// la lectura más fluida; la pausa configurable actúa entre bloques.
  static const int _maxCharsBloque = 2200;

  /// Identificador de la sesión de lectura en curso. Cada vez que se inicia o
  /// reinicia la lectura se incrementa, de modo que un bucle viejo se detenga.
  int _sesion = 0;

  final FlutterTts _tts = FlutterTts();
  final ScrollController _scrollController = ScrollController();

  // Sonido ambiental de fondo (estilo Pocket FM) para acompañar la lectura
  // y ayudar a mantener la atención. Hace bucle suave y a bajo volumen.
  final FondoSonido _fondo = FondoSonido();

  // Fragmentos visibles (oraciones agrupadas) para resaltar.
  List<String> _fragmentos = [];
  final List<GlobalKey> _keys = [];

  // Bloques continuos para una lectura fluida (cada bloque agrupa varios fragmentos).
  List<_Bloque> _bloques = [];
  int _bloqueActual = 0;
  // Offset de inicio del texto realmente hablado dentro del bloque (al reanudar a media).
  int _offsetBase = 0;

  bool _cargando = true;
  String? _error;
  bool _procesandoPdf = false;
  bool _reprocesando = false;
  Timer? _pollTimer;
  Timer? _syncTimer;
  DocumentoModel? _metaDocumento;

  // Edición de texto (sincroniza con la web vía PUT /texto-extraido).
  final TextEditingController _editorController = TextEditingController();
  String _textoCompletoRaw = '';
  bool _modoEdicion = false;
  bool _guardandoEdicion = false;
  bool _autoEditPendiente = false;
  bool _descargado = false;
  bool _leidoOffline = false;

  bool _reproduciendo = false;
  int _indiceActual = 0;

  // Resaltado palabra por palabra dentro del fragmento actual (estilo karaoke).
  int _palabraInicio = 0;
  int _palabraFin = 0;

  int? _empresaId;

  // Velocidad de lectura. flutter_tts usa un rate absoluto (≈0.5 = normal en Android).
  double _rate = 0.5;
  static const List<_OpcionVelocidad> _velocidades = [
    _OpcionVelocidad('Lenta', 0.40),
    _OpcionVelocidad('Normal', 0.50),
    _OpcionVelocidad('Rápida', 0.62),
    _OpcionVelocidad('Muy rápida', 0.78),
  ];

  // Volumen (0.0 – 1.0). Arranca al máximo para que se escuche fuerte.
  double _volumen = 1.0;

  // Pausa adicional entre frases (ms) para una lectura más pausada y natural.
  int _pausaMs = 250;
  static const List<_OpcionPausa> _pausas = [
    _OpcionPausa('Sin pausa', 0),
    _OpcionPausa('Pausa corta', 250),
    _OpcionPausa('Pausa media', 600),
    _OpcionPausa('Pausa larga', 1000),
  ];

  // Voces en español disponibles en el dispositivo (las "network" son las más claras).
  List<Map<String, String>> _vocesEs = [];
  String? _vozNombre;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _initTts();
    _initFondo();
    _autoEditPendiente = widget.abrirEnEdicion;
    _cargarTexto();
    _cargarEmpresa();
    _iniciarSyncContinuo();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _recargarSiCambioEnServidor();
    }
  }

  void _iniciarSyncContinuo() {
    _syncTimer?.cancel();
    _syncTimer = Timer.periodic(const Duration(seconds: 20), (_) {
      _recargarSiCambioEnServidor();
    });
  }

  Future<void> _recargarSiCambioEnServidor() async {
    if (!mounted || _procesandoPdf || _modoEdicion || _guardandoEdicion) return;
    try {
      final doc = await DocumentoService.getDocumento(widget.documentoId);
      final cambio = _metaDocumento == null ||
          await DocumentoSyncService.documentoCambioEnServidor(doc);
      if (!cambio) return;
      _metaDocumento = doc;
      if (doc.isProcesando) {
        if (!_procesandoPdf) {
          setState(() {
            _cargando = true;
            _procesandoPdf = true;
            _error = null;
          });
          _iniciarPollProcesamiento();
        }
        return;
      }
      if (_reproduciendo) return;
      await DocumentoOfflineService.eliminar(widget.documentoId);
      await _cargarTextoDesdeServidorOuOffline();
    } catch (_) {}
  }

  Future<void> _initFondo() async {
    await _fondo.init();
    if (mounted) setState(() {});
  }

  Future<void> _iniciarFondo() => _fondo.iniciar();
  Future<void> _detenerFondo() => _fondo.detener();

  Future<void> _cambiarAmbiente(String id) async {
    await _fondo.setAmbiente(id, sonando: _reproduciendo);
    if (mounted) setState(() {});
  }

  Future<void> _cambiarFondoVol(double v) async {
    await _fondo.setVolumen(v);
    if (mounted) setState(() {});
  }

  Future<void> _cargarEmpresa() async {
    try {
      final data = await AuthService().getUserData();
      final user = UserModel.fromJson(data);
      if (mounted) setState(() => _empresaId = user.empresaId);
    } catch (_) {
      // Sin empresa: el botón de preguntar por voz quedará oculto.
    }
  }

  Future<void> _abrirPreguntarVoz() async {
    if (_empresaId == null) return;
    // Pausa la lectura del documento mientras se usa el asistente de voz.
    if (_reproduciendo) await _pausar();
    if (!mounted) return;

    await showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => PreguntarVozSheet(
        documentoId: widget.documentoId,
        empresaId: _empresaId!,
      ),
    );
  }

  Future<void> _initTts() async {
    // El avance entre bloques se controla con un bucle + await (robusto para
    // documentos largos), no con el callback de completado.
    await _tts.awaitSpeakCompletion(true);
    await _tts.setLanguage('es-ES');
    await _tts.setSpeechRate(_rate);
    await _tts.setPitch(1.0);
    await _tts.setVolume(_volumen);

    // Resaltado palabra por palabra mientras habla el bloque.
    _tts.setProgressHandler((text, start, end, word) {
      if (!mounted || !_reproduciendo) return;
      _resaltarPalabra(start + _offsetBase, end + _offsetBase);
    });

    // Un error puntual de un bloque no debe cortar toda la lectura: el bucle
    // continúa con el siguiente bloque.
    _tts.setErrorHandler((msg) {});

    await _cargarVoces();
  }

  /// Carga las voces en español del dispositivo y elige la más clara
  /// (prioriza las voces "network"/neuronales de Google, que son gratis).
  Future<void> _cargarVoces() async {
    try {
      final raw = await _tts.getVoices;
      final todas = <Map<String, String>>[];
      final espanol = <Map<String, String>>[];
      if (raw is List) {
        for (final v in raw) {
          final m = Map<String, dynamic>.from(v as Map);
          final name = (m['name'] ?? '').toString();
          final locale = (m['locale'] ?? '').toString();
          if (name.isEmpty) continue;
          final voz = {
            'name': name,
            'locale': locale,
            'network': (m['network_required'] ?? '0').toString(),
            'quality': (m['quality'] ?? '').toString(),
          };
          todas.add(voz);
          final lang = locale.toLowerCase().replaceAll('_', '-');
          if (lang.startsWith('es') || lang.startsWith('spa')) {
            espanol.add(voz);
          }
        }
      }

      // Si el dispositivo no tiene voces en español, mostramos todas las
      // disponibles para que igualmente se pueda elegir.
      final voces = espanol.isNotEmpty ? espanol : todas;

      final online = await _hayInternet();
      voces.sort(
          (a, b) => _puntajeVoz(b, online).compareTo(_puntajeVoz(a, online)));

      if (!mounted) return;
      setState(() => _vocesEs = voces);

      if (voces.isNotEmpty) {
        final mejor = voces.first;
        _vozNombre = mejor['name'];
        await _tts.setVoice(mejor);
      }
    } catch (_) {
      // Si el motor no expone voces, se usa la voz por defecto del sistema.
    }
  }

  /// Puntúa una voz para ordenar. Con internet prioriza las voces "network"
  /// (más claras); sin internet prioriza las locales (funcionan offline).
  int _puntajeVoz(Map<String, String> voz, bool online) {
    final name = (voz['name'] ?? '').toLowerCase();
    final locale = (voz['locale'] ?? '').toLowerCase();
    final esNetwork = !_vozEsOffline(voz);
    final esLocal = !esNetwork;
    int p = 0;
    if (online) {
      if (esNetwork) p += 100; // neuronal en la nube (Google), más clara
      if (name.contains('neural') || name.contains('wavenet')) p += 80;
      if (esLocal) p += 5;
    } else {
      if (esLocal) p += 100; // funciona sin internet
      if (esNetwork) p -= 100; // no suena sin conexión
    }
    if (locale == 'es-es') p += 20;
    if (locale == 'es-us' || locale == 'es-mx') p += 12;
    return p;
  }

  bool _vozEsOffline(Map<String, String> voz) {
    // Dato real del motor: si requiere red, no funciona offline.
    if ((voz['network'] ?? '0') == '1') return false;
    final name = (voz['name'] ?? '').toLowerCase();
    return !name.contains('network');
  }

  /// Abre la pantalla de ajustes de "Texto a voz" del sistema (Android).
  Future<void> _abrirAjustesVoz() async {
    try {
      const intent = AndroidIntent(action: 'com.android.settings.TTS_SETTINGS');
      await intent.launch();
    } catch (_) {
      // Si el fabricante no expone esa pantalla, abrimos los ajustes generales.
      try {
        const intent =
            AndroidIntent(action: 'android.settings.SETTINGS');
        await intent.launch();
      } catch (_) {}
    }
  }

  Future<void> _cargarTexto() async {
    if (!mounted) return;
    setState(() {
      _cargando = true;
      _error = null;
      _procesandoPdf = false;
    });

    try {
      final doc = await DocumentoService.getDocumento(widget.documentoId);
      _metaDocumento = doc;
      await DocumentoSyncService.registrarDocumento(doc);
      if (!mounted) return;

      if (doc.isProcesando) {
        setState(() {
          _cargando = true;
          _procesandoPdf = true;
          _error = null;
        });
        _iniciarPollProcesamiento();
        return;
      }

      if (doc.isError) {
        if (_autoEditPendiente) {
          _abrirEdicionVacia();
          return;
        }
        setState(() {
          _cargando = false;
          _procesandoPdf = false;
          _error = doc.errorProcesamiento ??
              'Error al procesar el PDF. Puede reprocesarlo o editar el texto.';
        });
        return;
      }

      await _cargarTextoDesdeServidorOuOffline();
    } catch (e) {
      if (mounted) {
        setState(() {
          _error =
              'No se pudo cargar el documento. Verifica tu conexión e inténtalo de nuevo.';
          _cargando = false;
          _procesandoPdf = false;
        });
      }
    }
  }

  void _iniciarPollProcesamiento() {
    _pollTimer?.cancel();
    _pollTimer = Timer.periodic(const Duration(seconds: 3), (_) async {
      if (!mounted) return;
      try {
        final doc = await DocumentoService.getDocumento(widget.documentoId);
        if (!mounted) return;
        if (doc.isCompletado) {
          _pollTimer?.cancel();
          await DocumentoOfflineService.eliminar(widget.documentoId);
          await _cargarTextoDesdeServidorOuOffline();
        } else if (doc.isError) {
          _pollTimer?.cancel();
          setState(() {
            _cargando = false;
            _procesandoPdf = false;
            _error = doc.errorProcesamiento ??
                'Error al procesar el PDF. Puede reprocesarlo.';
          });
        }
      } catch (_) {}
    });
  }

  Future<void> _cargarTextoDesdeServidorOuOffline() async {
    final online = await _hayInternet();
    DocumentoTexto? texto;
    var desdeOffline = false;

    // Con internet: siempre pedir al servidor (evita caché vacía guardada antes)
    if (online) {
      try {
        final meta = _metaDocumento ??
            await DocumentoService.getDocumento(widget.documentoId);
        _metaDocumento = meta;
        final cambio =
            await DocumentoSyncService.documentoCambioEnServidor(meta);
        if (cambio) {
          await DocumentoOfflineService.eliminar(widget.documentoId);
        }
        texto = await DocumentoService.getTextoCompleto(widget.documentoId);
        if (texto.tieneTexto) {
          await DocumentoOfflineService.guardarTexto(
            texto,
            widget.titulo,
            updatedAt: meta.updatedAt ?? meta.createdAt,
          );
          await DocumentoSyncService.registrarDocumento(meta);
        } else {
          await DocumentoOfflineService.eliminar(widget.documentoId);
        }
      } catch (e) {
        if (!mounted) return;
        setState(() {
          _error = e is ApiException
              ? e.message
              : 'No se pudo descargar el texto del documento.';
          _cargando = false;
          _procesandoPdf = false;
        });
        return;
      }
    }

    // Sin internet o respuesta vacía: intentar caché local
    if (texto == null || !texto.tieneTexto) {
      final local = await DocumentoOfflineService.obtener(widget.documentoId);
      if (local != null && local.tieneTexto) {
        texto = local;
        desdeOffline = true;
      }
    }

    if (texto == null || !texto.tieneTexto) {
      if (!mounted) return;
      if (_autoEditPendiente) {
        _abrirEdicionVacia();
        return;
      }
      setState(() {
        _error = online
            ? 'Este documento no tiene texto extraído todavía. '
                'Pulse Reprocesar PDF para extraerlo de nuevo, '
                'o edite el texto manualmente.'
            : 'Sin texto descargado en el teléfono. Conéctese a internet '
                'y pulse Recargar texto, o edite manualmente.';
        _cargando = false;
        _procesandoPdf = false;
      });
      return;
    }

    final contenido = texto.textoParaLectura;
    if (!mounted) return;

    if (contenido.trim().isEmpty) {
      if (_autoEditPendiente) {
        _abrirEdicionVacia();
        return;
      }
      setState(() {
        _error =
            'No se pudo preparar el texto para lectura. Pulse Reprocesar PDF '
            'o edite el texto manualmente.';
        _cargando = false;
        _procesandoPdf = false;
      });
      return;
    }

    _aplicarContenidoTexto(contenido, desdeOffline: desdeOffline);
    await _ofrecerContinuar();
  }

  void _aplicarContenidoTexto(String contenido, {required bool desdeOffline}) {
    final fragmentos = _dividirEnFragmentos(contenido);
    if (fragmentos.isEmpty) {
      setState(() {
        _error =
            'No se pudo preparar el texto para lectura. Pulse Reprocesar PDF.';
        _cargando = false;
        _procesandoPdf = false;
      });
      return;
    }
    _textoCompletoRaw = contenido;
    setState(() {
      _fragmentos = fragmentos;
      _keys
        ..clear()
        ..addAll(List.generate(fragmentos.length, (_) => GlobalKey()));
      _bloques = _construirBloques(fragmentos);
      _descargado = true;
      _leidoOffline = desdeOffline;
      _cargando = false;
      _procesandoPdf = false;
      _error = null;
      _indiceActual = 0;
      _palabraInicio = 0;
      _palabraFin = 0;
    });
    if (_autoEditPendiente) {
      _autoEditPendiente = false;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _abrirPantallaEditor();
      });
    }
  }

  Future<void> _abrirPantallaEditor() async {
    final guardado = await Navigator.pushNamed<bool>(
      context,
      '/documento-editor',
      arguments: {
        'id': widget.documentoId,
        'titulo': widget.titulo,
      },
    );
    if (guardado == true && mounted) {
      await DocumentoOfflineService.eliminar(widget.documentoId);
      await _cargarTexto();
    }
  }

  void _abrirEdicionVacia() {
    _autoEditPendiente = false;
    _abrirPantallaEditor();
  }

  Future<void> _entrarEdicion() async {
    if (_modoEdicion) return;
    await _pausar();
    _editorController.text = _textoCompletoRaw.isNotEmpty
        ? _textoCompletoRaw
        : (_fragmentos.isNotEmpty ? _fragmentos.join('\n\n') : '');
    setState(() {
      _modoEdicion = true;
      _error = null;
    });
  }

  void _cancelarEdicion() {
    setState(() => _modoEdicion = false);
  }

  Future<void> _guardarEdicion() async {
    final plain = _editorController.text.trim();
    if (plain.isEmpty || _guardandoEdicion) return;

    setState(() => _guardandoEdicion = true);
    try {
      final html = DocumentoTexto.planoAHtmlParaGuardar(plain);
      final doc = await DocumentoService.guardarTextoExtraido(
        widget.documentoId,
        html,
      );
      _metaDocumento = doc;
      await DocumentoSyncService.registrarDocumento(doc);

      final textoResp =
          await DocumentoService.getTextoCompleto(widget.documentoId);
      if (textoResp.tieneTexto) {
        await DocumentoOfflineService.guardarTexto(
          textoResp,
          widget.titulo,
          updatedAt: doc.updatedAt ?? doc.createdAt,
        );
      }

      if (!mounted) return;
      _aplicarContenidoTexto(
        textoResp.textoParaLectura.isNotEmpty ? textoResp.textoParaLectura : plain,
        desdeOffline: false,
      );
      setState(() {
        _modoEdicion = false;
        _guardandoEdicion = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Texto guardado. Los cambios se verán también en la web.',
          ),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      setState(() => _guardandoEdicion = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            e is ApiException
                ? e.message
                : 'No se pudo guardar el texto. Verifica tu conexión.',
          ),
        ),
      );
    }
  }

  Future<void> _reprocesarPdf() async {
    if (_reprocesando) return;
    setState(() {
      _reprocesando = true;
      _cargando = true;
      _error = null;
      _procesandoPdf = true;
    });
    try {
      await DocumentoOfflineService.eliminar(widget.documentoId);
      await DocumentoService.reprocesar(widget.documentoId);
      if (!mounted) return;
      _iniciarPollProcesamiento();
    } catch (e) {
      if (mounted) {
        setState(() {
          _reprocesando = false;
          _cargando = false;
          _procesandoPdf = false;
          _error = 'No se pudo reprocesar el documento.';
        });
      }
    } finally {
      if (mounted) setState(() => _reprocesando = false);
    }
  }

  Future<void> _recargarTexto() async {
    await DocumentoOfflineService.eliminar(widget.documentoId);
    _pollTimer?.cancel();
    await _cargarTexto();
  }

  Future<void> _confirmarQuitarDescarga() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: _surface,
        title: const Text('Disponible sin internet',
            style: TextStyle(color: _onSurface)),
        content: const Text(
          'Este documento está guardado en el teléfono y se puede escuchar sin '
          'conexión. ¿Quieres eliminar la descarga para liberar espacio?',
          style: TextStyle(color: _onSurfaceVar),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Mantener'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Eliminar',
                style: TextStyle(color: Color(0xFFBA1A1A))),
          ),
        ],
      ),
    );
    if (ok == true) await _quitarDescarga();
  }

  /// Elimina la copia guardada en el teléfono.
  Future<void> _quitarDescarga() async {
    await DocumentoOfflineService.eliminar(widget.documentoId);
    if (mounted) setState(() => _descargado = false);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Descarga eliminada del teléfono')),
      );
    }
  }

  Future<bool> _hayInternet() async {
    try {
      final r = await Connectivity().checkConnectivity();
      return !r.contains(ConnectivityResult.none);
    } catch (_) {
      return true;
    }
  }

  /// Si hay avance guardado, ofrece continuar desde donde quedó.
  Future<void> _ofrecerContinuar() async {
    final progreso = await LecturaProgresoService.obtener(widget.documentoId);
    if (!mounted || progreso == null) return;
    if (progreso.indiceFragmento <= 0 ||
        progreso.indiceFragmento >= _fragmentos.length) {
      return;
    }

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        backgroundColor: _surface,
        duration: const Duration(seconds: 8),
        content: Text(
          'Continúa donde quedaste (${progreso.porcentajeEntero}%)',
          style: const TextStyle(color: _onSurface),
        ),
        action: SnackBarAction(
          label: 'Continuar',
          textColor: _primary,
          onPressed: () {
            setState(() => _indiceActual = progreso.indiceFragmento);
            _scrollAlActual();
            _reproducirDesdeFragmento(progreso.indiceFragmento);
          },
        ),
      ),
    );
  }

  /// Divide el texto en fragmentos cortos (oraciones agrupadas) para resaltar.
  List<String> _dividirEnFragmentos(String texto) {
    final limpio = texto.replaceAll('\r', '\n');
    final fragmentos = <String>[];
    final parrafos = limpio.split(RegExp(r'\n+'));

    for (final parrafo in parrafos) {
      final t = parrafo.trim();
      if (t.isEmpty) continue;

      final oraciones = t.split(RegExp(r'(?<=[\.\!\?])\s+'));
      final buffer = StringBuffer();

      for (final o in oraciones) {
        final s = o.trim();
        if (s.isEmpty) continue;
        if (buffer.isEmpty) {
          buffer.write(s);
        } else if (buffer.length + s.length < 180) {
          buffer.write(' ');
          buffer.write(s);
        } else {
          fragmentos.add(buffer.toString());
          buffer.clear();
          buffer.write(s);
        }
      }
      if (buffer.isNotEmpty) fragmentos.add(buffer.toString());
    }
    return fragmentos;
  }

  /// Agrupa fragmentos consecutivos en bloques grandes para leer de corrido.
  List<_Bloque> _construirBloques(List<String> fragmentos) {
    final bloques = <_Bloque>[];
    int i = 0;
    while (i < fragmentos.length) {
      final sb = StringBuffer();
      final offsets = <int>[];
      final fragInicio = i;
      while (i < fragmentos.length) {
        final f = fragmentos[i];
        final extra = sb.isEmpty ? f.length : f.length + 1;
        if (sb.isNotEmpty && sb.length + extra > _maxCharsBloque) break;
        // Salto de línea entre frases: el motor TTS pausa de forma natural.
        if (sb.isNotEmpty) sb.write('\n');
        offsets.add(sb.length);
        sb.write(f);
        i++;
      }
      bloques.add(_Bloque(
        texto: sb.toString(),
        fragInicio: fragInicio,
        offsets: offsets,
      ));
    }
    return bloques;
  }

  int _bloqueDeFragmento(int fragGlobal) {
    for (int b = 0; b < _bloques.length; b++) {
      final blo = _bloques[b];
      if (fragGlobal >= blo.fragInicio &&
          fragGlobal < blo.fragInicio + blo.offsets.length) {
        return b;
      }
    }
    return 0;
  }

  /// Resalta la palabra que se está leyendo (offsets dentro del bloque actual)
  /// y, si cambió de fragmento, hace scroll para seguir la lectura.
  void _resaltarPalabra(int inicioEnBloque, int finEnBloque) {
    if (_bloqueActual < 0 || _bloqueActual >= _bloques.length) return;
    final blo = _bloques[_bloqueActual];

    int local = 0;
    for (int k = 0; k < blo.offsets.length; k++) {
      if (inicioEnBloque >= blo.offsets[k]) {
        local = k;
      } else {
        break;
      }
    }
    final globalIdx = blo.fragInicio + local;
    final baseFrag = blo.offsets[local];
    final fragLen = _fragmentos[globalIdx].length;
    final ini = (inicioEnBloque - baseFrag).clamp(0, fragLen);
    final fin = (finEnBloque - baseFrag).clamp(ini, fragLen);

    final cambioFragmento = globalIdx != _indiceActual;
    setState(() {
      _indiceActual = globalIdx;
      _palabraInicio = ini;
      _palabraFin = fin;
    });
    if (cambioFragmento) {
      _scrollAlActual();
      _guardarProgreso();
    }
  }

  // ── Control de reproducción ────────────────────────────────────────
  Future<void> _reproducirDesdeFragmento(int fragGlobal) async {
    if (fragGlobal < 0 || fragGlobal >= _fragmentos.length) return;
    final b = _bloqueDeFragmento(fragGlobal);
    final blo = _bloques[b];
    final local = fragGlobal - blo.fragInicio;

    final sesion = ++_sesion; // invalida cualquier bucle anterior
    await _tts.stop();

    setState(() {
      _indiceActual = fragGlobal;
      _bloqueActual = b;
      _reproduciendo = true;
      _palabraInicio = 0;
      _palabraFin = 0;
    });

    _offsetBase = blo.offsets[local];
    _guardarProgreso();
    await _iniciarFondo();
    await _bucleLectura(sesion);
  }

  /// Lee de corrido, bloque por bloque, hasta el final del documento.
  /// El encadenamiento con `await` (awaitSpeakCompletion) es fiable incluso
  /// con cientos de bloques.
  Future<void> _bucleLectura(int sesion) async {
    while (mounted && _reproduciendo && sesion == _sesion &&
        _bloqueActual < _bloques.length) {
      final blo = _bloques[_bloqueActual];
      final inicio = _offsetBase;
      final texto = inicio > 0 ? blo.texto.substring(inicio) : blo.texto;

      await _tts.speak(texto);

      // Si se pausó o cambió de sesión mientras hablaba, salir sin avanzar.
      if (!mounted || !_reproduciendo || sesion != _sesion) return;

      // Pausa configurable entre bloques (lectura más pausada).
      if (_pausaMs > 0) {
        await Future.delayed(Duration(milliseconds: _pausaMs));
        if (!mounted || !_reproduciendo || sesion != _sesion) return;
      }

      final next = _bloqueActual + 1;
      if (next >= _bloques.length) break;
      _offsetBase = 0;
      setState(() {
        _bloqueActual = next;
        _indiceActual = _bloques[next].fragInicio;
        _palabraInicio = 0;
        _palabraFin = 0;
      });
      _scrollAlActual();
      _guardarProgreso();
    }

    if (mounted && sesion == _sesion) {
      setState(() => _reproduciendo = false);
      await _detenerFondo();
      await LecturaProgresoService.limpiar(widget.documentoId);
    }
  }

  Future<void> _togglePlay() async {
    if (_reproduciendo) {
      await _pausar();
    } else {
      await _reproducirDesdeFragmento(_indiceActual);
    }
  }

  Future<void> _pausar() async {
    _sesion++; // detiene el bucle en curso
    setState(() => _reproduciendo = false);
    await _tts.stop();
    await _detenerFondo();
    _guardarProgreso();
  }

  Future<void> _anterior() async {
    final previo = (_indiceActual - 1).clamp(0, _fragmentos.length - 1);
    if (_reproduciendo) {
      await _reproducirDesdeFragmento(previo);
    } else {
      setState(() {
        _indiceActual = previo;
        _palabraInicio = 0;
        _palabraFin = 0;
      });
      _scrollAlActual();
      _guardarProgreso();
    }
  }

  Future<void> _siguiente() async {
    final next = (_indiceActual + 1).clamp(0, _fragmentos.length - 1);
    if (_reproduciendo) {
      await _reproducirDesdeFragmento(next);
    } else {
      setState(() {
        _indiceActual = next;
        _palabraInicio = 0;
        _palabraFin = 0;
      });
      _scrollAlActual();
      _guardarProgreso();
    }
  }

  Future<void> _cambiarVelocidad(double rate) async {
    setState(() => _rate = rate);
    await _tts.setSpeechRate(rate);
    if (_reproduciendo) {
      await _reproducirDesdeFragmento(_indiceActual);
    }
  }

  Future<void> _cambiarVolumen(double v, {bool reaplicar = false}) async {
    setState(() => _volumen = v);
    await _tts.setVolume(v);
    if (reaplicar && _reproduciendo) {
      await _reproducirDesdeFragmento(_indiceActual);
    }
  }

  Future<void> _cambiarPausa(int ms) async {
    // La pausa se aplica entre bloques en el bucle; no hace falta reiniciar.
    setState(() => _pausaMs = ms);
  }

  /// Reproduce una frase de muestra con la voz indicada (para escucharla).
  Future<void> _probarVoz(Map<String, String> voz) async {
    await _pausar();
    await _tts.setVoice(voz);
    if (mounted) setState(() => _vozNombre = voz['name']);
    await _tts.speak('Hola, así se escuchará la lectura del documento.');
  }

  Future<void> _cambiarVoz(Map<String, String> voz) async {
    setState(() => _vozNombre = voz['name']);
    await _tts.setVoice(voz);
    if (_reproduciendo) {
      await _reproducirDesdeFragmento(_indiceActual);
    }
  }

  void _guardarProgreso() {
    LecturaProgresoService.guardar(
      documentoId: widget.documentoId,
      indiceFragmento: _indiceActual,
      totalFragmentos: _fragmentos.length,
    );
  }

  void _scrollAlActual({int? indice}) {
    final i = indice ?? _indiceActual;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (i < 0 || i >= _keys.length) return;
      final ctx = _keys[i].currentContext;
      if (ctx != null) {
        Scrollable.ensureVisible(
          ctx,
          duration: const Duration(milliseconds: 350),
          curve: Curves.easeInOut,
          alignment: 0.35,
        );
      }
    });
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    _syncTimer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    _editorController.dispose();
    _guardarProgreso();
    _tts.stop();
    _fondo.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  // ── UI ─────────────────────────────────────────────────────────────
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: _bg,
      appBar: AppBar(
        backgroundColor: _surface,
        foregroundColor: _onSurface,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        iconTheme: const IconThemeData(color: _onSurfaceVar),
        title: Text(
          widget.titulo,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(
              color: _onSurface, fontWeight: FontWeight.w700, fontSize: 16),
        ),
        actions: [
          if (!_cargando && !_modoEdicion) ...[
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 4),
              child: ElevatedButton.icon(
                onPressed: _abrirPantallaEditor,
                icon: const Icon(Icons.edit_rounded, size: 18),
                label: const Text(
                  'EDITAR',
                  style: TextStyle(fontWeight: FontWeight.w800, fontSize: 13),
                ),
                style: ElevatedButton.styleFrom(
                  backgroundColor: _primary,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  minimumSize: const Size(0, 36),
                  elevation: 0,
                ),
              ),
            ),
            PopupMenuButton<String>(
              icon: const Icon(Icons.more_vert_rounded, color: _onSurfaceVar),
              tooltip: 'Más opciones',
              onSelected: (value) {
                if (value == 'voz') _abrirPreguntarVoz();
                if (value == 'offline') _confirmarQuitarDescarga();
              },
              itemBuilder: (context) => [
                if (_empresaId != null)
                  const PopupMenuItem(
                    value: 'voz',
                    child: ListTile(
                      leading: Icon(Icons.mic_rounded),
                      title: Text('Preguntar por voz'),
                      contentPadding: EdgeInsets.zero,
                      dense: true,
                    ),
                  ),
                if (_descargado)
                  const PopupMenuItem(
                    value: 'offline',
                    child: ListTile(
                      leading: Icon(Icons.offline_pin_rounded),
                      title: Text('Quitar descarga offline'),
                      subtitle: Text(
                        'Libera espacio en el teléfono',
                        style: TextStyle(fontSize: 11),
                      ),
                      contentPadding: EdgeInsets.zero,
                      dense: true,
                    ),
                  ),
              ],
            ),
          ],
          if (_modoEdicion)
            IconButton(
              onPressed: _guardandoEdicion ? null : _guardarEdicion,
              icon: _guardandoEdicion
                  ? const SizedBox(
                      width: 22,
                      height: 22,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.save_rounded),
              color: _secondary,
              tooltip: 'Guardar en servidor',
            ),
          if (_modoEdicion)
            IconButton(
              onPressed: _guardandoEdicion ? null : _cancelarEdicion,
              icon: const Icon(Icons.close_rounded),
              color: _onSurfaceVar,
              tooltip: 'Cancelar edición',
            ),
        ],
      ),
      body: _cargando
          ? Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const CircularProgressIndicator(color: _primary),
                  if (_procesandoPdf) ...[
                    const SizedBox(height: 20),
                    const Text(
                      'Procesando PDF…',
                      style: TextStyle(color: _onSurfaceVar, fontSize: 15),
                    ),
                    const SizedBox(height: 8),
                    const Padding(
                      padding: EdgeInsets.symmetric(horizontal: 32),
                      child: Text(
                        'Extrayendo texto del documento. Esto puede tardar unos minutos.',
                        textAlign: TextAlign.center,
                        style: TextStyle(color: _onSurfaceVar, fontSize: 13),
                      ),
                    ),
                  ],
                ],
              ),
            )
          : _error != null
              ? _buildError()
              : _modoEdicion
                  ? _buildEditor()
                  : _buildLector(),
      bottomNavigationBar: (_cargando || _error != null || _modoEdicion)
          ? null
          : Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                _buildBarraEditarFija(),
                _buildReproductor(),
              ],
            ),
    );
  }

  Widget _buildError() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.volume_off_rounded, size: 56, color: _onSurfaceVar),
            const SizedBox(height: 16),
            Text(
              _error!,
              textAlign: TextAlign.center,
              style: const TextStyle(color: _onSurfaceVar, fontSize: 15, height: 1.4),
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              onPressed: _reprocesando ? null : _reprocesarPdf,
              icon: _reprocesando
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.refresh_rounded),
              label: Text(_reprocesando ? 'Reprocesando…' : 'Reprocesar PDF'),
              style: ElevatedButton.styleFrom(
                backgroundColor: _primary,
                foregroundColor: Colors.white,
              ),
            ),
            const SizedBox(height: 8),
            TextButton.icon(
              onPressed: _recargarTexto,
              icon: const Icon(Icons.cloud_download_outlined),
              label: const Text('Recargar texto'),
            ),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: _abrirPantallaEditor,
              icon: const Icon(Icons.edit_note_rounded),
              label: const Text('Editar o escribir texto'),
              style: OutlinedButton.styleFrom(
                foregroundColor: _primary,
                side: const BorderSide(color: _primary),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildEditor() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Container(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
          color: _high.withValues(alpha: 0.35),
          child: const Text(
            'Edite el texto: elimine lo que no necesite y deje líneas en blanco '
            'entre párrafos. Al guardar, se actualiza la base de datos y la web.',
            style: TextStyle(color: _onSurfaceVar, fontSize: 13, height: 1.4),
          ),
        ),
        Expanded(
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: TextField(
              controller: _editorController,
              maxLines: null,
              expands: true,
              textAlignVertical: TextAlignVertical.top,
              style: const TextStyle(
                color: _onSurface,
                fontSize: 15,
                height: 1.55,
              ),
              decoration: InputDecoration(
                filled: true,
                fillColor: _surface,
                hintText: 'Contenido del procedimiento…',
                hintStyle: TextStyle(color: _onSurfaceVar.withValues(alpha: 0.7)),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: const BorderSide(color: _border),
                ),
                enabledBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: const BorderSide(color: _border),
                ),
                focusedBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: const BorderSide(color: _primary, width: 1.5),
                ),
              ),
            ),
          ),
        ),
        SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
            child: Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: _guardandoEdicion ? null : _cancelarEdicion,
                    child: const Text('Cancelar'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: _guardandoEdicion ? null : _guardarEdicion,
                    icon: _guardandoEdicion
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.white,
                            ),
                          )
                        : const Icon(Icons.cloud_upload_outlined),
                    label: Text(_guardandoEdicion ? 'Guardando…' : 'Guardar'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: _primary,
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(vertical: 14),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildLector() {
    return ListView.builder(
      controller: _scrollController,
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
      itemCount: _fragmentos.length,
      itemBuilder: (context, index) {
        final esActual = index == _indiceActual;
        return Padding(
          key: _keys[index],
          padding: const EdgeInsets.only(bottom: 6),
          child: GestureDetector(
            onTap: () => _reproducirDesdeFragmento(index),
            onLongPress: _abrirPantallaEditor,
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              decoration: BoxDecoration(
                color: esActual
                    ? _primary.withValues(alpha: 0.08)
                    : Colors.transparent,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(
                  color: esActual
                      ? _primary.withValues(alpha: 0.35)
                      : Colors.transparent,
                ),
              ),
              child: _textoFragmento(index, esActual),
            ),
          ),
        );
      },
    );
  }

  /// Barra fija encima del reproductor: siempre visible al leer.
  Widget _buildBarraEditarFija() {
    return Material(
      color: const Color(0xFFEAEDFF),
      elevation: 4,
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 10, 12, 8),
          child: SizedBox(
            width: double.infinity,
            height: 48,
            child: ElevatedButton.icon(
              onPressed: _abrirPantallaEditor,
              icon: const Icon(Icons.edit_rounded, size: 22),
              label: const Text(
                'EDITAR TEXTO DEL PROCEDIMIENTO',
                style: TextStyle(fontSize: 14, fontWeight: FontWeight.w800),
              ),
              style: ElevatedButton.styleFrom(
                backgroundColor: _primary,
                foregroundColor: Colors.white,
                elevation: 0,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  /// Texto de un fragmento. En el fragmento actual, resalta la palabra que se
  /// está leyendo (estilo karaoke) usando los offsets del motor.
  Widget _textoFragmento(int index, bool esActual) {
    final texto = _fragmentos[index];
    const baseSize = 17.0;
    const altura = 1.6;

    if (!esActual || !_reproduciendo || _palabraFin <= _palabraInicio) {
      return Text(
        texto,
        style: TextStyle(
          color: esActual ? _onSurface : _onSurfaceVar,
          fontSize: baseSize,
          height: altura,
          fontWeight: esActual ? FontWeight.w600 : FontWeight.w400,
        ),
      );
    }

    final ini = _palabraInicio.clamp(0, texto.length);
    final fin = _palabraFin.clamp(ini, texto.length);

    return RichText(
      text: TextSpan(
        style: const TextStyle(
          color: _onSurface,
          fontSize: baseSize,
          height: altura,
          fontWeight: FontWeight.w600,
        ),
        children: [
          if (ini > 0) TextSpan(text: texto.substring(0, ini)),
          TextSpan(
            text: texto.substring(ini, fin),
            style: TextStyle(
              color: _onSurface,
              background: Paint()
                ..color = _highlight
                ..strokeWidth = 18
                ..strokeCap = StrokeCap.round
                ..style = PaintingStyle.fill,
              fontWeight: FontWeight.w800,
            ),
          ),
          if (fin < texto.length) TextSpan(text: texto.substring(fin)),
        ],
      ),
    );
  }

  Widget _buildReproductor() {
    final total = _fragmentos.length;
    final progreso = total <= 0 ? 0.0 : (_indiceActual + 1) / total;
    final velocidadActual = _velocidades.firstWhere(
      (v) => (v.rate - _rate).abs() < 0.001,
      orElse: () => _velocidades[1],
    );

    return Container(
      decoration: const BoxDecoration(
        color: _surface,
        border: Border(top: BorderSide(color: _high)),
      ),
      padding: EdgeInsets.fromLTRB(
        16,
        12,
        16,
        12 + MediaQuery.of(context).padding.bottom,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Control de volumen (con botones - / + y deslizador)
          Row(
            children: [
              Icon(
                _volumen <= 0.01
                    ? Icons.volume_off_rounded
                    : _volumen < 0.5
                        ? Icons.volume_down_rounded
                        : Icons.volume_up_rounded,
                color: _onSurfaceVar,
                size: 20,
              ),
              IconButton(
                visualDensity: VisualDensity.compact,
                onPressed: () => _cambiarVolumen(
                  (_volumen - 0.1).clamp(0.0, 1.0),
                  reaplicar: true,
                ),
                icon: const Icon(Icons.remove_circle_outline_rounded),
                color: _onSurfaceVar,
                tooltip: 'Bajar volumen',
              ),
              Expanded(
                child: SliderTheme(
                  data: SliderTheme.of(context).copyWith(
                    trackHeight: 4,
                    activeTrackColor: _primary,
                    inactiveTrackColor: _border,
                    thumbColor: _primary,
                    overlayShape:
                        const RoundSliderOverlayShape(overlayRadius: 16),
                    thumbShape:
                        const RoundSliderThumbShape(enabledThumbRadius: 9),
                  ),
                  child: Slider(
                    value: _volumen,
                    onChanged: (v) => _cambiarVolumen(v),
                    onChangeEnd: (v) => _cambiarVolumen(v, reaplicar: true),
                  ),
                ),
              ),
              IconButton(
                visualDensity: VisualDensity.compact,
                onPressed: () => _cambiarVolumen(
                  (_volumen + 0.1).clamp(0.0, 1.0),
                  reaplicar: true,
                ),
                icon: const Icon(Icons.add_circle_outline_rounded),
                color: _primary,
                tooltip: 'Subir volumen',
              ),
              SizedBox(
                width: 38,
                child: Text(
                  '${(_volumen * 100).round()}%',
                  textAlign: TextAlign.right,
                  style: const TextStyle(color: _onSurfaceVar, fontSize: 12),
                ),
              ),
            ],
          ),
          const SizedBox(height: 2),

          // Música de fondo (acompaña la lectura para mantener la atención)
          Row(
            children: [
              InkWell(
                onTap: _mostrarSelectorFondo,
                borderRadius: BorderRadius.circular(8),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4, horizontal: 2),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        _fondo.activo
                            ? Icons.music_note_rounded
                            : Icons.music_off_rounded,
                        color: _fondo.activo ? _primary : _onSurfaceVar,
                        size: 20,
                      ),
                      const SizedBox(width: 5),
                      Text(
                        _fondo.ambiente.label,
                        style: const TextStyle(color: _onSurfaceVar, fontSize: 12),
                      ),
                      const Icon(Icons.arrow_drop_down_rounded,
                          color: _onSurfaceVar, size: 18),
                    ],
                  ),
                ),
              ),
              Expanded(
                child: IgnorePointer(
                  ignoring: !_fondo.activo,
                  child: Opacity(
                    opacity: _fondo.activo ? 1 : 0.35,
                    child: SliderTheme(
                      data: SliderTheme.of(context).copyWith(
                        trackHeight: 3,
                        activeTrackColor: _primary,
                        inactiveTrackColor: _border,
                        thumbColor: _primary,
                        overlayShape:
                            const RoundSliderOverlayShape(overlayRadius: 12),
                        thumbShape:
                            const RoundSliderThumbShape(enabledThumbRadius: 7),
                      ),
                      child: Slider(
                        value: _fondo.volumen.clamp(0.0, 0.6),
                        max: 0.6,
                        onChanged: (v) => _cambiarFondoVol(v),
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),

          // Barra de progreso
          ClipRRect(
            borderRadius: BorderRadius.circular(99),
            child: LinearProgressIndicator(
              value: progreso,
              minHeight: 5,
              backgroundColor: _border,
              valueColor: const AlwaysStoppedAnimation(_primary),
            ),
          ),
          const SizedBox(height: 6),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                (_reproduciendo ? 'Reproduciendo…' : 'En pausa') +
                    (_leidoOffline ? '  ·  sin internet' : ''),
                style: const TextStyle(color: _onSurfaceVar, fontSize: 12),
              ),
              Text(
                '${_indiceActual + 1} / $total',
                style: const TextStyle(color: _onSurfaceVar, fontSize: 12),
              ),
            ],
          ),
          const SizedBox(height: 6),

          // Transporte (anterior / play / siguiente / reiniciar)
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              IconButton(
                onPressed: _anterior,
                icon: const Icon(Icons.skip_previous_rounded),
                color: _onSurface,
                iconSize: 30,
                tooltip: 'Anterior',
              ),
              const SizedBox(width: 8),
              _BotonPlay(
                reproduciendo: _reproduciendo,
                onTap: _togglePlay,
              ),
              const SizedBox(width: 8),
              IconButton(
                onPressed: _siguiente,
                icon: const Icon(Icons.skip_next_rounded),
                color: _onSurface,
                iconSize: 30,
                tooltip: 'Siguiente',
              ),
              const SizedBox(width: 8),
              IconButton(
                onPressed: () {
                  _scrollAlActual(indice: 0);
                  _reproducirDesdeFragmento(0);
                },
                icon: const Icon(Icons.replay_rounded),
                color: _onSurfaceVar,
                tooltip: 'Desde el inicio',
              ),
            ],
          ),
          const SizedBox(height: 8),

          // Ajustes: velocidad, pausa y voz
          Row(
            children: [
              Expanded(
                child: _ChipAjuste(
                  icon: Icons.speed_rounded,
                  label: velocidadActual.label,
                  onTap: _mostrarSelectorVelocidad,
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _ChipAjuste(
                  icon: Icons.more_horiz_rounded,
                  label: _pausaActual.label,
                  onTap: _mostrarSelectorPausa,
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _ChipAjuste(
                  icon: Icons.record_voice_over_rounded,
                  label: 'Voz',
                  onTap: _mostrarSelectorVoz,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  _OpcionPausa get _pausaActual => _pausas.firstWhere(
        (p) => p.ms == _pausaMs,
        orElse: () => _pausas[1],
      );

  void _mostrarSelectorVelocidad() {
    showModalBottomSheet(
      context: context,
      backgroundColor: _surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (context) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Padding(
                padding: EdgeInsets.all(16),
                child: Text(
                  'Velocidad de lectura',
                  style: TextStyle(
                    color: _onSurface,
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              ..._velocidades.map((v) {
                final seleccionada = (v.rate - _rate).abs() < 0.001;
                return ListTile(
                  title: Text(
                    v.label,
                    style: TextStyle(
                      color: seleccionada ? _primary : _onSurface,
                      fontWeight:
                          seleccionada ? FontWeight.w700 : FontWeight.w400,
                    ),
                  ),
                  trailing: seleccionada
                      ? const Icon(Icons.check_rounded, color: _primary)
                      : null,
                  onTap: () {
                    Navigator.pop(context);
                    _cambiarVelocidad(v.rate);
                  },
                );
              }),
              const SizedBox(height: 8),
            ],
          ),
        );
      },
    );
  }

  void _mostrarSelectorPausa() {
    showModalBottomSheet(
      context: context,
      backgroundColor: _surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (context) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Padding(
                padding: EdgeInsets.all(16),
                child: Text(
                  'Pausa entre frases',
                  style: TextStyle(
                    color: _onSurface,
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              ..._pausas.map((p) {
                final seleccionada = p.ms == _pausaMs;
                return ListTile(
                  title: Text(
                    p.label,
                    style: TextStyle(
                      color: seleccionada ? _primary : _onSurface,
                      fontWeight:
                          seleccionada ? FontWeight.w700 : FontWeight.w400,
                    ),
                  ),
                  trailing: seleccionada
                      ? const Icon(Icons.check_rounded, color: _primary)
                      : null,
                  onTap: () {
                    Navigator.pop(context);
                    _cambiarPausa(p.ms);
                  },
                );
              }),
              const SizedBox(height: 8),
            ],
          ),
        );
      },
    );
  }

  void _mostrarSelectorFondo() {
    showModalBottomSheet(
      context: context,
      backgroundColor: _surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (context) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Padding(
                padding: EdgeInsets.all(16),
                child: Text(
                  'Sonido de fondo',
                  style: TextStyle(
                    color: _onSurface,
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              ...FondoSonido.opciones.map((o) {
                final seleccionada = o.id == _fondo.ambienteId;
                return ListTile(
                  leading: Icon(
                    o.asset == null
                        ? Icons.music_off_rounded
                        : (o.id == 'lluvia'
                            ? Icons.water_drop_rounded
                            : Icons.graphic_eq_rounded),
                    color: seleccionada ? _primary : _onSurfaceVar,
                  ),
                  title: Text(
                    o.label,
                    style: TextStyle(
                      color: seleccionada ? _primary : _onSurface,
                      fontWeight:
                          seleccionada ? FontWeight.w700 : FontWeight.w400,
                    ),
                  ),
                  trailing: seleccionada
                      ? const Icon(Icons.check_rounded, color: _primary)
                      : null,
                  onTap: () {
                    Navigator.pop(context);
                    _cambiarAmbiente(o.id);
                  },
                );
              }),
              const SizedBox(height: 8),
            ],
          ),
        );
      },
    );
  }

  void _mostrarSelectorVoz() {
    if (_vocesEs.isEmpty) {
      showDialog(
        context: context,
        builder: (ctx) => AlertDialog(
          backgroundColor: _surface,
          title: const Text('No hay voces instaladas',
              style: TextStyle(color: _onSurface)),
          content: const Text(
            'Tu teléfono no tiene voces de lectura instaladas. Abre los ajustes '
            'de "Texto a voz", elige el motor "Google" e instala las voces en '
            'español.\n\nDespués vuelve aquí y podrás elegir entre varias voces.',
            style: TextStyle(color: _onSurfaceVar, height: 1.4),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Cerrar'),
            ),
            if (Platform.isAndroid)
              TextButton(
                onPressed: () {
                  Navigator.pop(ctx);
                  _abrirAjustesVoz();
                },
                child: const Text('Abrir ajustes',
                    style: TextStyle(color: _primary)),
              ),
          ],
        ),
      );
      return;
    }
    showModalBottomSheet(
      context: context,
      backgroundColor: _surface,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setSheetState) {
            return SafeArea(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const SizedBox(height: 12),
                  Container(
                    width: 40,
                    height: 4,
                    decoration: BoxDecoration(
                      color: _border,
                      borderRadius: BorderRadius.circular(99),
                    ),
                  ),
                  const Padding(
                    padding: EdgeInsets.fromLTRB(20, 14, 20, 2),
                    child: Align(
                      alignment: Alignment.centerLeft,
                      child: Text(
                        'Voz de lectura',
                        style: TextStyle(
                          color: _onSurface,
                          fontSize: 18,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ),
                  const Padding(
                    padding: EdgeInsets.fromLTRB(20, 0, 20, 8),
                    child: Align(
                      alignment: Alignment.centerLeft,
                      child: Text(
                        'Toca ▶ para escuchar cada voz y elige la que más te guste.',
                        style: TextStyle(color: _onSurfaceVar, fontSize: 13),
                      ),
                    ),
                  ),
                  Flexible(
                    child: ListView.separated(
                      shrinkWrap: true,
                      padding: const EdgeInsets.symmetric(horizontal: 12),
                      itemCount: _vocesEs.length,
                      separatorBuilder: (_, __) =>
                          const Divider(height: 1, color: _border),
                      itemBuilder: (context, i) {
                        final voz = _vocesEs[i];
                        final seleccionada = voz['name'] == _vozNombre;
                        final offline = _vozEsOffline(voz);
                        return ListTile(
                          contentPadding:
                              const EdgeInsets.symmetric(horizontal: 8),
                          leading: CircleAvatar(
                            radius: 18,
                            backgroundColor: seleccionada
                                ? _primary
                                : _low,
                            child: Icon(
                              Icons.record_voice_over_rounded,
                              size: 18,
                              color: seleccionada ? Colors.white : _primary,
                            ),
                          ),
                          title: Text(
                            _etiquetaVoz(voz, i),
                            style: TextStyle(
                              color: _onSurface,
                              fontWeight: seleccionada
                                  ? FontWeight.w700
                                  : FontWeight.w500,
                            ),
                          ),
                          subtitle: Text(
                            '${_nombreVoz(voz)}\n${offline ? 'Funciona sin internet' : 'Más clara · requiere internet'}',
                            style: const TextStyle(
                                color: _onSurfaceVar, fontSize: 12, height: 1.3),
                          ),
                          isThreeLine: true,
                          trailing: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              IconButton(
                                icon: const Icon(Icons.play_circle_fill_rounded),
                                color: _primary,
                                tooltip: 'Escuchar muestra',
                                onPressed: () {
                                  setSheetState(() {});
                                  _probarVoz(voz);
                                },
                              ),
                              if (seleccionada)
                                const Icon(Icons.check_circle_rounded,
                                    color: _primary),
                            ],
                          ),
                          onTap: () {
                            Navigator.pop(context);
                            _cambiarVoz(voz);
                          },
                        );
                      },
                    ),
                  ),
                  const SizedBox(height: 8),
                ],
              ),
            );
          },
        );
      },
    );
  }

  String _etiquetaVoz(Map<String, String> voz, int i) {
    final loc = (voz['locale'] ?? '').toLowerCase();
    String pais = '';
    if (loc == 'es-es') {
      pais = 'España';
    } else if (loc == 'es-us') {
      pais = 'EE. UU.';
    } else if (loc == 'es-mx') {
      pais = 'México';
    } else if (loc.isNotEmpty) {
      pais = loc.toUpperCase();
    }
    return 'Voz ${i + 1}${pais.isNotEmpty ? ' · $pais' : ''}';
  }

  /// Nombre técnico legible de la voz (para distinguirlas).
  String _nombreVoz(Map<String, String> voz) {
    final n = (voz['name'] ?? '').trim();
    return n.isEmpty ? 'Voz del sistema' : n;
  }
}

class _Bloque {
  final String texto;
  final int fragInicio;
  final List<int> offsets;
  const _Bloque({
    required this.texto,
    required this.fragInicio,
    required this.offsets,
  });
}

class _OpcionVelocidad {
  final String label;
  final double rate;
  const _OpcionVelocidad(this.label, this.rate);
}

class _OpcionPausa {
  final String label;
  final int ms;
  const _OpcionPausa(this.label, this.ms);
}

class _ChipAjuste extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback? onTap;
  const _ChipAjuste({required this.icon, required this.label, this.onTap});

  static const _low = Color(0xFFF2F3FF);
  static const _border = Color(0xFFC3C5D9);
  static const _onSurface = Color(0xFF131B2E);
  static const _primary = Color(0xFF003EC7);

  @override
  Widget build(BuildContext context) {
    final habilitado = onTap != null;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(99),
      child: Opacity(
        opacity: habilitado ? 1 : 0.5,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 9),
          decoration: BoxDecoration(
            color: _low,
            borderRadius: BorderRadius.circular(99),
            border: Border.all(color: _border),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, size: 16, color: _primary),
              const SizedBox(width: 6),
              Flexible(
                child: Text(
                  label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(color: _onSurface, fontSize: 13),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _BotonPlay extends StatelessWidget {
  final bool reproduciendo;
  final VoidCallback onTap;
  const _BotonPlay({required this.reproduciendo, required this.onTap});

  static const _primary = Color(0xFF003EC7);

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 58,
        height: 58,
        decoration: const BoxDecoration(
          color: _primary,
          shape: BoxShape.circle,
        ),
        child: Icon(
          reproduciendo ? Icons.pause_rounded : Icons.play_arrow_rounded,
          color: Colors.white,
          size: 34,
        ),
      ),
    );
  }
}
