import 'dart:io';

import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import '../models/notificacion_model.dart';
import '../services/notificacion_service.dart';

class NotificacionesScreen extends StatefulWidget {
  const NotificacionesScreen({super.key});

  @override
  State<NotificacionesScreen> createState() => _NotificacionesScreenState();
}

class _NotificacionesScreenState extends State<NotificacionesScreen> {
  List<NotificacionModel> _notificaciones = [];
  bool _isLoading = true;
  final AudioPlayer _audioPlayer = AudioPlayer();
  int? _reproduciendoId;

  @override
  void initState() {
    super.initState();
    _loadNotificaciones();
  }

  @override
  void dispose() {
    _audioPlayer.dispose();
    super.dispose();
  }

  Future<void> _loadNotificaciones() async {
    try {
      final notis = await NotificacionService.getBandeja();
      if (mounted) {
        setState(() {
          _notificaciones = notis;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _marcarLeida(int id) async {
    await NotificacionService.marcarLeida(id);
    setState(() {
      final idx = _notificaciones.indexWhere((n) => n.id == id);
      if (idx >= 0) {
        _notificaciones[idx] = NotificacionModel.fromJson({
          ...(_notificaciones[idx] as dynamic).toJson(),
          'leida': true,
          'fechaLectura': DateTime.now().toIso8601String(),
        } as Map<String, dynamic>);
      }
    });
  }

  Future<void> _marcarTodasLeidas() async {
    await NotificacionService.marcarTodasLeidas();
    _loadNotificaciones();
  }

  Future<void> _reproducirAudio(NotificacionModel noti) async {
    try {
      if (_reproduciendoId == noti.id) {
        await _audioPlayer.stop();
        setState(() => _reproduciendoId = null);
        return;
      }

      setState(() => _reproduciendoId = noti.id);

      final response = await NotificacionService.descargarAudio(noti.id);
      if (response != null && response.bodyBytes.isNotEmpty) {
        // Guardar temporalmente y reproducir
        final tempDir = await getTemporaryDirectory();
        final tempFile = File('${tempDir.path}/audio_${noti.id}.mp3');
        await tempFile.writeAsBytes(response.bodyBytes);
        
        await _audioPlayer.play(DeviceFileSource(tempFile.path));
        _audioPlayer.onPlayerComplete.listen((_) {
          if (mounted) setState(() => _reproduciendoId = null);
        });
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error reproduciendo audio: $e')),
        );
        setState(() => _reproduciendoId = null);
      }
    }
  }

  int get _pendientes => _notificaciones.where((n) => !n.leida).length;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0F172A),
      appBar: AppBar(
        title: const Text('Notificaciones'),
        actions: [
          if (_pendientes > 0)
            TextButton.icon(
              icon: const Icon(Icons.done_all, size: 18),
              label: Text('$_pendientes'),
              onPressed: _marcarTodasLeidas,
            ),
        ],
      ),
      body: _isLoading
          ? const Center(
              child: CircularProgressIndicator(color: Color(0xFF059669)),
            )
          : _notificaciones.isEmpty
              ? Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.notifications_none, size: 64, color: const Color(0xFF475569)),
                      const SizedBox(height: 16),
                      const Text(
                        'No tienes notificaciones',
                        style: TextStyle(color: Color(0xFF94A3B8), fontSize: 16),
                      ),
                      const SizedBox(height: 8),
                      const Text(
                        'Estás al día',
                        style: TextStyle(color: Color(0xFF64748B), fontSize: 13),
                      ),
                    ],
                  ),
                )
              : RefreshIndicator(
                  onRefresh: _loadNotificaciones,
                  color: const Color(0xFF059669),
                  child: ListView.builder(
                    padding: const EdgeInsets.all(16),
                    itemCount: _notificaciones.length,
                    itemBuilder: (context, index) {
                      final noti = _notificaciones[index];
                      return _NotificacionCard(
                        notificacion: noti,
                        reproduciendo: _reproduciendoId == noti.id,
                        onTap: () {
                          if (!noti.leida) _marcarLeida(noti.id);
                        },
                        onPlayAudio: noti.tieneAudio ? () => _reproducirAudio(noti) : null,
                      );
                    },
                  ),
                ),
    );
  }
}

class _NotificacionCard extends StatelessWidget {
  final NotificacionModel notificacion;
  final bool reproduciendo;
  final VoidCallback onTap;
  final VoidCallback? onPlayAudio;

  const _NotificacionCard({
    required this.notificacion,
    required this.reproduciendo,
    required this.onTap,
    this.onPlayAudio,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(
          color: notificacion.leida
              ? const Color(0xFF334155)
              : const Color(0xFF0284C7).withOpacity(0.5),
        ),
      ),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Indicador
              Container(
                margin: const EdgeInsets.only(top: 4),
                width: 8,
                height: 8,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: notificacion.leida
                      ? const Color(0xFF334155)
                      : const Color(0xFF38BDF8),
                  boxShadow: notificacion.leida
                      ? []
                      : [BoxShadow(
                          color: const Color(0xFF38BDF8).withOpacity(0.5),
                          blurRadius: 6,
                        )],
                ),
              ),
              const SizedBox(width: 12),

              // Contenido
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      notificacion.titulo,
                      style: TextStyle(
                        color: const Color(0xFFF1F5F9),
                        fontWeight: notificacion.leida ? FontWeight.normal : FontWeight.w600,
                        fontSize: 14,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      notificacion.mensaje,
                      style: const TextStyle(color: Color(0xFF94A3B8), fontSize: 13),
                      maxLines: 3,
                      overflow: TextOverflow.ellipsis,
                    ),
                    if (notificacion.documentoTitulo != null) ...[
                      const SizedBox(height: 6),
                      Row(
                        children: [
                          const Icon(Icons.description, size: 14, color: Color(0xFF60A5FA)),
                          const SizedBox(width: 4),
                          Text(
                            notificacion.documentoTitulo!,
                            style: const TextStyle(color: Color(0xFF60A5FA), fontSize: 12),
                          ),
                        ],
                      ),
                    ],
                    const SizedBox(height: 6),
                    Row(
                      children: [
                        Text(
                          notificacion.tiempoRelativo,
                          style: const TextStyle(color: Color(0xFF475569), fontSize: 11),
                        ),
                        if (notificacion.leida) ...[
                          const SizedBox(width: 8),
                          const Icon(Icons.check_circle, size: 12, color: Color(0xFF34D399)),
                          const SizedBox(width: 4),
                          const Text('Leída', style: TextStyle(color: Color(0xFF34D399), fontSize: 11)),
                        ],
                      ],
                    ),
                  ],
                ),
              ),

              // Audio button
              if (onPlayAudio != null)
                GestureDetector(
                  onTap: onPlayAudio,
                  child: Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: reproduciendo
                          ? const Color(0xFF7C3AED).withOpacity(0.2)
                          : const Color(0xFF7C3AED).withOpacity(0.1),
                      borderRadius: BorderRadius.circular(22),
                      border: Border.all(
                        color: const Color(0xFF7C3AED).withOpacity(0.3),
                      ),
                    ),
                    child: Icon(
                      reproduciendo ? Icons.stop : Icons.volume_up,
                      color: const Color(0xFFA78BFA),
                      size: 20,
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
