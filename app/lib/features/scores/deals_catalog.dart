import 'dart:math' as math;
import 'package:geolocator/geolocator.dart';

/// Fallback city anchors (Makati CBD Ayala Triangle and Mandaluyong Megamall).
const double kDefaultLat = 14.5547;
const double kDefaultLng = 121.0244;
const double kMandaluyongLat = 14.5840;
const double kMandaluyongLng = 121.0568;

/// Resolves user physical GPS coordinates via device location services.
/// Returns null if GPS is disabled, in test environments, or permissions are not granted.
Future<Map<String, double>?> getDeviceCoordinates() async {
  try {
    final isEnabled = await Geolocator.isLocationServiceEnabled();
    if (!isEnabled) return null;

    var permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied) return null;
    }
    if (permission == LocationPermission.deniedForever) return null;

    final pos = await Geolocator.getCurrentPosition(
      locationSettings: const LocationSettings(
        accuracy: LocationAccuracy.medium,
        timeLimit: Duration(seconds: 4),
      ),
    );
    return {'lat': pos.latitude, 'lng': pos.longitude};
  } catch (_) {
    return null;
  }
}

/// Represents a curated merchant deal for retail banking members.
class PartnerDeal {
  final String id;
  final String name;
  final String area;
  final String city; // 'Makati CBD' or 'Mandaluyong'
  final String category; // 'FOOD' or 'SHOPPING'
  final String subcategory; // 'Cafe' or 'Clothes'
  final double lat;
  final double lng;
  final String discount;
  final String title;
  final String promoCode;
  final String terms;
  final String validUntil;

  const PartnerDeal({
    required this.id,
    required this.name,
    required this.area,
    required this.city,
    required this.category,
    required this.subcategory,
    required this.lat,
    required this.lng,
    required this.discount,
    required this.title,
    required this.promoCode,
    required this.terms,
    this.validUntil = '31 Dec 2026',
  });
}

