package core;

public final class App {
    private static final int DEFAULT_PORT = 5000;
    private static final String DEFAULT_HOST = "127.0.0.1";

    private App() {
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            String mode = args[0].trim().toLowerCase();

            if ("server".equals(mode)) {
                int port = args.length > 1 ? parsePort(args[1], DEFAULT_PORT) : DEFAULT_PORT;
                ServerLauncher.startServer(port);
                return;
            }
        }

        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;
        boolean clientMode = false;

        if (args.length > 0 && "client".equalsIgnoreCase(args[0].trim())) {
            clientMode = true;
            host = args.length > 1 ? defaultIfBlank(args[1], DEFAULT_HOST) : DEFAULT_HOST;
            port = args.length > 2 ? parsePort(args[2], DEFAULT_PORT) : DEFAULT_PORT;
        }

        try {
            DesktopApp.configureLaunch(clientMode, host, port);
            DesktopApp.launchDesktop(args);
        } catch (NoClassDefFoundError ex) {
            System.err.println("JavaFX runtime is required for the client UI.");
            System.err.println(
                    "Run client with: java --module-path <path-to-javafx-sdk>/lib --add-modules javafx.controls,javafx.fxml -cp bin core.App client 127.0.0.1 5000");
        }
    }

    private static int parsePort(String value, int fallback) {
        try {
            int port = Integer.parseInt(value.trim());
            return port > 0 && port <= 65535 ? port : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
