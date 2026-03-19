package core;

import java.nio.file.Files;
import java.nio.file.Path;

public final class StoragePaths {
    private StoragePaths() {
    }

    public static Path usersFile() {
        return resolveWritable("data", "users.txt");
    }

    public static Path avatarDirectory() {
        return resolveWritableDirectory("data", "avatars");
    }

    private static Path resolveWritable(String... parts) {
        Path preferred = Path.of(parts[0], java.util.Arrays.copyOfRange(parts, 1, parts.length));
        if (Files.exists(preferred.getParent())) {
            return preferred.toAbsolutePath().normalize();
        }

        Path sibling = Path.of("..").resolve(preferred).normalize();
        if (Files.exists(sibling.getParent())) {
            return sibling.toAbsolutePath().normalize();
        }

        return preferred.toAbsolutePath().normalize();
    }

    private static Path resolveWritableDirectory(String... parts) {
        Path preferred = Path.of(parts[0], java.util.Arrays.copyOfRange(parts, 1, parts.length));
        Path sibling = Path.of("..").resolve(preferred).normalize();
        if (Files.exists(preferred) || Files.exists(preferred.getParent())) {
            return preferred.toAbsolutePath().normalize();
        }
        if (Files.exists(sibling) || Files.exists(sibling.getParent())) {
            return sibling.toAbsolutePath().normalize();
        }
        return preferred.toAbsolutePath().normalize();
    }
}
