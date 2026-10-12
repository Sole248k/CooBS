import 'package:flutter/material.dart';

/// Models the promotion and easter-egg ad banners displayed upon app open and login.
class AdItem {
  final String id;
  final String title;
  final String subtitle;
  final String assetPath;
  final String oddsDescription;
  final String badgeText;
  final bool isEasterEgg;
  final Color primaryColor;
  final Color accentGlowColor;

  const AdItem({
    required this.id,
    required this.title,
    required this.subtitle,
    required this.assetPath,
    required this.oddsDescription,
    required this.badgeText,
    required this.isEasterEgg,
    required this.primaryColor,
    required this.accentGlowColor,
  });

  /// Image 1: CooBS Duo Banner (Probability 1/1,000)
  static const AdItem duo = AdItem(
    id: 'ad_coobs_duo',
    title: 'Sit, Bank, and Relax with CooBS',
    subtitle: 'Share the comfort of seamless digital banking with family & friends.',
    assetPath: 'assets/images/ad_coobs_duo.jpg',
    oddsDescription: '1 in 1,000 Lucky Draw',
    badgeText: 'MEMBER SPOTLIGHT • 1/1000',
    isEasterEgg: false,
    primaryColor: Color(0xFF0091AE),
    accentGlowColor: Color(0xFF38BDF8),
  );

  /// Image 2: Umamusume Pretty Derby (Probability 1/2,000)
  static const AdItem umamusume = AdItem(
    id: 'ad_umamusume',
    title: 'Umamusume: Pretty Derby',
    subtitle: 'Run towards your dreams! Special guest campaign event banner.',
    assetPath: 'assets/images/ad_umamusume.jpg',
    oddsDescription: '1 in 2,000 Ultra Rare Easter Egg',
    badgeText: '★ ULTRA RARE EASTER EGG • 1/2000 ★',
    isEasterEgg: true,
    primaryColor: Color(0xFFF59E0B),
    accentGlowColor: Color(0xFFFBBF24),
  );

  /// Image 3: CooBS Solo Banner (Most Frequent / Remainder 1997/2,000)
  static const AdItem solo = AdItem(
    id: 'ad_coobs_solo',
    title: 'Sit, Bank, and Relax with CooBS',
    subtitle: 'Experience fast zero-fee transfers and high-yield cooperative savings.',
    assetPath: 'assets/images/ad_coobs_solo.png',
    oddsDescription: 'Standard Member Showcase',
    badgeText: 'COOBS EXCLUSIVE',
    isEasterEgg: false,
    primaryColor: Color(0xFF0091AE),
    accentGlowColor: Color(0xFF2DD4BF),
  );

  static const List<AdItem> all = [duo, umamusume, solo];
}
