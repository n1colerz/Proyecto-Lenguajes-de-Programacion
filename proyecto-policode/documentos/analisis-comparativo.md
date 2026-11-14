# Análisis comparativo: sistema de gestión de pedidos

**Proyecto final de Lenguajes de Programación**  
**Título:** Gestión de pedidos de una tienda en Python, Java y JavaScript  
**Fecha:** noviembre de 2026  
**Equipo:** completar por el equipo  
**Institución:** completar por el equipo

## 1. Introducción

Este trabajo implementa el mismo dominio de pedidos en tres lenguajes y compara cómo expresan el modelado, las validaciones, el cálculo de precios, el manejo de errores, la entrada/salida JSON y el procesamiento concurrente o asíncrono. Los datos se centralizan en `datos/` para evitar que cada solución mida o interprete un conjunto distinto.

## 2. Descripción del problema

Una tienda necesita registrar clientes, productos, cantidades, envío, pago simulado, estado y fecha; calcular importes; consultar y ordenar pedidos; actualizar estados; y obtener un resumen de ventas. También debe atender primero las solicitudes de mayor prioridad y conservar solicitudes en cola cuando no haya técnicos disponibles. El sistema es demostrativo: no ejecuta cobros reales ni integra servicios externos.

## 3. Reglas de negocio compartidas

| Aspecto | Regla |
|---|---|
| Datos requeridos | ID, cliente y al menos un producto; cada producto tiene código, nombre, precio y cantidad |
| Cantidad y precio | Cantidad entera > 0; precio >= 0 |
| Descuento | VIP 10 %; regular con subtotal >= $1,000, 5 %; no acumulables |
| Impuesto | 16 % del subtotal después del descuento |
| Envío | Estándar $50; express $100; retiro $0 |
| Prioridad alta / crítica | Subtotal >= $1,000; plazo máximo 2 h |
| Prioridad media | Subtotal >= $500; plazo máximo 8 h |
| Prioridad baja | Subtotal < $500; plazo máximo 24 h |
| Cola | Se procesa primero prioridad alta, luego media y baja; empates por fecha ascendente. Sin técnicos, permanecen en cola |
| Estados | Recibido, En preparación, Enviado, Entregado, Cancelado |
| Ventas | Cancelados cuentan en cantidades por estado, pero no en importes del resumen |
| Redondeo | Centavos; monto de impuesto y descuento redondeados al centavo más cercano |

El total es `subtotal - descuento + impuesto + envío`. La prioridad se determina sobre el subtotal previo al descuento.

## 4. Justificación de los lenguajes

- **Python:** sintaxis concisa y módulos de biblioteca estándar para JSON, pruebas y ejecutores de tareas.
- **Java:** tipado estático y estructura explícita adecuada para comparar validaciones; la ejecución autónoma demuestra el procesamiento JSON sin librerías externas.
- **JavaScript con Node.js:** lenguaje dinámico con asincronía nativa para operaciones de entrada/salida y APIs basadas en promesas; Jest provee pruebas.

## 5. Clasificación de lenguajes

| Propiedad | Python | Java | JavaScript (Node.js) |
|---|---|---|---|
| Abstracción | Alto nivel, multipropósito | Alto nivel, orientado a objetos | Alto nivel, multipropósito |
| Ejecución | Interpretado por CPython y bytecode interno | Compilado a bytecode al ejecutar el archivo fuente y ejecutado por JVM/JIT | Interpretado/JIT por motor V8 en Node.js |
| Tipos | Dinámicos y fuertes en operaciones ordinarias: las incompatibilidades suelen detectarse en ejecución, sin coerciones generales entre tipos incompatibles | Estáticos y fuertes: tipos declarados y comprobados al compilar; existen conversiones explícitas y APIs que requieren validaciones | Dinámicos y usualmente descritos como débiles frente a coerciones implícitas, por ejemplo `+` con texto; `Number.isInteger` se usa para la validación del dominio |
| Propósito | General, automatización, scripting y servicios | General, aplicaciones empresariales y multiplataforma | General; frontend y backend con Node.js |
| Paradigma principal | Multiparadigma con énfasis en objetos | Orientado a objetos, basado en clases | Multiparadigma, prototipos y funciones |
| Elementos visibles | `Order`, `Product`, `OrderService`, listas, comprensiones y funciones lambda | Clases `Order`, `Product`, `OrderService`, colecciones `List`/`Map` y `ExecutorService` | Clases `Order`/`Product`, `Array`, `filter`/`reduce`, promesas y `async`/`await` |
| Tipo de lenguaje | General, de alto nivel, propósito general | General, de alto nivel, compilado a bytecode | General, de alto nivel, interpretado/JIT |

