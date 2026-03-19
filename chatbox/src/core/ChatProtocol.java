package core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.StringJoiner;

public final class ChatProtocol {
    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private ChatProtocol() {
    }

    // Dong goi command + fields thanh 1 dong text de gui qua socket.
    public static String encode(String command, String... fields) {
        StringJoiner joiner = new StringJoiner("|");
        joiner.add(command.trim().toUpperCase(Locale.ROOT));

        for (String field : fields) {
            String safeValue = field == null ? "" : field;
            joiner.add(ENCODER.encodeToString(safeValue.getBytes(StandardCharsets.UTF_8)));
        }

        return joiner.toString();
    }

    // Encode fields dang byte[] (dung cho gui anh/file nho) ma khong can base64 lồng nhau.
    public static String encodeBytes(String command, byte[]... fields) {
        StringJoiner joiner = new StringJoiner("|");
        joiner.add(command.trim().toUpperCase(Locale.ROOT));

        for (byte[] field : fields) {
            byte[] safeValue = field == null ? new byte[0] : field;
            joiner.add(ENCODER.encodeToString(safeValue));
        }

        return joiner.toString();
    }

    // Parse 1 dong protocol thanh command va danh sach fields.
    public static Command decode(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("Yeu cau rong.");
        }

        String[] parts = line.split("\\|", -1);
        String command = parts[0].replace("\uFEFF", "").trim().toUpperCase(Locale.ROOT);
        if (command.isEmpty()) {
            throw new IllegalArgumentException("Lenh khong hop le.");
        }

        byte[][] fields = new byte[parts.length - 1][];
        for (int i = 1; i < parts.length; i++) {
            try {
                fields[i - 1] = DECODER.decode(parts[i]);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Du lieu gui len khong hop le.");
            }
        }

        return new Command(command, fields);
    }

    // Hash password theo username (lam salt don gian) de tranh luu password plain-text.
    // Luu y: day la demo, chua co slow-hash (bcrypt/argon2) hay per-user random salt.
    public static String hashPassword(String username, String password) {
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        String rawValue = normalizedUsername + ":" + (password == null ? "" : password);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 khong kha dung.", ex);
        }
    }

    // Ket qua sau khi decode: name la COMMAND, fields la cac tham so da duoc giai base64.
    public record Command(String name, byte[][] fields) {
        // Lay field theo index, nem loi neu thieu du lieu.
        public String field(int index) {
            return fieldText(index);
        }

        // Lay field text (UTF-8) theo index.
        public String fieldText(int index) {
            return new String(fieldBytes(index), StandardCharsets.UTF_8);
        }

        // Lay field raw bytes theo index (dung cho gui anh/file).
        public byte[] fieldBytes(int index) {
            if (index < 0 || index >= fields.length) {
                throw new IllegalArgumentException("Thieu du lieu cho lenh " + name + ".");
            }
            return fields[index];
        }

        // Decode tat ca field thanh String[] (UTF-8) de dung cho cac lenh text nhu ONLINE.
        public String[] fieldsText() {
            String[] textFields = new String[fields.length];
            for (int i = 0; i < fields.length; i++) {
                textFields[i] = new String(fields[i], StandardCharsets.UTF_8);
            }
            return textFields;
        }

        // Kiem tra so field toi thieu.
        public boolean hasFields(int expectedCount) {
            return fields.length >= expectedCount;
        }
    }
}
