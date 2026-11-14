# Proyecto Policode: gestión de pedidos

## Alcance

Tres implementaciones equivalentes de un sistema de pedidos de tienda. Comparten los archivos de `datos/`, nombres de campos, reglas de cálculo, prioridades, filtros, ordenamientos y estructura de resumen. Los pagos son simulados; no se conecta a procesadores ni se almacenan credenciales.

## Reglas comunes

- Los productos deben tener código y nombre, precio no negativo y cantidad entera mayor que cero. Todo pedido debe incluir al menos un producto.
- Clientes `vip`: 10 % de descuento. Clientes regulares con subtotal de al menos $1,000: 5 %. Se aplica un solo descuento; VIP prevalece si ambas condiciones pudieran aplicar.
- Impuesto: 16 % sobre subtotal menos descuento. Cada operación monetaria se redondea al centavo más cercano.
- Envío: estándar $50, express $100 y retiro en tienda $0.
- Prioridad según subtotal, antes de descuento: alta/ crítica desde $1,000 (respuesta en 2 h), media desde $500 (8 h) y baja en otro caso (24 h).
- Al procesar, prioridad alta va primero; empates se resuelven por fecha de creación ascendente. Con cero técnicos disponibles los pedidos permanecen en cola.
- Estados permitidos: `Recibido`, `En preparación`, `Enviado`, `Entregado`, `Cancelado`.
- El resumen cuenta todos los pedidos y estados, pero excluye los cancelados de los importes de venta.

## Requisitos

- Python 3.10 o posterior (no requiere paquetes externos).
- Node.js 18 o posterior (npm solo es necesario para ejecutar las pruebas).
- Java 11 o posterior para la ejecución de archivo único. Maven solo es necesario para ejecutar las pruebas.

## Ejecutar

Entra al directorio indicado y ejecuta el comando mostrado. Los tres comandos funcionan sin argumentos; por defecto leen `solicitudes_pequenas.json` y usan cuatro técnicos.

```sh
# Python
cd python
python3 main.py
cd ..

# JavaScript / Node.js
cd javascript-o-typescript
node main.js
cd ..

# Java
cd java
java Main.java
```

Opcionalmente, cada programa acepta `--dataset solicitudes_medias` o `--dataset solicitudes_grandes`, `--tecnicos 0` y `--modo secuencial`. Por ejemplo: `python3 main.py --dataset solicitudes_grandes --modo secuencial`. `--tecnicos` representa los trabajadores concurrentes disponibles; el valor cero conserva los pedidos en cola. El modo predeterminado es concurrente.

## Pruebas automatizadas

Las pruebas pueden ejecutarse con las herramientas de desarrollo de cada lenguaje:

```sh
cd python && python3 -m unittest discover -s tests -v
cd ../javascript-o-typescript && npm install && npm test
cd ../java && mvn test
```

Las pruebas cubren montos, descuento, impuesto, envío, validaciones, estado, filtros, ordenamientos, plazo de prioridad, cola y agregado de productos. La ejecución normal no necesita npm ni Maven. No se publican tiempos de ejecución comparativos hasta medir las tres versiones en el mismo equipo y entorno.

## Resultado por consola

Cada punto de entrada produce JSON con exactamente dos partes:

```json
{
  "resumen": {
    "cantidad_pedidos": 0,
    "subtotal": 0.0,
    "descuentos": 0.0,
    "impuestos": 0.0,
    "envios": 0.0,
    "ventas_total": 0.0,
    "por_estado": {},
    "por_prioridad": {}
  },
  "procesamiento": {
    "procesados": [],
    "en_cola": []
  }
}
```

Los valores de ejemplo son solo un esquema, no un resultado medido. Las cifras se generan leyendo el conjunto elegido.

## Documentación

- [Análisis comparativo](./documentos/analisis-comparativo.md)
- [Manual técnico](./documentos/manual-tecnico.md)
- [Tablero interactivo](./documentos/comparativo.html)
- [Diagrama de arquitectura](./documentos/diagramas/arquitectura.md)

Las carpetas `evidencias/capturas/` y `evidencias/videos/` quedan disponibles para incorporar evidencias propias. Los PDFs heredados no se alteraron; los documentos Markdown editables contienen el análisis y manual actuales.
