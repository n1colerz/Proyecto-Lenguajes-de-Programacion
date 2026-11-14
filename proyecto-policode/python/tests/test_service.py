import unittest

from src.model import Order, Product
from src.service import OrderService


def sample_order(**overrides):
    values = {
        "id": "T-1",
        "customer_name": "Test",
        "customer_type": "regular",
        "products": [Product("A", "Artículo", 100.00, 2)],
        "shipping_type": "estandar",
        "payment_method": "tarjeta_simulada",
        "status": "Recibido",
        "created_at": "2026-01-01T10:00:00",
    }
    values.update(overrides)
    return Order(**values)


class OrderServiceTests(unittest.TestCase):
    def test_subtotal_discount_and_tax(self):
        order = sample_order(customer_type="vip")
        self.assertEqual(OrderService.subtotal_cents(order), 20_000)
        self.assertEqual(OrderService.discount_cents(order), 2_000)
        self.assertEqual(OrderService.tax_cents(order), 2_880)

    def test_discount_threshold_and_shipping_types(self):
        threshold = sample_order(products=[Product("A", "Artículo", 1_000, 1)])
        self.assertEqual(OrderService.discount_cents(threshold), 5_000)
        self.assertEqual(OrderService.shipping_cents(sample_order(shipping_type="express")), 10_000)
        self.assertEqual(OrderService.shipping_cents(sample_order(shipping_type="retiro")), 0)

    def test_quantity_must_be_positive(self):
        with self.assertRaises(ValueError):
            Product.from_dict({"codigo": "A", "nombre": "Artículo", "precio": 1, "cantidad": 0})

    def test_order_must_contain_products(self):
        with self.assertRaises(ValueError):
            Order.from_dict({
                "id": "T-0", "cliente": {"nombre": "Test", "tipo": "regular"}, "productos": [],
                "tipo_envio": "estandar", "metodo_pago": "tarjeta_simulada",
                "estado": "Recibido", "fecha_creacion": "2026-01-01T10:00:00",
            })

    def test_status_change_and_filtering(self):
        order = sample_order()
        OrderService.update_status(order, "Enviado")
        self.assertEqual(OrderService.by_status([order], "Enviado"), [order])
        self.assertEqual(OrderService.by_customer([order], "test"), [order])
        with self.assertRaises(ValueError):
            OrderService.update_status(order, "Desconocido")

    def test_sorting_by_date_amount_and_priority(self):
        old = sample_order(id="old", created_at="2026-01-01T10:00:00")
        high = sample_order(
            id="high",
            products=[Product("A", "Artículo", 1_000, 1)],
            created_at="2026-01-02T10:00:00",
        )
        self.assertEqual(OrderService.sort_orders([high, old], "fecha")[0].id, "old")
        self.assertEqual(OrderService.sort_orders([old, high], "monto")[0].id, "old")
        self.assertEqual(OrderService.sort_orders([old, high], "prioridad")[0].id, "high")

    def test_response_time_and_empty_technician_queue(self):
        critical = sample_order(products=[Product("A", "Artículo", 1_000, 1)])
        self.assertEqual(OrderService.response_time_hours(critical), 2)
        self.assertEqual(OrderService.process_concurrently([critical], 0)["en_cola"], ["T-1"])
        self.assertEqual(
            OrderService.process_sequentially([critical])["procesados"],
            OrderService.process_concurrently([critical])["procesados"],
        )

    def test_order_can_accept_an_additional_product(self):
        order = sample_order()
        order.add_products([Product("B", "Otro", 50, 1), Product("C", "Tercero", 75, 2)])
        self.assertEqual(len(order.products), 3)


if __name__ == "__main__":
    unittest.main()
