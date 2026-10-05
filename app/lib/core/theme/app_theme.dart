import 'package:flutter/material.dart';

/// CooBS Banking Design System Theme Tokens.
/// Follows high-contrast, clean-editorial fintech aesthetic (Navy, Emerald, Slate).
class AppTheme {
  // Primary Color Tokens
  static const Color navyPrimary = Color(0xFF0A2540);
  static const Color navyDark = Color(0xFF05192D);
  static const Color emeraldAccent = Color(0xFF00D4B2);
  static const Color emeraldDark = Color(0xFF00A389);
  
  // Neutral & Surface Tokens
  static const Color backgroundLight = Color(0xFFF7FAFC);
  static const Color surfaceWhite = Color(0xFFFFFFFF);
  static const Color textPrimary = Color(0xFF1A1F36);
  static const Color textSecondary = Color(0xFF4F566B);
  static const Color textMuted = Color(0xFF8792A2);
  static const Color borderSubtle = Color(0xFFE3E8EE);
  
  // Status Indicator Tokens
  static const Color successGreen = Color(0xFF00875A);
  static const Color warningAmber = Color(0xFFF5A623);
  static const Color errorCarmine = Color(0xFFDF1B41);
  static const Color infoBlue = Color(0xFF0066CC);

  /// Light theme for the CooBS Mobile Application.
  static ThemeData get lightTheme {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.light,
      primaryColor: navyPrimary,
      scaffoldBackgroundColor: backgroundLight,
      colorScheme: const ColorScheme.light(
        primary: navyPrimary,
        onPrimary: surfaceWhite,
        secondary: emeraldAccent,
        onSecondary: navyDark,
        surface: surfaceWhite,
        onSurface: textPrimary,
        error: errorCarmine,
        onError: surfaceWhite,
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: navyPrimary,
        foregroundColor: surfaceWhite,
        elevation: 0,
        centerTitle: true,
        titleTextStyle: TextStyle(
          color: surfaceWhite,
          fontSize: 18,
          fontWeight: FontWeight.w600,
          letterSpacing: 0.2,
        ),
      ),
      cardTheme: CardThemeData(
        color: surfaceWhite,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: borderSubtle, width: 1),
        ),
        margin: const EdgeInsets.symmetric(vertical: 8, horizontal: 0),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: navyPrimary,
          foregroundColor: surfaceWhite,
          minimumSize: const Size.fromHeight(52),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          elevation: 0,
          textStyle: const TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.w600,
            letterSpacing: 0.2,
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: navyPrimary,
          minimumSize: const Size.fromHeight(52),
          side: const BorderSide(color: navyPrimary, width: 1.5),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          textStyle: const TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: surfaceWhite,
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: borderSubtle),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: borderSubtle),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: navyPrimary, width: 2),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: errorCarmine),
        ),
        labelStyle: const TextStyle(color: textSecondary),
        hintStyle: const TextStyle(color: textMuted),
      ),
      dividerTheme: const DividerThemeData(
        color: borderSubtle,
        thickness: 1,
        space: 24,
      ),
    );
  }
}
