import 'package:flutter/material.dart';

import '../../core/theme/theme_controller.dart';
import '../accounts/ui.dart';
import 'shop_store.dart';

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
          Text(
            'PURESILVER · SAMPLE SHOP',
            style: TextStyle(color: kNavyFg, fontWeight: FontWeight.w600),
          ),
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
        Container(
          margin: const EdgeInsets.only(bottom: 16),
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: kInBg,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Text(
            'SAMPLE ONLY · No bank charges or real orders. Shopping data resets when this banking session ends.',
            style: TextStyle(color: kInk, fontSize: 12),
          ),
        ),
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

  List<Widget> _catalogue() => [
    _ShopCard(
      children: [
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
      ],
    ),
    for (final product in shopProducts)
      Card(
        child: ListTile(
          contentPadding: const EdgeInsets.all(16),
          leading: Icon(_icon(product), size: 36, color: kNavyFg),
          title: Text(product.name),
          subtitle: Text('${product.unit}\n${shopMoney(product.priceCents)}'),
          isThreeLine: true,
          trailing: const Icon(Icons.chevron_right),
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

  List<Widget> _product(ShopProduct product) => [
    _ShopCard(
      children: [
        Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Icon(_icon(product), size: 96, color: kNavyFg),
          ),
        ),
        Text(
          product.name,
          style: TextStyle(
            fontSize: 24,
            fontWeight: FontWeight.w600,
            color: kInk,
          ),
        ),
        Text('${product.unit} · ${shopMoney(product.priceCents)}'),
        Text(
          'Sample stock: up to ${product.stock} per basket. Final retailer stock checks are not connected.',
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
    // The completed checkout must not remain in the back stack as a payable form.
    _history.clear();
    _go(_Page.order, order: order, replace: true);
  });

  List<Widget> _orders() => [
    if (store.orders.isEmpty)
      const _ShopCard(children: [Text('No sample orders yet.')]),
    for (final order in store.orders)
      Card(
        child: ListTile(
          title: Text(order.reference),
          subtitle: Text('${order.status}\n${shopMoney(order.totalCents)}'),
          isThreeLine: true,
          trailing: const Icon(Icons.chevron_right),
          onTap: () => _go(_Page.order, order: order),
        ),
      ),
    _action('Shop groceries', () => _go(_Page.shop), secondary: true),
  ];

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
              child: Text(
                '${order.stage != ShopStage.cancelled && stage.index <= order.stage.index ? '✓' : '○'} ${switch (stage) {
                  ShopStage.held => 'Order accepted · funds held',
                  ShopStage.preparing => 'Preparing basket',
                  ShopStage.ready => 'Ready for pickup',
                  ShopStage.completed => 'Collected · payment posted',
                  ShopStage.cancelled => 'Cancelled',
                }}',
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

IconData _icon(ShopProduct product) => switch (product.id) {
  'rice' => Icons.rice_bowl_outlined,
  'eggs' => Icons.egg_outlined,
  _ => Icons.local_drink_outlined,
};

// statRow's label cannot wrap, so it overflows at large text sizes.
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
              backgroundColor: kNavy,
              foregroundColor: Colors.white,
              minimumSize: const Size.fromHeight(52),
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
            ),
            child: Text(label, textAlign: TextAlign.center),
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
              padding: const EdgeInsets.symmetric(vertical: 5),
              child: child,
            ),
        ],
      ),
    ),
  );
}
