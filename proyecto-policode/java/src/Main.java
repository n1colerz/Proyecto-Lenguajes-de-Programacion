import com.google.gson.GsonBuilder;
import com.policode.JsonStorage;
import com.policode.Order;
import com.policode.OrderService;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class Main {
    public static void main(String[] args) {
        try {
            String dataset = option(args, "--dataset", "solicitudes_pequenas");
            int technicians = Integer.parseInt(option(args, "--tecnicos", "4"));
            String mode = option(args, "--modo", "concurrente");
            if (!List.of("solicitudes_pequenas", "solicitudes_medias", "solicitudes_grandes").contains(dataset)) {
                throw new IllegalArgumentException("Dataset desconocido: " + dataset);
            }
            if (!List.of("secuencial", "concurrente").contains(mode)) {
                throw new IllegalArgumentException("Modo desconocido: " + mode);
            }
            Path projectRoot = Path.of("").toAbsolutePath();
            Path dataPath = projectRoot.resolve("../datos").resolve(dataset + ".json").normalize();
            if (!dataPath.toFile().exists()) {
                dataPath = projectRoot.resolve("datos").resolve(dataset + ".json");
            }
            List<Order> orders = JsonStorage.loadOrders(dataPath);
            Map<String, Object> processing = mode.equals("secuencial")
                    ? OrderService.processSequentially(orders, technicians)
                    : OrderService.processConcurrently(orders, technicians);
            Map<String, Object> result = Map.of(
                    "resumen", OrderService.salesSummary(orders),
                    "procesamiento", processing);
            System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Exception error) {
            System.err.println(error.getMessage());
            System.exit(1);
        }
    }

    private static String option(String[] args, String option, String fallback) {
        for (int index = 0; index < args.length; index++) {
            if (args[index].equals(option)) {
                if (index + 1 >= args.length) throw new IllegalArgumentException("Falta valor para " + option);
                return args[index + 1];
            }
        }
        return fallback;
    }
}
