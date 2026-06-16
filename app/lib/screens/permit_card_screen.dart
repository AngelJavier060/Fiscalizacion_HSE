import 'dart:io';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../models/permit_model.dart';
import 'document_scanner_screen.dart';

class PermitCardScreen extends StatelessWidget {
  final PermitModel permit;
  const PermitCardScreen({super.key, required this.permit});

  // ── Colores semánticos ──────────────────────────────────────────
  Color get _statusColor {
    return switch (permit.status) {
      PermitStatus.active => const Color(0xFF3B6D11),
      PermitStatus.warning => const Color(0xFF854F0B),
      PermitStatus.expired => const Color(0xFFA32D2D),
    };
  }

  Color get _statusBg {
    return switch (permit.status) {
      PermitStatus.active => const Color(0xFFEAF3DE),
      PermitStatus.warning => const Color(0xFFFAEEDA),
      PermitStatus.expired => const Color(0xFFFCEBEB),
    };
  }

  Color get _barColor {
    return switch (permit.status) {
      PermitStatus.active => const Color(0xFF639922),
      PermitStatus.warning => const Color(0xFFEF9F27),
      PermitStatus.expired => const Color(0xFFE24B4A),
    };
  }

  String get _statusLabel {
    return switch (permit.status) {
      PermitStatus.active => '✓  Vigente',
      PermitStatus.warning => '⏱  Por vencer',
      PermitStatus.expired => '✕  Expirado',
    };
  }

  String get _remainingLabel {
    if (permit.status == PermitStatus.expired) {
      return 'Venció hace ${permit.remainingDays.abs()} días';
    }
    if (permit.remainingDays == 0) return 'Vence hoy';
    return 'Vence en ${permit.remainingDays} días';
  }

  String get _taskIcon {
    return switch (permit.criticalTask) {
      CriticalTask.hot => '🔥',
      CriticalTask.height => '⬆️',
      CriticalTask.confined => '⬛',
      CriticalTask.electrical => '⚡',
      CriticalTask.excavation => '⛏️',
      null => '📋',
    };
  }

