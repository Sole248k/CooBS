import 'package:flutter/foundation.dart';
import 'package:intl/intl.dart';

import '../../core/config/app_config.dart';
import '../accounts/demo_store.dart';
import '../data/mobile_repo.dart';

String shopMoney(int cents) => NumberFormat.currency(
  locale: 'en_PH',
  symbol: '₱',
  decimalDigits: 2,
).format(cents / 100);

class ShopProduct {
  final String id, name, unit;
  final int priceCents, stock;
  final int originalPriceCents;
  final String category;
  final String badge;
  final double rating;
  final String farmSource;
  final int? backendProductId;
  final String? sku;

  const ShopProduct(
    this.id,
    this.name,
    this.unit,
    this.priceCents,
    this.stock, {
    this.originalPriceCents = 0,
    this.category = 'Essentials',
    this.badge = 'Co-op Subsidy',
    this.rating = 4.9,
    this.farmSource = 'Local Producer Co-op',
    this.backendProductId,
    this.sku,
  });

  factory ShopProduct.fromJson(Map<String, dynamic> json) {
    final sku = json['sku']?.toString() ?? '';
    final name = json['name']?.toString() ?? 'Essential Item';
    final lowerName = name.toLowerCase();
    final lowerSku = sku.toLowerCase();

    // Map to canonical product ID so tests, fixtures and icons resolve seamlessly
    String id;
    if (json['id'] != null) {
      id = json['id'].toString();
    } else if (lowerSku.contains('rice') || lowerName.contains('rice')) {
      id = 'rice';
    } else if (lowerSku.contains('egg') || lowerName.contains('egg')) {
      id = 'eggs';
    } else if (lowerSku.contains('milk') || lowerName.contains('milk')) {
      id = 'milk';
    } else {
      id = json['productId']?.toString() ?? 'prod_${json['productId']}';
    }

    final priceNum = (json['price'] as num?)?.toDouble() ?? 0.0;
    final origPriceNum = (json['originalPrice'] as num?)?.toDouble() ?? 0.0;
    final priceCents = json['priceCents'] is num
        ? (json['priceCents'] as num).toInt()
        : (priceNum * 100).round();
    final originalPriceCents = json['originalPriceCents'] is num
        ? (json['originalPriceCents'] as num).toInt()
        : (origPriceNum * 100).round();

    final stock = (json['stockQuantity'] as num?)?.toInt() ??
        (json['stock'] as num?)?.toInt() ??
        10;

    return ShopProduct(
      id,
      name,
      json['unit']?.toString() ?? '1 pc',
      priceCents,
      stock,
      originalPriceCents: originalPriceCents,
      category: json['category']?.toString() ?? 'Essentials',
      badge: json['badge']?.toString() ?? 'Co-op Subsidy',
      rating: (json['rating'] as num?)?.toDouble() ?? 4.9,
      farmSource: json['farmSource']?.toString() ?? 'Local Producer Co-op',
      backendProductId: (json['productId'] as num?)?.toInt(),
      sku: sku.isNotEmpty ? sku : null,
    );
  }
}

const shopProducts = [
  ShopProduct(
    'rice',
    'Premium rice',
    '5 kg bag',
    32000,
    10,
    originalPriceCents: 38000,
    category: 'Grains & Rice',
    badge: 'Co-op Subsidy -16%',
    rating: 4.9,
    farmSource: 'Nueva Ecija Grain Producers Co-op',
    backendProductId: 100,
    sku: 'RICE-SINANDOMENG-5KG',
  ),
  ShopProduct(
    'eggs',
    'Fresh eggs',
    '12 pack',
    12000,
    10,
    originalPriceCents: 15000,
    category: 'Dairy & Poultry',
    badge: 'Fresh Harvest',
    rating: 4.8,
    farmSource: 'Batangas Poultry Federation',
    backendProductId: 101,
    sku: 'EGGS-BATANGAS-12PC',
  ),
  ShopProduct(
    'milk',
    'Fresh milk',
    '1 litre',
    9500,
    10,
    originalPriceCents: 12000,
    category: 'Dairy & Poultry',
    badge: 'Farm Direct',
    rating: 4.9,
    farmSource: 'Laguna Dairy Cooperative Union',
    backendProductId: 102,
    sku: 'MILK-LAGUNA-1L',
  ),
];

