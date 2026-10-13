import 'package:flutter/foundation.dart';
import 'package:intl/intl.dart';

String shopMoney(int cents) => NumberFormat.currency(
  locale: 'en_PH',
  symbol: '₱',
  decimalDigits: 2,
).format(cents / 100);

class ShopProduct {
  final String id, name, unit;
  final int priceCents, stock;
  const ShopProduct(this.id, this.name, this.unit, this.priceCents, this.stock);
}

const shopProducts = [
  ShopProduct('rice', 'Premium rice', '5 kg bag', 32000, 10),
  ShopProduct('eggs', 'Fresh eggs', '12 pack', 12000, 10),
  ShopProduct('milk', 'Fresh milk', '1 litre', 9500, 10),
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
  final String reference;
  final ShopQuote quote;
  ShopStage _stage = ShopStage.held;
  bool _paymentUnknown = false;
  String? _supportCase;
  ShopOrder._(this.reference, this.quote);
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
}

/// No banking repositories or network clients: sample money is isolated by design.
class ShopStore extends ChangeNotifier {
  // shortcut: session-only sample orders; replace with authenticated order APIs for backend integration.
  final Map<String, int> _cart = {};
  final List<ShopOrder> _orders = [];
  final Map<int, ShopQuote> _quotes = {};
  int _revision = 0, _nextQuote = 0;
  int _ledgerCents;

  ShopStore({int sampleBalanceCents = 1250000})
    : _ledgerCents = sampleBalanceCents {
    if (sampleBalanceCents < 0) throw ArgumentError.value(sampleBalanceCents);
  }

  List<ShopOrder> get orders => List.unmodifiable(_orders.reversed);
  List<ShopLine> get cart => [
    for (final p in shopProducts)
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
    final product = shopProducts.firstWhere((p) => p.id == id);
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

  ShopOrder placeOrder(ShopQuote quote, {bool loseReply = false}) {
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
    order._stage = order.stage == ShopStage.held
        ? ShopStage.preparing
        : ShopStage.ready;
    notifyListeners();
  }

  void cancel(ShopOrder order) {
    _owned(order);
    if (order.stage == ShopStage.cancelled) return;
    if (!order.canCancel) {
      throw StateError('Cancellation needs review after preparation begins.');
    }
    order._stage = ShopStage.cancelled;
    notifyListeners();
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
}
