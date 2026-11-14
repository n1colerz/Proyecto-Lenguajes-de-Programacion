# Diagrama de arquitectura

```mermaid
flowchart TD
    U[Usuario / CLI] --> M[Modelo: pedido, cliente, productos]
    D[datos/*.json compartidos] --> F[Utilidad de lectura JSON]
    F --> M
    M --> S[Servicio de negocio]
    S --> V[Validación y cálculos monetarios]
    S --> Q[Ordenar por prioridad y procesar]
    Q --> W[Pool / workers asíncronos]
    S --> R[Filtros, ordenamientos y resumen]
    R --> O[Salida JSON de consola]
    W --> O
    S --> F2[Utilidad de escritura JSON]
    F2 --> D2[Archivo JSON destino opcional]
```

Cada lenguaje implementa el mismo flujo con sus primitivas: `ThreadPoolExecutor` en Python, trabajadores `async` coordinados por promesas en Node.js y `ExecutorService` en Java.