class ShopLine {
  final ShopProduct product;
  final int quantity;
  const ShopLine(this.product, this.quantity);
  int get totalCents => product.priceCents * quantity;
}

class ShopQuote {
  final int _revision, _id;
  final List<ShopLine> lines;
  ShopQuote._(this._revision, this._id, List<ShopLine> lines)
    : lines = List.unmodifiable(lines);
  int get totalCents => lines.fold(0, (sum, line) => sum + line.totalCents);
}

enum ShopStage { held, preparing, ready, completed, cancelled }

class ShopOrder {
  String _reference;
  final ShopQuote quote;
  ShopStage _stage = ShopStage.held;
  bool _paymentUnknown = false;
  String? _supportCase;
  int? backendOrderId;
  int? holdId;
  String? pickupPassToken;
  String? pickupBranch;
  DateTime? createdAt;

  ShopOrder._(
    this._reference,
    this.quote, {
    this.backendOrderId,
    this.holdId,
    this.pickupPassToken,
    this.pickupBranch,
    this.createdAt,
    ShopStage? stage,
  }) : _stage = stage ?? ShopStage.held;

  String get reference => _reference;
  ShopStage get stage => _stage;
  bool get paymentUnknown => _paymentUnknown;
  String? get supportCase => _supportCase;
  int get totalCents => quote.totalCents;
  bool get holdsFunds =>
      stage != ShopStage.completed && stage != ShopStage.cancelled;
  bool get canCancel =>
      stage == ShopStage.held && !_paymentUnknown && _supportCase == null;
  bool get canProgress =>
      holdsFunds && !_paymentUnknown && _supportCase == null;
  String get status => paymentUnknown
      ? 'Checking payment'
      : switch (stage) {
          ShopStage.held => 'Funds held',
          ShopStage.preparing => 'Preparing your basket',
          ShopStage.ready => 'Ready for pickup',
          ShopStage.completed => 'Collected & paid',
          ShopStage.cancelled => 'Cancelled · funds released',
        };

  static ShopStage parseStage(String? raw) {
    return switch (raw?.toUpperCase()) {
      'PREPARING' => ShopStage.preparing,
      'READY' => ShopStage.ready,
      'COMPLETED' => ShopStage.completed,
      'CANCELLED' => ShopStage.cancelled,
      _ => ShopStage.held,
    };
  }

  factory ShopOrder.fromBackendJson(
    Map<String, dynamic> json,
    List<ShopProduct> knownProducts,
  ) {
    final ref = json['referenceNo']?.toString() ?? 'ORD-${json['orderId']}';
    final stageStr = json['stage']?.toString();
    final stage = parseStage(stageStr);

    DateTime? createdAt;
    if (json['createdAt'] != null) {
      try {
        createdAt = DateTime.parse(json['createdAt'].toString());
      } catch (_) {}
    }

    final rawItems = json['items'] as List? ?? [];
    final lines = <ShopLine>[];
    for (final it in rawItems) {
      if (it is Map<String, dynamic>) {
        final pId = (it['productId'] as num?)?.toInt();
        final pName = it['productName']?.toString();
        final qty = (it['quantity'] as num?)?.toInt() ?? 1;
        final unitPrice = (it['unitPrice'] as num?)?.toDouble() ?? 0.0;
        final priceCents = (unitPrice * 100).round();

        final product = knownProducts.firstWhere(
          (p) => p.backendProductId == pId || p.name == pName,
          orElse: () => ShopProduct(
            pId?.toString() ?? 'item',
            pName ?? 'Item',
            '1 pc',
            priceCents,
            99,
            backendProductId: pId,
          ),
        );
        lines.add(ShopLine(product, qty));
      }
    }

    final quote = ShopQuote._(0, 0, lines);
    return ShopOrder._(
      ref,
      quote,
      backendOrderId: (json['orderId'] as num?)?.toInt(),
      holdId: (json['holdId'] as num?)?.toInt(),
      pickupPassToken: json['pickupPassToken']?.toString(),
      pickupBranch: json['pickupBranch']?.toString(),
      createdAt: createdAt,
      stage: stage,
    );
  }
}

