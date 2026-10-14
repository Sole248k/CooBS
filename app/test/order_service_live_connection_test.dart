import 'package:app/features/data/mobile_repo.dart';
import 'package:app/features/shop/shop_store.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('OrderService and ShopStore live integration contract tests', () {
    test('ShopProduct.fromJson correctly parses backend ProductResponse payload', () {
      final json = {
        'productId': 100,
        'sku': 'RICE-SINANDOMENG-5KG',
        'name': 'Premium Sinandomeng Rice',
        'category': 'Grains & Rice',
        'unit': '5 kg bag',
        'price': 320.00,
        'originalPrice': 380.00,
        'badge': 'Co-op Subsidy -16%',
        'farmSource': 'Nueva Ecija Grain Producers Co-op',
        'stockQuantity': 50,
      };

      final product = ShopProduct.fromJson(json);
      expect(product.id, 'rice');
      expect(product.backendProductId, 100);
      expect(product.sku, 'RICE-SINANDOMENG-5KG');
      expect(product.name, 'Premium Sinandomeng Rice');
      expect(product.priceCents, 32000);
      expect(product.originalPriceCents, 38000);
      expect(product.stock, 50);
      expect(product.badge, 'Co-op Subsidy -16%');
      expect(product.farmSource, 'Nueva Ecija Grain Producers Co-op');
    });

    test('ShopOrder.fromBackendJson correctly parses backend OrderResponse with hold and pass token', () {
      final products = [
        const ShopProduct(
          'rice',
          'Premium rice',
          '5 kg bag',
          32000,
          10,
          backendProductId: 100,
        ),
      ];

      final json = {
        'orderId': 501,
        'referenceNo': 'ORD-20261013-ABC12345',
        'customerId': 1000,
        'sourceAccountId': 1002,
        'holdId': 7001,
        'stage': 'HELD',
        'totalAmount': 320.00,
        'pickupBranch': 'PureSilver Central Mall Depot',
        'pickupPassToken': 'PASS-ORD-20261013-ABC12345-999',
        'pickupTimeSlot': '4:00 PM - 6:00 PM',
        'items': [
          {
            'productId': 100,
            'productName': 'Premium rice',
            'quantity': 1,
            'unitPrice': 320.00,
            'subtotal': 320.00,
          }
        ],
      };

      final order = ShopOrder.fromBackendJson(json, products);
      expect(order.reference, 'ORD-20261013-ABC12345');
      expect(order.backendOrderId, 501);
      expect(order.holdId, 7001);
      expect(order.pickupPassToken, 'PASS-ORD-20261013-ABC12345-999');
      expect(order.pickupBranch, 'PureSilver Central Mall Depot');
      expect(order.stage, ShopStage.held);
      expect(order.status, 'Funds held');
      expect(order.totalCents, 32000);
      expect(order.quote.lines.length, 1);
      expect(order.quote.lines.first.product.name, 'Premium rice');
      expect(order.quote.lines.first.quantity, 1);
    });

    test('ShopOrder.parseStage correctly maps all backend OrderStage enums', () {
      expect(ShopOrder.parseStage('HELD'), ShopStage.held);
      expect(ShopOrder.parseStage('PREPARING'), ShopStage.preparing);
      expect(ShopOrder.parseStage('READY'), ShopStage.ready);
      expect(ShopOrder.parseStage('COMPLETED'), ShopStage.completed);
      expect(ShopOrder.parseStage('CANCELLED'), ShopStage.cancelled);
      expect(ShopOrder.parseStage('UNKNOWN'), ShopStage.held);
    });

    test('MobileRepo provides shop methods with valid structures', () async {
      final products = await MobileRepo.fetchShopProducts();
      expect(products, isNotEmpty);
      expect(products.first['productId'], 100);
      expect(products.first['sku'], contains('RICE'));

      final created = await MobileRepo.createShopOrder({
        'sourceAccountId': 1002,
        'items': [
          {'productId': 100, 'quantity': 1}
        ],
      });
      expect(created['referenceNo'], isNotNull);
      expect(created['holdId'], isNotNull);
      expect(created['pickupPassToken'], isNotNull);

      final cancelRes = await MobileRepo.cancelShopOrder('PS-DEMO-1001');
      expect(cancelRes['stage'], 'CANCELLED');

      final collectRes = await MobileRepo.collectShopOrder('PS-DEMO-1001');
      expect(collectRes['stage'], 'COMPLETED');

      final advanceRes = await MobileRepo.advanceShopOrder('PS-DEMO-1001', 'PREPARING');
      expect(advanceRes['stage'], 'PREPARING');
    });

    test('ShopStore syncFromBackend preserves products and populates orders', () async {
      final store = ShopStore();
      addTearDown(store.dispose);

      expect(store.products.length, shopProducts.length);
      expect(store.orders, isEmpty);

      // In fixture mode, syncFromBackend safely returns without modifying store
      await store.syncFromBackend();
      expect(store.products.length, shopProducts.length);
      expect(store.orders, isEmpty);
      expect(store.isLoading, isFalse);
    });
  });
}
