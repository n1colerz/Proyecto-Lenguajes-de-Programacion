import json
from pathlib import Path

from .model import Order


def load_orders(path: str | Path) -> list[Order]:
    try:
        with Path(path).open(encoding="utf-8") as data_file:
            records = json.load(data_file)
        if not isinstance(records, list):
            raise ValueError("El archivo JSON debe contener una lista de pedidos.")
        return [Order.from_dict(record) for record in records]
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"No se pudo leer el archivo de pedidos {path}: {error}") from error


def save_orders(path: str | Path, orders: list[Order]) -> None:
    destination = Path(path)
    try:
        destination.parent.mkdir(parents=True, exist_ok=True)
        with destination.open("w", encoding="utf-8") as data_file:
            json.dump([order.to_dict() for order in orders], data_file, ensure_ascii=False, indent=2)
            data_file.write("\n")
    except OSError as error:
        raise ValueError(f"No se pudo guardar el archivo de pedidos {path}: {error}") from error