/// Dual-mode ShopStore: maintains sample funds isolation for air-gapped demo
/// while bridging to live order-service and ledger two-phase escrow when AppConfig.useFixtures == false.
class ShopStore extends ChangeNotifier {
  final Map<String, int> _cart = {};
  final List<ShopOrder> _orders = [];
  final Map<int, ShopQuote> _quotes = {};
  List<ShopProduct> _products = List.of(shopProducts);
  int _revision = 0, _nextQuote = 0;
  int _ledgerCents;
  bool _isLoading = false;
  String? _errorMessage;

  ShopStore({int sampleBalanceCents = 1250000})
    : _ledgerCents = sampleBalanceCents {
    if (sampleBalanceCents < 0) throw ArgumentError.value(sampleBalanceCents);
  }

  bool get isLoading => _isLoading;
  String? get errorMessage => _errorMessage;
  List<ShopProduct> get products => List.unmodifiable(_products);
  List<ShopOrder> get orders => List.unmodifiable(_orders.reversed);
  List<ShopLine> get cart => [
    for (final p in _products)
      if (quantity(p.id) > 0) ShopLine(p, quantity(p.id)),
  ];

  int quantity(String id) => _cart[id] ?? 0;
  int get cartCount => _cart.values.fold(0, (a, b) => a + b);
  int get cartTotalCents => cart.fold(0, (sum, line) => sum + line.totalCents);
  int get ledgerCents => _ledgerCents;
  int get availableCents =>
      _ledgerCents -
      _orders
          .where((o) => o.holdsFunds)
          .fold(0, (sum, o) => sum + o.totalCents);

  void setQuantity(String id, int quantity) {
    final product = _products.firstWhere(
      (p) => p.id == id,
      orElse: () => shopProducts.firstWhere((p) => p.id == id),
    );
    if (quantity < 0 || quantity > product.stock) {
      throw StateError(
        'Choose between 0 and ${product.stock} for ${product.name}.',
      );
    }
    if (quantity == this.quantity(id)) return;
    if (quantity == 0) {
      _cart.remove(id);
    } else {
      _cart[id] = quantity;
    }
    _revision++;
    notifyListeners();
  }

  ShopQuote review() {
    if (_cart.isEmpty) throw StateError('Add an item to your basket first.');
    final quote = ShopQuote._(_revision, ++_nextQuote, cart);
    _quotes[quote._id] = quote;
    return quote;
  }

  ShopOrder placeOrder(
    ShopQuote quote, {
    bool loseReply = false,
    int? sourceAccountId,
  }) {
    if (!identical(_quotes[quote._id], quote)) {
      throw StateError('Review this basket again.');
    }
    for (final order in _orders) {
      if (identical(order.quote, quote)) return order;
    }
    if (quote._revision != _revision || _cart.isEmpty) {
      throw StateError(
        'Your basket changed. Review it again before reserving.',
      );
    }
    if (quote.totalCents > availableCents) {
      throw StateError(
        'Not enough sample funds. Available: ${shopMoney(availableCents)}.',
      );
    }
    final order = ShopOrder._('PS-DEMO-${1001 + _orders.length}', quote);
    order._paymentUnknown = loseReply;
    _orders.add(order);
    _cart.clear();
    _revision++;
    notifyListeners();

    // In live mode, asynchronously propagate order creation and ledger escrow hold
    if (!AppConfig.useFixtures && !loseReply) {
      _syncCreateLiveOrder(order, sourceAccountId: sourceAccountId);
    }

    return order;
  }

  void _owned(ShopOrder order) {
    if (!_orders.contains(order)) throw StateError('Sample order not found.');
  }

  void advance(ShopOrder order) {
    _owned(order);
    if (!order.canProgress || order.stage == ShopStage.ready) {
      throw StateError(
        'This order cannot progress. Check its status or support case.',
      );
    }
    final nextStage = order.stage == ShopStage.held
        ? ShopStage.preparing
        : ShopStage.ready;
    order._stage = nextStage;
    notifyListeners();

    if (!AppConfig.useFixtures) {
      final stageStr = nextStage == ShopStage.preparing ? 'PREPARING' : 'READY';
      MobileRepo.advanceShopOrder(order.reference, stageStr).catchError((_) => <String, dynamic>{});
    }
  }

