import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import '../theme/landing_theme.dart';

/// Pantalla pública «Improvement Solutions» — rediseño profesional.
class LandingScreen extends StatefulWidget {
  const LandingScreen({super.key});

  @override
  State<LandingScreen> createState() => _LandingScreenState();
}

class _LandingScreenState extends State<LandingScreen> {
  static const String _apiExplorerImage =
      'https://lh3.googleusercontent.com/aida-public/AB6AXuCsfctAfKLeHmKeQyi_G3mTcJJio36le1Ctk-p2P5QYgvwnefsoaeh4LCABsr11S_9rBN3TGp-Qb4U6X33xxtFLE6hOWaABSMH_SKf8DQMWIZGheGWWd7iFAWAWgcgBa7IUouJUy2Di9auRCHwx0yfohr7CLwG7XF0hobfFr5ckq38DaJuV5wR3l1YymKXcv0i3VsNWCEYn9-ibySk1j6HVsHTPs6qbIzHCO-svlG4qSaov0OjPia9zwkYHQY-_HnxhQXor9VOT4rE';

  void _irAlLogin() {
    Navigator.of(context).pushNamed('/login', arguments: {'fromLanding': true});
  }

  @override
  Widget build(BuildContext context) {
    return Theme(
      data: buildLandingTheme(),
      child: Scaffold(
        backgroundColor: LandingColors.background,
        body: Stack(
          children: [
            const _DotPatternBackground(),
            Column(
              children: [
                _LandingHeader(onAcceder: _irAlLogin),
                Expanded(
                  child: SingleChildScrollView(
                    physics: const BouncingScrollPhysics(),
                    padding: const EdgeInsets.only(bottom: 88),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        _HeroSection(onEmpezar: _irAlLogin),
                        const _StatsStrip(),
                        const _ValuePropositionSection(),
                        _PillarsSection(apiImageUrl: _apiExplorerImage),
                        _CtaSection(onContactar: _irAlLogin),
                        const _LandingFooter(),
                      ],
                    ),
                  ),
                ),
              ],
            ),
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              child: _BottomAccederBar(onTap: _irAlLogin),
            ),
          ],
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Fondo de puntos
// ---------------------------------------------------------------------------

class _DotPatternBackground extends StatelessWidget {
  const _DotPatternBackground();

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      child: CustomPaint(
        painter: _DotPatternPainter(),
        size: Size.infinite,
      ),
    );
  }
}