/// Up to 25 spanning discounts across cafes and clothes in Makati CBD and Mandaluyong.
const List<PartnerDeal> kPartnerDeals = [
  // --- MAKATI CBD: CAFES (8) ---
  PartnerDeal(
    id: 'MKT-CF-01',
    name: 'Wildflour Cafe + Bakery',
    area: 'Salcedo Village',
    city: 'Makati CBD',
    category: 'FOOD',
    subcategory: 'Cafe',
    lat: 14.5550,
    lng: 121.0265,
    discount: '20% OFF',
    title: '20% off all weekday brunch & specialty pastries',
    promoCode: 'COOBSFLOUR20',
    terms: 'Valid Mon–Fri for dine-in and takeout with any CooBS deposit account.',
  ),
  PartnerDeal(
    id: 'MKT-CF-02',
    name: 'The Curator Coffee & Cocktails',
    area: 'Legazpi Village',
    city: 'Makati CBD',
    category: 'FOOD',
    subcategory: 'Cafe',
    lat: 14.5518,
    lng: 121.0189,
    discount: 'Free Upgrade',
    title: 'Free pour-over bean upgrade with pastry purchase',
    promoCode: 'COOBSCURATOR',
    terms: 'Present promo code to barista before ordering.',
  ),
  PartnerDeal(
    id: 'MKT-CF-03',
    name: 'Yardstick Coffee',
    area: 'Legazpi Village',
    city: 'Makati CBD',
    category: 'FOOD',
    subcategory: 'Cafe',
    lat: 14.5534,
    lng: 121.0182,
    discount: '15% OFF',
    title: '15% off signature espresso drinks & retail beans',
    promoCode: 'COOBSYARD15',
    terms: 'Valid on whole bean bags 250g and hand-pulled espresso beverages.',
  ),
  PartnerDeal(
    id: 'MKT-CF-04',
    name: 'Starbucks Reserve Ayala',
    area: 'Ayala Center',
    city: 'Makati CBD',
    category: 'FOOD',
    subcategory: 'Cafe',
    lat: 14.5512,
    lng: 121.0240,
    discount: 'Free Upsize',
    title: 'Complimentary beverage upsize on all handcrafted drinks',
    promoCode: 'COOBSBUCKS',
    terms: 'Exclusive to CooBS Tier 2 and Tier 3 verified members.',
  ),
  PartnerDeal(
    id: 'MKT-CF-05',
    name: "Toby's Estate",
    area: 'Century City',
    city: 'Makati CBD',
    category: 'FOOD',
    subcategory: 'Cafe',
    lat: 14.5670,
    lng: 121.0298,
    discount: '₱100 OFF',
    title: '₱100 voucher on orders above ₱500',
    promoCode: 'COOBSTOBYS100',
    terms: 'Applicable on dine-in meals, flat whites, and artisanal toasts.',
  ),
  PartnerDeal(
    id: 'MKT-CF-06',
    name: 'Commune Cafe',
    area: 'Poblacion',
    city: 'Makati CBD',
    category: 'FOOD',
    subcategory: 'Cafe',
    lat: 14.5645,
    lng: 121.0310,
    discount: 'Buy 1 Get 1',
    title: 'Buy 1 Get 1 on single-origin cold brew coffee',
    promoCode: 'COOBSCOMMUNE',
    terms: 'Valid every Tuesday and Thursday between 1:00 PM and 6:00 PM.',
  ),
  PartnerDeal(
    id: 'MKT-CF-07',
    name: 'Panco Cafe',
    area: 'Legazpi Village',
    city: 'Makati CBD',
    category: 'FOOD',
    subcategory: 'Cafe',
    lat: 14.5529,
    lng: 121.0175,
    discount: '10% OFF',
    title: '10% off Melbourne-style breakfast & latte combos',
    promoCode: 'COOBSPANCO',
    terms: 'Valid daily with cashless settlement.',
  ),
  PartnerDeal(
    id: 'MKT-CF-08',
    name: 'Omotesando Koffee',
    area: 'Power Plant Mall, Rockwell',
    city: 'Makati CBD',
    category: 'FOOD',
    subcategory: 'Cafe',
    lat: 14.5654,
    lng: 121.0366,
    discount: '15% OFF',
    title: '15% off iced cappuccino and baked kashi cubes',
    promoCode: 'COOBSOMOTE15',
    terms: 'One redemption per customer per visit.',
  ),

  // --- MAKATI CBD: CLOTHES & APPAREL (5) ---
  PartnerDeal(
    id: 'MKT-CL-01',
    name: 'Uniqlo Greenbelt 5',
    area: 'Greenbelt 5, Ayala Center',
    city: 'Makati CBD',
    category: 'SHOPPING',
    subcategory: 'Clothes',
    lat: 14.5521,
    lng: 121.0210,
    discount: '₱300 OFF',
    title: '₱300 discount on min. ₱2,500 LifeWear apparel purchase',
    promoCode: 'COOBSUNIQLO',
    terms: 'Valid at cashier with CooBS member barcode presentation.',
  ),
  PartnerDeal(
    id: 'MKT-CL-02',
    name: 'Zara Glorietta 2',
    area: 'Glorietta 2, Ayala Center',
    city: 'Makati CBD',
    category: 'SHOPPING',
    subcategory: 'Clothes',
    lat: 14.5510,
    lng: 121.0255,
    discount: '15% OFF',
    title: '15% off new seasonal fashion collection',
    promoCode: 'COOBSZARA15',
    terms: 'Applies to regular-priced apparel and outerwear.',
  ),
  PartnerDeal(
    id: 'MKT-CL-03',
    name: 'Massimo Dutti Greenbelt 3',
    area: 'Greenbelt 3, Ayala Center',
    city: 'Makati CBD',
    category: 'SHOPPING',
    subcategory: 'Clothes',
    lat: 14.5529,
    lng: 121.0225,
    discount: '10% Priv.',
    title: '10% Member Privilege on tailored linen and blazers',
    promoCode: 'COOBSDUTTI10',
    terms: 'Applicable in-store upon presenting active membership ID.',
  ),
  PartnerDeal(
    id: 'MKT-CL-04',
    name: 'Sunnies Studios & Flaunt',
    area: 'Glorietta 3',
    city: 'Makati CBD',
    category: 'SHOPPING',
    subcategory: 'Clothes',
    lat: 14.5505,
    lng: 121.0245,
    discount: '20% OFF',
    title: '20% off eyewear and capsule lifestyle apparel',
    promoCode: 'COOBSSUNNIES',
    terms: 'Cannot be combined with mall-wide holiday sales.',
  ),
  PartnerDeal(
    id: 'MKT-CL-05',
    name: 'Commonwealth Manila',
    area: 'Power Plant Mall, Rockwell',
    city: 'Makati CBD',
    category: 'SHOPPING',
    subcategory: 'Clothes',
    lat: 14.5648,
    lng: 121.0372,
    discount: '₱500 Voucher',
    title: '₱500 voucher on premium streetwear and sneakers',
    promoCode: 'COOBSCMMN500',
    terms: 'Min. spend of ₱4,000 required. Single receipt transaction.',
  ),

  // --- MANDALUYONG: CAFES (6) ---
  PartnerDeal(
    id: 'MDL-CF-01',
    name: 'EDSA BDG (Beverage Design Group)',
    area: 'CLMC Bldg, EDSA-Reliance',
    city: 'Mandaluyong',
    category: 'FOOD',
    subcategory: 'Cafe',
    lat: 14.5802,
    lng: 121.0542,
    discount: '20% OFF',
    title: '20% off all artisan batch brews and sourdough bakes',
    promoCode: 'COOBSEDSA20',
    terms: 'Valid on all handcrafted beverages and pastry items.',
  ),
  PartnerDeal(
    id: 'MDL-CF-02',
    name: 'UCC Clockwork Megamall',
    area: 'SM Megamall Fashion Hall',
    city: 'Mandaluyong',
    category: 'FOOD',
    subcategory: 'Cafe',
    lat: 14.5847,
    lng: 121.0573,
    discount: 'Free Waffle',
    title: 'Free Japanese waffle with any siphon coffee order',
    promoCode: 'COOBSUCC',
    terms: 'Valid for dine-in guests ordering siphon brew.',
  ),
  PartnerDeal(
    id: 'MDL-CF-03',
    name: 'Frank & Dean Cafe',
    area: 'Greenfield District',
    city: 'Mandaluyong',
    category: 'FOOD',
    subcategory: 'Cafe',
    lat: 14.5772,
    lng: 121.0538,
    discount: '15% OFF',
    title: '15% off wood-fired pizza and specialty coffee brunch',
    promoCode: 'COOBSFRANK15',
    terms: 'Valid on weekends and public holidays.',
  ),
  PartnerDeal(
    id: 'MDL-CF-04',
    name: 'Habitual Coffee',
    area: 'Shangri-La Plaza',
    city: 'Mandaluyong',
    category: 'FOOD',
    subcategory: 'Cafe',
    lat: 14.5815,
    lng: 121.0558,
    discount: '10% OFF',
    title: '10% off Aeropress specialty coffee and bowls',
    promoCode: 'COOBSHABIT',
    terms: 'Show promo code at register before checkout.',
  ),
  PartnerDeal(
    id: 'MDL-CF-05',
    name: 'The Coffee Academics',
    area: 'SM Megamall Mega Atrium',
    city: 'Mandaluyong',
    category: 'FOOD',
    subcategory: 'Cafe',
    lat: 14.5840,
    lng: 121.0568,
    discount: 'Buy 1 Get 1',
    title: 'Buy 1 Get 1 on Okinawa brown sugar espresso lattes',
    promoCode: 'COOBSTCA',
    terms: 'Valid between 2:00 PM and 5:00 PM on weekdays.',
  ),
  PartnerDeal(
    id: 'MDL-CF-06',
    name: 'Elephant Grounds',
    area: 'The Podium, ADB Ave',
    city: 'Mandaluyong',
    category: 'FOOD',
    subcategory: 'Cafe',
    lat: 14.5862,
    lng: 121.0592,
    discount: '15% OFF',
    title: '15% off ice cream sandwiches and roasted coffee',
    promoCode: 'COOBSELEPHANT',
    terms: 'Valid for all retail orders paid with CooBS accounts.',
  ),

  // --- MANDALUYONG: CLOTHES & APPAREL (6) ---
  PartnerDeal(
    id: 'MDL-CL-01',
    name: 'H&M SM Megamall',
    area: 'SM Megamall Bldg A',
    city: 'Mandaluyong',
    category: 'SHOPPING',
    subcategory: 'Clothes',
    lat: 14.5845,
    lng: 121.0565,
    discount: '20% OFF',
    title: '20% off conscious sustainable apparel & denim collection',
    promoCode: 'COOBSHM20',
    terms: 'Valid in-store at SM Megamall branch.',
  ),
  PartnerDeal(
    id: 'MDL-CL-02',
    name: 'Mango Shangri-La Plaza',
    area: 'Shangri-La Plaza Mall',
    city: 'Mandaluyong',
    category: 'SHOPPING',
    subcategory: 'Clothes',
    lat: 14.5812,
    lng: 121.0555,
    discount: '15% OFF',
    title: '15% off modern European dresses and smart casualwear',
    promoCode: 'COOBSMANGO15',
    terms: 'Not valid on promotional or marked-down clearance items.',
  ),
  PartnerDeal(
    id: 'MDL-CL-03',
    name: 'Bench / Park Pioneer',
    area: 'Pioneer Center, Reliance',
    city: 'Mandaluyong',
    category: 'SHOPPING',
    subcategory: 'Clothes',
    lat: 14.5728,
    lng: 121.0512,
    discount: '₱250 OFF',
    title: '₱250 discount on min. ₱1,500 daily casual clothing',
    promoCode: 'COOBSBENCH',
    terms: 'Exclusive to Pioneer Center branch shoppers.',
  ),
  PartnerDeal(
    id: 'MDL-CL-04',
    name: 'Cotton On Megamall',
    area: 'SM Megamall Bldg B',
    city: 'Mandaluyong',
    category: 'SHOPPING',
    subcategory: 'Clothes',
    lat: 14.5852,
    lng: 121.0580,
    discount: 'B2G1 50%',
    title: 'Buy 2, Get 1 at 50% off on all everyday tees and shorts',
    promoCode: 'COOBSCOTTON',
    terms: 'Discount applies to lower-priced item in cart.',
  ),
  PartnerDeal(
    id: 'MDL-CL-05',
    name: 'Bratpack Greenfield',
    area: 'Greenfield District Pavilion',
    city: 'Mandaluyong',
    category: 'SHOPPING',
    subcategory: 'Clothes',
    lat: 14.5768,
    lng: 121.0532,
    discount: '15% OFF',
    title: '15% off outdoor lifestyle apparel and urban daypacks',
    promoCode: 'COOBSBRAT15',
    terms: 'Must show CooBS member screen at register.',
  ),
  PartnerDeal(
    id: 'MDL-CL-06',
    name: 'Marks & Spencer Shangri-La',
    area: 'Shangri-La Plaza Level 2',
    city: 'Mandaluyong',
    category: 'SHOPPING',
    subcategory: 'Clothes',
    lat: 14.5818,
    lng: 121.0552,
    discount: '10% Rebate',
    title: '10% member cashback on fine knitwear and linen shirts',
    promoCode: 'COOBSMS10',
    terms: 'Cashback credited within 24 hours to CooBS primary account.',
  ),
];

