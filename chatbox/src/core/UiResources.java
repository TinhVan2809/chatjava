package core;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.image.Image;

public final class UiResources {
    private UiResources() {
    }

    public static URL fxml(String relativePath) {
        URL fromClasspath = UiResources.class.getResource("/" + relativePath);
        if (fromClasspath != null) {
            return fromClasspath;
        }
        return toFileUrl("src", relativePath);
    }

    public static String stylesheet(String relativePath) {
        URL fromClasspath = UiResources.class.getResource("/" + relativePath);
        if (fromClasspath != null) {
            return fromClasspath.toExternalForm();
        }
        URL fromFile = toFileUrl("src", relativePath);
        return fromFile == null ? "" : fromFile.toExternalForm();
    }

    public static Image image(String relativePath) {
        URL fromClasspath = UiResources.class.getResource("/" + relativePath);
        if (fromClasspath != null) {
            return new Image(fromClasspath.toExternalForm(), true);
        }

        URL fromFile = toFileUrl("", relativePath);
        if (fromFile != null) {
            return new Image(fromFile.toExternalForm(), true);
        }

        return null;
    }

    private static URL toFileUrl(String prefix, String relativePath) {
        Path path = prefix == null || prefix.isBlank()
                ? Path.of(relativePath)
                : Path.of(prefix, relativePath);
        if (!Files.exists(path)) {
            return null;
        }

        try {
            return path.toAbsolutePath().normalize().toUri().toURL();
        } catch (MalformedURLException ex) {
            return null;
        }
    }
}
