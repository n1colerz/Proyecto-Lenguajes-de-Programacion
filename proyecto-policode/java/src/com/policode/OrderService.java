package com.policode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public final class OrderService {
    private static final Map<String, BigDecimal> SHIPPING_CENTS =
            Map.of("estandar", new BigDecimal("5000"), "express", new BigDecimal("10000"), "retiro", BigDecimal.ZERO);

    private OrderService() {}

    public static long subtotalCents(Order order) {
        return order.products.stream()
                .map(product -> product.price.setScale(2, RoundingMode.HALF_UP)
                        .movePointRight(2).longValueExact() * product.quantity)
                .mapToLong(Long::longValue)
                .sum();
    }

    public static long discountCents(Order order) {
        long subtotal = subtotalCents(order);
        int percentage = order.customerType.equals("vip") ? 10 : (subtotal >= 100_000 ? 5 : 0);
        return (subtotal * percentage + 50) / 100;
    }

    public static long taxCents(Order order) {
        long taxable = subtotalCents(order) - discountCents(order);
        return (taxable * 16 + 50) / 100;
    }

    public static long shippingCents(Order order) {
        return SHIPPING_CENTS.get(order.shippingType).longValueExact();
    }

    public static long totalCents(Order order) {
        return subtotalCents(order) - discountCents(order) + taxCents(order) + shippingCents(order);
    }

    public static String priority(Order order) {
        long subtotal = subtotalCents(order);
        if (subtotal >= 100_000) return "alta";
        if (subtotal >= 50_000) return "media";
        return "baja";
    }

    public static int responseTimeHours(Order order) {
        return switch (priority(order)) {
            case "alta" -> 2;
            case "media" -> 8;
            default -> 24;
        };
    }

    private static double money(long cents) {
        return cents / 100.0;
    }

    public static Map<String, Object> orderSummary(Order order) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", order.id);
        summary.put("subtotal", money(subtotalCents(order)));
        summary.put("descuento", money(discountCents(order)));
        summary.put("impuesto", money(taxCents(order)));
        summary.put("envio", money(shippingCents(order)));
        summary.put("total", money(totalCents(order)));
        summary.put("prioridad", priority(order));
        summary.put("tiempo_respuesta_horas", responseTimeHours(order));
        return summary;
    }

    public static void updateStatus(Order order, String status) {
        if (!Order.STATES.contains(status)) {
            throw new IllegalArgumentException("Estado inválido: " + status);
        }
        order.status = status;
    }

    public static List<Order> byCustomer(List<Order> orders, String name) {
        return orders.stream()
                .filter(order -> order.customerName.equalsIgnoreCase(name))
                .collect(Collectors.toList());
    }

    public static List<Order> byStatus(List<Order> orders, String status) {
        if (!Order.STATES.contains(status)) {
            throw new IllegalArgumentException("Estado inválido: " + status);
        }
        return orders.stream().filter(order -> order.status.equals(status)).collect(Collectors.toList());
    }

    public static List<Order> sortOrders(List<Order> orders, String criterion) {
        List<Order> result = new ArrayList<>(orders);
        Comparator<Order> comparator = switch (criterion) {
            case "fecha" -> Comparator.comparing(order -> order.createdAt);
            case "monto" -> Comparator.comparingLong(OrderService::totalCents);
            case "prioridad" -> Comparator.comparingInt((Order order) -> switch (priority(order)) {
                case "alta" -> 0;
                case "media" -> 1;
                default -> 2;
            }).thenComparing(order -> order.createdAt);
            default -> throw new IllegalArgumentException("El criterio debe ser fecha, monto o prioridad.");
        };
        result.sort(comparator);
        return result;
    }

    public static Map<String, Object> salesSummary(List<Order> orders) {
        List<Order> included = orders.stream().filter(order -> !order.status.equals("Cancelado")).toList();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("cantidad_pedidos", orders.size());
        summary.put("subtotal", money(included.stream().mapToLong(OrderService::subtotalCents).sum()));
        summary.put("descuentos", money(included.stream().mapToLong(OrderService::discountCents).sum()));
        summary.put("impuestos", money(included.stream().mapToLong(OrderService::taxCents).sum()));
        summary.put("envios", money(included.stream().mapToLong(OrderService::shippingCents).sum()));
        summary.put("ventas_total", money(included.stream().mapToLong(OrderService::totalCents).sum()));
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String status : List.of("Recibido", "En preparación", "Enviado", "Entregado", "Cancelado")) {
            byStatus.put(status, orders.stream().filter(order -> order.status.equals(status)).count());
        }
        Map<String, Long> byPriority = new LinkedHashMap<>();
        for (String priority : List.of("alta", "media", "baja")) {
            byPriority.put(priority, orders.stream().filter(order -> priority(order).equals(priority)).count());
        }
        summary.put("por_estado", byStatus);
        summary.put("por_prioridad", byPriority);
        return summary;
    }

    public static Map<String, Object> processConcurrently(List<Order> orders, int technicians)
            throws InterruptedException, ExecutionException {
        if (technicians < 0) {
            throw new IllegalArgumentException("La cantidad de técnicos no puede ser negativa.");
        }
        List<Order> ordered = sortOrders(orders, "prioridad");
        Map<String, Object> result = new LinkedHashMap<>();
        if (technicians == 0) {
            result.put("procesados", List.of());
            result.put("en_cola", ordered.stream().map(order -> order.id).toList());
            return result;
        }
        ExecutorService executor = Executors.newFixedThreadPool(technicians);
        try {
            List<Future<Map<String, Object>>> futures = ordered.stream()
                    .map(order -> executor.submit(() -> orderSummary(order)))
                    .toList();
            List<Map<String, Object>> processed = new ArrayList<>();
            for (Future<Map<String, Object>> future : futures) {
                processed.add(future.get());
            }
            result.put("procesados", processed);
            result.put("en_cola", List.of());
            return result;
        } finally {
            executor.shutdown();
        }
    }

    public static Map<String, Object> processSequentially(List<Order> orders, int technicians) {
        if (technicians < 0) {
            throw new IllegalArgumentException("La cantidad de técnicos no puede ser negativa.");
        }
        List<Order> ordered = sortOrders(orders, "prioridad");
        Map<String, Object> result = new LinkedHashMap<>();
        if (technicians == 0) {
            result.put("procesados", List.of());
            result.put("en_cola", ordered.stream().map(order -> order.id).toList());
            return result;
        }
        result.put("procesados", ordered.stream().map(OrderService::orderSummary).toList());
        result.put("en_cola", List.of());
        return result;
    }
}
