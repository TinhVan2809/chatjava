package core;

import java.io.IOException;
import java.net.URL;

import controllers.AuthController;
import controllers.LauncherController;
import controllers.MessengerController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import models.ClientSession;
import services.ChatClientService;

public class DesktopApp extends Application {
    private enum LaunchMode {
        LAUNCHER,
        CLIENT
    }

    private static final int DEFAULT_PORT = 5000;
    private static final String DEFAULT_HOST = "127.0.0.1";

    private static LaunchMode launchMode = LaunchMode.LAUNCHER;
    private static String bootHost = DEFAULT_HOST;
    private static int bootPort = DEFAULT_PORT;

    private Stage primaryStage;
    private Thread embeddedServerThread;
    private Integer embeddedServerPort;

    public static void configureLaunch(boolean clientMode, String host, int port) {
        launchMode = clientMode ? LaunchMode.CLIENT : LaunchMode.LAUNCHER;
        bootHost = host == null || host.isBlank() ? DEFAULT_HOST : host.trim();
        bootPort = port > 0 ? port : DEFAULT_PORT;
    }

    public static void launchDesktop(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(760);

        if (launchMode == LaunchMode.CLIENT) {
            showAuthView(bootHost, bootPort);
        } else {
            showLauncherView();
        }

        primaryStage.show();
    }

    public void showLauncherView() {
        try {
            FXMLLoader loader = new FXMLLoader(requireResource("views/LauncherView.fxml"));
            Parent root = loader.load();
            LauncherController controller = loader.getController();
            controller.setApp(this, DEFAULT_HOST, DEFAULT_PORT);

            Scene scene = createScene(root);
            primaryStage.setTitle("Realtime Messenger");
            primaryStage.setScene(scene);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load launcher view.", ex);
        }
    }

    public void showAuthView(String host, int port) {
        showAuthView(host, port, null, false);
    }

    public void showAuthView(String host, int port, String statusMessage, boolean error) {
        try {
            FXMLLoader loader = new FXMLLoader(requireResource("views/AuthView.fxml"));
            Parent root = loader.load();
            AuthController controller = loader.getController();
            controller.setApp(this, defaultIfBlank(host, DEFAULT_HOST), port > 0 ? port : DEFAULT_PORT);
            controller.setInitialStatus(statusMessage, error);

            Scene scene = createScene(root);
            primaryStage.setTitle("Realtime Messenger - Sign In");
            primaryStage.setScene(scene);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load auth view.", ex);
        }
    }

    public void showMessengerView(ClientSession session) {
        try {
            FXMLLoader loader = new FXMLLoader(requireResource("views/MessengerView.fxml"));
            Parent root = loader.load();
            MessengerController controller = loader.getController();
            ChatClientService service = new ChatClientService(session);
            controller.setApp(this, service);
            service.setListener(controller);
            service.startListening();

            Scene scene = createScene(root);
            primaryStage.setTitle("Realtime Messenger - " + session.getFullName());
            primaryStage.setScene(scene);
            primaryStage.setOnCloseRequest(event -> controller.shutdown());
        } catch (IOException ex) {
            session.closeQuietly();
            throw new IllegalStateException("Unable to load messenger view.", ex);
        }
    }

    public void logoutToAuth(ClientSession session) {
        primaryStage.setOnCloseRequest(null);
        showAuthView(session.getHost(), session.getPort(), "Signed out successfully.", false);
    }

    public void exitApplication() {
        Platform.exit();
    }

    public void startEmbeddedServer(int port, java.util.function.Consumer<String> statusCallback) {
        if (embeddedServerThread != null && embeddedServerThread.isAlive()) {
            if (statusCallback != null) {
                statusCallback.accept("Server is already running on port " + embeddedServerPort + ".");
            }
            return;
        }

        embeddedServerPort = port;
        embeddedServerThread = new Thread(() -> {
            try {
                if (statusCallback != null) {
                    Platform.runLater(() -> statusCallback.accept(
                            "Server is running on port " + port + ". Accounts file: "
                                    + StoragePaths.usersFile().toAbsolutePath()));
                }
                new ChatServer(port).start();
            } catch (IOException ex) {
                if (statusCallback != null) {
                    Platform.runLater(() -> statusCallback.accept("Unable to start server: " + ex.getMessage()));
                }
            }
        }, "embedded-chat-server");
        embeddedServerThread.setDaemon(true);
        embeddedServerThread.start();
    }

    private Scene createScene(Parent root) {
        Scene scene = new Scene(root);
        String stylesheet = UiResources.stylesheet("css/MessengerStyle.css");
        if (!stylesheet.isBlank()) {
            scene.getStylesheets().setAll(stylesheet);
        }
        return scene;
    }

    private static URL requireResource(String relativePath) {
        URL resource = UiResources.fxml(relativePath);
        if (resource == null) {
            throw new IllegalStateException("Missing resource: " + relativePath);
        }
        return resource;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