  void cancel(ShopOrder order) {
    _owned(order);
    if (order.stage == ShopStage.cancelled) return;
    if (!order.canCancel) {
      throw StateError('Cancellation needs review after preparation begins.');
    }
    order._stage = ShopStage.cancelled;
    notifyListeners();

    if (!AppConfig.useFixtures) {
      MobileRepo.cancelShopOrder(order.reference).catchError((_) => <String, dynamic>{});
    }
  }

  void collect(ShopOrder order, {bool loseReply = false}) {
    _owned(order);
    if (order.stage == ShopStage.completed) return;
    if (!order.canProgress || order.stage != ShopStage.ready) {
      throw StateError('This order is not ready for collection.');
    }
    _ledgerCents -= order.totalCents;
    order._stage = ShopStage.completed;
    order._paymentUnknown = loseReply;
    notifyListeners();

    if (!AppConfig.useFixtures && !loseReply) {
      MobileRepo.collectShopOrder(order.reference).catchError((_) => <String, dynamic>{});
    }
  }

  void reconcile(ShopOrder order) {
    _owned(order);
    order._paymentUnknown = false;
    notifyListeners();
  }

  void requestHelp(ShopOrder order) {
    _owned(order);
    order._supportCase ??= 'CASE-${order.reference}';
    notifyListeners();
  }

  /// Syncs products and orders from the live order-service backend.
  Future<void> syncFromBackend() async {
    if (AppConfig.useFixtures) return;
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      // 1. Fetch live products from order-service
      final productList = await MobileRepo.fetchShopProducts();
      if (productList.isNotEmpty) {
        final parsed = productList
            .whereType<Map<String, dynamic>>()
            .map((j) => ShopProduct.fromJson(j))
            .toList();
        if (parsed.isNotEmpty) {
          _products = parsed;
        }
      }

      // 2. Fetch live orders from order-service
      final orderList = await MobileRepo.fetchShopOrders();
      if (orderList.isNotEmpty) {
        for (final raw in orderList) {
          if (raw is Map<String, dynamic>) {
            final ref = raw['referenceNo']?.toString();
            if (ref != null && !_orders.any((o) => o.reference == ref)) {
              final o = ShopOrder.fromBackendJson(raw, _products);
              _orders.add(o);
            }
          }
        }
      }
    } catch (e) {
      _errorMessage = e.toString();
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> _syncCreateLiveOrder(
    ShopOrder order, {
    int? sourceAccountId,
  }) async {
    try {
      final items = order.quote.lines.map((l) {
        final pId = l.product.backendProductId ?? switch (l.product.id) {
          'rice' => 100,
          'eggs' => 101,
          'milk' => 102,
          _ => int.tryParse(l.product.id) ?? 100,
        };
        return {
          'productId': pId,
          'quantity': l.quantity,
        };
      }).toList();

      final resolvedAccount = sourceAccountId ?? _resolveSourceAccountId();

      final res = await MobileRepo.createShopOrder({
        'sourceAccountId': resolvedAccount,
        'items': items,
        'pickupBranch': 'PureSilver Central Mall Depot',
        'pickupTimeSlot': '4:00 PM - 6:00 PM',
      });

      if (res['referenceNo'] != null) {
        order._reference = res['referenceNo'].toString();
      }
      if (res['orderId'] != null) {
        order.backendOrderId = (res['orderId'] as num).toInt();
      }
      if (res['holdId'] != null) {
        order.holdId = (res['holdId'] as num).toInt();
      }
      if (res['pickupPassToken'] != null) {
        order.pickupPassToken = res['pickupPassToken'].toString();
      }
      notifyListeners();
    } catch (_) {
      // In case of network error, order remains locally with its sample reference
    }
  }

  int _resolveSourceAccountId() {
    try {
      if (Demo.i.accts.isNotEmpty) {
        final acct = Demo.i.accts.firstWhere(
          (a) => a.type.toLowerCase().contains('saving'),
          orElse: () => Demo.i.accts.first,
        );
        final digits = acct.no.replaceAll(RegExp(r'[^0-9]'), '');
        final id = int.tryParse(digits);
        if (id != null && id > 0) return id;
      }
    } catch (_) {}
    return 1002;
  }
}

