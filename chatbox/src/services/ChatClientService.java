package services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import core.ChatProtocol;
import javafx.application.Platform;
import models.ClientSession;

public final class ChatClientService {
    private final ClientSession session;
    private volatile boolean manualDisconnect;
    private volatile ChatClientListener listener;

    public ChatClientService(ClientSession session) {
        this.session = session;
    }

    public static ClientSession login(String host, int port, String username, String password)
            throws IOException, AuthException {
        return authenticate("LOGIN", host, port, null, username, password);
    }

    public static ClientSession register(String host, int port, String fullName, String username, String password)
            throws IOException, AuthException {
        return authenticate("REGISTER", host, port, fullName, username, password);
    }

    public ClientSession getSession() {
        return session;
    }

    public void setListener(ChatClientListener listener) {
        this.listener = listener;
    }

    public void startListening() {
        Thread listenerThread = new Thread(this::readLoop, "fx-chat-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void sendGroupMessage(String text) {
        String safeText = text == null ? "" : text.trim();
        if (!safeText.isEmpty()) {
            session.getWriter().println(ChatProtocol.encode("MSG", safeText));
        }
    }

    public void sendPrivateMessage(String toUsername, String messageId, String text) {
        String safeUsername = normalizeUsername(toUsername);
        String safeMessage = text == null ? "" : text.trim();
        if (!safeUsername.isEmpty() && !safeMessage.isEmpty()) {
            session.getWriter().println(ChatProtocol.encode("PM", safeUsername, messageId, safeMessage));
        }
    }

    public void sendGroupImage(String fileName, String mimeType, byte[] imageBytes) {
        sendBinaryCommand("IMG", fileName, mimeType, imageBytes);
    }

    public void sendGroupFile(String fileName, String mimeType, byte[] fileBytes) {
        sendBinaryCommand("FILE", fileName, mimeType, fileBytes);
    }

    public void sendPrivateImage(String toUsername, String fileName, String mimeType, byte[] imageBytes) {
        String safeUsername = normalizeUsername(toUsername);
        if (safeUsername.isEmpty() || imageBytes == null || imageBytes.length == 0) {
            return;
        }

        session.getWriter().println(ChatProtocol.encodeBytes(
                "PM_IMG",
                utf8(safeUsername),
                utf8(fileName),
                utf8(mimeType),
                imageBytes));
    }

    public void sendPrivateFile(String toUsername, String fileName, String mimeType, byte[] fileBytes) {
        String safeUsername = normalizeUsername(toUsername);
        if (safeUsername.isEmpty() || fileBytes == null || fileBytes.length == 0) {
            return;
        }

        session.getWriter().println(ChatProtocol.encodeBytes(
                "PM_FILE",
                utf8(safeUsername),
                utf8(fileName),
                utf8(mimeType),
                fileBytes));
    }

    public void sendPrivateSeen(String originalSenderUsername, String messageId) {
        String safeUsername = normalizeUsername(originalSenderUsername);
        String safeMessageId = messageId == null ? "" : messageId.trim();
        if (!safeUsername.isEmpty() && !safeMessageId.isEmpty()) {
            session.getWriter().println(ChatProtocol.encode("PM_SEEN", safeUsername, safeMessageId));
        }
    }

    public void sendGroupTyping(boolean typing) {
        session.getWriter().println(ChatProtocol.encode("TYPING", typing ? "1" : "0"));
    }

    public void sendPrivateTyping(String toUsername, boolean typing) {
        String safeUsername = normalizeUsername(toUsername);
        if (!safeUsername.isEmpty()) {
            session.getWriter().println(ChatProtocol.encode("PM_TYPING", safeUsername, typing ? "1" : "0"));
        }
    }

    public void sendAvatarUpdate(byte[] avatarBytes) {
        if (avatarBytes == null || avatarBytes.length == 0) {
            return;
        }

        session.getWriter().println(ChatProtocol.encodeBytes("UPDATE_AVATAR", avatarBytes));
    }

    public void disconnect() {
        manualDisconnect = true;
        try {
            session.getWriter().println(ChatProtocol.encode("QUIT"));
        } catch (Exception ignored) {
        }
        session.closeQuietly();
    }

    private void readLoop() {
        try {
            String line;
            BufferedReader reader = session.getReader();
            while ((line = reader.readLine()) != null) {
                handleIncoming(line);
            }
            if (!manualDisconnect) {
                dispatchConnectionClosed("Connection closed by server.");
            }
        } catch (IOException ex) {
            if (!manualDisconnect) {
                dispatchConnectionClosed("Lost connection: " + ex.getMessage());
            }
        } finally {
            session.closeQuietly();
        }
    }

    private void handleIncoming(String line) {
        ChatProtocol.Command command;
        try {
            command = ChatProtocol.decode(line);
        } catch (IllegalArgumentException ex) {
            dispatchConnectionClosed("Received invalid data from server.");
            return;
        }

        switch (command.name()) {
            case "CHAT" -> {
                if (command.hasFields(1)) {
                    dispatch(listener -> listener.onGroupChatLine(command.field(0)));
                }
            }
            case "ONLINE" -> dispatch(listener -> listener.onOnlineUsers(Arrays.asList(command.fieldsText())));
            case "TYPING" -> {
                if (command.hasFields(3)) {
                    dispatch(listener -> listener.onGroupTyping(
                            normalizeUsername(command.field(0)),
                            command.field(1),
                            parseTyping(command.field(2))));
                }
            }
            case "PM" -> {
                if (command.hasFields(4)) {
                    dispatch(listener -> listener.onPrivateMessage(
                            normalizeUsername(command.field(0)),
                            command.field(1),
                            command.field(2),
                            command.field(3)));
                }
            }
            case "PM_SENT" -> {
                if (command.hasFields(4)) {
                    dispatch(listener -> listener.onPrivateMessageSent(
                            normalizeUsername(command.field(0)),
                            command.field(1),
                            command.field(2),
                            command.field(3)));
                }
            }
            case "PM_READ" -> {
                if (command.hasFields(2)) {
                    dispatch(listener -> listener.onPrivateRead(
                            normalizeUsername(command.field(0)),
                            command.field(1)));
                }
            }
            case "PM_TYPING" -> {
                if (command.hasFields(3)) {
                    dispatch(listener -> listener.onPrivateTyping(
                            normalizeUsername(command.field(0)),
                            command.field(1),
                            parseTyping(command.field(2))));
                }
            }
            case "PM_ERROR" -> {
                if (command.hasFields(2)) {
                    dispatch(listener -> listener.onPrivateSystemMessage(
                            normalizeUsername(command.field(0)),
                            command.field(1)));
                }
            }
            case "INFO" -> {
                if (command.hasFields(1)) {
                    dispatch(listener -> listener.onGroupChatLine("[System] " + command.field(0)));
                }
            }
            case "IMG" -> {
                if (command.hasFields(6)) {
                    dispatch(listener -> listener.onGroupImage(
                            command.field(0),
                            normalizeUsername(command.field(1)),
                            command.field(2),
                            command.field(3),
                            command.field(4),
                            command.fieldBytes(5)));
                }
            }
            case "FILE" -> {
                if (command.hasFields(6)) {
                    dispatch(listener -> listener.onGroupFile(
                            command.field(0),
                            normalizeUsername(command.field(1)),
                            command.field(2),
                            command.field(3),
                            command.field(4),
                            command.fieldBytes(5)));
                }
            }
            case "PM_IMG" -> {
                if (command.hasFields(5)) {
                    dispatch(listener -> listener.onPrivateImage(
                            normalizeUsername(command.field(0)),
                            command.field(1),
                            command.field(2),
                            command.field(3),
                            command.fieldBytes(4)));
                }
            }
            case "PM_FILE" -> {
                if (command.hasFields(5)) {
                    dispatch(listener -> listener.onPrivateFile(
                            normalizeUsername(command.field(0)),
                            command.field(1),
                            command.field(2),
                            command.field(3),
                            command.fieldBytes(4)));
                }
            }
            case "PM_IMG_SENT" -> {
                if (command.hasFields(5)) {
                    dispatch(listener -> listener.onPrivateImageSent(
                            normalizeUsername(command.field(0)),
                            command.field(1),
                            command.field(2),
                            command.field(3),
                            command.fieldBytes(4)));
                }
            }
            case "PM_FILE_SENT" -> {
                if (command.hasFields(5)) {
                    dispatch(listener -> listener.onPrivateFileSent(
                            normalizeUsername(command.field(0)),
                            command.field(1),
                            command.field(2),
                            command.field(3),
                            parseSize(command.field(4))));
                }
            }
            case "USER_AVATAR" -> {
                if (command.hasFields(2)) {
                    dispatch(listener -> listener.onUserAvatarUpdated(
                            normalizeUsername(command.field(0)),
                            command.fieldBytes(1)));
                }
            }
            default -> {
            }
        }
    }

    private void dispatch(java.util.function.Consumer<ChatClientListener> action) {
        ChatClientListener currentListener = listener;
        if (currentListener == null) {
            return;
        }

        Platform.runLater(() -> action.accept(currentListener));
    }

    private void dispatchConnectionClosed(String message) {
        dispatch(listener -> listener.onConnectionClosed(message));
    }

    private static ClientSession authenticate(String mode, String host, int port, String fullName, String username,
            String password) throws IOException, AuthException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);

        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

