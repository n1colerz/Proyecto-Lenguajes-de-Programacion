package com.policode;

import java.math.BigDecimal;

public final class Product {
    public final String code;
    public final String name;
    public final BigDecimal price;
    public final int quantity;

    public Product(String code, String name, BigDecimal price, int quantity) {
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("El producto requiere código y nombre.");
        }
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("El precio debe ser un número no negativo.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad solicitada debe ser mayor que cero.");
        }
        this.code = code;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }
}
