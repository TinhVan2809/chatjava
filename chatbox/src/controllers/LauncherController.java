package controllers;

import core.DesktopApp;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class LauncherController {
    @FXML
    private TextField hostField;
    @FXML
    private TextField portField;
    @FXML
    private Label statusLabel;
    @FXML
    private Button startServerButton;

    private DesktopApp app;

    public void setApp(DesktopApp app, String defaultHost, int defaultPort) {
        this.app = app;
        hostField.setText(defaultHost);
        portField.setText(String.valueOf(defaultPort));
        statusLabel.setText("Use one app instance for the server and open another for clients.");
    }

    @FXML
    private void onOpenClient() {
        int port = parsePort();
        if (port < 0) {
            return;
        }

        app.showAuthView(hostField.getText().trim(), port);
    }

    @FXML
    private void onStartServer() {
        int port = parsePort();
        if (port < 0) {
            return;
        }

        startServerButton.setDisable(true);
        app.startEmbeddedServer(port, message -> {
            statusLabel.setText(message);
            if (message.startsWith("Unable")) {
                startServerButton.setDisable(false);
            }
        });
    }

    @FXML
    private void onExit() {
        app.exitApplication();
    }

    private int parsePort() {
        try {
            int port = Integer.parseInt(portField.getText().trim());
            if (port <= 0 || port > 65535) {
                statusLabel.setText("Port must be between 1 and 65535.");
                return -1;
            }
            return port;
        } catch (NumberFormatException ex) {
            statusLabel.setText("Port must be a valid number.");
            return -1;
        }
    }
}
