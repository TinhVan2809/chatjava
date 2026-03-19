package core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

// Server socket nhan client, xu ly dang ky/dang nhap va broadcast tin nhan theo thoi gian thuc.
public class ChatServer {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024; // 2MB
    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024; // 10MB

    private final int port;
    private final UserStore userStore;
    // Tat ca client da xac thuc (dang o trong phong chat).
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    // Set de chan 1 tai khoan dang nhap 2 noi cung luc.
    private final Set<String> onlineUsers = ConcurrentHashMap.newKeySet();
    // Map username -> handler de gui tin nhan rieng (PM) nhanh.
    private final ConcurrentHashMap<String, ClientHandler> clientsByUsername = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> activeCallPeers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> activeCallIds = new ConcurrentHashMap<>();
    private final ExecutorService clientPool = Executors.newCachedThreadPool();

    // Khoi tao server va nap UserStore de dang ky/dang nhap.
    public ChatServer(int port) throws IOException {
        this.port = port;
        this.userStore = new UserStore(StoragePaths.usersFile());
    }

    // Lang nghe ket noi va tao handler rieng cho moi client.
    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log("Server da san sang.");
            log("Du lieu tai khoan: " + userStore.getStoragePath().toAbsolutePath());

            while (true) {
                Socket socket = serverSocket.accept();
                clientPool.execute(new ClientHandler(socket));
            }
        } finally {
            clientPool.shutdownNow();
        }
    }

    // Broadcast thong diep chat (duoi dang string da format) toi tat ca client.
    private void broadcast(String message) {
        log(message);
        String payload = ChatProtocol.encode("CHAT", message);
        for (ClientHandler client : clients) {
            client.send(payload);
        }
    }

    // Broadcast anh toi tat ca client (command IMG).
    private void broadcastImage(UserStore.UserAccount from, String fileName, String mimeType, byte[] imageBytes) {
        String time = LocalTime.now().format(TIME_FORMAT);
        log("[" + time + "] " + from.displayName() + " gui anh: " + fileName + " (" + imageBytes.length + " bytes)");

        String payload = ChatProtocol.encodeBytes(
                "IMG",
                utf8(time),
                utf8(from.username()),
                utf8(from.displayName()),
                utf8(fileName),
                utf8(mimeType),
                imageBytes);

        for (ClientHandler client : clients) {
            client.send(payload);
        }
    }

    // Broadcast file toi tat ca client (command FILE).
    private void broadcastFile(UserStore.UserAccount from, String fileName, String mimeType, byte[] fileBytes) {
        String time = LocalTime.now().format(TIME_FORMAT);
        log("[" + time + "] " + from.displayName() + " gui file: " + fileName + " (" + fileBytes.length + " bytes)");

        String payload = ChatProtocol.encodeBytes(
                "FILE",
                utf8(time),
                utf8(from.username()),
                utf8(from.displayName()),
                utf8(fileName),
                utf8(mimeType),
                fileBytes);

        for (ClientHandler client : clients) {
            client.send(payload);
        }
    }

    // Broadcast trang thai dang soan tin cho phong chat chung (khong gui lai cho chinh nguoi gui).
    private void broadcastTyping(UserStore.UserAccount from, boolean typing) {
        if (from == null) {
            return;
        }

        String payload = ChatProtocol.encode(
                "TYPING",
                from.username(),
                from.displayName(),
                typing ? "1" : "0");

        for (ClientHandler client : clients) {
            UserStore.UserAccount account = client.account;
            if (account == null) {
                continue;
            }
            if (account.username().equalsIgnoreCase(from.username())) {
                continue;
            }
            client.send(payload);
        }
    }

    private static byte[] utf8(String value) {
        return (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
    }

    private void sendStoredAvatarsTo(ClientHandler client) {
        if (client == null) {
            return;
        }

        Path avatarDirectory = StoragePaths.avatarDirectory();
        if (!Files.exists(avatarDirectory)) {
            return;
        }

        try (Stream<Path> avatarPaths = Files.list(avatarDirectory)) {
            avatarPaths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName() != null
                            && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted()
                    .forEach(path -> sendStoredAvatarFile(client, path));
        } catch (IOException ex) {
            log("Loi dong bo avatar cho client moi: " + ex.getMessage());
        }
    }

    private void sendStoredAvatarFile(ClientHandler client, Path avatarPath) {
        if (client == null || avatarPath == null || avatarPath.getFileName() == null) {
            return;
        }

        String fileName = avatarPath.getFileName().toString();
        int extensionIndex = fileName.lastIndexOf('.');
        String username = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
        if (username.isBlank()) {
            return;
        }

        try {
            byte[] avatarBytes = Files.readAllBytes(avatarPath);
            if (avatarBytes.length == 0 || avatarBytes.length > MAX_IMAGE_BYTES) {
                return;
            }

            client.send(ChatProtocol.encodeBytes("USER_AVATAR", utf8(username), avatarBytes));
        } catch (IOException ex) {
            log("Loi doc avatar " + fileName + ": " + ex.getMessage());
        }
    }

    private void broadcastStoredAvatarIfPresent(String username, ClientHandler excludedClient) {
        String safeUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (safeUsername.isEmpty()) {
            return;
        }

        Path avatarPath = StoragePaths.avatarDirectory().resolve(safeUsername + ".png");
        if (!Files.exists(avatarPath)) {
            return;
        }

        try {
            byte[] avatarBytes = Files.readAllBytes(avatarPath);
            if (avatarBytes.length == 0 || avatarBytes.length > MAX_IMAGE_BYTES) {
                return;
            }

            String payload = ChatProtocol.encodeBytes("USER_AVATAR", utf8(safeUsername), avatarBytes);
            for (ClientHandler client : clients) {
                if (client == excludedClient) {
                    continue;
                }
                client.send(payload);
            }
        } catch (IOException ex) {
            log("Loi phat avatar cho " + safeUsername + ": " + ex.getMessage());
        }
    }

    // Broadcast danh sach nguoi dang online de client cap nhat UI.
    private void broadcastOnlineUsers() {
        List<String> users = new ArrayList<>();
        for (ClientHandler client : clients) {
            UserStore.UserAccount account = client.account;
            if (account != null) {
                users.add(account.displayName());
            }
        }

        users.sort(String.CASE_INSENSITIVE_ORDER);
        String payload = ChatProtocol.encode("ONLINE", users.toArray(new String[0]));
        for (ClientHandler client : clients) {
            client.send(payload);
        }
    }

    // Format thong bao he thong co kem timestamp.
    private String formatSystemMessage(String text) {
        return "[" + LocalTime.now().format(TIME_FORMAT) + "] [He thong] " + text;
    }

    // Format tin nhan chat theo tai khoan (ho ten + username).
    private String formatChatMessage(UserStore.UserAccount account, String text) {
        return "[" + LocalTime.now().format(TIME_FORMAT) + "] " + account.displayName() + ": " + text;
    }

    // Ghi log ra console cua server.
    private void log(String message) {
        System.out.println(message);
    }

    private void startActiveCall(String callerUsername, String calleeUsername, String callId) {
        if (callerUsername == null || calleeUsername == null || callId == null) {
            return;
        }

        activeCallPeers.put(callerUsername, calleeUsername);
        activeCallPeers.put(calleeUsername, callerUsername);
        activeCallIds.put(callerUsername, callId);
        activeCallIds.put(calleeUsername, callId);
    }

    private void clearActiveCall(String username) {
        String safeUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (safeUsername.isEmpty()) {
            return;
        }

        String peerUsername = activeCallPeers.remove(safeUsername);
        String callId = activeCallIds.remove(safeUsername);
        if (peerUsername == null || peerUsername.isBlank()) {
            return;
        }

        activeCallPeers.remove(peerUsername, safeUsername);
        String peerCallId = activeCallIds.get(peerUsername);
        if (callId != null && callId.equals(peerCallId)) {
            activeCallIds.remove(peerUsername, callId);
        }
    }

    private boolean isMatchingActiveCall(String username, String peerUsername, String callId) {
        String safeUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        String safePeerUsername = peerUsername == null ? "" : peerUsername.trim().toLowerCase(Locale.ROOT);
        String safeCallId = callId == null ? "" : callId.trim();
        if (safeUsername.isEmpty() || safePeerUsername.isEmpty() || safeCallId.isEmpty()) {
            return false;
        }

        return safePeerUsername.equals(activeCallPeers.get(safeUsername))
                && safeCallId.equals(activeCallIds.get(safeUsername));
    }

    // Handler 1 ket noi socket: auth truoc, sau do moi nhan/gui tin nhan chat.
    private final class ClientHandler implements Runnable {
        private final Socket socket;
        private final Object sendLock = new Object();
        private PrintWriter writer;
        private UserStore.UserAccount account;
        private boolean authenticated;

        private ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (socket;
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    PrintWriter socketWriter = new PrintWriter(
                            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
                writer = socketWriter;

                String line;
                while ((line = reader.readLine()) != null) {
                    ChatProtocol.Command command;
                    try {
                        command = ChatProtocol.decode(line);
                    } catch (IllegalArgumentException ex) {
                        if (authenticated) {
                            send(ChatProtocol.encode("CHAT", formatSystemMessage(ex.getMessage())));
                        } else {
                            send(ChatProtocol.encode("AUTH_ERROR", ex.getMessage()));
                        }
                        continue;
                    }

                    if (!authenticated) {
                        // Chi chap nhan REGISTER/LOGIN truoc khi vao phong chat.
                        handleAuthCommand(command);
                        continue;
                    }

                    if (!handleChatCommand(command)) {
                        break;
                    }
                }
            } catch (IOException ex) {
                if (authenticated && account != null) {
                    log(formatSystemMessage("Mat ket noi voi " + account.displayName() + "."));
                }
            } finally {
                disconnect();
            }
        }

        // Xu ly REGISTER/LOGIN. Neu thanh cong thi them vao phong chat va gui AUTH_OK.
        private void handleAuthCommand(ChatProtocol.Command command) {
            UserStore.AuthResult result;

            switch (command.name()) {
                case "REGISTER" -> {
                    if (!command.hasFields(3)) {
                        send(ChatProtocol.encode("AUTH_ERROR", "Thong tin dang ky chua day du."));
                        return;
                    }
                    result = userStore.register(command.field(0), command.field(1), command.field(2));
                }
                case "LOGIN" -> {
                    if (!command.hasFields(2)) {
                        send(ChatProtocol.encode("AUTH_ERROR", "Thong tin dang nhap chua day du."));
                        return;
                    }
                    result = userStore.authenticate(command.field(0), command.field(1));
                }
                default -> {
                    send(ChatProtocol.encode("AUTH_ERROR", "Hay dang nhap hoac dang ky truoc khi chat."));
                    return;
                }
            }

            if (!result.success()) {
                send(ChatProtocol.encode("AUTH_ERROR", result.message()));
                return;
            }

            UserStore.UserAccount authenticatedUser = result.user();
            if (!onlineUsers.add(authenticatedUser.username())) {
                send(ChatProtocol.encode("AUTH_ERROR", "Tai khoan nay dang duoc su dung o mot noi khac."));
                return;
            }

            account = authenticatedUser;
            authenticated = true;

            send(ChatProtocol.encode("AUTH_OK", account.fullName(), account.username()));
            sendStoredAvatarsTo(this);

            clients.add(this);
            clientsByUsername.put(account.username(), this);
            broadcastStoredAvatarIfPresent(account.username(), this);
            broadcast(formatSystemMessage(account.displayName() + " da tham gia phong chat."));
            broadcastOnlineUsers();
        }

        // Xu ly tin nhan chat hoac lenh QUIT.
        private boolean handleChatCommand(ChatProtocol.Command command) {
            if ("MSG".equals(command.name())) {
                if (!command.hasFields(1)) {
                    send(ChatProtocol.encode("CHAT", formatSystemMessage("Tin nhan khong hop le.")));
                    return true;
                }

                String message = command.field(0).trim();
                if (message.isEmpty()) {
                    return true;
                }

                if (message.length() > 400) {
                    message = message.substring(0, 400);
                }

                broadcast(formatChatMessage(account, message));
                return true;
            }

            if ("IMG".equals(command.name())) {
                if (!command.hasFields(3)) {
                    send(ChatProtocol.encode("CHAT", formatSystemMessage("Anh gui len khong hop le.")));
                    return true;
                }

                String fileName = command.field(0) == null ? "" : command.field(0).trim();
                if (fileName.isEmpty()) {
                    fileName = "image";
                }
                if (fileName.length() > 120) {
                    fileName = fileName.substring(0, 120);
                }

                String mimeType = command.field(1) == null ? "" : command.field(1).trim();
                if (mimeType.isEmpty()) {
                    mimeType = "application/octet-stream";
                }

                byte[] imageBytes = command.fieldBytes(2);
                if (imageBytes == null || imageBytes.length == 0) {
                    return true;
                }
                if (imageBytes.length > MAX_IMAGE_BYTES) {
                    send(ChatProtocol.encode("CHAT",
                            formatSystemMessage("Anh vuot qua gioi han " + (MAX_IMAGE_BYTES / (1024 * 1024)) + "MB.")));
                    return true;
                }

                broadcastImage(account, fileName, mimeType, imageBytes);
                return true;
            }

            if ("FILE".equals(command.name())) {
                if (!command.hasFields(3)) {
                    send(ChatProtocol.encode("CHAT", formatSystemMessage("File gui len khong hop le.")));
                    return true;
                }

                String fileName = command.field(0) == null ? "" : command.field(0).trim();
                if (fileName.isEmpty()) {
                    fileName = "file";
                }
                if (fileName.length() > 160) {
                    fileName = fileName.substring(0, 160);
                }

                String mimeType = command.field(1) == null ? "" : command.field(1).trim();
                if (mimeType.isEmpty()) {
                    mimeType = "application/octet-stream";
                }

                byte[] fileBytes = command.fieldBytes(2);
                if (fileBytes == null || fileBytes.length == 0) {
                    return true;
                }
                if (fileBytes.length > MAX_FILE_BYTES) {
                    send(ChatProtocol.encode("CHAT",
                            formatSystemMessage("File vuot qua gioi han " + (MAX_FILE_BYTES / (1024 * 1024)) + "MB.")));
                    return true;
                }

                broadcastFile(account, fileName, mimeType, fileBytes);
                return true;
            }

            if ("TYPING".equals(command.name())) {
                if (!command.hasFields(1)) {
                    return true;
                }

                String state = command.field(0) == null ? "" : command.field(0).trim();
                boolean typing = "1".equals(state) || "true".equalsIgnoreCase(state) || "start".equalsIgnoreCase(state);
                broadcastTyping(account, typing);
                return true;
            }

            if ("PM_TYPING".equals(command.name())) {
                if (!command.hasFields(2)) {
                    return true;
                }

                String toUsername = command.field(0).trim().toLowerCase(Locale.ROOT);
                if (toUsername.isEmpty()) {
                    return true;
                }

                if (account != null && toUsername.equals(account.username())) {
                    return true;
                }

                String state = command.field(1) == null ? "" : command.field(1).trim();
                boolean typing = "1".equals(state) || "true".equalsIgnoreCase(state) || "start".equalsIgnoreCase(state);

                ClientHandler recipient = clientsByUsername.get(toUsername);
                if (recipient == null || recipient.account == null) {
                    return true;
                }

                if (account == null) {
                    return true;
                }

                recipient.send(ChatProtocol.encode(
                        "PM_TYPING",
                        account.username(),
                        account.displayName(),
                        typing ? "1" : "0"));
                return true;
            }

            if ("PM_SEEN".equals(command.name())) {
                if (!command.hasFields(2)) {
                    return true; // Ignore malformed command
                }

                String originalSenderUsername = command.field(0).trim().toLowerCase(Locale.ROOT);
                String messageId = command.field(1).trim();

                if (originalSenderUsername.isEmpty() || messageId.isEmpty()) {
                    return true;
                }

                // Find the original sender's handler to forward the receipt
                ClientHandler originalSender = clientsByUsername.get(originalSenderUsername);
                if (originalSender == null || originalSender.account == null) {
                    return true; // Original sender is offline
                }

                // Forward the read receipt. The sender of this PM_SEEN command is the one who read it.
                if (account != null) {
                    originalSender.send(ChatProtocol.encode(
                            "PM_READ",
                            account.username(), // The user who read the message
                            messageId));
                }
                return true;
            }

            if ("PM".equals(command.name())) {
                if (!command.hasFields(3)) {
                    send(ChatProtocol.encode("PM_ERROR", "", "Thong tin gui tin nhan rieng chua day du."));
                    return true;
                }

                String toUsername = command.field(0).trim().toLowerCase(Locale.ROOT);
                if (toUsername.isEmpty()) {
                    send(ChatProtocol.encode("PM_ERROR", "", "Khong tim thay nguoi nhan."));
                    return true;
                }

                String messageId = command.field(1).trim();
                String message = command.field(2).trim();
                if (message.isEmpty()) {
                    return true;
                }

                if (message.length() > 400) {
                    message = message.substring(0, 400);
                }

                if (account != null && toUsername.equals(account.username())) {
                    send(ChatProtocol.encode("PM_ERROR", toUsername, "Khong the gui tin nhan cho chinh minh."));
                    return true;
                }

                ClientHandler recipient = clientsByUsername.get(toUsername);
                if (recipient == null || recipient.account == null) {
                    send(ChatProtocol.encode("PM_ERROR", toUsername, "Nguoi nhan hien dang offline."));
                    return true;
                }

                recipient.send(ChatProtocol.encode("PM", account.username(), account.displayName(), messageId, message));
                send(ChatProtocol.encode("PM_SENT", recipient.account.username(), recipient.account.displayName(), messageId, message));
                return true;
            }

            if ("PM_FILE".equals(command.name())) {
                if (!command.hasFields(4)) {
                    send(ChatProtocol.encode("PM_ERROR", "", "Thong tin gui file rieng chua day du."));
                    return true;
                }

                String toUsername = command.field(0).trim().toLowerCase(Locale.ROOT);
                if (toUsername.isEmpty()) {
                    send(ChatProtocol.encode("PM_ERROR", "", "Khong tim thay nguoi nhan."));
                    return true;
                }

                String fileName = command.field(1) == null ? "" : command.field(1).trim();
                if (fileName.isEmpty()) {
                    fileName = "file";
                }
                if (fileName.length() > 160) {
                    fileName = fileName.substring(0, 160);
                }

                String mimeType = command.field(2) == null ? "" : command.field(2).trim();
                if (mimeType.isEmpty()) {
                    mimeType = "application/octet-stream";
                }

                byte[] fileBytes = command.fieldBytes(3);
                if (fileBytes == null || fileBytes.length == 0) {
                    return true;
                }
                if (fileBytes.length > MAX_FILE_BYTES) {
                    send(ChatProtocol.encode("PM_ERROR", toUsername,
                            "File vuot qua gioi han " + (MAX_FILE_BYTES / (1024 * 1024)) + "MB."));
                    return true;
                }

                if (account != null && toUsername.equals(account.username())) {
                    send(ChatProtocol.encode("PM_ERROR", toUsername, "Khong the gui file cho chinh minh."));
                    return true;
                }

                ClientHandler recipient = clientsByUsername.get(toUsername);
                if (recipient == null || recipient.account == null) {
                    send(ChatProtocol.encode("PM_ERROR", toUsername, "Nguoi nhan hien dang offline."));
                    return true;
                }

                recipient.send(ChatProtocol.encodeBytes(
                        "PM_FILE",
                        utf8(account.username()),
                        utf8(account.displayName()),
                        utf8(fileName),
                        utf8(mimeType),
                        fileBytes));

                // Chi can ACK ve metadata + size (khong can gui lai bytes).
                send(ChatProtocol.encode(
                        "PM_FILE_SENT",
                        recipient.account.username(),
                        recipient.account.displayName(),
                        fileName,
                        mimeType,
                        String.valueOf(fileBytes.length)));
                return true;
            }

            if ("PM_IMG".equals(command.name())) {
                if (!command.hasFields(4)) {
                    send(ChatProtocol.encode("PM_ERROR", "", "Thong tin gui anh rieng chua day du."));
                    return true;
                }

                String toUsername = command.field(0).trim().toLowerCase(Locale.ROOT);
                if (toUsername.isEmpty()) {
                    send(ChatProtocol.encode("PM_ERROR", "", "Khong tim thay nguoi nhan."));
                    return true;
                }

                String fileName = command.field(1) == null ? "" : command.field(1).trim();
                if (fileName.isEmpty()) {
                    fileName = "image";
                }
                if (fileName.length() > 120) {
                    fileName = fileName.substring(0, 120);
                }

                String mimeType = command.field(2) == null ? "" : command.field(2).trim();
                if (mimeType.isEmpty()) {
                    mimeType = "application/octet-stream";
                }

                byte[] imageBytes = command.fieldBytes(3);
                if (imageBytes == null || imageBytes.length == 0) {
                    return true;
                }
                if (imageBytes.length > MAX_IMAGE_BYTES) {
                    send(ChatProtocol.encode("PM_ERROR", toUsername,
                            "Anh vuot qua gioi han " + (MAX_IMAGE_BYTES / (1024 * 1024)) + "MB."));
                    return true;
                }

                if (account != null && toUsername.equals(account.username())) {
                    send(ChatProtocol.encode("PM_ERROR", toUsername, "Khong the gui anh cho chinh minh."));
                    return true;
                }

                ClientHandler recipient = clientsByUsername.get(toUsername);
                if (recipient == null || recipient.account == null) {
                    send(ChatProtocol.encode("PM_ERROR", toUsername, "Nguoi nhan hien dang offline."));
                    return true;
                }

                recipient.send(ChatProtocol.encodeBytes(
                        "PM_IMG",
                        utf8(account.username()),
                        utf8(account.displayName()),
                        utf8(fileName),
                        utf8(mimeType),
                        imageBytes));

                send(ChatProtocol.encodeBytes(
                        "PM_IMG_SENT",
                        utf8(recipient.account.username()),
                        utf8(recipient.account.displayName()),
                        utf8(fileName),
                        utf8(mimeType),
                        imageBytes));
                return true;
            }

            if ("UPDATE_AVATAR".equals(command.name())) {
                if (!command.hasFields(1)) return true;
                byte[] avatarBytes = command.fieldBytes(0);
                if (avatarBytes == null || avatarBytes.length == 0 || avatarBytes.length > MAX_IMAGE_BYTES) {
                    return true;
                }

                // Luu avatar vao server (de co the load lai sau nay - phan load lai se lam o buoc mo rong)
                try {
                    Path avatarPath = StoragePaths.avatarDirectory().resolve(account.username() + ".png");
                    Files.createDirectories(avatarPath.getParent());
                    Files.write(avatarPath, avatarBytes);
                } catch (IOException e) {
                    log("Loi luu avatar cho " + account.username() + ": " + e.getMessage());
                }

                // Broadcast cho tat ca moi nguoi biet de cap nhat ngay lap tuc
                String payload = ChatProtocol.encodeBytes("USER_AVATAR", utf8(account.username()), avatarBytes);
                for (ClientHandler client : clients) client.send(payload);
                return true;
            }

            if ("CALL_INVITE".equals(command.name())) {
                if (!command.hasFields(2) || account == null) {
                    return true;
                }

                String toUsername = command.field(0).trim().toLowerCase(Locale.ROOT);
                String callId = command.field(1).trim();
                if (toUsername.isEmpty() || callId.isEmpty() || toUsername.equals(account.username())) {
                    return true;
                }

                ClientHandler recipient = clientsByUsername.get(toUsername);
                if (recipient == null || recipient.account == null) {
                    send(ChatProtocol.encode(
                            "CALL_DECLINE",
                            toUsername,
                            toUsername,
                            callId,
                            "Nguoi nhan hien dang offline."));
                    return true;
                }

                if (activeCallPeers.containsKey(account.username()) || activeCallPeers.containsKey(toUsername)) {
                    send(ChatProtocol.encode(
                            "CALL_DECLINE",
                            recipient.account.username(),
                            recipient.account.displayName(),
                            callId,
                            "Nguoi nhan dang ban."));
                    return true;
                }

                recipient.send(ChatProtocol.encode(
                        "CALL_INVITE",
                        account.username(),
                        account.displayName(),
                        callId));
                send(ChatProtocol.encode(
                        "CALL_RINGING",
                        recipient.account.username(),
                        recipient.account.displayName(),
                        callId));
                return true;
            }

            if ("CALL_ACCEPT".equals(command.name())) {
                if (!command.hasFields(2) || account == null) {
                    return true;
                }

                String toUsername = command.field(0).trim().toLowerCase(Locale.ROOT);
                String callId = command.field(1).trim();
                if (toUsername.isEmpty() || callId.isEmpty() || toUsername.equals(account.username())) {
                    return true;
                }

                ClientHandler caller = clientsByUsername.get(toUsername);
                if (caller == null || caller.account == null) {
                    send(ChatProtocol.encode(
                            "CALL_DECLINE",
                            toUsername,
                            toUsername,
                            callId,
                            "Nguoi goi da offline."));
                    return true;
                }

                if ((activeCallPeers.containsKey(account.username()) && !toUsername.equals(activeCallPeers.get(account.username())))
                        || (activeCallPeers.containsKey(toUsername) && !account.username().equals(activeCallPeers.get(toUsername)))) {
                    send(ChatProtocol.encode(
                            "CALL_DECLINE",
                            caller.account.username(),
                            caller.account.displayName(),
                            callId,
                            "Cuoc goi nay khong con kha dung."));
                    return true;
                }

                startActiveCall(account.username(), toUsername, callId);
                caller.send(ChatProtocol.encode(
                        "CALL_ACCEPT",
                        account.username(),
                        account.displayName(),
                        callId));
                return true;
            }

            if ("CALL_DECLINE".equals(command.name())) {
                if (!command.hasFields(3) || account == null) {
                    return true;
                }

                String toUsername = command.field(0).trim().toLowerCase(Locale.ROOT);
                String callId = command.field(1).trim();
                String reason = command.field(2).trim();
                if (toUsername.isEmpty() || callId.isEmpty()) {
                    return true;
                }

                ClientHandler caller = clientsByUsername.get(toUsername);
                if (caller != null && caller.account != null) {
                    caller.send(ChatProtocol.encode(
                            "CALL_DECLINE",
                            account.username(),
                            account.displayName(),
                            callId,
                            reason));
                }
                return true;
            }

            if ("CALL_END".equals(command.name())) {
                if (!command.hasFields(2) || account == null) {
                    return true;
                }

                String toUsername = command.field(0).trim().toLowerCase(Locale.ROOT);
                String callId = command.field(1).trim();
                if (toUsername.isEmpty() || callId.isEmpty()) {
                    return true;
                }

                clearActiveCall(account.username());
                ClientHandler peer = clientsByUsername.get(toUsername);
                if (peer != null && peer.account != null) {
                    peer.send(ChatProtocol.encode(
                            "CALL_END",
                            account.username(),
                            callId));
                }
                return true;
            }

            if ("CALL_AUDIO".equals(command.name())) {
                if (!command.hasFields(3) || account == null) {
                    return true;
                }

                String toUsername = command.field(0).trim().toLowerCase(Locale.ROOT);
                String callId = command.field(1).trim();
                byte[] audioBytes = command.fieldBytes(2);
                if (toUsername.isEmpty() || callId.isEmpty() || audioBytes == null || audioBytes.length == 0) {
                    return true;
                }

                if (!isMatchingActiveCall(account.username(), toUsername, callId)) {
                    return true;
                }

                ClientHandler peer = clientsByUsername.get(toUsername);
                if (peer == null || peer.account == null) {
                    clearActiveCall(account.username());
                    return true;
                }

                peer.send(ChatProtocol.encodeBytes(
                        "CALL_AUDIO",
                        utf8(account.username()),
                        utf8(callId),
                        audioBytes));
                return true;
            }

            if ("QUIT".equals(command.name())) {
                return false;
            }

            send(ChatProtocol.encode("CHAT", formatSystemMessage("Lenh khong duoc ho tro.")));
            return true;
        }

        // Gui 1 dong protocol ve client.
        private void send(String message) {
            PrintWriter currentWriter = writer;
            if (currentWriter != null) {
                synchronized (sendLock) {
                    currentWriter.println(message);
                }
            }
        }

        // Don dep khi client ngat ket noi: xoa khoi phong chat va broadcast su kien roi phong.
        private void disconnect() {
            clients.remove(this);

            if (account != null && onlineUsers.remove(account.username())) {
                String peerUsername = activeCallPeers.get(account.username());
                String callId = activeCallIds.get(account.username());
                clearActiveCall(account.username());
                if (peerUsername != null && !peerUsername.isBlank() && callId != null && !callId.isBlank()) {
                    ClientHandler peer = clientsByUsername.get(peerUsername);
                    if (peer != null && peer.account != null) {
                        peer.send(ChatProtocol.encode("CALL_END", account.username(), callId));
                    }
                }
                clientsByUsername.remove(account.username(), this);
                broadcast(formatSystemMessage(account.displayName() + " da roi phong chat."));
                broadcastOnlineUsers();
            }

            try {
                socket.close();
            } catch (IOException ignored) {
                // Socket is already closing during shutdown paths.
            }
        }
    }
}
