import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class CarServer {
    private static final String BRAND = "Mercedes-Benz";
    private static final String MODEL = "S 500 Sedan";
    private static final String CAR_JSON = """
        {"id":%d,"brand":"%s","model":"%s","price":2500,"transmission":"A/T","fuel":"Бензин"}
        """;

    public static void main(String[] args) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8000), 0);
        server.createContext("/", CarServer::handle);
        server.start();
        System.out.println("DriveNext server: http://127.0.0.1:8000/cars");
        System.out.println("Press Ctrl+C to stop.");
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"/cars".equals(exchange.getRequestURI().getPath())) {
                send(exchange, 404, "{\"error\":\"Not found\"}");
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                send(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String query = searchTerm(exchange.getRequestURI().getRawQuery());
            String name = (BRAND + " " + MODEL).toLowerCase(Locale.ROOT);
            String result = name.contains(query) ? IntStream.rangeClosed(1, 6)
                .mapToObj(id -> CAR_JSON.formatted(id, BRAND, MODEL).strip())
                .collect(Collectors.joining(",", "[", "]")) : "[]";
            send(exchange, 200, result);
        }
    }

    private static String searchTerm(String rawQuery) {
        if (rawQuery != null) {
            for (String parameter : rawQuery.split("&")) {
                if (parameter.startsWith("q=")) {
                    return URLDecoder.decode(parameter.substring(2), StandardCharsets.UTF_8)
                        .strip().toLowerCase(Locale.ROOT);
                }
            }
        }
        return "";
    }

    private static void send(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }
}