class _DotPatternPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = LandingColors.primary.withValues(alpha: 0.055)
      ..style = PaintingStyle.fill;
    const step = 28.0;
    for (double x = 0; x < size.width; x += step) {
      for (double y = 0; y < size.height; y += step) {
        canvas.drawCircle(Offset(x, y), 0.7, paint);
      }
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

// ---------------------------------------------------------------------------
// Header / TopBar
// ---------------------------------------------------------------------------

class _LandingHeader extends StatelessWidget {
  const _LandingHeader({required this.onAcceder});

  final VoidCallback onAcceder;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      elevation: 0,
      child: Container(
        decoration: BoxDecoration(
          border: Border(
            bottom: BorderSide(
              color: LandingColors.outlineVariant.withValues(alpha: 0.7),
              width: 0.8,
            ),
          ),
        ),
        child: SafeArea(
          bottom: false,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
            child: Row(
              children: [
                Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                    gradient: const LinearGradient(
                      colors: [LandingColors.primary, Color(0xFF1A5CF8)],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(Icons.insights, color: Colors.white, size: 18),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    'Improvement Solutions',
                    style: GoogleFonts.hankenGrotesk(
                      fontSize: 15,
                      fontWeight: FontWeight.w800,
                      color: LandingColors.primary,
                      letterSpacing: -0.3,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                GestureDetector(
                  onTap: onAcceder,
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                    decoration: BoxDecoration(
                      color: LandingColors.primary,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      'Acceder',
                      style: GoogleFonts.inter(
                        fontWeight: FontWeight.w600,
                        fontSize: 13,
                        color: Colors.white,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Hero
// ---------------------------------------------------------------------------

class _HeroSection extends StatelessWidget {
  const _HeroSection({required this.onEmpezar});

  final VoidCallback onEmpezar;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Stack(
      children: [
        // Círculo decorativo
        Positioned(
          top: 0,
          right: -20,
          child: Container(
            width: 180,
            height: 180,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              gradient: RadialGradient(
                colors: [
                  LandingColors.primary.withValues(alpha: 0.1),
                  LandingColors.primary.withValues(alpha: 0.0),
                ],
              ),
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 28, 20, 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Badge
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                decoration: BoxDecoration(
                  color: LandingColors.primaryContainer.withValues(alpha: 0.55),
                  borderRadius: BorderRadius.circular(999),
                  border: Border.all(
                    color: LandingColors.primary.withValues(alpha: 0.2),
                    width: 0.8,
                  ),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.verified, color: LandingColors.primary, size: 14),
                    const SizedBox(width: 5),
                    Text(
                      'FISCALIZACIÓN 4.0',
                      style: GoogleFonts.inter(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: LandingColors.primary,
                        letterSpacing: 0.5,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              // Título principal
              Text.rich(
                TextSpan(
                  style: theme.textTheme.displayLarge,
                  children: const [
                    TextSpan(text: 'Centralización\n'),
                    TextSpan(text: 'para una '),
                    TextSpan(
                      text: 'Eficiencia\nAbsoluta',
                      style: TextStyle(color: LandingColors.onBackground),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              Text(
                'Optimice sus procesos de fiscalización técnica mediante investigación avanzada y estándares normativos globales.',
                style: theme.textTheme.bodyLarge,
              ),
              const SizedBox(height: 20),
              // Botón CTA principal
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  onPressed: onEmpezar,
                  icon: const Icon(Icons.arrow_forward_rounded, size: 18),
                  label: const Text('Empezar ahora'),
                  style: FilledButton.styleFrom(
                    backgroundColor: LandingColors.primary,
                    foregroundColor: LandingColors.onPrimary,
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    textStyle: GoogleFonts.inter(fontWeight: FontWeight.w600, fontSize: 15),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(10),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 10),
              Row(
                children: [
                  const Icon(Icons.shield_outlined, size: 13, color: LandingColors.outline),
                  const SizedBox(width: 5),
                  Text(
                    'Certificado por Industry Standards',
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: LandingColors.outline,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ],
    );
  }
}

// ---------------------------------------------------------------------------
// Tira de estadísticas
// ---------------------------------------------------------------------------

class _StatsStrip extends StatelessWidget {
  const _StatsStrip();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.symmetric(horizontal: 20),
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: LandingColors.outlineVariant, width: 0.8),
        boxShadow: [
          BoxShadow(
            color: LandingColors.primary.withValues(alpha: 0.06),
            blurRadius: 10,
            offset: const Offset(0, 3),
          ),
        ],
      ),
      child: const Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          _MiniStat(value: '99.9%', label: 'Precisión'),
          _VerticalDivider(),
          _MiniStat(value: '<0.5s', label: 'Respuesta'),
          _VerticalDivider(),
          _MiniStat(value: '2k+', label: 'Usuarios'),
        ],
      ),
    );
  }
}

class _VerticalDivider extends StatelessWidget {
  const _VerticalDivider();
  @override
  Widget build(BuildContext context) {
    return Container(width: 1, height: 32, color: LandingColors.outlineVariant);
  }
}

class _MiniStat extends StatelessWidget {
  const _MiniStat({required this.value, required this.label});

  final String value;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          value,
          style: GoogleFonts.hankenGrotesk(
            fontSize: 20,
            fontWeight: FontWeight.w800,
            color: LandingColors.primary,
            letterSpacing: -0.3,
          ),
        ),
        const SizedBox(height: 2),
        Text(
          label,
          style: GoogleFonts.inter(
            fontSize: 11,
            fontWeight: FontWeight.w500,
            color: LandingColors.onSurfaceVariant,
          ),
        ),
      ],
    );
  }
}

// ---------------------------------------------------------------------------
// Propuesta de valor
// ---------------------------------------------------------------------------

class _ValuePropositionSection extends StatelessWidget {
  const _ValuePropositionSection();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      margin: const EdgeInsets.only(top: 20),
      width: double.infinity,
      color: LandingColors.surfaceContainerLow,
      padding: const EdgeInsets.fromLTRB(20, 24, 20, 24),
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: LandingColors.outlineVariant.withValues(alpha: 0.6),
            width: 0.8,
          ),
          boxShadow: [
            BoxShadow(
              color: LandingColors.primary.withValues(alpha: 0.06),
              blurRadius: 10,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: 42,
                  height: 42,
                  decoration: BoxDecoration(
                    gradient: const LinearGradient(
                      colors: [LandingColors.primary, Color(0xFF1A5CF8)],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(Icons.track_changes, color: Colors.white, size: 22),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    'Precisión Técnica Inmediata',
                    style: theme.textTheme.titleMedium,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              'Arquitectura diseñada para reducir el tiempo de búsqueda a milisegundos. Acceda a información crítica con la exactitud de un estándar industrial.',
              style: theme.textTheme.bodyMedium,
            ),
            const SizedBox(height: 16),
            Divider(height: 1, color: LandingColors.outlineVariant.withValues(alpha: 0.6)),
            const SizedBox(height: 14),
            const Row(
              children: [
                Expanded(child: _StatBlock(value: '99.9%', label: 'Precisión Normativa')),
                SizedBox(width: 12),
                Expanded(child: _StatBlock(value: '<0.5s', label: 'Tiempo de Respuesta')),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _StatBlock extends StatelessWidget {
  const _StatBlock({required this.value, required this.label});

  final String value;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: LandingColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            value,
            style: GoogleFonts.hankenGrotesk(
              fontSize: 22,
              fontWeight: FontWeight.w800,
              color: LandingColors.primary,
            ),
          ),
          const SizedBox(height: 2),
          Text(label, style: Theme.of(context).textTheme.labelSmall),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Tres Pilares
// ---------------------------------------------------------------------------

class _PillarsSection extends StatelessWidget {
  const _PillarsSection({required this.apiImageUrl});

  final String apiImageUrl;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 24, 20, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 4,
                height: 20,
                decoration: BoxDecoration(
                  color: LandingColors.primary,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              const SizedBox(width: 10),
              Text('Nuestros Pilares', style: theme.textTheme.headlineMedium),
            ],
          ),
          const SizedBox(height: 16),
          _PillarCard(
            icon: Icons.account_tree_rounded,
            iconColor: Colors.white,
            iconBg: const LinearGradient(
              colors: [Color(0xFF006B5B), Color(0xFF00A38E)],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
            title: 'Explorador de APIs',
            description:
                'Visualice y analice integraciones técnicas en tiempo real para supervisión digital sin fisuras.',
            child: ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: SizedBox(
                height: 100,
                width: double.infinity,
                child: CachedNetworkImage(
                  imageUrl: apiImageUrl,
                  fit: BoxFit.cover,
                  color: Colors.black.withValues(alpha: 0.1),
                  colorBlendMode: BlendMode.multiply,
                  placeholder: (_, __) => Container(color: LandingColors.surfaceContainerHigh),
                  errorWidget: (_, __, ___) => Container(
                    color: LandingColors.surfaceContainerHigh,
                    child: const Icon(Icons.image_not_supported_outlined, color: LandingColors.outline),
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(height: 12),
          _PillarCard(
            icon: Icons.policy_rounded,
            iconColor: Colors.white,
            iconBg: const LinearGradient(
              colors: [LandingColors.primary, Color(0xFF1A5CF8)],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
            title: 'Biblioteca Normativa',
            description:
                'Documentación legal y técnica indexada. Consulta por lenguaje natural.',
            child: Wrap(
              spacing: 6,
              runSpacing: 6,
              children: ['ISO 27001', 'GDPR', 'OHSAS', '+12 más']
                  .map(
                    (t) => Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: LandingColors.primaryContainer.withValues(alpha: 0.4),
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Text(
                        t,
                        style: GoogleFonts.inter(
                          fontSize: 11,
                          fontWeight: FontWeight.w600,
                          color: LandingColors.primary,
                        ),
                      ),
                    ),
                  )
                  .toList(),
            ),
          ),
          const SizedBox(height: 12),
          _PillarCard(
            icon: Icons.school_rounded,
            iconColor: Colors.white,
            iconBg: const LinearGradient(
              colors: [Color(0xFF5C4200), Color(0xFF8A6500)],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
            title: 'Centro de Aprendizaje',
            description:
                'Capacitación continua para inspectores sobre nuevos estándares y protocolos.',
            child: Row(
              children: [
                // Avatares superpuestos con Stack
                SizedBox(
                  width: 30 + 24 + 24 + 30.0, // 3 dots + counter con solapamiento de 6px
                  height: 30,
                  child: Stack(
                    clipBehavior: Clip.none,
                    children: [
                      Positioned(
                        left: 0,
                        child: _AvatarDot(color: LandingColors.primaryFixed),
                      ),
                      Positioned(
                        left: 24,
                        child: _AvatarDot(color: LandingColors.secondaryContainer),
                      ),
                      Positioned(
                        left: 48,
                        child: _AvatarDot(color: const Color(0xFFB8F600)),
                      ),
                      Positioned(
                        left: 72,
                        child: Container(
                          width: 30,
                          height: 30,
                          alignment: Alignment.center,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: LandingColors.surfaceContainerHighest,
                            border: Border.all(color: Colors.white, width: 2),
                          ),
                          child: Text(
                            '+2k',
                            style: GoogleFonts.inter(
                              fontSize: 9,
                              fontWeight: FontWeight.w700,
                              color: LandingColors.primary,
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                Text(
                  'Inspectores activos',
                  style: Theme.of(context).textTheme.labelSmall,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _AvatarDot extends StatelessWidget {
  const _AvatarDot({required this.color});

  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 30,
      height: 30,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: color,
        border: Border.all(color: Colors.white, width: 2),
      ),
    );
  }
}

class _PillarCard extends StatelessWidget {
  const _PillarCard({
    required this.icon,
    required this.iconColor,
    required this.iconBg,
    required this.title,
    required this.description,
    required this.child,
  });

  final IconData icon;
  final Color iconColor;
  final LinearGradient iconBg;
  final String title;
  final String description;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: LandingColors.outlineVariant, width: 0.8),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 6,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(
                  gradient: iconBg,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Icon(icon, color: iconColor, size: 20),
              ),
              const SizedBox(width: 10),
              Expanded(child: Text(title, style: theme.textTheme.titleMedium)),
            ],
          ),
          const SizedBox(height: 10),
          Text(description, style: theme.textTheme.bodyMedium),
          const SizedBox(height: 12),
          child,
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Sección CTA
// ---------------------------------------------------------------------------

class _CtaSection extends StatelessWidget {
  const _CtaSection({required this.onContactar});

  final VoidCallback onContactar;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 24, 20, 0),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(24),
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            colors: [LandingColors.primary, Color(0xFF1A5CF8)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(20),
        ),
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            Positioned(
              top: -12,
              right: -12,
              child: Icon(
                Icons.bolt_rounded,
                size: 80,
                color: Colors.white.withValues(alpha: 0.12),
              ),
            ),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '¿Listo para elevar sus estándares?',
                  style: theme.textTheme.headlineMedium?.copyWith(
                    color: LandingColors.onPrimary,
                    fontSize: 18,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  'Únase a instituciones líderes que ya transforman su modelo de fiscalización.',
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: Colors.white.withValues(alpha: 0.85),
                  ),
                ),
                const SizedBox(height: 18),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton(
                    onPressed: onContactar,
                    style: FilledButton.styleFrom(
                      backgroundColor: Colors.white,
                      foregroundColor: LandingColors.primary,
                      padding: const EdgeInsets.symmetric(vertical: 13),
                      textStyle: GoogleFonts.inter(fontWeight: FontWeight.w600, fontSize: 14),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10),
                      ),
                    ),
                    child: const Text('Contactar Especialista'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Footer
// ---------------------------------------------------------------------------

class _LandingFooter extends StatelessWidget {
  const _LandingFooter();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.only(top: 24),
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 28),
      decoration: BoxDecoration(
        color: LandingColors.surfaceContainerHighest,
        border: Border(
          top: BorderSide(color: LandingColors.outlineVariant, width: 0.8),
        ),
      ),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                width: 28,
                height: 28,
                decoration: BoxDecoration(
                  gradient: const LinearGradient(
                    colors: [LandingColors.primary, Color(0xFF1A5CF8)],
                  ),
                  borderRadius: BorderRadius.circular(7),
                ),
                child: const Icon(Icons.insights, color: Colors.white, size: 16),
              ),
              const SizedBox(width: 8),
              Text(
                'Improvement Solutions',
                style: GoogleFonts.hankenGrotesk(
                  fontWeight: FontWeight.w800,
                  fontSize: 14,
                  color: LandingColors.primary,
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            'Estandarización y Precisión Técnica',
            textAlign: TextAlign.center,
            style: theme.textTheme.labelSmall?.copyWith(color: LandingColors.outline),
          ),
          const SizedBox(height: 16),
          Wrap(
            alignment: WrapAlignment.center,
            spacing: 20,
            children: [
              Text('Privacidad', style: theme.textTheme.labelMedium),
              Text('Términos', style: theme.textTheme.labelMedium),
              Text('Soporte', style: theme.textTheme.labelMedium),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            '© 2026 Improvement Solutions. Todos los derechos reservados.',
            textAlign: TextAlign.center,
            style: theme.textTheme.labelSmall?.copyWith(color: LandingColors.outline),
          ),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Barra inferior fija "Ingresa al sistema"
// ---------------------------------------------------------------------------

class _BottomAccederBar extends StatelessWidget {
  const _BottomAccederBar({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.08),
              blurRadius: 12,
              offset: const Offset(0, -3),
            ),
          ],
        ),
        child: SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 10, 20, 10),
            child: SizedBox(
              width: double.infinity,
              child: FilledButton.icon(
                onPressed: onTap,
                icon: const Icon(Icons.login_rounded, size: 18),
                label: Text(
                  'Ingresa al sistema',
                  style: GoogleFonts.inter(fontWeight: FontWeight.w600, fontSize: 15),
                ),
                style: FilledButton.styleFrom(
                  backgroundColor: LandingColors.primary,
                  foregroundColor: LandingColors.onPrimary,
                  padding: const EdgeInsets.symmetric(vertical: 13),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
