import 'dart:math';
import 'package:flutter/material.dart';

import 'ad_liquid_crystal_dialog.dart';
import 'ad_models.dart';

/// Service managing advertisement probability roll, session lifecycle,
/// and Liquid Crystal Display dialog presentation.
class AdService {
  AdService._();

  /// Whether the ad has already been presented during the current active session.
  /// Resets upon logout or explicit session reset.
  static bool hasShownThisSession = false;

  /// Global flag to control automatic ad presentation on app startup.
  /// Automatically disabled during automated widget tests to prevent dialog overlay collisions.
  static bool enableAutoShow =
      !WidgetsBinding.instance.runtimeType.toString().contains('Test');

  /// Rolls for an ad based on strict probability rules:
  /// - Image 2 (Umamusume): 1 in 2,000 (0.05%)
  /// - Image 1 (CooBS Duo): 1 in 1,000 (0.10%, i.e. 2 in 2,000)
  /// - Image 4 (Oggy Crossover): 1 in 500 (0.20%, i.e. 4 in 2,000)
  /// - Image 5 (Uma x Miku): 1 in 500 (0.20%, i.e. 4 in 2,000)
  /// - Image 6 (Chiikawa Duo): 1 in 500 (0.20%, i.e. 4 in 2,000)
  /// - Image 3 (CooBS Solo): Most frequent (1,985 in 2,000 = 99.25%)
  ///
  /// Pass [forceRoll] (0..1999) or a seeded [random] for deterministic test coverage.
  static AdItem pickAd({Random? random, int? forceRoll}) {
    final rng = random ?? Random();
    final roll = forceRoll ?? rng.nextInt(2000); // 0 .. 1999

    if (roll == 0) {
      // 1 in 2,000 (0.05%) -> Second image (Umamusume Easter Egg)
      return AdItem.umamusume;
    } else if (roll == 1 || roll == 2) {
      // 2 in 2,000 = 1 in 1,000 (0.10%) -> First image (CooBS Duo)
      return AdItem.duo;
    } else if (roll >= 3 && roll <= 6) {
      // 4 in 2,000 = 1 in 500 (0.20%) -> Fourth image (Oggy & Friends Crossover)
      return AdItem.oggyCrossover;
    } else if (roll >= 7 && roll <= 10) {
      // 4 in 2,000 = 1 in 500 (0.20%) -> Fifth image (Uma Musume x Miku Campaign)
      return AdItem.umaMiku;
    } else if (roll >= 11 && roll <= 14) {
      // 4 in 2,000 = 1 in 500 (0.20%) -> Sixth image (Chiikawa & Usagi Duo)
      return AdItem.chiikawaDuo;
    } else {
      // 1,985 in 2,000 (99.25%) -> Third image, most frequent (CooBS Solo)
      return AdItem.solo;
    }
  }

  /// Evaluates session state and presents the ad dialog if eligible.
  /// Returns `true` if an ad was displayed, `false` otherwise.
  static Future<bool> maybeShowAd(BuildContext context, {AdItem? forcedAd}) async {
    if (!enableAutoShow && forcedAd == null) return false;
    if (hasShownThisSession && forcedAd == null) return false;

    hasShownThisSession = true;
    final ad = forcedAd ?? pickAd();

    if (context.mounted) {
      await AdLiquidCrystalDialog.show(context, ad);
      return true;
    }
    return false;
  }

  /// Directly displays the ad dialog regardless of session state.
  static Future<void> showAd(BuildContext context, {AdItem? forcedAd}) async {
    final ad = forcedAd ?? pickAd();
    if (context.mounted) {
      await AdLiquidCrystalDialog.show(context, ad);
    }
  }

  /// Marks the current session as having seen an ad.
  static void markShown() {
    hasShownThisSession = true;
  }

  /// Resets session tracking (called upon user logout or app restart).
  static void resetSession() {
    hasShownThisSession = false;
  }
}
