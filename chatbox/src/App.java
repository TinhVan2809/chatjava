import java.io.IOException;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class App {
    private static final int DEFAULT_PORT = 5000;
    private static final String DEFAULT_HOST = "127.0.0.1";

    public static void main(String[] args) {
        if (args.length > 0) {
            handleCommandLine(args);
            return;
        }

        installLookAndFeel();
        SwingUtilities.invokeLater(App::showLauncher);
    }

    private static void handleCommandLine(String[] args) {
        String mode = args[0].trim().toLowerCase();

        if ("server".equals(mode)) {
            int port = args.length > 1 ? parsePort(args[1], DEFAULT_PORT) : DEFAULT_PORT;
            startServer(port);
            return;
        }

        if ("client".equals(mode)) {
            String host = args.length > 1 ? defaultIfBlank(args[1], DEFAULT_HOST) : DEFAULT_HOST;
            int port = args.length > 2 ? parsePort(args[2], DEFAULT_PORT) : DEFAULT_PORT;
            installLookAndFeel();
            launchClient(host, port);
            return;
        }

        printUsage();
    }

    private static void showLauncher() {
        Object[] options = {"Chay server", "Mo client", "Thoat"};
        int choice = JOptionPane.showOptionDialog(
                null,
                "Simple Realtime Chat\n\nChon che do ban muon mo:",
                "Java Chat Box",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]);

        if (choice == 0) {
            promptAndStartServer();
        } else if (choice == 1) {
            promptAndOpenClient();
        }
    }

    private static void promptAndStartServer() {
        String portText = JOptionPane.showInputDialog(
                null,
                "Nhap cong server:",
                String.valueOf(DEFAULT_PORT));

        if (portText == null) {
            return;
        }

        int port = parsePort(portText, DEFAULT_PORT);
        Thread serverThread = new Thread(() -> startServer(port), "chat-server-main");
        serverThread.start();

        JOptionPane.showMessageDialog(
                null,
                "Server dang chay o cong " + port + ".\nHay mo them mot instance nua de ket noi client.",
                "Server Started",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static void promptAndOpenClient() {
        String host = JOptionPane.showInputDialog(null, "Dia chi server:", DEFAULT_HOST);
        if (host == null) {
            return;
        }

        String portText = JOptionPane.showInputDialog(
                null,
                "Cong server:",
                String.valueOf(DEFAULT_PORT));
        if (portText == null) {
            return;
        }

        launchClient(defaultIfBlank(host, DEFAULT_HOST), parsePort(portText, DEFAULT_PORT));
    }

    private static void launchClient(String host, int port) {
        SwingUtilities.invokeLater(() -> {
            ChatClientFrame frame = new ChatClientFrame(host, port);
            frame.setVisible(true);
        });
    }

    private static void installLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // If the OS look & feel cannot be loaded, fall back to default.
        }
    }

    private static void startServer(int port) {
        System.out.println("Simple Realtime Chat Server");
        System.out.println("Dang lang nghe tai cong " + port);
        System.out.println("Mo client (JavaFX embedded) bang lenh (cap nhat module-path toi JavaFX SDK cua ban):");
        System.out.println(
                "  java --module-path <path-to-javafx-sdk>/lib --add-modules javafx.controls,javafx.swing -cp bin App client 127.0.0.1 "
                        + port);

        try {
            new ChatServer(port).start();
        } catch (IOException e) {
            System.err.println("Khong the khoi dong server: " + e.getMessage());
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

    private static void printUsage() {
        System.out.println("Cach dung:");
        System.out.println("  java -cp bin App server [port]");
        System.out.println(
                "  java --module-path <path-to-javafx-sdk>/lib --add-modules javafx.controls,javafx.swing -cp bin App client [host] [port]");
    }
}
