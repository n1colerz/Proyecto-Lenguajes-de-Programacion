# Manual técnico

## Requisitos

| Lenguaje | Requisito para ejecutar | Dependencias de ejecución |
|---|---|---|
| Python | Python 3.10+ | Ninguna externa |
| JavaScript | Node.js 18+ | Ninguna externa |
| Java | Java 11+ (modo archivo fuente) | Ninguna externa |

Ejecuta cada comando desde el directorio de su implementación. Las herramientas `npm` y Maven son opcionales para la ejecución normal y solo se usan para instalar/ejecutar pruebas.

## Ejecución simple

```sh
# Desde proyecto-policode/python/
python3 main.py

# Desde proyecto-policode/javascript-o-typescript/
node main.js

# Desde proyecto-policode/java/
java Main.java
```

Los tres comandos sin argumentos leen `datos/solicitudes_pequenas.json`, usan cuatro técnicos y muestran en consola un objeto JSON con `resumen` y `procesamiento`.

Opciones disponibles: `--dataset solicitudes_pequenas|solicitudes_medias|solicitudes_grandes`, `--tecnicos N` y `--modo secuencial|concurrente`. Todos son opcionales. Ejemplo: `java Main.java --dataset solicitudes_grandes --tecnicos 2 --modo secuencial`.

## Pruebas automatizadas

```sh
cd python && python3 -m unittest discover -s tests -v
cd ../javascript-o-typescript && npm install && npm test
cd ../java && mvn test
```

Python y JavaScript mantienen la lógica organizada en módulos de `src/`; sus lanzadores raíz permiten usar los comandos simples solicitados. El punto de entrada Java `Main.java` en la raíz es autocontenido, incluido su lector y escritor JSON. Maven conserva la compilación/pruebas de los módulos Java existentes, pero no es necesario para ejecutar `java Main.java`.

## Esquema de datos

Cada archivo bajo `datos/` contiene una lista de pedidos:

```json
{
  "id": "P-1001",
  "cliente": {"nombre": "Ana Ruiz", "tipo": "regular"},
  "productos": [{"codigo": "LIB-01", "nombre": "Cuaderno", "precio": 45.5, "cantidad": 2}],
  "tipo_envio": "estandar",
  "metodo_pago": "tarjeta_simulada",
  "estado": "Recibido",
  "fecha_creacion": "2026-10-10T09:00:00"
}
```

Clientes válidos: `regular`/`vip`; envíos: `estandar`/`express`/`retiro`; pagos: `tarjeta_simulada`/`efectivo_simulado`/`transferencia_simulada`; estados: `Recibido`/`En preparación`/`Enviado`/`Entregado`/`Cancelado`.

## Reglas y redondeo

Importes en centavos: subtotal = precio por cantidad; descuento VIP 10 % o, si es cliente regular con subtotal >= $1,000, 5 %; impuesto 16 % sobre subtotal después del descuento; envío $50 estándar, $100 express y $0 retiro. El total combina estos importes. Cada descuento/impuesto se redondea al centavo más cercano. Prioridad: alta desde $1,000, media desde $500, baja debajo de $500. Los plazos máximos son 2, 8 y 24 horas respectivamente.

## Contrato de salida

- `resumen`: cantidad total, subtotal, descuentos, impuestos, envíos, venta final (sin pedidos cancelados), conteos por estado y prioridad.
- `procesamiento`: pedidos procesados en orden de prioridad y pedidos que permanecen en cola.

Cuando `--tecnicos 0`, todos los pedidos aparecen en la cola. Las opciones de ejecución y archivos fuente compartidos se detallan en el [README del proyecto](../README.md).
