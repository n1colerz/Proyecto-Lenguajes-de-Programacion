from dataclasses import dataclass, field
from datetime import datetime
import math
from typing import Any


ORDER_STATES = ("Recibido", "En preparación", "Enviado", "Entregado", "Cancelado")
SHIPPING_TYPES = ("estandar", "express", "retiro")
PAYMENT_METHODS = ("tarjeta_simulada", "efectivo_simulado", "transferencia_simulada")
CUSTOMER_TYPES = ("regular", "vip")


@dataclass
class Product:
    code: str
    name: str
    price: float
    quantity: int

    def __post_init__(self) -> None:
        if not isinstance(self.code, str) or not isinstance(self.name, str):
            raise ValueError("El código y nombre del producto deben ser texto.")
        if not self.code.strip() or not self.name.strip():
            raise ValueError("El código y nombre del producto son obligatorios.")
        if isinstance(self.price, bool) or not isinstance(self.price, (int, float)):
            raise ValueError("El precio debe ser numérico.")
        if not math.isfinite(self.price) or self.price < 0:
            raise ValueError("El precio debe ser un número finito no negativo.")
        if isinstance(self.quantity, bool) or not isinstance(self.quantity, int) or self.quantity <= 0:
            raise ValueError("La cantidad solicitada debe ser un entero mayor que cero.")

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "Product":
        try:
            if data["codigo"] is None or data["nombre"] is None:
                raise TypeError("El código y nombre no pueden ser nulos.")
            if not isinstance(data["precio"], (int, float)) or isinstance(data["precio"], bool):
                raise TypeError("El precio debe ser numérico.")
            if not isinstance(data["cantidad"], (int, float)) or isinstance(data["cantidad"], bool):
                raise TypeError("La cantidad debe ser numérica.")
            code, name = str(data["codigo"]).strip(), str(data["nombre"]).strip()
            price = float(data["precio"])
            raw_quantity = data["cantidad"]
            quantity = int(raw_quantity)
        except (KeyError, TypeError, ValueError) as error:
            raise ValueError("Producto inválido: se requieren código, nombre, precio y cantidad.") from error
        if isinstance(raw_quantity, bool) or float(raw_quantity) != quantity:
            raise ValueError("La cantidad solicitada debe ser un número entero.")
        return cls(code, name, price, quantity)

    def to_dict(self) -> dict[str, Any]:
        return {"codigo": self.code, "nombre": self.name, "precio": self.price, "cantidad": self.quantity}


@dataclass
class Order:
    id: str
    customer_name: str
    customer_type: str
    products: list[Product] = field(default_factory=list)
    shipping_type: str = "estandar"
    payment_method: str = "tarjeta_simulada"
    status: str = "Recibido"
    created_at: str = field(default_factory=lambda: datetime.now().isoformat(timespec="seconds"))

    def __post_init__(self) -> None:
        if not self.id.strip() or not self.customer_name.strip():
            raise ValueError("El pedido requiere un identificador y nombre de cliente.")
        if self.customer_type not in CUSTOMER_TYPES:
            raise ValueError(f"Tipo de cliente inválido: {self.customer_type}")
        if self.shipping_type not in SHIPPING_TYPES:
            raise ValueError(f"Tipo de envío inválido: {self.shipping_type}")
        if self.payment_method not in PAYMENT_METHODS:
            raise ValueError(f"Método de pago inválido: {self.payment_method}")
        if self.status not in ORDER_STATES:
            raise ValueError(f"Estado inválido: {self.status}")
        if not self.products:
            raise ValueError("Un pedido debe contener al menos un producto.")
        if any(not isinstance(product, Product) for product in self.products):
            raise ValueError("Todos los productos del pedido deben ser válidos.")
        try:
            datetime.fromisoformat(self.created_at)
        except ValueError as error:
            raise ValueError("La fecha de creación debe usar formato ISO 8601.") from error

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "Order":
        try:
            customer = data["cliente"]
            if customer["nombre"] is None or customer["tipo"] is None:
                raise TypeError("Los datos del cliente no pueden ser nulos.")
            order = cls(
                id=str(data["id"]),
                customer_name=str(customer["nombre"]),
                customer_type=str(customer["tipo"]),
                products=[Product.from_dict(item) for item in data["productos"]],
                shipping_type=str(data["tipo_envio"]),
                payment_method=str(data["metodo_pago"]),
                status=str(data["estado"]),
                created_at=str(data["fecha_creacion"]),
            )
        except (KeyError, TypeError) as error:
            raise ValueError("Pedido inválido: faltan campos requeridos o tienen un tipo incorrecto.") from error
        return order

    def add_product(self, product: Product) -> None:
        self.add_products([product])

    def add_products(self, products: list[Product]) -> None:
        if not products or any(not isinstance(product, Product) for product in products):
            raise ValueError("Se requiere al menos un producto válido.")
        self.products.extend(products)

    def to_dict(self) -> dict[str, Any]:
        return {
            "id": self.id,
            "cliente": {"nombre": self.customer_name, "tipo": self.customer_type},
            "productos": [product.to_dict() for product in self.products],
            "tipo_envio": self.shipping_type,
            "metodo_pago": self.payment_method,
            "estado": self.status,
            "fecha_creacion": self.created_at,
        }
