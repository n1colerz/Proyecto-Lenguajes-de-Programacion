package com.policode;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonStorage() {}

    public static List<Order> loadOrders(Path path) throws IOException {
        try {
            JsonElement root = JsonParser.parseString(Files.readString(path));
            if (!root.isJsonArray()) {
                throw new IllegalArgumentException("El archivo JSON debe contener una lista de pedidos.");
            }
            List<Order> orders = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray()) {
                JsonObject json = element.getAsJsonObject();
                JsonObject customer = json.getAsJsonObject("cliente");
                List<Product> products = new ArrayList<>();
                JsonArray productArray = json.getAsJsonArray("productos");
                for (JsonElement productElement : productArray) {
                    JsonObject product = productElement.getAsJsonObject();
                    products.add(new Product(
                            product.get("codigo").getAsString(),
                            product.get("nombre").getAsString(),
                            product.get("precio").getAsBigDecimal(),
                            product.get("cantidad").getAsBigDecimal().intValueExact()));
                }
                orders.add(new Order(
                        json.get("id").getAsString(),
                        customer.get("nombre").getAsString(),
                        customer.get("tipo").getAsString(),
                        products,
                        json.get("tipo_envio").getAsString(),
                        json.get("metodo_pago").getAsString(),
                        json.get("estado").getAsString(),
                        json.get("fecha_creacion").getAsString()));
            }
            return orders;
        } catch (RuntimeException error) {
            throw new IOException("No se pudieron validar los pedidos de " + path + ": " + error.getMessage(), error);
        }
    }

    public static void saveOrders(Path path, List<Order> orders) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        List<Map<String, Object>> records = new ArrayList<>();
        for (Order order : orders) {
            Map<String, Object> customer = Map.of("nombre", order.customerName, "tipo", order.customerType);
            List<Map<String, Object>> products = order.products.stream().map(product -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("codigo", product.code);
                item.put("nombre", product.name);
                item.put("precio", product.price);
                item.put("cantidad", product.quantity);
                return item;
            }).toList();
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", order.id);
            record.put("cliente", customer);
            record.put("productos", products);
            record.put("tipo_envio", order.shippingType);
            record.put("metodo_pago", order.paymentMethod);
            record.put("estado", order.status);
            record.put("fecha_creacion", order.createdAt);
            records.add(record);
        }
        Files.writeString(path, GSON.toJson(records));
    }
}
