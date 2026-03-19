package core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UserStore {
    private final Path storagePath;
    private final Map<String, UserAccount> accounts = new HashMap<>();

    public UserStore(Path storagePath) throws IOException {
        this.storagePath = storagePath;
        initializeStorage();
        loadAccounts();
    }

    public synchronized AuthResult register(String fullName, String username, String password) {
        String cleanFullName = sanitizeFullName(fullName);
        if (cleanFullName == null) {
            return AuthResult.failure("Ho ten phai dai tu 2 den 60 ky tu.");
        }

        String cleanUsername = normalizeUsername(username);
        if (cleanUsername == null) {
            return AuthResult.failure("Username chỉ được gồm chữc cái, số, '.', '_' hoặc '-' và dài 3-20 ký tự.");
        }

        if (!isValidPassword(password)) {
            return AuthResult.failure("Password phải dài ít nhất 6 ký tự.");
        }

        if (accounts.containsKey(cleanUsername)) {
            return AuthResult.failure("Username đã tồn tại.");
        }

        UserAccount account = new UserAccount(
                cleanFullName,
                cleanUsername,
                ChatProtocol.hashPassword(cleanUsername, password));
        accounts.put(cleanUsername, account);

        try {
            persistAccounts();
        } catch (IOException ex) {
            accounts.remove(cleanUsername);
            return AuthResult.failure("Không thể lưu tài khoản vào file.");
        }

        return AuthResult.success("Dang ky thanh cong.", account);
    }

    public synchronized AuthResult authenticate(String username, String password) {
        String cleanUsername = normalizeUsername(username);
        if (cleanUsername == null) {
            return AuthResult.failure("Username khong hop le.");
        }

        if (!isValidPassword(password)) {
            return AuthResult.failure("Password khong hop le.");
        }

        UserAccount account = accounts.get(cleanUsername);
        if (account == null) {
            return AuthResult.failure("Tai khoan khong ton tai.");
        }

        String passwordHash = ChatProtocol.hashPassword(cleanUsername, password);
        if (!account.passwordHash().equals(passwordHash)) {
            return AuthResult.failure("Sai password.");
        }

        return AuthResult.success("Dang nhap thanh cong.", account);
    }

    public Path getStoragePath() {
        return storagePath;
    }

    private void initializeStorage() throws IOException {
        Path parent = storagePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (!Files.exists(storagePath)) {
            Files.createFile(storagePath);
        }
    }

    private void loadAccounts() throws IOException {
        List<String> lines = Files.readAllLines(storagePath, StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }

            String[] parts = line.split("\t", -1);
            if (parts.length != 3) {
                continue;
            }

            String username = normalizeUsername(parts[0]);
            String fullName = sanitizeFullName(parts[1]);
            String passwordHash = parts[2].trim();
            if (username == null || fullName == null || passwordHash.isEmpty()) {
                continue;
            }

            accounts.put(username, new UserAccount(fullName, username, passwordHash));
        }
    }

    private void persistAccounts() throws IOException {
        List<UserAccount> orderedAccounts = new ArrayList<>(accounts.values());
        orderedAccounts.sort(Comparator.comparing(UserAccount::username));

        List<String> lines = new ArrayList<>(orderedAccounts.size());
        for (UserAccount account : orderedAccounts) {
            lines.add(account.username() + "\t" + account.fullName() + "\t" + account.passwordHash());
        }

        Files.write(
                storagePath,
                lines,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private String sanitizeFullName(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value.trim().replaceAll("\\s+", " ");
        if (cleaned.length() < 2 || cleaned.length() > 60) {
            return null;
        }

        return cleaned;
    }

    private String normalizeUsername(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value.trim().toLowerCase(Locale.ROOT);
        if (!cleaned.matches("[a-z0-9._-]{3,20}")) {
            return null;
        }

        return cleaned;
    }

    private boolean isValidPassword(String value) {
        return value != null && value.length() >= 6 && value.length() <= 64;
    }

    public record UserAccount(String fullName, String username, String passwordHash) {
        public String displayName() {
            return fullName + " (" + username + ")";
        }
    }

    public record AuthResult(boolean success, String message, UserAccount user) {
        public static AuthResult success(String message, UserAccount user) {
            return new AuthResult(true, message, user);
        }

        public static AuthResult failure(String message) {
            return new AuthResult(false, message, null);
        }
    }
}
