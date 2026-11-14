package com.policode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderServiceTest {
    private Order order(String id, String customerType, String state, String date, String price, int quantity,
                        String shipping) {
        return new Order(id, "Test", customerType,
                List.of(new Product("A", "Artículo", new BigDecimal(price), quantity)),
                shipping, "tarjeta_simulada", state, date);
    }

    @Test
    void calculatesSubtotalDiscountAndTax() {
        Order order = order("T-1", "vip", "Recibido", "2026-01-01T10:00:00", "100.00", 2, "estandar");
        assertEquals(20_000, OrderService.subtotalCents(order));
        assertEquals(2_000, OrderService.discountCents(order));
        assertEquals(2_880, OrderService.taxCents(order));
    }

    @Test
    void appliesAmountDiscountAndShippingRates() {
        Order threshold = order("T-2", "regular", "Recibido", "2026-01-01T10:00:00", "1000", 1, "express");
        assertEquals(5_000, OrderService.discountCents(threshold));
        assertEquals(10_000, OrderService.shippingCents(threshold));
        assertEquals(0, OrderService.shippingCents(
                order("T-3", "regular", "Recibido", "2026-01-01T10:00:00", "1", 1, "retiro")));
    }

    @Test
    void validatesQuantityAndNonEmptyOrders() {
        assertThrows(IllegalArgumentException.class,
                () -> new Product("A", "Artículo", new BigDecimal("1"), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Order("T", "Test", "regular", List.of(), "estandar",
                        "tarjeta_simulada", "Recibido", "2026-01-01T10:00:00"));
    }

    @Test
    void changesAndFiltersStatusAndCustomer() {
        Order order = order("T-4", "regular", "Recibido", "2026-01-01T10:00:00", "20", 1, "estandar");
        OrderService.updateStatus(order, "Enviado");
        assertEquals(List.of(order), OrderService.byStatus(List.of(order), "Enviado"));
        assertEquals(List.of(order), OrderService.byCustomer(List.of(order), "test"));
        assertThrows(IllegalArgumentException.class, () -> OrderService.updateStatus(order, "No válido"));
    }

    @Test
    void sortsByDateAmountAndPriority() {
        Order low = order("low", "regular", "Recibido", "2026-01-01T10:00:00", "10", 1, "estandar");
        Order high = order("high", "regular", "Recibido", "2026-01-02T10:00:00", "1000", 1, "estandar");
        assertEquals("low", OrderService.sortOrders(List.of(high, low), "fecha").get(0).id);
        assertEquals("low", OrderService.sortOrders(List.of(high, low), "monto").get(0).id);
        assertEquals("high", OrderService.sortOrders(List.of(low, high), "prioridad").get(0).id);
        assertEquals(2, OrderService.responseTimeHours(high));
    }

    @Test
    void queuesOrdersWhenThereAreNoTechnicians() throws Exception {
        Order order = order("T-6", "regular", "Recibido", "2026-01-01T10:00:00", "10", 1, "estandar");
        var processing = OrderService.processConcurrently(List.of(order), 0);
        assertEquals(List.of(), processing.get("procesados"));
        assertEquals(List.of("T-6"), processing.get("en_cola"));
        assertEquals(
                OrderService.processSequentially(List.of(order), 4).get("procesados"),
                OrderService.processConcurrently(List.of(order), 4).get("procesados"));
    }

    @Test
    void addsProductToOrder() {
        Order order = order("T-7", "regular", "Recibido", "2026-01-01T10:00:00", "10", 1, "estandar");
        order.addProducts(List.of(
                new Product("B", "Otro", new BigDecimal("4"), 2),
                new Product("C", "Tercero", new BigDecimal("5"), 1)));
        assertEquals(3, order.products.size());
    }
}
