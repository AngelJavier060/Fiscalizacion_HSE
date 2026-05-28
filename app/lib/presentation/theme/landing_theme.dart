import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

abstract final class LandingColors {
  static const Color primary        = Color(0xFF003EC7);
  static const Color primaryDark    = Color(0xFF002A8A);
  static const Color onPrimary      = Color(0xFFFFFFFF);
  static const Color primaryContainer = Color(0xFFDDE1FF);
  static const Color background     = Color(0xFFFAF8FF);
  static const Color onBackground   = Color(0xFF0D1526);
  static const Color onSurface      = Color(0xFF0D1526);
  static const Color onSurfaceVariant = Color(0xFF525669);
  static const Color surface        = Color(0xFFFFFFFF);
  static const Color surfaceContainerLow  = Color(0xFFF2F3FF);
  static const Color surfaceContainerHigh = Color(0xFFE2E7FF);
  static const Color surfaceContainerHighest = Color(0xFFDAE2FD);
  static const Color outlineVariant = Color(0xFFD4D6E8);
  static const Color outline        = Color(0xFF8E91A4);
  static const Color secondary      = Color(0xFF006B5B);
  static const Color secondaryContainer = Color(0xFF26FEDC);
  static const Color tertiary       = Color(0xFF3E5600);
  static const Color tertiaryContainer = Color(0xFF527000);
  static const Color primaryFixed   = Color(0xFFDDE1FF);
  static const Color accentBlue     = Color(0xFF4A90E2);
}

ThemeData buildLandingTheme() {
  final base = ThemeData(
    useMaterial3: true,
    brightness: Brightness.light,
    scaffoldBackgroundColor: LandingColors.background,
    colorScheme: const ColorScheme.light(
      primary: LandingColors.primary,
      onPrimary: LandingColors.onPrimary,
      primaryContainer: LandingColors.primaryContainer,
      surface: LandingColors.surface,
      onSurface: LandingColors.onSurface,
      onSurfaceVariant: LandingColors.onSurfaceVariant,
      outlineVariant: LandingColors.outlineVariant,
      secondary: LandingColors.secondary,
      tertiary: LandingColors.tertiary,
    ),
  );

  return base.copyWith(
    textTheme: GoogleFonts.interTextTheme(base.textTheme).copyWith(
      // Hero title
      displayLarge: GoogleFonts.hankenGrotesk(
        fontSize: 34,
        fontWeight: FontWeight.w800,
        height: 1.1,
        color: LandingColors.primary,
        letterSpacing: -0.5,
      ),
      // Section headings
      headlineMedium: GoogleFonts.hankenGrotesk(
        fontSize: 20,
        fontWeight: FontWeight.w700,
        height: 1.25,
        color: LandingColors.onSurface,
      ),
      // Card titles
      titleMedium: GoogleFonts.hankenGrotesk(
        fontSize: 16,
        fontWeight: FontWeight.w700,
        height: 1.3,
        color: LandingColors.onSurface,
      ),
      // Subtitles
      titleSmall: GoogleFonts.hankenGrotesk(
        fontSize: 14,
        fontWeight: FontWeight.w600,
        color: LandingColors.onSurface,
      ),
      bodyLarge: GoogleFonts.inter(
        fontSize: 15,
        fontWeight: FontWeight.w400,
        height: 1.6,
        color: LandingColors.onSurfaceVariant,
      ),
      bodyMedium: GoogleFonts.inter(
        fontSize: 13,
        fontWeight: FontWeight.w400,
        height: 1.55,
        color: LandingColors.onSurfaceVariant,
      ),
      labelLarge: GoogleFonts.inter(
        fontSize: 13,
        fontWeight: FontWeight.w600,
        letterSpacing: 0.02,
      ),
      labelMedium: GoogleFonts.inter(
        fontSize: 12,
        fontWeight: FontWeight.w500,
        color: LandingColors.onSurfaceVariant,
      ),
      labelSmall: GoogleFonts.inter(
        fontSize: 11,
        fontWeight: FontWeight.w600,
        height: 1.2,
        color: LandingColors.onSurfaceVariant,
      ),
    ),
  );
}