Las etiquetas “fuerte/débil” son una simplificación didáctica; describen el comportamiento observable relevante en estas implementaciones, no una jerarquía de calidad.

## 6. Arquitecturas

Cada versión separa modelo, reglas/servicio, lectura y escritura JSON y consola. El diagrama se encuentra en [diagramas/arquitectura.md](./diagramas/arquitectura.md).

### Python

`src/model.py` valida y representa productos y pedidos; `src/service.py` centraliza cálculo, filtros, orden y procesamiento con `ThreadPoolExecutor`; `src/storage.py` valida JSON al cargar y persiste el esquema común; `src/main.py` ofrece la CLI. `unittest` está bajo `tests/`.

### JavaScript

`src/model.js` valida y modela entidades; `src/service.js` reúne operaciones y limita la concurrencia con workers asíncronos; `src/storage.js` utiliza `fs/promises`; `src/index.js` es la CLI. Jest está bajo `tests/`.

### Java

El comando de ejecución simple usa `java/Main.java`, un punto de entrada autónomo con lector/escritor JSON y `ExecutorService`; funciona en modo archivo fuente desde Java 11. Maven conserva las clases de apoyo y pruebas JUnit del módulo, pero no se requiere para ejecutar el programa.

## 7. Comparación de estructuras de datos

Las tres versiones reciben arreglos/listas de pedidos, un arreglo/lista de productos por pedido y objetos/mapas para cliente y resumen. Python usa `list`/`dict`, Java `List`/`Map` y JavaScript `Array`/objetos. Los productos se recorren para calcular en centavos, y los pedidos filtrados se representan como una nueva colección. Se mantiene una misma lista de estados y prioridades en el resumen.

## 8. Comparación de errores

Las tres validan antes de operar, rechazan cantidad cero/negativa y pedido vacío, validan enums y propagan errores de lectura JSON con contexto. Python usa `ValueError` y `try/except`, Java `IllegalArgumentException`/`IOException`, JavaScript `TypeError`/`Error` con `try/catch` en el punto de entrada. La salida CLI falla con código no exitoso ante errores.

## 9. Tipos, ejecución y compilación

Java define tipos estáticos y convierte precios JSON a centavos; Python usa tipos dinámicos y `Decimal` en redondeo; JavaScript valida en ejecución y transforma el precio a centavos. La ruta Java principal usa el modo de archivo fuente de la JVM (Java 11+), sin Maven ni librerías; Python se ejecuta con el intérprete y JavaScript, con Node.js. Maven queda reservado para las pruebas Java modulares. Los tres comparten el contrato JSON y se invocan desde su directorio.

## 10. Concurrencia y asincronía

Cada implementación ofrece `--modo secuencial` y `--modo concurrente`. Python y Java procesan el modo concurrente con un pool limitado de trabajadores. Node.js coordina workers asíncronos con `Promise.all`; la fase de cálculo es corta y CPU-bound, por lo que este diseño no implica aceleración multiproceso. En las tres versiones la lista está ordenada por prioridad antes de asignarse. Con cero técnicos se informa la lista completa en `en_cola`. Para validar tiempos se deben ejecutar ambos modos en el mismo equipo, versión y condiciones; no se debe interpretar el número de workers como tiempo de servicio real.

## 11. Resultados de pruebas y ejecución

| Lenguaje | Casos automatizados escritos | Resultado de ejecución en el entorno de preparación |
|---|---:|---|
| Python / unittest | 8 | 8/8 aprobados |
| JavaScript / Jest | 7 | 7/7 aprobados |
| Java / JUnit 5 | 7 | 7/7 aprobados |

