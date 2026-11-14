import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Main {
    private static final List<String> STATES =
            Arrays.asList("Recibido", "En preparación", "Enviado", "Entregado", "Cancelado");
    private static final List<String> DATASETS =
            Arrays.asList("solicitudes_pequenas", "solicitudes_medias", "solicitudes_grandes");
    private static final Map<String, Long> SHIPPING_CENTS = new LinkedHashMap<>();

    static {
        SHIPPING_CENTS.put("estandar", 5000L);
        SHIPPING_CENTS.put("express", 10000L);
        SHIPPING_CENTS.put("retiro", 0L);
    }

    private static final class Product {
        final String code;
        final String name;
        final long unitPriceCents;
        final int quantity;

        Product(Map<String, Object> data) {
            code = requiredText(data, "codigo");
            name = requiredText(data, "nombre");
            Object priceValue = data.get("precio");
            Object quantityValue = data.get("cantidad");
            if (!(priceValue instanceof Number) || !(quantityValue instanceof Number)) {
                throw new IllegalArgumentException("Precio y cantidad deben ser numéricos.");
            }
            BigDecimal price = new BigDecimal(priceValue.toString());
            BigDecimal quantityNumber = new BigDecimal(quantityValue.toString());
            if (price.signum() < 0) {
                throw new IllegalArgumentException("El precio no puede ser negativo.");
            }
            if (quantityNumber.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException("La cantidad solicitada debe ser entera.");
            }
            quantity = quantityNumber.intValueExact();
            if (quantity <= 0) {
                throw new IllegalArgumentException("La cantidad solicitada debe ser mayor que cero.");
            }
            unitPriceCents = price.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        }
    }

    private static final class Order {
        final String id;
        final String customerName;
        final String customerType;
        final List<Product> products;
        final String shippingType;
        final String paymentMethod;
        final String status;
        final String createdAt;

        Order(Map<String, Object> data) {
            id = requiredText(data, "id");
            Map<String, Object> customer = object(data.get("cliente"), "cliente");
            customerName = requiredText(customer, "nombre");
            customerType = requiredText(customer, "tipo");
            if (!Arrays.asList("regular", "vip").contains(customerType)) {
                throw new IllegalArgumentException("Tipo de cliente inválido: " + customerType);
            }
            shippingType = requiredText(data, "tipo_envio");
            if (!SHIPPING_CENTS.containsKey(shippingType)) {
                throw new IllegalArgumentException("Tipo de envío inválido: " + shippingType);
            }
            paymentMethod = requiredText(data, "metodo_pago");
            if (!Arrays.asList("tarjeta_simulada", "efectivo_simulado", "transferencia_simulada")
                    .contains(paymentMethod)) {
                throw new IllegalArgumentException("Método de pago inválido: " + paymentMethod);
            }
            status = requiredText(data, "estado");
            if (!STATES.contains(status)) {
                throw new IllegalArgumentException("Estado inválido: " + status);
            }
            createdAt = requiredText(data, "fecha_creacion");
            try {
                LocalDateTime.parse(createdAt);
            } catch (DateTimeParseException error) {
                throw new IllegalArgumentException("La fecha debe usar formato ISO 8601.", error);
            }

            Object productData = data.get("productos");
            if (!(productData instanceof List) || ((List<?>) productData).isEmpty()) {
                throw new IllegalArgumentException("Un pedido debe contener al menos un producto.");
            }
            products = new ArrayList<>();
            for (Object item : (List<?>) productData) {
                products.add(new Product(object(item, "producto")));
            }
        }
    }

    public static void main(String[] args) {
        try {
            String dataset = option(args, "--dataset", "solicitudes_pequenas");
            int technicians = Integer.parseInt(option(args, "--tecnicos", "4"));
            String mode = option(args, "--modo", "concurrente");
            if (!DATASETS.contains(dataset)) {
                throw new IllegalArgumentException("Dataset desconocido: " + dataset);
            }
            if (technicians < 0) {
                throw new IllegalArgumentException("La cantidad de técnicos no puede ser negativa.");
            }
            if (!Arrays.asList("secuencial", "concurrente").contains(mode)) {
                throw new IllegalArgumentException("Modo desconocido: " + mode);
            }

            List<Order> orders = loadOrders(dataset);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("resumen", salesSummary(orders));
            output.put("procesamiento", mode.equals("secuencial")
                    ? processSequentially(orders, technicians)
                    : processConcurrently(orders, technicians));
            System.out.println(Json.write(output));
        } catch (Exception error) {
            System.err.println(error.getMessage());
            System.exit(1);
        }
    }

    private static String option(String[] args, String name, String fallback) {
        for (int index = 0; index < args.length; index++) {
            if (args[index].equals(name)) {
                if (index + 1 >= args.length) {
                    throw new IllegalArgumentException("Falta valor para " + name);
                }
                return args[index + 1];
            }
        }
        return fallback;
    }

    private static List<Order> loadOrders(String dataset) throws IOException {
        String fileName = dataset + ".json";
        List<Path> candidates = Arrays.asList(
                Path.of("datos", fileName),
                Path.of("../datos", fileName),
                Path.of("proyecto-policode", "datos", fileName));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                Object parsed = new JsonParser(Files.readString(candidate)).parse();
                if (!(parsed instanceof List)) {
                    throw new IllegalArgumentException("El archivo JSON debe contener una lista de pedidos.");
                }
                List<Order> orders = new ArrayList<>();
                for (Object item : (List<?>) parsed) {
                    orders.add(new Order(object(item, "pedido")));
                }
                return orders;
            }
        }
        throw new IOException("No se encontró " + fileName + " en la carpeta datos.");
    }

    private static long subtotalCents(Order order) {
        long result = 0;
        for (Product product : order.products) {
            result += product.unitPriceCents * product.quantity;
        }
        return result;
    }

    private static long discountCents(Order order) {
        long subtotal = subtotalCents(order);
        int percent = order.customerType.equals("vip") ? 10 : (subtotal >= 100000 ? 5 : 0);
        return (subtotal * percent + 50) / 100;
    }

    private static long taxCents(Order order) {
        long taxable = subtotalCents(order) - discountCents(order);
        return (taxable * 16 + 50) / 100;
    }

    private static long totalCents(Order order) {
        return subtotalCents(order) - discountCents(order) + taxCents(order) + SHIPPING_CENTS.get(order.shippingType);
    }

    private static String priority(Order order) {
        long subtotal = subtotalCents(order);
        if (subtotal >= 100000) return "alta";
        if (subtotal >= 50000) return "media";
        return "baja";
    }

    private static int responseTimeHours(Order order) {
        String priority = priority(order);
        if (priority.equals("alta")) return 2;
        if (priority.equals("media")) return 8;
        return 24;
    }

    private static double money(long cents) {
        return cents / 100.0;
    }

    private static Map<String, Object> orderSummary(Order order) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", order.id);
        result.put("subtotal", money(subtotalCents(order)));
        result.put("descuento", money(discountCents(order)));
        result.put("impuesto", money(taxCents(order)));
        result.put("envio", money(SHIPPING_CENTS.get(order.shippingType)));
        result.put("total", money(totalCents(order)));
        result.put("prioridad", priority(order));
        result.put("tiempo_respuesta_horas", responseTimeHours(order));
        return result;
    }

    private static Comparator<Order> priorityComparator() {
        return Comparator.comparingInt((Order order) -> {
            String priority = priority(order);
            return priority.equals("alta") ? 0 : (priority.equals("media") ? 1 : 2);
        }).thenComparing(order -> order.createdAt);
    }

    private static Map<String, Object> processSequentially(List<Order> orders, int technicians) {
        List<Order> ordered = new ArrayList<>(orders);
        ordered.sort(priorityComparator());
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> processed = new ArrayList<>();
        List<String> queue = new ArrayList<>();
        if (technicians == 0) {
            for (Order order : ordered) queue.add(order.id);
        } else {
            for (Order order : ordered) processed.add(orderSummary(order));
        }
        result.put("procesados", processed);
        result.put("en_cola", queue);
        return result;
    }

    private static Map<String, Object> processConcurrently(List<Order> orders, int technicians)
            throws InterruptedException, ExecutionException {
        List<Order> ordered = new ArrayList<>(orders);
        ordered.sort(priorityComparator());
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> processed = new ArrayList<>();
        List<String> queue = new ArrayList<>();
        if (technicians == 0) {
            for (Order order : ordered) queue.add(order.id);
        } else if (!ordered.isEmpty()) {
            ExecutorService executor = Executors.newFixedThreadPool(Math.min(technicians, ordered.size()));
            try {
                List<Future<Map<String, Object>>> futures = new ArrayList<>();
                for (Order order : ordered) {
                    final Order current = order;
                    futures.add(executor.submit(new Callable<Map<String, Object>>() {
                        public Map<String, Object> call() {
                            return orderSummary(current);
                        }
                    }));
                }
                for (Future<Map<String, Object>> future : futures) processed.add(future.get());
            } finally {
                executor.shutdown();
            }
        }
        result.put("procesados", processed);
        result.put("en_cola", queue);
        return result;
    }

    private static Map<String, Object> salesSummary(List<Order> orders) {
        List<Order> included = new ArrayList<>();
        for (Order order : orders) {
            if (!order.status.equals("Cancelado")) included.add(order);
        }
        long subtotal = 0;
        long discounts = 0;
        long taxes = 0;
        long shipping = 0;
        long total = 0;
        for (Order order : included) {
            subtotal += subtotalCents(order);
            discounts += discountCents(order);
            taxes += taxCents(order);
            shipping += SHIPPING_CENTS.get(order.shippingType);
            total += totalCents(order);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cantidad_pedidos", orders.size());
        result.put("subtotal", money(subtotal));
        result.put("descuentos", money(discounts));
        result.put("impuestos", money(taxes));
        result.put("envios", money(shipping));
        result.put("ventas_total", money(total));

        Map<String, Long> byState = new LinkedHashMap<>();
        for (String state : STATES) {
            long count = 0;
            for (Order order : orders) if (order.status.equals(state)) count++;
            byState.put(state, count);
        }
        Map<String, Long> byPriority = new LinkedHashMap<>();
        for (String level : Arrays.asList("alta", "media", "baja")) {
            long count = 0;
            for (Order order : orders) if (priority(order).equals(level)) count++;
            byPriority.put(level, count);
        }
        result.put("por_estado", byState);
        result.put("por_prioridad", byPriority);
        return result;
    }

    private static String requiredText(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new IllegalArgumentException("Campo requerido inválido: " + key);
        }
        return (String) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String label) {
        if (!(value instanceof Map)) throw new IllegalArgumentException("Se esperaba un objeto JSON para " + label);
        return (Map<String, Object>) value;
    }

    private static final class Json {
        static String write(Object value) {
            StringBuilder output = new StringBuilder();
            append(value, output, 0);
            return output.toString();
        }

        private static void append(Object value, StringBuilder output, int depth) {
            if (value == null) {
                output.append("null");
            } else if (value instanceof String) {
                quote((String) value, output);
            } else if (value instanceof Number || value instanceof Boolean) {
                output.append(value);
            } else if (value instanceof Map) {
                output.append("{");
                boolean first = true;
                for (Object rawEntry : ((Map<?, ?>) value).entrySet()) {
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) rawEntry;
                    if (!first) output.append(",");
                    newline(output, depth + 1);
                    quote(String.valueOf(entry.getKey()), output);
                    output.append(": ");
                    append(entry.getValue(), output, depth + 1);
                    first = false;
                }
                if (!first) newline(output, depth);
                output.append("}");
            } else if (value instanceof Iterable) {
                output.append("[");
                boolean first = true;
                for (Object item : (Iterable<?>) value) {
                    if (!first) output.append(",");
                    newline(output, depth + 1);
                    append(item, output, depth + 1);
                    first = false;
                }
                if (!first) newline(output, depth);
                output.append("]");
            } else {
                throw new IllegalArgumentException("Tipo no serializable: " + value.getClass().getName());
            }
        }

        private static void newline(StringBuilder output, int depth) {
            output.append("\n").append(String.join("", Collections.nCopies(depth * 2, " ")));
        }

        private static void quote(String text, StringBuilder output) {
            output.append('"');
            for (int index = 0; index < text.length(); index++) {
                char character = text.charAt(index);
                if (character == '"' || character == '\\') output.append('\\').append(character);
                else if (character == '\n') output.append("\\n");
                else if (character == '\r') output.append("\\r");
                else if (character == '\t') output.append("\\t");
                else output.append(character);
            }
            output.append('"');
        }
    }

    private static final class JsonParser {
        private final String input;
        private int index;

        JsonParser(String input) {
            this.input = input;
        }

        Object parse() {
            Object result = value();
            whitespace();
            if (index != input.length()) fail("Contenido adicional después del JSON");
            return result;
        }

        private Object value() {
            whitespace();
            if (index >= input.length()) return fail("JSON incompleto");
            char current = input.charAt(index);
            if (current == '{') return object();
            if (current == '[') return array();
            if (current == '"') return string();
            if (current == 't') return literal("true", Boolean.TRUE);
            if (current == 'f') return literal("false", Boolean.FALSE);
            if (current == 'n') return literal("null", null);
            if (current == '-' || Character.isDigit(current)) return number();
            return fail("Valor JSON inválido");
        }

        private Map<String, Object> object() {
            Map<String, Object> result = new LinkedHashMap<>();
            index++;
            whitespace();
            if (take('}')) return result;
            while (true) {
                whitespace();
                if (index >= input.length() || input.charAt(index) != '"') return fail("Clave JSON inválida");
                String key = string();
                whitespace();
                expect(':');
                result.put(key, value());
                whitespace();
                if (take('}')) return result;
                expect(',');
            }
        }

        private List<Object> array() {
            List<Object> result = new ArrayList<>();
            index++;
            whitespace();
            if (take(']')) return result;
            while (true) {
                result.add(value());
                whitespace();
                if (take(']')) return result;
                expect(',');
            }
        }

        private String string() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (index < input.length()) {
                char character = input.charAt(index++);
                if (character == '"') return result.toString();
                if (character != '\\') {
                    result.append(character);
                    continue;
                }
                if (index >= input.length()) return fail("Escape JSON incompleto");
                char escape = input.charAt(index++);
                switch (escape) {
                    case '"': result.append('"'); break;
                    case '\\': result.append('\\'); break;
                    case '/': result.append('/'); break;
                    case 'b': result.append('\b'); break;
                    case 'f': result.append('\f'); break;
                    case 'n': result.append('\n'); break;
                    case 'r': result.append('\r'); break;
                    case 't': result.append('\t'); break;
                    case 'u':
                        if (index + 4 > input.length()) return fail("Escape Unicode incompleto");
                        try {
                            result.append((char) Integer.parseInt(input.substring(index, index + 4), 16));
                        } catch (NumberFormatException error) {
                            return fail("Escape Unicode inválido");
                        }
                        index += 4;
                        break;
                    default: return fail("Escape JSON inválido");
                }
            }
            return fail("Cadena JSON sin cerrar");
        }

        private Number number() {
            int start = index;
            if (take('-') && index >= input.length()) return fail("Número JSON incompleto");
            if (take('0')) {
                if (index < input.length() && Character.isDigit(input.charAt(index))) return fail("Número inválido");
            } else {
                digits();
            }
            if (take('.')) digits();
            if (take('e') || take('E')) {
                if (!take('+')) take('-');
                digits();
            }
            try {
                return new BigDecimal(input.substring(start, index));
            } catch (NumberFormatException error) {
                return fail("Número JSON inválido");
            }
        }

        private void digits() {
            int start = index;
            while (index < input.length() && Character.isDigit(input.charAt(index))) index++;
            if (index == start) fail("Se esperaba un dígito");
        }

        private Object literal(String text, Object value) {
            if (!input.startsWith(text, index)) return fail("Literal JSON inválido");
            index += text.length();
            return value;
        }

        private void whitespace() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) index++;
        }

        private boolean take(char expected) {
            if (index < input.length() && input.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!take(expected)) fail("Se esperaba '" + expected + "'");
        }

        private <T> T fail(String message) {
            throw new IllegalArgumentException(message + " en posición " + index);
        }
    }
}