  @override
  Widget build(BuildContext context) {
    final fmtShort = DateFormat('dd/MM/yy', 'es');
    final timeFmt = DateFormat('HH:mm', 'es');

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
                      'Detalle del permiso',
                      style: TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.w500,
                        color: Color(0xFF003398),
                      ),
                    ),
                  ),
                  Material(
                    color: Colors.transparent,
                    child: InkWell(
                      borderRadius: BorderRadius.circular(20),
                      onTap: () {
                        Navigator.pushReplacement(
                          context,
                          MaterialPageRoute(
                            builder: (_) => const DocumentScannerScreen(),
                          ),
                        );
                      },
                      child: const Padding(
                        padding: EdgeInsets.all(8),
                        child: Icon(Icons.camera_alt_outlined, color: Color(0xFF003398), size: 24),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            // ── Tarjeta principal ──────────────────────────────
            _DetailCard(
              children: [
                // Header: N° permiso + estado
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            permit.id,
                            style: const TextStyle(
                              fontSize: 22,
                              fontWeight: FontWeight.bold,
                              letterSpacing: -0.3,
                              color: Color(0xFF1A1C1C),
                            ),
                              ),
                          const SizedBox(height: 4),
                          Text(
                            permit.title,
                            style: TextStyle(
                              fontSize: 15,
                              color: Colors.grey.shade700,
                            ),
                        ),
                        ],
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 14, vertical: 8),
                      decoration: BoxDecoration(
                        color: _statusBg,
                        borderRadius: BorderRadius.circular(20),
                      ),
                      child: Text(
                        _statusLabel,
                        style: TextStyle(
                          color: _statusColor,
                          fontSize: 13,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                  ),
                  ],
                ),

                  const SizedBox(height: 20),

                // Barra de progreso de vigencia
                _buildProgressBar(),
                const SizedBox(height: 6),
                Align(
                  alignment: Alignment.centerRight,
                  child: Text(
                    _remainingLabel,
                    style: TextStyle(
                      color: _statusColor,
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),

                const SizedBox(height: 20),
                const Divider(height: 1, color: Color(0xFFEEEEEE)),
                const SizedBox(height: 16),

                // ── Campos del permiso ──────────────────────────
                // Tarea Crítica
                if (permit.criticalTask != null) ...[  
                  _fieldRow(Icons.warning_amber_outlined, 'Tarea Crítica',
                      '$_taskIcon ${permit.criticalTask!.label}'),
                  const SizedBox(height: 14),
                ],
                _fieldRow(Icons.location_on_outlined, 'Área / Ubicación',
                    permit.area),
                const SizedBox(height: 14),
                if (permit.description != null && permit.description!.isNotEmpty) ...[  
                  _fieldRow(Icons.description_outlined, 'Descripción',
                      permit.description!),
                  const SizedBox(height: 14),
                ],
                _fieldRow(Icons.person_outline, 'Responsable',
                    permit.responsible),
                const SizedBox(height: 14),
                Row(
                  children: [
                    Expanded(
                      child: _fieldRow(
                          Icons.calendar_today, 'Inicio', fmtShort.format(permit.startDate)),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: _fieldRow(
                          Icons.event_busy, 'Vencimiento', fmtShort.format(permit.endDate)),
                    ),
                  ],
                ),
                if (permit.startTime != null && permit.endTime != null) ...[  
                  const SizedBox(height: 14),
                  Row(
                    children: [
                      Expanded(
                        child: _fieldRow(
                            Icons.access_time, 'Hora inicio', timeFmt.format(permit.startTime!)),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: _fieldRow(
                            Icons.access_time, 'Hora fin', timeFmt.format(permit.endTime!)),
                      ),
                    ],
                  ),
                ],
              ],
            ),

            // ── Tarjeta: Emisión y Ejecución ────────────────────
            if (permit.emisor != null ||
                permit.ejecutante != null ||
                permit.empresaEjecutante != null) ...[  
              const SizedBox(height: 16),
              _DetailCard(
                icon: Icons.assignment_outlined,
                title: 'Emisión y Ejecución',
                children: [
                  if (permit.emisor != null && permit.emisor!.isNotEmpty)
                    _fieldRow(Icons.edit_outlined, 'Emisor', permit.emisor!),
                  if (permit.emisor != null && permit.emisor!.isNotEmpty)
                    const SizedBox(height: 14),
                  if (permit.ejecutante != null && permit.ejecutante!.isNotEmpty)
                    _fieldRow(Icons.engineering_outlined, 'Ejecutante',
                        permit.ejecutante!),
                  if (permit.ejecutante != null && permit.ejecutante!.isNotEmpty)
                    const SizedBox(height: 14),
                  if (permit.empresaEjecutante != null &&
                      permit.empresaEjecutante!.isNotEmpty)
                    _fieldRow(Icons.business_outlined, 'Empresa ejecutante',
                        permit.empresaEjecutante!),
                ],
              ),
            ],

            // ── Tarjeta: Nota ─────────────────────────────────
            if (permit.nota != null && permit.nota!.isNotEmpty) ...[  
              const SizedBox(height: 16),
              _DetailCard(
                icon: Icons.note_outlined,
                title: 'Nota',
                children: [
                  Text(
                    permit.nota!,
                    style: const TextStyle(
                      fontSize: 14,
                      color: Color(0xFF1A1C1C),
                      height: 1.5,
                    ),
                  ),
                ],
              ),
            ],

            const SizedBox(height: 20),

            // ── Imagen escaneada ───────────────────────────────
            _buildScannedImage(context),

            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }

  // ── Barra de progreso con tramos de color ──────────────────────
  Widget _buildProgressBar() {
    final fraction = permit.progressFraction;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            const Text(
              'Vigencia',
              style: TextStyle(fontSize: 12, color: Colors.grey),
            ),
            Text(
              '${(permit.remainingPercent).toStringAsFixed(0)}% restante',
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: _statusColor,
              ),
            ),
          ],
        ),
        const SizedBox(height: 6),
        ClipRRect(
          borderRadius: BorderRadius.circular(6),
          child: LinearProgressIndicator(
            value: fraction,
            minHeight: 10,
            backgroundColor: Colors.grey.shade200,
            valueColor: AlwaysStoppedAnimation<Color>(_barColor),
          ),
        ),
      ],
    );
  }

  // ── Fila de campo ─────────────────────────────────────────────
  Widget _fieldRow(IconData icon, String label, String value) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, size: 18, color: Colors.grey.shade500),
        const SizedBox(width: 8),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: TextStyle(
                  fontSize: 11,
                  color: Colors.grey.shade500,
                  fontWeight: FontWeight.w500,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                value,
                style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                  height: 1.3,
                  color: Color(0xFF1A1C1C),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  // ── Imagen escaneada tipo CamScanner ──────────────────────────
  Widget _buildScannedImage(BuildContext context) {
    final hasImage =
        permit.imagePath != null && File(permit.imagePath!).existsSync();

    return _DetailCard(
      icon: Icons.document_scanner_outlined,
      title: 'Documento escaneado',
      trailing: hasImage
          ? Text(
              DateFormat('dd/MM/yy – HH:mm').format(DateTime.now()),
              style: TextStyle(fontSize: 11, color: Colors.grey.shade500),
            )
          : null,
      children: [
        const SizedBox(height: 4),
        if (hasImage)
          ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: Image.file(
              File(permit.imagePath!),
              fit: BoxFit.cover,
              width: double.infinity,
              height: 260,
            ),
          )
        else
          Container(
            height: 160,
            decoration: BoxDecoration(
              color: Colors.grey.shade100,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                  color: Colors.grey.shade300, style: BorderStyle.solid),
            ),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(Icons.image_not_supported_outlined,
                    size: 40, color: Colors.grey.shade400),
                const SizedBox(height: 8),
                Text(
                  'Sin imagen escaneada',
                  style: TextStyle(
                      color: Colors.grey.shade500, fontSize: 13),
                ),
              ],
            ),
          ),
        const SizedBox(height: 12),
        SizedBox(
          width: double.infinity,
          child: OutlinedButton.icon(
            onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (_) =>
                      DocumentScannerScreen(permit: permit),
                ),
              );
            },
            icon: Icon(
              hasImage ? Icons.refresh : Icons.camera_alt_outlined,
              size: 18,
            ),
            label: Text(hasImage ? 'Re-escanear' : 'Escanear documento'),
            style: OutlinedButton.styleFrom(
              foregroundColor: const Color(0xFF434656),
              side: const BorderSide(color: Color(0xFFC3C5D9)),
              padding: const EdgeInsets.symmetric(vertical: 12),
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12)),
            ),
          ),
        ),
      ],
    );
  }
}

// ── Tarjeta de detalle reutilizable ─────────────────────────────────
class _DetailCard extends StatelessWidget {
  final IconData? icon;
  final String? title;
  final Widget? trailing;
  final List<Widget> children;

  const _DetailCard({
    this.icon,
    this.title,
    this.trailing,
    required this.children,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.black.withValues(alpha: 0.08)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.06),
            blurRadius: 12,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (title != null || icon != null)
            Row(
              children: [
                if (icon != null) ...[  
                  Icon(icon, size: 20, color: const Color(0xFF003398)),
                  const SizedBox(width: 8),
                ],
                if (title != null)
                  Text(
                    title!,
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: Color(0xFF1A1C1C),
                    ),
                  ),
                const Spacer(),
                if (trailing != null) trailing!,
              ],
            ),
          if (title != null || icon != null) const SizedBox(height: 12),
          ...children,
        ],
      ),
    );
  }
}