Los casos cubren subtotal, descuentos por cliente y monto, impuesto, envío, validación de cantidad y pedido vacío, cambio/filtro de estado, consulta de cliente, ordenamientos, plazos, cola y agregado de productos. Las pruebas se ejecutaron con Python 3.14.6, Node.js 22.14.0, Java 23.0.2 y Maven 3.9.9. Además, `java Main.java` fue probado y el archivo compila con `javac --release 11`.

Se ejecutaron los tres puntos de entrada con dos técnicos para cada conjunto. Las estructuras completas de salida `resumen` y `procesamiento` coincidieron exactamente entre lenguajes:

| Conjunto | Pedidos | Subtotal | Descuentos | Impuestos | Envíos | Ventas (cancelados excluidos) |
|---|---:|---:|---:|---:|---:|---:|
| Pequeño | 3 | $1,447.00 | $62.00 | $221.60 | $150.00 | $1,756.60 |
| Mediano | 10 | $9,031.00 | $511.00 | $1,363.20 | $550.00 | $10,433.20 |
| Grande | 20 | $25,641.00 | $1,576.00 | $3,850.40 | $1,100.00 | $29,015.40 |

Estos son importes reproducidos por las tres implementaciones en el entorno de preparación; no son mediciones de rendimiento. No se midieron tiempo de pared ni comparación secuencial/concurrente.

## 12. Ventajas, limitaciones y mantenibilidad

| Lenguaje | Ventajas observadas en la solución | Límites/consideraciones |
|---|---|---|
| Python | Código breve, biblioteca JSON incluida, cambios rápidos | Validaciones de tipo se realizan principalmente en ejecución; GIL limita paralelismo CPU en CPython |
| Java | Contratos explícitos, colecciones y tipos robustos, ejecutor de hilos | Mayor volumen de código; dependencias de desarrollo para las pruebas Maven |
| JavaScript | I/O asíncrona natural, sintaxis compacta, promesas | Tipos y datos dependen de validación explícita; concurrencia async no acelera cálculo CPU pesado |

La separación entre modelos, servicio, almacenamiento y CLI reduce acoplamiento y facilita pruebas. Una evolución real debería persistir en una base transaccional, definir transiciones permitidas de estado, autenticar actores, auditar cambios y separar la prioridad de negocio de la disponibilidad concreta de personal. Estas capacidades quedan fuera del prototipo.

## 13. Tablero y mediciones

El tablero [comparativo.html](./comparativo.html) permite filtrar/ordenar una tabla de comparación. Ya contiene resultados equivalentes, conteos de pruebas y líneas fuente aproximadas de la implementación ejecutable: Python 334, Java 550 y JavaScript 275 (sin pruebas). Las mediciones por volumen y la comparación secuencial frente a concurrente se dejan como “pendiente”. Para completarlas, ejecutar las tres implementaciones bajo el mismo hardware, calentar runtimes cuando corresponda y repetir cada medición; registrar mediana, método y versiones.

## 14. Conclusiones

Una representación de datos y un conjunto de reglas compartidos permiten comparar los tres lenguajes sin alterar el problema. Python favorece concisión, Java estructura los contratos y la concurrencia explícita, y JavaScript integra con naturalidad I/O asíncrona. La corrección funcional debe evaluarse con los casos automatizados; el rendimiento no puede inferirse de la sintaxis ni compararse sin una medición controlada.

## 15. Referencias técnicas

- Python Software Foundation, *Python 3 Documentation*: https://docs.python.org/3/
- Python Software Foundation, `concurrent.futures`: https://docs.python.org/3/library/concurrent.futures.html
- Oracle, *Java SE Documentation*: https://docs.oracle.com/en/java/
- OpenJDK, `ExecutorService`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html
- Google, Gson: https://github.com/google/gson
- Node.js, *API Documentation*: https://nodejs.org/docs/latest/api/
- Jest, *Documentation*: https://jestjs.io/docs/getting-started
- JUnit, *User Guide*: https://junit.org/junit5/docs/current/user-guide/