        try {
            if ("REGISTER".equals(mode)) {
                writer.println(ChatProtocol.encode(mode, fullName, username, password));
            } else {
                writer.println(ChatProtocol.encode(mode, username, password));
            }

            String responseLine = reader.readLine();
            if (responseLine == null) {
                throw new IOException("Server did not respond.");
            }

            ChatProtocol.Command response = ChatProtocol.decode(responseLine);
            if ("AUTH_OK".equals(response.name()) && response.hasFields(2)) {
                return new ClientSession(
                        socket,
                        reader,
                        writer,
                        host,
                        port,
                        response.field(1),
                        response.field(0));
            }

            String errorMessage = response.hasFields(1) ? response.field(0) : "Authentication failed.";
            throw new AuthException(errorMessage);
        } catch (IOException | RuntimeException ex) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            throw ex;
        }
    }

    private static boolean parseTyping(String value) {
        String safeValue = value == null ? "" : value.trim();
        return "1".equals(safeValue)
                || "true".equalsIgnoreCase(safeValue)
                || "start".equalsIgnoreCase(safeValue);
    }

    private static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private void sendBinaryCommand(String command, String fileName, String mimeType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return;
        }

        session.getWriter().println(ChatProtocol.encodeBytes(
                command,
                utf8(fileName),
                utf8(mimeType),
                bytes));
    }

    private static byte[] utf8(String value) {
        return (value == null ? "" : value.trim()).getBytes(StandardCharsets.UTF_8);
    }

    private static long parseSize(String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }

        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    public static final class AuthException extends Exception {
        public AuthException(String message) {
            super(message);
        }
    }
}
