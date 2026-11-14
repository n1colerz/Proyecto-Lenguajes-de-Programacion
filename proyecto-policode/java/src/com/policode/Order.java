package com.policode;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class Order {
    public static final Set<String> STATES =
            Set.of("Recibido", "En preparación", "Enviado", "Entregado", "Cancelado");
    public static final Set<String> SHIPPING_TYPES = Set.of("estandar", "express", "retiro");
    public static final Set<String> PAYMENT_METHODS =
            Set.of("tarjeta_simulada", "efectivo_simulado", "transferencia_simulada");
    public static final Set<String> CUSTOMER_TYPES = Set.of("regular", "vip");

    public final String id;
    public final String customerName;
    public final String customerType;
    public final List<Product> products;
    public final String shippingType;
    public final String paymentMethod;
    public String status;
    public final String createdAt;

    public Order(String id, String customerName, String customerType, List<Product> products,
                 String shippingType, String paymentMethod, String status, String createdAt) {
        if (id == null || id.isBlank() || customerName == null || customerName.isBlank()) {
            throw new IllegalArgumentException("El pedido requiere un identificador y nombre de cliente.");
        }
        if (!CUSTOMER_TYPES.contains(customerType)) {
            throw new IllegalArgumentException("Tipo de cliente inválido: " + customerType);
        }
        if (!SHIPPING_TYPES.contains(shippingType)) {
            throw new IllegalArgumentException("Tipo de envío inválido: " + shippingType);
        }
        if (!PAYMENT_METHODS.contains(paymentMethod)) {
            throw new IllegalArgumentException("Método de pago inválido: " + paymentMethod);
        }
        if (!STATES.contains(status)) {
            throw new IllegalArgumentException("Estado inválido: " + status);
        }
        try {
            LocalDateTime.parse(createdAt);
        } catch (DateTimeParseException | NullPointerException error) {
            throw new IllegalArgumentException("La fecha debe usar formato ISO 8601.", error);
        }
        if (products == null || products.isEmpty()) {
            throw new IllegalArgumentException("Un pedido debe contener al menos un producto.");
        }
        this.id = id;
        this.customerName = customerName;
        this.customerType = customerType;
        this.products = new ArrayList<>(products);
        this.shippingType = shippingType;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.createdAt = createdAt;
    }

    public void addProduct(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("El producto no puede ser nulo.");
        }
        addProducts(List.of(product));
    }

    public void addProducts(List<Product> newProducts) {
        if (newProducts == null || newProducts.isEmpty() || newProducts.stream().anyMatch(product -> product == null)) {
            throw new IllegalArgumentException("Se requiere al menos un producto válido.");
        }
        products.addAll(newProducts);
    }
}
