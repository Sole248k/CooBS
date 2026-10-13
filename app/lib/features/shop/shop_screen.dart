import 'package:flutter/material.dart';

import '../../core/config/app_config.dart';
import '../../core/theme/theme_controller.dart';
import '../accounts/ui.dart';
import 'shop_store.dart';

/// Promotional Home Screen Entry Card for CooBS PureSilver Essentials.
class PureSilverEntry extends StatelessWidget {
  final ShopStore store;
  const PureSilverEntry({super.key, required this.store});

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: store,
    builder: (context, _) => Padding(
      padding: const EdgeInsets.only(top: 16),
      child: _ShopCard(
        children: [
          Wrap(
            alignment: WrapAlignment.spaceBetween,
            crossAxisAlignment: WrapCrossAlignment.center,
            spacing: 8,
            runSpacing: 4,
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: const Color(0xFF10B981).withAlpha(30),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Text(
                  'PURESILVER · SAMPLE SHOP',
                  style: TextStyle(
                    color: Color(0xFF047857),
                    fontWeight: FontWeight.w700,
                    fontSize: 11,
                    letterSpacing: 0.5,
                  ),
                ),
              ),
              if (store.cartCount > 0)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: const Color(0xFFEF4444),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Text(
                    '${store.cartCount} in basket',
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 11,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            'Groceries, sorted.',
            style: TextStyle(
              fontSize: 24,
              fontWeight: FontWeight.w600,
              color: kInk,
            ),
          ),
          const Text(
            'Explore grocery pickup with sample money. Your bank account is never charged.',
          ),
          const SizedBox(height: 6),
          Wrap(
            spacing: 6,
            runSpacing: 4,
            children: [
              _featureTag(Icons.verified_outlined, 'Co-op Partner'),
              _featureTag(Icons.local_shipping_outlined, 'Branch Pickup'),
              _featureTag(Icons.percent_rounded, 'Co-op Subsidy'),
            ],
          ),
          const SizedBox(height: 8),
          _action('Shop PureSilver', () => _open(context, false)),
          if (store.orders.isNotEmpty)
            _action(
              'My sample orders (${store.orders.length})',
              () => _open(context, true),
              secondary: true,
            ),
        ],
      ),
    ),
  );

  static Widget _featureTag(IconData icon, String text) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2.5),
    decoration: BoxDecoration(
      color: kInBg,
      borderRadius: BorderRadius.circular(8),
      border: Border.all(color: kLine.withAlpha(80)),
    ),
    child: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 11, color: const Color(0xFF047857)),
        const SizedBox(width: 4),
        Flexible(
          child: Text(
            text,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: 10,
              fontWeight: FontWeight.w600,
              color: kInk,
            ),
          ),
        ),
      ],
    ),
  );

  void _open(BuildContext context, bool orders) => Navigator.of(context).push(
    MaterialPageRoute(
      builder: (_) => PureSilverScreen(store: store, showOrders: orders),
    ),
  );
}

enum _Page { shop, product, cart, review, orders, order, confirm, help }

typedef _View = ({
  _Page page,
  ShopProduct? product,
  ShopQuote? quote,
  ShopOrder? order,
});

class PureSilverScreen extends StatefulWidget {
  final ShopStore store;
  final bool showOrders;
  const PureSilverScreen({
    super.key,
    required this.store,
    this.showOrders = false,
  });
  @override
  State<PureSilverScreen> createState() => _PureSilverScreenState();
}

class _PureSilverScreenState extends State<PureSilverScreen> {
  final List<_View> _history = [];
  late _View _view;
  String _selectedCategory = 'All';
  ShopStore get store => widget.store;

  @override
  void initState() {
    super.initState();
    _view = (
      page: widget.showOrders ? _Page.orders : _Page.shop,
      product: null,
      quote: null,
      order: null,
    );
    if (!AppConfig.useFixtures) {
      store.syncFromBackend();
    }
  }

