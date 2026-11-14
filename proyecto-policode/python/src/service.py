from concurrent.futures import ThreadPoolExecutor
from decimal import Decimal, ROUND_HALF_UP
from typing import Callable

from .model import ORDER_STATES, Order

TAX_RATE = 0.16
PRIORITY_THRESHOLDS = {"alta": 100_000, "media": 50_000}
RESPONSE_HOURS = {"alta": 2, "media": 8, "baja": 24}
SHIPPING_CENTS = {"estandar": 5_000, "express": 10_000, "retiro": 0}


def _cents(amount: float) -> int:
    return int((Decimal(str(amount)) * 100).quantize(Decimal("1"), rounding=ROUND_HALF_UP))


def _money(cents: int) -> float:
    return cents / 100


class OrderService:
    @staticmethod
    def subtotal_cents(order: Order) -> int:
        return sum(_cents(product.price) * product.quantity for product in order.products)

    @staticmethod
    def discount_cents(order: Order) -> int:
        subtotal = OrderService.subtotal_cents(order)
        percentage = 10 if order.customer_type == "vip" else (5 if subtotal >= 100_000 else 0)
        return (subtotal * percentage + 50) // 100

    @staticmethod
    def tax_cents(order: Order) -> int:
        taxable = OrderService.subtotal_cents(order) - OrderService.discount_cents(order)
        return (taxable * 16 + 50) // 100

    @staticmethod
    def shipping_cents(order: Order) -> int:
        return SHIPPING_CENTS[order.shipping_type]

    @staticmethod
    def total_cents(order: Order) -> int:
        return (
            OrderService.subtotal_cents(order)
            - OrderService.discount_cents(order)
            + OrderService.tax_cents(order)
            + OrderService.shipping_cents(order)
        )

    @staticmethod
    def priority(order: Order) -> str:
        subtotal = OrderService.subtotal_cents(order)
        if subtotal >= PRIORITY_THRESHOLDS["alta"]:
            return "alta"
        if subtotal >= PRIORITY_THRESHOLDS["media"]:
            return "media"
        return "baja"

    @staticmethod
    def response_time_hours(order: Order) -> int:
        return RESPONSE_HOURS[OrderService.priority(order)]

    @staticmethod
    def order_summary(order: Order) -> dict:
        return {
            "id": order.id,
            "subtotal": _money(OrderService.subtotal_cents(order)),
            "descuento": _money(OrderService.discount_cents(order)),
            "impuesto": _money(OrderService.tax_cents(order)),
            "envio": _money(OrderService.shipping_cents(order)),
            "total": _money(OrderService.total_cents(order)),
            "prioridad": OrderService.priority(order),
            "tiempo_respuesta_horas": OrderService.response_time_hours(order),
        }

    @staticmethod
    def update_status(order: Order, status: str) -> None:
        if status not in ORDER_STATES:
            raise ValueError(f"Estado inválido: {status}")
        order.status = status

    @staticmethod
    def by_customer(orders: list[Order], name: str) -> list[Order]:
        return [order for order in orders if order.customer_name.casefold() == name.casefold()]

    @staticmethod
    def by_status(orders: list[Order], status: str) -> list[Order]:
        if status not in ORDER_STATES:
            raise ValueError(f"Estado inválido: {status}")
        return [order for order in orders if order.status == status]

    @staticmethod
    def sort_orders(orders: list[Order], criterion: str) -> list[Order]:
        keys: dict[str, Callable[[Order], object]] = {
            "fecha": lambda order: order.created_at,
            "monto": lambda order: OrderService.total_cents(order),
            "prioridad": lambda order: (
                {"alta": 0, "media": 1, "baja": 2}[OrderService.priority(order)],
                order.created_at,
            ),
        }
        if criterion not in keys:
            raise ValueError("El criterio debe ser fecha, monto o prioridad.")
        return sorted(orders, key=keys[criterion])

    @staticmethod
    def sales_summary(orders: list[Order]) -> dict:
        included = [order for order in orders if order.status != "Cancelado"]
        statuses = ("Recibido", "En preparación", "Enviado", "Entregado", "Cancelado")
        priorities = ("alta", "media", "baja")
        return {
            "cantidad_pedidos": len(orders),
            "subtotal": _money(sum(OrderService.subtotal_cents(order) for order in included)),
            "descuentos": _money(sum(OrderService.discount_cents(order) for order in included)),
            "impuestos": _money(sum(OrderService.tax_cents(order) for order in included)),
            "envios": _money(sum(OrderService.shipping_cents(order) for order in included)),
            "ventas_total": _money(sum(OrderService.total_cents(order) for order in included)),
            "por_estado": {status: sum(order.status == status for order in orders) for status in statuses},
            "por_prioridad": {
                priority: sum(OrderService.priority(order) == priority for order in orders)
                for priority in priorities
            },
        }

    @staticmethod
    def process_concurrently(orders: list[Order], technicians: int = 4) -> dict:
        if technicians < 0:
            raise ValueError("La cantidad de técnicos no puede ser negativa.")
        ordered = OrderService.sort_orders(orders, "prioridad")
        if technicians == 0:
            return {"procesados": [], "en_cola": [order.id for order in ordered]}
        with ThreadPoolExecutor(max_workers=technicians) as executor:
            summaries = list(executor.map(OrderService.order_summary, ordered))
        return {"procesados": summaries, "en_cola": []}

    @staticmethod
    def process_sequentially(orders: list[Order], technicians: int = 4) -> dict:
        if technicians < 0:
            raise ValueError("La cantidad de técnicos no puede ser negativa.")
        ordered = OrderService.sort_orders(orders, "prioridad")
        if technicians == 0:
            return {"procesados": [], "en_cola": [order.id for order in ordered]}
        return {
            "procesados": [OrderService.order_summary(order) for order in ordered],
            "en_cola": [],
        }
