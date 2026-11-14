import argparse
import json
from pathlib import Path

from .service import OrderService
from .storage import load_orders


def main() -> None:
    parser = argparse.ArgumentParser(description="Sistema de gestión de pedidos Policode")
    parser.add_argument(
        "--dataset",
        choices=("solicitudes_pequenas", "solicitudes_medias", "solicitudes_grandes"),
        default="solicitudes_pequenas",
    )
    parser.add_argument("--tecnicos", type=int, default=4, help="Cantidad de técnicos disponibles")
    parser.add_argument("--modo", choices=("concurrente", "secuencial"), default="concurrente")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    orders = load_orders(root / "datos" / f"{args.dataset}.json")
    process = (
        OrderService.process_sequentially
        if args.modo == "secuencial"
        else OrderService.process_concurrently
    )
    result = {
        "resumen": OrderService.sales_summary(orders),
        "procesamiento": process(orders, args.tecnicos),
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