/// Computes distance in kilometers between two geo coordinates using Haversine formula.
double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
  const p = 0.017453292519943295; // Math.PI / 180
  final a = 0.5 -
      math.cos((lat2 - lat1) * p) / 2 +
      math.cos(lat1 * p) *
          math.cos(lat2 * p) *
          (1 - math.cos((lon2 - lon1) * p)) /
          2;
  return 12742 * math.asin(math.sqrt(a)); // 2 * R * asin... where R = 6371 km
}

/// Evaluates and ranks deals based on spending habits affinity and proximity.
List<EnrichedDeal> getRankedDeals({
  required double userLat,
  required double userLng,
  required String topCategory, // e.g. 'FOOD' or 'SHOPPING'
  String? cityFilter, // 'Makati CBD', 'Mandaluyong', or null
  String? subcategoryFilter, // 'Cafe', 'Clothes', or null
}) {
  final normalizedCategory = topCategory.toUpperCase().trim();

  final list = <EnrichedDeal>[];
  for (final deal in kPartnerDeals) {
    if (cityFilter != null && cityFilter != 'All' && deal.city != cityFilter) {
      continue;
    }
    if (subcategoryFilter != null &&
        subcategoryFilter != 'All' &&
        deal.subcategory != subcategoryFilter) {
      continue;
    }

    final dist = calculateDistanceKm(userLat, userLng, deal.lat, deal.lng);
    final isHabitMatch = deal.category.toUpperCase() == normalizedCategory ||
        (normalizedCategory.contains('FOOD') && deal.category == 'FOOD') ||
        (normalizedCategory.contains('SHOP') && deal.category == 'SHOPPING');

    // Affinity weight gives a solid 10.0 boost to habits match
    final affinityScore = isHabitMatch ? 15.0 : 0.0;
    // Closer distance gets higher score
    final distanceScore = (10.0 - dist).clamp(0.0, 10.0);
    final compositeRank = affinityScore + distanceScore;

    list.add(EnrichedDeal(
      deal: deal,
      distanceKm: dist,
      isHabitMatch: isHabitMatch,
      rankScore: compositeRank,
    ));
  }

  // Sort: highest rank score first (matches habit first, then closest distance)
  list.sort((a, b) => b.rankScore.compareTo(a.rankScore));
  return list;
}

class EnrichedDeal {
  final PartnerDeal deal;
  final double distanceKm;
  final bool isHabitMatch;
  final double rankScore;

  const EnrichedDeal({
    required this.deal,
    required this.distanceKm,
    required this.isHabitMatch,
    required this.rankScore,
  });

  String get formattedDistance {
    if (distanceKm < 1.0) {
      return '${(distanceKm * 1000).round()}m away';
    }
    return '${distanceKm.toStringAsFixed(1)} km away';
  }
}