  void _go(
    _Page page, {
    ShopProduct? product,
    ShopQuote? quote,
    ShopOrder? order,
    bool replace = false,
  }) {
    setState(() {
      if (!replace) _history.add(_view);
      _view = (page: page, product: product, quote: quote, order: order);
    });
  }

  void _back() {
    if (_history.isEmpty) {
      Navigator.pop(context);
    } else {
      setState(() => _view = _history.removeLast());
    }
  }

  void _run(VoidCallback action) {
    try {
      action();
    } on StateError catch (error) {
      snack(context, error.message);
    }
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: Listenable.merge([store, ThemeController.i]),
    builder: (context, _) => PopScope(
      canPop: _history.isEmpty,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _back();
      },
      child: Pg(_title, [
        ..._content(),
      ], onBack: _back),
    ),
  );

  String get _title => switch (_view.page) {
    _Page.shop => 'PureSilver',
    _Page.product => 'Product details',
    _Page.cart => 'Your basket',
    _Page.review => 'Review & reserve',
    _Page.orders => 'Sample orders',
    _Page.order => 'Order details',
    _Page.confirm => 'Confirm collection',
    _Page.help => 'Order help',
  };

  List<Widget> _content() => switch (_view.page) {
    _Page.shop => _catalogue(),
    _Page.product => _product(_view.product!),
    _Page.cart => _cart(),
    _Page.review => _review(_view.quote!),
    _Page.orders => _orders(),
    _Page.order => _order(_view.order!),
    _Page.confirm => _confirm(_view.order!),
    _Page.help => _help(_view.order!),
  };

  static const _categories = [
    'All',
    'Grains & Rice',
    'Dairy & Eggs',
    'Fresh Produce',
    'Pantry & Cooking',
    'Bakery & Beverages',
  ];

  // --------------------------------------------------------------------------
  // MODERN E-COMMERCE GROCERY CATALOGUE
  // --------------------------------------------------------------------------
  List<Widget> _catalogue() {
    final filtered = _selectedCategory == 'All'
        ? store.products
        : store.products.where((p) => p.category == _selectedCategory).toList();

    return [
      _ShopCard(
        children: [
          Align(
            alignment: Alignment.centerLeft,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: const Color(0xFF10B981).withAlpha(30),
                borderRadius: BorderRadius.circular(8),
              ),
              child: const Text(
                'COOPERATIVE ESSENTIALS DEPOT · ZERO MARKUP',
                style: TextStyle(
                  color: Color(0xFF047857),
                  fontSize: 10.5,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 0.5,
                ),
              ),
            ),
          ),
          const SizedBox(height: 4),
          Text(
            'Everyday essentials.',
            style: TextStyle(
              fontSize: 26,
              fontWeight: FontWeight.w600,
              color: kInk,
            ),
          ),
          const Text(
            'Bank-partner shopping concept. Collect at PureSilver Central Mall.',
          ),
          const Text('Sample branch · 4:00–6:00 PM pickup · No substitutions'),
          const SizedBox(height: 4),
          Wrap(
            spacing: 6,
            runSpacing: 4,
            children: [
              PureSilverEntry._featureTag(Icons.verified_outlined, 'Co-op Partner'),
              PureSilverEntry._featureTag(Icons.storefront_outlined, 'Branch Pickup'),
              PureSilverEntry._featureTag(Icons.percent_rounded, 'Co-op Subsidized'),
            ],
          ),
        ],
      ),
      Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Wrap(
          spacing: 6,
          runSpacing: 6,
          children: [
            for (final cat in _categories)
              FilterChip(
                selected: _selectedCategory == cat,
                showCheckmark: false,
                label: Text(
                  cat,
                  style: TextStyle(
                    fontSize: 11.5,
                    fontWeight: _selectedCategory == cat ? FontWeight.bold : FontWeight.w500,
                    color: _selectedCategory == cat
                        ? Colors.white
                        : (kDark ? Colors.white70 : const Color(0xFF1F2937)),
                  ),
                ),
                backgroundColor: kDark ? const Color(0xFF1E293B) : const Color(0xFFF3F4F6),
                selectedColor: const Color(0xFF047857),
                padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(20),
                  side: BorderSide(
                    color: _selectedCategory == cat
                        ? const Color(0xFF047857)
                        : (kDark ? Colors.white12 : const Color(0xFFE5E7EB)),
                  ),
                ),
                onSelected: (selected) {
                  if (selected) {
                    setState(() => _selectedCategory = cat);
                  }
                },
              ),
          ],
        ),
      ),
      if (store.isLoading)
        const Padding(
          padding: EdgeInsets.symmetric(vertical: 8),
          child: Center(
            child: SizedBox(
              height: 20,
              width: 20,
              child: CircularProgressIndicator(strokeWidth: 2, color: Color(0xFF047857)),
            ),
          ),
        ),
      for (final product in filtered)
        Card(
          elevation: 0,
          margin: const EdgeInsets.symmetric(vertical: 4),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
            side: BorderSide(
              color: store.quantity(product.id) > 0 ? const Color(0xFF047857) : kLine,
              width: store.quantity(product.id) > 0 ? 1.5 : 1.0,
            ),
          ),
          color: kSurface,
          child: ListTile(
            contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
            leading: ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: Container(
                height: 52,
                width: 52,
                color: kDark ? const Color(0xFF1E293B) : const Color(0xFFE8F5E9),
                child: product.imageUrl != null
                    ? Image.network(
                        product.imageUrl!,
                        fit: BoxFit.cover,
                        errorBuilder: (_, __, ___) => Icon(
                          _icon(product),
                          size: 28,
                          color: const Color(0xFF047857),
                        ),
                      )
                    : Icon(_icon(product), size: 28, color: const Color(0xFF047857)),
              ),
            ),
            title: Text(
              product.name,
              style: const TextStyle(fontWeight: FontWeight.w600),
            ),
            subtitle: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('${product.unit} · ${product.badge}'),
                const SizedBox(height: 2),
                Wrap(
                  spacing: 6,
                  runSpacing: 2,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Text(
                      shopMoney(product.priceCents),
                      style: const TextStyle(fontWeight: FontWeight.bold, color: Color(0xFF047857)),
                    ),
                    if (product.originalPriceCents > product.priceCents)
                      Text(
                        shopMoney(product.originalPriceCents),
                        style: TextStyle(
                          fontSize: 11.5,
                          color: kMut,
                          decoration: TextDecoration.lineThrough,
                        ),
                      ),
                  ],
                ),
              ],
            ),
            isThreeLine: true,
            trailing: store.quantity(product.id) > 0
                ? Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: const Color(0xFF047857),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Text(
                      '${store.quantity(product.id)}',
                      style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.bold),
                    ),
                  )
                : const Icon(Icons.chevron_right),
            onTap: () => _go(_Page.product, product: product),
          ),
        ),
      _action(
        'View basket (${store.cartCount}) · ${shopMoney(store.cartTotalCents)}',
        () => _go(_Page.cart),
      ),
      _action('My sample orders', () => _go(_Page.orders), secondary: true),
      _action('Back to banking', () => Navigator.pop(context), secondary: true),
    ];
  }

  // --------------------------------------------------------------------------
  // PRODUCT DETAILS
  // --------------------------------------------------------------------------
  List<Widget> _product(ShopProduct product) => [
    _ShopCard(
      children: [
        ClipRRect(
          borderRadius: BorderRadius.circular(18),
          child: Container(
            height: 180,
            width: double.infinity,
            color: kDark ? const Color(0xFF1E293B) : const Color(0xFFE8F5E9),
            child: product.imageUrl != null
                ? Image.network(
                    product.imageUrl!,
                    fit: BoxFit.cover,
                    errorBuilder: (_, __, ___) => Icon(
                      _icon(product),
                      size: 68,
                      color: const Color(0xFF047857),
                    ),
                  )
                : Icon(_icon(product), size: 68, color: const Color(0xFF047857)),
          ),
        ),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 4,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: const Color(0xFF10B981).withAlpha(25),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                product.badge,
                style: const TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFF047857),
                ),
              ),
            ),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: kInBg,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                product.category,
                style: TextStyle(
                  fontSize: 11,
                  color: kMut,
                ),
              ),
            ),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.star_rounded, size: 16, color: Color(0xFFF59E0B)),
                const SizedBox(width: 2),
                Text(
                  product.rating.toStringAsFixed(1),
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.bold,
                    color: kInk,
                  ),
                ),
              ],
            ),
          ],
        ),
        const SizedBox(height: 4),
        Text(
          product.name,
          style: TextStyle(
            fontSize: 22,
            fontWeight: FontWeight.w700,
            color: kInk,
          ),
        ),
        Wrap(
          spacing: 8,
          runSpacing: 2,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            Text(
              shopMoney(product.priceCents),
              style: const TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                color: Color(0xFF047857),
              ),
            ),
            if (product.originalPriceCents > product.priceCents)
              Text(
                shopMoney(product.originalPriceCents),
                style: TextStyle(
                  fontSize: 14,
                  color: kMut,
                  decoration: TextDecoration.lineThrough,
                ),
              ),
            Text('· ${product.unit}', style: TextStyle(color: kMut, fontSize: 13)),
          ],
        ),
        const SizedBox(height: 6),
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: kInBg,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '🌱 Farm Source: ${product.farmSource}',
                style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: kInk),
              ),
              const SizedBox(height: 2),
              Text(
                'Sample stock: up to ${product.stock} per basket. Co-op partner depot pickup.',
                style: TextStyle(fontSize: 11.5, color: kMut),
              ),
            ],
          ),
        ),
      ],
    ),
    _quantity(product),
    _action('View basket (${store.cartCount})', () => _go(_Page.cart)),
  ];

  Widget _quantity(ShopProduct product) => Row(
    children: [
      IconButton(
        tooltip: 'Remove one ${product.name}',
        onPressed: store.quantity(product.id) == 0
            ? null
            : () => _run(
                () => store.setQuantity(
                  product.id,
                  store.quantity(product.id) - 1,
                ),
              ),
        icon: const Icon(Icons.remove_circle_outline),
      ),
      Expanded(
        child: Semantics(
          liveRegion: true,
          child: Text(
            'Quantity: ${store.quantity(product.id)}',
            textAlign: TextAlign.center,
          ),
        ),
      ),
      IconButton(
        tooltip: 'Add one ${product.name}',
        onPressed: store.quantity(product.id) >= product.stock
            ? null
            : () => _run(
                () => store.setQuantity(
                  product.id,
                  store.quantity(product.id) + 1,
                ),
              ),
        icon: const Icon(Icons.add_circle_outline),
      ),
    ],
  );

  // --------------------------------------------------------------------------
  // BASKET / CART
  // --------------------------------------------------------------------------
  List<Widget> _cart() => [
    if (store.cart.isEmpty)
      const _ShopCard(
        children: [
          Text('Your basket is empty. Add some essentials to get started.'),
        ],
      ),
    for (final line in store.cart)
      _ShopCard(
        children: [
          _kv(line.product.name, shopMoney(line.totalCents)),
          Text(
            '${shopMoney(line.product.priceCents)} each · ${line.product.unit}',
          ),
          _quantity(line.product),
          TextButton(
            onPressed: () => store.setQuantity(line.product.id, 0),
            child: Text('Remove ${line.product.name}'),
          ),
        ],
      ),
    if (store.cart.isNotEmpty)
      _ShopCard(
        children: [
          Row(
            children: [
              const Icon(Icons.verified_user_outlined, size: 16, color: Color(0xFF10B981)),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  'Buyer Protection: Funds remain reserved in your account until you collect your basket.',
                  style: TextStyle(fontSize: 11.5, color: kInk),
                ),
              ),
            ],
          ),
          const Divider(height: 16),
          _kv('Pickup fee', shopMoney(0)),
          _kv('Total', shopMoney(store.cartTotalCents)),
        ],
      )
    else
      _ShopCard(
        children: [
          _kv('Pickup fee', shopMoney(0)),
          _kv('Total', shopMoney(store.cartTotalCents)),
        ],
      ),
    _action(
      'Continue to checkout',
      store.cart.isEmpty
          ? null
          : () => _run(() => _go(_Page.review, quote: store.review())),
    ),
  ];

  // --------------------------------------------------------------------------
  // REVIEW & RESERVE
  // --------------------------------------------------------------------------
  List<Widget> _review(ShopQuote quote) => [
    const _ShopCard(
      children: [
        Text('Pickup · PureSilver Central Mall'),
        Text('Sample slot: 4:00–6:00 PM. No real pickup is booked.'),
      ],
    ),
    _ShopCard(
      children: [
        for (final line in quote.lines)
          _kv(
            '${line.quantity} × ${line.product.name}',
            shopMoney(line.totalCents),
          ),
        _kv('Pickup fee', shopMoney(0)),
        _kv('Total to reserve', shopMoney(quote.totalCents)),
      ],
    ),
    _ShopCard(
      children: [
        const Text('Sample savings •••• 4821'),
        _kv('Available', shopMoney(store.availableCents)),
        if (store.availableCents >= quote.totalCents)
          _kv('After hold', shopMoney(store.availableCents - quote.totalCents))
        else
          Text(
            'Not enough sample funds. Reduce your basket or release another eligible sample hold.',
            style: TextStyle(color: kRed),
          ),
        const Text(
          'Reserve now; simulate collection to pay. Cancel only before preparation begins.',
        ),
      ],
    ),
    _action(
      'Reserve ${shopMoney(quote.totalCents)} · sample',
      () => _place(quote, false),
    ),
    _action(
      'Demo: lose reservation reply',
      () => _place(quote, true),
      secondary: true,
    ),
  ];

  void _place(ShopQuote quote, bool loseReply) => _run(() {
    final order = store.placeOrder(quote, loseReply: loseReply);
    _history.clear();
    _go(_Page.order, order: order, replace: true);
  });

  // --------------------------------------------------------------------------
  // SAMPLE ORDERS LIST
  // --------------------------------------------------------------------------
  List<Widget> _orders() => [
    if (store.orders.isEmpty)
      const _ShopCard(children: [Text('No sample orders yet.')]),
    for (final order in store.orders)
      Card(
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: BorderSide(color: kLine),
        ),
        child: ListTile(
          leading: Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: const Color(0xFF047857).withAlpha(25),
              borderRadius: BorderRadius.circular(10),
            ),
            child: const Icon(Icons.qr_code_rounded, color: Color(0xFF047857), size: 24),
          ),
          title: Text(order.reference),
          subtitle: Text('${order.status}\n${shopMoney(order.totalCents)}'),
          isThreeLine: true,
          trailing: const Icon(Icons.chevron_right),
          onTap: () => _go(_Page.order, order: order),
        ),
      ),
    _action('Shop groceries', () => _go(_Page.shop), secondary: true),
  ];

  // --------------------------------------------------------------------------
  // ORDER DETAILS & DIGITAL PICKUP VOUCHER
  // --------------------------------------------------------------------------
  List<Widget> _order(ShopOrder order) => [
    _ShopCard(
      children: [
        Text(
          order.reference,
          style: const TextStyle(fontWeight: FontWeight.w600),
        ),
        Semantics(
          liveRegion: true,
          child: Text(
            order.status,
            style: TextStyle(
              fontSize: 24,
              color: kInk,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
        Text('Order total: ${shopMoney(order.totalCents)}'),
        const SizedBox(height: 8),

        // Digital Pickup Pass Visual Box
        Center(
          child: Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: const Color(0xFFE2E8F0)),
            ),
            child: Column(
              children: [
                const Icon(Icons.qr_code_2_rounded, size: 84, color: Color(0xFF0F172A)),
                const SizedBox(height: 4),
                Text(
                  'PICKUP PASS: ${order.pickupPassToken ?? order.reference}',
                  style: const TextStyle(
                    fontFamily: 'monospace',
                    fontWeight: FontWeight.bold,
                    fontSize: 10,
                    color: Color(0xFF0F172A),
                    letterSpacing: 0.8,
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    ),
    if (order.paymentUnknown) ...[
      const _ShopCard(
        children: [
          Text(
            'The reply was lost. This is not a failed payment. Check the same order; do not pay again.',
          ),
        ],
      ),
      _action('Check sample payment status', () => store.reconcile(order)),
    ] else ...[
      _ShopCard(
        children: [
          Text(switch (order.stage) {
            ShopStage.completed =>
              'Sample payment captured once. PureSilver paid.',
            ShopStage.cancelled => 'Hold released. PureSilver was not paid.',
            _ =>
              'Money remains held in sample savings. PureSilver has not been paid.',
          }),
          const Divider(height: 14),
          if (order.holdId != null) ...[
            _kv('Authorization Hold', '#${order.holdId}'),
          ],
          _kv('Sample available balance', shopMoney(store.availableCents)),
          _kv('Sample ledger balance', shopMoney(store.ledgerCents)),
        ],
      ),
      _ShopCard(
        children: [
          for (final stage in ShopStage.values.where(
            (s) => s != ShopStage.cancelled,
          ))
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 6),
              child: Row(
                children: [
                  Icon(
                    order.stage != ShopStage.cancelled && stage.index <= order.stage.index
                        ? Icons.check_circle_rounded
                        : Icons.radio_button_unchecked,
                    size: 16,
                    color: order.stage != ShopStage.cancelled && stage.index <= order.stage.index
                        ? const Color(0xFF10B981)
                        : kMut,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      switch (stage) {
                        ShopStage.held => 'Order accepted · funds held',
                        ShopStage.preparing => 'Preparing basket',
                        ShopStage.ready => 'Ready for pickup',
                        ShopStage.completed => 'Collected · payment posted',
                        ShopStage.cancelled => 'Cancelled',
                      },
                      style: TextStyle(
                        fontWeight: order.stage == stage ? FontWeight.bold : FontWeight.normal,
                        color: order.stage == stage ? kInk : kMut,
                      ),
                    ),
                  ),
                ],
              ),
            ),
        ],
      ),
      if (order.stage == ShopStage.ready)
        const _ShopCard(
          children: [
            Text('PureSilver Central Mall · 4:00–6:00 PM'),
            Text(
              'DEMO pickup reference only. No real order can be collected. The store must verify collection in a live integration.',
            ),
          ],
        ),
      if (order.canProgress && order.stage != ShopStage.ready)
        _action(
          order.stage == ShopStage.held
              ? 'Demo: start preparation'
              : 'Demo: mark ready for pickup',
          () => _run(() => store.advance(order)),
        ),
      if (order.canProgress && order.stage == ShopStage.ready)
        _action(
          'Demo: verify collection',
          () => _go(_Page.confirm, order: order),
        ),
      if (order.canCancel)
        _action(
          'Cancel sample order',
          () => _run(() => store.cancel(order)),
          secondary: true,
        ),
      if (order.stage == ShopStage.completed)
        _ShopCard(
          children: [
            const Text('Sample receipt · No rewards or cashback promised.'),
            for (final line in order.quote.lines)
              _kv(
                '${line.quantity} × ${line.product.name}',
                shopMoney(line.totalCents),
              ),
          ],
        ),
    ],
    if (order.supportCase != null)
      _ShopCard(
        children: [
          Text('Sample case: ${order.supportCase}'),
          const Text(
            'Under review in this demo. No staff were contacted. Unpaid orders cannot progress or cancel while under review.',
          ),
        ],
      ),
    _action(
      'Get order help',
      () => _go(_Page.help, order: order),
      secondary: true,
    ),
    _action('My sample orders', () => _go(_Page.orders), secondary: true),
    _action('Back to banking', () => Navigator.pop(context), secondary: true),
  ];

  List<Widget> _confirm(ShopOrder order) => [
    _ShopCard(
      children: [
        Text(order.reference),
        Text(
          'Pay ${shopMoney(order.totalCents)} from the existing sample hold.',
        ),
        const Text(
          'In the real flow, the store verifies collection and you check the goods. Here, this only completes a simulated purchase.',
        ),
      ],
    ),
    _action('Confirm sample collection & pay', () => _collect(order, false)),
    _action(
      'Demo: lose payment reply',
      () => _collect(order, true),
      secondary: true,
    ),
    _action('Not yet', _back, secondary: true),
  ];

  void _collect(ShopOrder order, bool loseReply) => _run(() {
    store.collect(order, loseReply: loseReply);
    _back();
  });

  List<Widget> _help(ShopOrder order) => [
    const _ShopCard(
      children: [
        Text(
          'PureSilver: products, pickup and cancellation after preparation.',
        ),
        Text('CooBS: holds, debits and payment status.'),
        Text(
          'This prototype cannot contact either support team or issue refunds. A completed payment needs a separate refund process.',
        ),
      ],
    ),
    if (order.supportCase != null) Text('Sample case: ${order.supportCase}'),
    _action(
      'Simulate support case',
      () => _run(() {
        store.requestHelp(order);
        _back();
      }),
    ),
  ];
}

IconData _icon(ShopProduct product) {
  final id = product.id.toLowerCase();
  final name = product.name.toLowerCase();
  if (id == 'rice' || name.contains('rice')) {
    return Icons.rice_bowl_outlined;
  }
  if (id == 'eggs' || name.contains('egg')) {
    return Icons.egg_outlined;
  }
  if (id == 'milk' || name.contains('milk')) {
    return Icons.local_drink_outlined;
  }
  return Icons.shopping_basket_outlined;
}

Widget _kv(String k, String v) => Row(
  crossAxisAlignment: CrossAxisAlignment.start,
  children: [
    Expanded(
      child: Text(k, style: TextStyle(color: kMut)),
    ),
    const SizedBox(width: 12),
    Text(
      v,
      style: TextStyle(fontWeight: FontWeight.w600, color: kInk),
    ),
  ],
);

Widget _action(
  String label,
  VoidCallback? onPressed, {
  bool secondary = false,
}) => Padding(
  padding: const EdgeInsets.only(top: 8, bottom: 4),
  child: SizedBox(
    width: double.infinity,
    child: secondary
        ? OutlinedButton(
            onPressed: onPressed,
            child: Text(label, textAlign: TextAlign.center),
          )
        : FilledButton(
            onPressed: onPressed,
            style: FilledButton.styleFrom(
              backgroundColor: const Color(0xFF047857),
              foregroundColor: Colors.white,
              minimumSize: const Size.fromHeight(52),
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
            ),
            child: Text(
              label,
              textAlign: TextAlign.center,
              style: const TextStyle(fontWeight: FontWeight.bold),
            ),
          ),
  ),
);

class _ShopCard extends StatelessWidget {
  final List<Widget> children;
  const _ShopCard({required this.children});
  @override
  Widget build(BuildContext context) => Card(
    color: kSurface,
    elevation: 0,
    shape: RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(24),
      side: BorderSide(color: kLine),
    ),
    child: Padding(
      padding: const EdgeInsets.all(18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (final child in children)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 4),
              child: child,
            ),
        ],
      ),
    ),
  );
}
