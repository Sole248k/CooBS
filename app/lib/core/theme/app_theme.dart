import 'package:flutter/material.dart';

/// CooBS Banking Design System Theme Tokens.
/// Follows high-contrast, clean-editorial fintech aesthetic (Navy, Emerald, Slate).
class AppTheme {
  // Primary CooBS Brand Tokens: Deep Obsidian Black (#000107) & Vibrant Cyan-Teal (#0091AE)
  static const Color navyPrimary = Color(0xFF000107);
  static const Color navyDark = Color(0xFF000107);
  static const Color emeraldAccent = Color(0xFF0091AE);
  static const Color emeraldDark = Color(0xFF006B82);
  static const Color brandCyan = Color(0xFF0091AE);
  static const Color brandCyanLight = Color(0xFF00B4D8);
  
  // Neutral & Surface Tokens
  static const Color backgroundLight = Color(0xFFF8FAFC);
  static const Color surfaceWhite = Color(0xFFFFFFFF);
  static const Color textPrimary = Color(0xFF000107);
  static const Color textSecondary = Color(0xFF475569);
  static const Color textMuted = Color(0xFF94A3B8);
  static const Color borderSubtle = Color(0xFFE2E8F0);
  
  // Status Indicator Tokens
  static const Color successGreen = Color(0xFF059669);
  static const Color warningAmber = Color(0xFFD97706);
  static const Color errorCarmine = Color(0xFFDC2626);
  static const Color infoBlue = Color(0xFF0091AE);

  // Dark mode tokens (mobile app)
  static const Color darkBackground = Color(0xFF000107);
  static const Color darkSurface = Color(0xFF0C141F);
  static const Color darkBorder = Color(0xFF1E293B);
  static const Color darkTextPrimary = Color(0xFFF1F5F9);
  static const Color darkTextSecondary = Color(0xFF94A3B8);
  static const Color darkAccent = Color(0xFF00B4D8);
  static const Color darkButton = Color(0xFF0091AE);

  /// Dark theme for the CooBS Mobile Application (toggled from Profile).
  static ThemeData get darkTheme {
    final base = lightTheme;
    final border = OutlineInputBorder(
      borderRadius: BorderRadius.circular(12),
      borderSide: const BorderSide(color: darkBorder),
    );
    return base.copyWith(
      brightness: Brightness.dark,
      primaryColor: darkAccent,
      scaffoldBackgroundColor: darkBackground,
      canvasColor: darkSurface,
      colorScheme: const ColorScheme.dark(
        primary: darkAccent,
        onPrimary: Colors.black,
        secondary: brandCyan,
        onSecondary: Colors.white,
        surface: darkSurface,
        onSurface: darkTextPrimary,
        onSurfaceVariant: darkTextSecondary,
        error: Color(0xFFFF7A6B),
        onError: Colors.black,
      ),
      textTheme: base.textTheme.apply(bodyColor: darkTextPrimary, displayColor: darkTextPrimary),
      listTileTheme: const ListTileThemeData(
        textColor: darkTextPrimary,
        iconColor: darkTextSecondary,
        subtitleTextStyle: TextStyle(color: darkTextSecondary, fontSize: 13),
      ),
      iconTheme: const IconThemeData(color: darkTextPrimary),
      cardTheme: base.cardTheme.copyWith(
        color: darkSurface,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: darkBorder, width: 1),
        ),
      ),
      bottomSheetTheme: const BottomSheetThemeData(backgroundColor: darkSurface, modalBackgroundColor: darkSurface),
      dialogTheme: const DialogThemeData(backgroundColor: darkSurface),
      popupMenuTheme: const PopupMenuThemeData(color: darkSurface),
      snackBarTheme: const SnackBarThemeData(backgroundColor: darkTextPrimary, contentTextStyle: TextStyle(color: darkBackground)),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: darkButton,
          foregroundColor: surfaceWhite,
          minimumSize: const Size.fromHeight(52),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          elevation: 0,
          textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600, letterSpacing: 0.2),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: darkAccent,
          minimumSize: const Size.fromHeight(52),
          side: const BorderSide(color: darkAccent, width: 1.5),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
        ),
      ),
      textButtonTheme: TextButtonThemeData(style: TextButton.styleFrom(foregroundColor: darkAccent)),
      inputDecorationTheme: base.inputDecorationTheme.copyWith(
        fillColor: darkSurface,
        border: border,
        enabledBorder: border,
        focusedBorder: border.copyWith(borderSide: const BorderSide(color: darkAccent, width: 2)),
        labelStyle: const TextStyle(color: darkTextSecondary),
        hintStyle: const TextStyle(color: darkTextSecondary),
      ),
      dividerTheme: const DividerThemeData(color: darkBorder, thickness: 1, space: 24),
    );
  }

  /// Light theme for the CooBS Mobile Application.
  static ThemeData get lightTheme {
    return ThemeData(
      useMaterial3: true,
      fontFamily: 'Poppins',
      brightness: Brightness.light,
      primaryColor: brandCyan,
      scaffoldBackgroundColor: backgroundLight,
      colorScheme: const ColorScheme.light(
        primary: brandCyan,
        onPrimary: surfaceWhite,
        secondary: navyPrimary,
        onSecondary: surfaceWhite,
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
          backgroundColor: brandCyan,
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
          foregroundColor: brandCyan,
          minimumSize: const Size.fromHeight(52),
          side: const BorderSide(color: brandCyan, width: 1.5),
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
          borderSide: const BorderSide(color: brandCyan, width: 2),
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
      listTileTheme: const ListTileThemeData(
        textColor: textPrimary,
        iconColor: textSecondary,
        subtitleTextStyle: TextStyle(color: textSecondary, fontSize: 13),
      ),
      snackBarTheme: const SnackBarThemeData(
        backgroundColor: navyPrimary,
        contentTextStyle: TextStyle(color: surfaceWhite),
      ),
    );
  }
}
