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

  /// Image 3: CooBS Solo Banner (Most Frequent / Remainder 1985/2,000)
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

  /// Image 4: Oggy & Friends Special Crossover (Probability 1/500)
  static const AdItem oggyCrossover = AdItem(
    id: 'ad_oggy_crossover',
    title: 'Your Money, Your Way with CooBS',
    subtitle: 'Oggy, Helios & friends team up for effortless digital banking!',
    assetPath: 'assets/images/ad_oggy_crossover.jpg',
    oddsDescription: '1 in 500 Rare Special Crossover',
    badgeText: '★ SPECIAL CROSSOVER • 1/500 ★',
    isEasterEgg: true,
    primaryColor: Color(0xFF10B981),
    accentGlowColor: Color(0xFF34D399),
  );

  /// Image 5: Uma Musume x Hatsune Miku Campaign (Probability 1/500)
  static const AdItem umaMiku = AdItem(
    id: 'ad_uma_miku',
    title: 'Your Money, Your Way with CooBS',
    subtitle: 'Helios, Miku & Optimus Prime celebrate effortless banking!',
    assetPath: 'assets/images/ad_uma_miku.jpg',
    oddsDescription: '1 in 500 Rare Special Campaign',
    badgeText: '★ MIKU & HELIOS CAMPAIGN • 1/500 ★',
    isEasterEgg: true,
    primaryColor: Color(0xFF06B6D4),
    accentGlowColor: Color(0xFF22D3EE),
  );

  /// Image 6: Chiikawa & Usagi Duo Edition (Probability 1/500)
  static const AdItem chiikawaDuo = AdItem(
    id: 'ad_chiikawa_duo',
    title: 'Sit, Bank, and Relax with CooBS',
    subtitle: 'Chiikawa & Usagi special member edition! Relax with digital banking.',
    assetPath: 'assets/images/ad_chiikawa_duo.png',
    oddsDescription: '1 in 500 Rare Member Showcase',
    badgeText: 'CHIIKAWA & USAGI • 1/500',
    isEasterEgg: true,
    primaryColor: Color(0xFF0091AE),
    accentGlowColor: Color(0xFF38BDF8),
  );

  static const List<AdItem> all = [
    duo,
    umamusume,
    solo,
    oggyCrossover,
    umaMiku,
    chiikawaDuo,
  ];
}
