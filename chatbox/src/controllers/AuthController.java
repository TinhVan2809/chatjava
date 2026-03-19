package controllers;

import java.io.IOException;

import core.DesktopApp;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import models.ClientSession;
import services.ChatClientService;

public class AuthController {
    @FXML
    private TextField hostField;
    @FXML
    private TextField portField;
    @FXML
    private TextField loginUsernameField;
    @FXML
    private PasswordField loginPasswordField;
    @FXML
    private TextField registerNameField;
    @FXML
    private TextField registerUsernameField;
    @FXML
    private PasswordField registerPasswordField;
    @FXML
    private Label statusLabel;
    @FXML
    private Button loginButton;
    @FXML
    private Button registerButton;

    private DesktopApp app;

    public void setApp(DesktopApp app, String host, int port) {
        this.app = app;
        hostField.setText(host);
        portField.setText(String.valueOf(port));
        updateStatus("Connect to server and sign in to start chatting.", false);
    }

    public void setInitialStatus(String message, boolean error) {
        if (message != null && !message.isBlank()) {
            updateStatus(message, error);
        }
    }

    @FXML
    private void initialize() {
        loginPasswordField.setOnAction(event -> onLogin());
        registerPasswordField.setOnAction(event -> onRegister());
    }

    @FXML
    private void onBack() {
        if (app != null) {
            app.showLauncherView();
        }
    }

    @FXML
    private void onLogin() {
        String username = loginUsernameField.getText().trim();
        String password = loginPasswordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            updateStatus("Username and password are required.", true);
            return;
        }

        HostPort hostPort = readHostPort();
        if (hostPort == null) {
            return;
        }

        runAuthentication(() -> ChatClientService.login(hostPort.host(), hostPort.port(), username, password));
    }

    @FXML
    private void onRegister() {
        String fullName = registerNameField.getText().trim();
        String username = registerUsernameField.getText().trim();
        String password = registerPasswordField.getText();

        if (fullName.isEmpty() || username.isEmpty() || password.isEmpty()) {
            updateStatus("Full name, username, and password are required.", true);
            return;
        }

        HostPort hostPort = readHostPort();
        if (hostPort == null) {
            return;
        }

        runAuthentication(() -> ChatClientService.register(hostPort.host(), hostPort.port(), fullName, username, password));
    }

    private void runAuthentication(AuthAction action) {
        setBusy(true);
        updateStatus("Connecting...", false);

        Thread worker = new Thread(() -> {
            try {
                ClientSession session = action.run();
                Platform.runLater(() -> app.showMessengerView(session));
            } catch (ChatClientService.AuthException ex) {
                Platform.runLater(() -> {
                    setBusy(false);
                    updateStatus(ex.getMessage(), true);
                });
            } catch (IOException ex) {
                Platform.runLater(() -> {
                    setBusy(false);
                    updateStatus("Unable to connect to server: " + ex.getMessage(), true);
                });
            }
        }, "auth-request");
        worker.setDaemon(true);
        worker.start();
    }

    private HostPort readHostPort() {
        String host = hostField.getText().trim();
        if (host.isEmpty()) {
            updateStatus("Host is required.", true);
            return null;
        }

        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            updateStatus("Port must be a valid number.", true);
            return null;
        }

        if (port <= 0 || port > 65535) {
            updateStatus("Port must be between 1 and 65535.", true);
            return null;
        }

        return new HostPort(host, port);
    }

    private void setBusy(boolean busy) {
        loginButton.setDisable(busy);
        registerButton.setDisable(busy);
        hostField.setDisable(busy);
        portField.setDisable(busy);
        loginUsernameField.setDisable(busy);
        loginPasswordField.setDisable(busy);
        registerNameField.setDisable(busy);
        registerUsernameField.setDisable(busy);
        registerPasswordField.setDisable(busy);
    }

    private void updateStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("status-error", "status-success");
        statusLabel.getStyleClass().add(error ? "status-error" : "status-success");
    }

    @FunctionalInterface
    private interface AuthAction {
        ClientSession run() throws IOException, ChatClientService.AuthException;
    }

    private record HostPort(String host, int port) {
    }
}
