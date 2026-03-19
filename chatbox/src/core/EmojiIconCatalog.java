package core;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.imageio.ImageIO;
import javafx.scene.image.Image;

public final class EmojiIconCatalog {
    public static final String FILE_PREFIX = "emoji-icon-";
    private static final Map<String, BiConsumer<Graphics2D, Integer>> GENERATED_RENDERERS = new LinkedHashMap<>();
    private static final Map<String, Image> PREVIEW_CACHE = new LinkedHashMap<>();
    private static volatile List<EmojiIconSpec> catalog = List.of();
    private static volatile Map<String, EmojiIconSpec> iconsById = Map.of();

    static {
        GENERATED_RENDERERS.put("smile", EmojiIconCatalog::paintSmile);
        GENERATED_RENDERERS.put("grin", EmojiIconCatalog::paintGrin);
        GENERATED_RENDERERS.put("laugh", EmojiIconCatalog::paintLaugh);
        GENERATED_RENDERERS.put("love", EmojiIconCatalog::paintLove);
        GENERATED_RENDERERS.put("wink", EmojiIconCatalog::paintWink);
        GENERATED_RENDERERS.put("cool", EmojiIconCatalog::paintCool);
        GENERATED_RENDERERS.put("kiss", EmojiIconCatalog::paintKiss);
        GENERATED_RENDERERS.put("wow", EmojiIconCatalog::paintWow);
        GENERATED_RENDERERS.put("cry", EmojiIconCatalog::paintCry);
        GENERATED_RENDERERS.put("angry", EmojiIconCatalog::paintAngry);
        GENERATED_RENDERERS.put("heart", EmojiIconCatalog::paintHeart);
        GENERATED_RENDERERS.put("star", EmojiIconCatalog::paintStar);
        GENERATED_RENDERERS.put("fire", EmojiIconCatalog::paintFire);
        GENERATED_RENDERERS.put("party", EmojiIconCatalog::paintParty);
        reloadCatalog();
    }

    private EmojiIconCatalog() {
    }

    public static synchronized void reloadCatalog() {
        List<EmojiIconSpec> loadedIcons = loadCatalogEntries();
        Map<String, EmojiIconSpec> iconMap = new LinkedHashMap<>();
        for (EmojiIconSpec icon : loadedIcons) {
            iconMap.put(icon.id(), icon);
        }

        catalog = List.copyOf(loadedIcons);
        iconsById = Map.copyOf(iconMap);
        PREVIEW_CACHE.clear();
    }

    public static List<EmojiIconSpec> allIcons() {
        return catalog;
    }

    public static List<String> categories() {
        Set<String> categories = new LinkedHashSet<>();
        categories.add("All");
        for (EmojiIconSpec icon : catalog) {
            categories.add(icon.category());
        }
        return List.copyOf(categories);
    }

    public static List<EmojiIconSpec> filter(String category, String query) {
        String normalizedCategory = category == null ? "All" : category.trim();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<EmojiIconSpec> filteredIcons = new ArrayList<>();

        for (EmojiIconSpec icon : catalog) {
            boolean categoryMatches = normalizedCategory.isBlank()
                    || "All".equalsIgnoreCase(normalizedCategory)
                    || icon.category().equalsIgnoreCase(normalizedCategory);
            if (!categoryMatches) {
                continue;
            }

            if (!normalizedQuery.isBlank()) {
                String searchableText = (icon.displayName() + " " + icon.id() + " " + icon.category()).toLowerCase(Locale.ROOT);
                if (!searchableText.contains(normalizedQuery)) {
                    continue;
                }
            }

            filteredIcons.add(icon);
        }

        return filteredIcons;
    }

    public static EmojiIconSpec icon(String id) {
        return iconsById.get(normalizeId(id));
    }

    public static Image previewImage(String id) {
        String normalizedId = normalizeId(id);
        return PREVIEW_CACHE.computeIfAbsent(normalizedId, key -> {
            byte[] bytes = pngBytes(key, 128);
            return new Image(new ByteArrayInputStream(bytes), 38, 38, true, true);
        });
    }

    public static byte[] pngBytes(String id, int size) {
        EmojiIconSpec icon = icon(id);
        if (icon == null) {
            icon = defaultGeneratedIcons().get(0);
        }

        if (icon.sourceType() == SourceType.FILE) {
            byte[] fileBytes = loadAssetBytes(icon.assetFileName());
            if (fileBytes != null && fileBytes.length > 0) {
                return fileBytes;
            }
        }

        return renderGenerated(normalizeId(icon.id()), size);
    }

    public static String fileName(String id) {
        String normalizedId = normalizeId(id);
        return FILE_PREFIX + (normalizedId.isBlank() ? "smile" : normalizedId) + ".png";
    }

    public static boolean isEmojiIconFile(String fileName) {
        String safeName = fileName == null ? "" : fileName.trim().toLowerCase(Locale.ROOT);
        return safeName.startsWith(FILE_PREFIX) && safeName.endsWith(".png");
    }

    public static String displayNameFromFileName(String fileName) {
        String safeName = fileName == null ? "" : fileName.trim().toLowerCase(Locale.ROOT);
        if (!isEmojiIconFile(safeName)) {
            return "";
        }

        String rawId = safeName.substring(FILE_PREFIX.length(), safeName.length() - 4);
        EmojiIconSpec icon = icon(rawId);
        if (icon != null) {
            return icon.displayName();
        }
        return rawId.isBlank()
                ? "Icon"
                : Character.toUpperCase(rawId.charAt(0)) + rawId.substring(1);
    }

    private static List<EmojiIconSpec> loadCatalogEntries() {
        Path catalogFile = StoragePaths.emojiCatalogFile();
        List<String> lines = readCatalogLines(catalogFile);
        List<EmojiIconSpec> parsedIcons = new ArrayList<>();

        for (String line : lines) {
            EmojiIconSpec icon = parseCatalogLine(line);
            if (icon != null) {
                parsedIcons.add(icon);
            }
        }

        appendDiscoveredAssetIcons(parsedIcons);
        if (!parsedIcons.isEmpty()) {
            return parsedIcons;
        }
        return defaultGeneratedIcons();
    }

    private static List<String> readCatalogLines(Path catalogFile) {
        if (catalogFile == null || !Files.exists(catalogFile)) {
            return List.of();
        }

        try {
            return Files.readAllLines(catalogFile);
        } catch (IOException ex) {
            return List.of();
        }
    }

    private static EmojiIconSpec parseCatalogLine(String line) {
        String safeLine = line == null ? "" : line.trim();
        if (safeLine.isEmpty() || safeLine.startsWith("#")) {
            return null;
        }

        String[] parts = Arrays.stream(safeLine.split("\\|", -1))
                .map(String::trim)
                .toArray(String[]::new);
        if (parts.length < 4) {
            return null;
        }

        String type = parts[0].toLowerCase(Locale.ROOT);
        String id = normalizeId(parts[1]);
        String displayName = parts[2].isBlank() ? humanizeId(id) : parts[2];
        String category = parts[3].isBlank() ? "General" : parts[3];
        String assetFileName = parts.length >= 5 ? parts[4] : "";

        if (id.isBlank()) {
            return null;
        }

        if ("file".equals(type)) {
            return new EmojiIconSpec(id, displayName, category, SourceType.FILE, assetFileName);
        }
        if (GENERATED_RENDERERS.containsKey(id)) {
            return new EmojiIconSpec(id, displayName, category, SourceType.GENERATED, "");
        }
        if (!assetFileName.isBlank()) {
            return new EmojiIconSpec(id, displayName, category, SourceType.FILE, assetFileName);
        }
        return null;
    }

    private static void appendDiscoveredAssetIcons(List<EmojiIconSpec> icons) {
        Path assetDirectory = StoragePaths.emojiAssetDirectory();
        if (assetDirectory == null || !Files.isDirectory(assetDirectory)) {
            return;
        }

        Set<String> existingIds = new LinkedHashSet<>();
        Set<String> existingAssetNames = new LinkedHashSet<>();
        for (EmojiIconSpec icon : icons) {
            existingIds.add(normalizeId(icon.id()));
            if (icon.assetFileName() != null && !icon.assetFileName().isBlank()) {
                existingAssetNames.add(icon.assetFileName().trim().toLowerCase(Locale.ROOT));
            }
        }

        try (var stream = Files.list(assetDirectory)) {
            List<Path> assetFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(EmojiIconCatalog::isSupportedAssetFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();

            for (Path assetFile : assetFiles) {
                String fileName = assetFile.getFileName().toString();
                String normalizedFileName = fileName.toLowerCase(Locale.ROOT);
                if (existingAssetNames.contains(normalizedFileName)) {
                    continue;
                }

                String id = normalizeId(stripExtension(fileName).replace('_', '-').replace(' ', '-'));
                if (id.isBlank() || existingIds.contains(id)) {
                    continue;
                }

                icons.add(new EmojiIconSpec(id, humanizeId(id), "Custom", SourceType.FILE, fileName));
                existingIds.add(id);
                existingAssetNames.add(normalizedFileName);
            }
        } catch (IOException ex) {
            // Ignore discovery failure and keep the parsed catalog entries.
        }
    }

    private static boolean isSupportedAssetFile(Path file) {
        String fileName = file == null || file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".png");
    }

    private static String stripExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }

        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex <= 0) {
            return fileName;
        }
        return fileName.substring(0, extensionIndex);
    }

    private static List<EmojiIconSpec> defaultGeneratedIcons() {
        return List.of(
                new EmojiIconSpec("smile", "Smile", "Faces", SourceType.GENERATED, ""),
                new EmojiIconSpec("grin", "Grin", "Faces", SourceType.GENERATED, ""),
                new EmojiIconSpec("laugh", "Laugh", "Faces", SourceType.GENERATED, ""),
                new EmojiIconSpec("love", "Love Eyes", "Faces", SourceType.GENERATED, ""),
                new EmojiIconSpec("wink", "Wink", "Faces", SourceType.GENERATED, ""),
                new EmojiIconSpec("cool", "Cool", "Faces", SourceType.GENERATED, ""),
                new EmojiIconSpec("kiss", "Kiss", "Faces", SourceType.GENERATED, ""),
                new EmojiIconSpec("wow", "Wow", "Faces", SourceType.GENERATED, ""),
                new EmojiIconSpec("cry", "Cry", "Faces", SourceType.GENERATED, ""),
                new EmojiIconSpec("angry", "Angry", "Faces", SourceType.GENERATED, ""),
                new EmojiIconSpec("heart", "Heart", "Symbols", SourceType.GENERATED, ""),
                new EmojiIconSpec("star", "Star", "Symbols", SourceType.GENERATED, ""),
                new EmojiIconSpec("fire", "Fire", "Symbols", SourceType.GENERATED, ""),
                new EmojiIconSpec("party", "Party", "Celebration", SourceType.GENERATED, ""));
    }

    private static byte[] renderGenerated(String id, int size) {
        BiConsumer<Graphics2D, Integer> renderer = GENERATED_RENDERERS.get(normalizeId(id));
        if (renderer == null) {
            renderer = GENERATED_RENDERERS.get("smile");
        }

        BufferedImage image = new BufferedImage(Math.max(64, size), Math.max(64, size), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        renderer.accept(graphics, image.getWidth());
        graphics.dispose();

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate emoji icon.", ex);
        }
    }

    private static byte[] loadAssetBytes(String assetFileName) {
        String safeFileName = assetFileName == null ? "" : assetFileName.trim();
        if (safeFileName.isBlank()) {
            return null;
        }

        Path assetPath = StoragePaths.emojiAssetDirectory().resolve(safeFileName);
        if (!Files.exists(assetPath)) {
            return null;
        }

        try {
            return Files.readAllBytes(assetPath);
        } catch (IOException ex) {
            return null;
        }
    }

    private static void paintSmile(Graphics2D graphics, Integer size) {
        paintFaceBase(graphics, size);
        float stroke = Math.max(3f, size * 0.05f);
        graphics.setColor(new Color(51, 40, 28));
        graphics.fill(oval(size, 0.34, 0.37, 0.07, 0.09));
        graphics.fill(oval(size, 0.59, 0.37, 0.07, 0.09));
        graphics.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(new Arc2D.Double(size * 0.30, size * 0.38, size * 0.40, size * 0.32, 205, 130, Arc2D.OPEN));
    }

    private static void paintGrin(Graphics2D graphics, Integer size) {
        paintFaceBase(graphics, size);
        float stroke = Math.max(3f, size * 0.05f);
        graphics.setColor(new Color(51, 40, 28));
        graphics.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(new Arc2D.Double(size * 0.28, size * 0.36, size * 0.12, size * 0.08, 200, 140, Arc2D.OPEN));
        graphics.draw(new Arc2D.Double(size * 0.60, size * 0.36, size * 0.12, size * 0.08, 200, 140, Arc2D.OPEN));

        GeneralPath mouth = new GeneralPath();
        mouth.moveTo(size * 0.28, size * 0.58);
        mouth.curveTo(size * 0.33, size * 0.77, size * 0.67, size * 0.77, size * 0.72, size * 0.58);
        mouth.lineTo(size * 0.72, size * 0.52);
        mouth.curveTo(size * 0.66, size * 0.66, size * 0.34, size * 0.66, size * 0.28, size * 0.52);
        mouth.closePath();
        graphics.setColor(new Color(255, 255, 255));
        graphics.fill(mouth);
        graphics.setColor(new Color(51, 40, 28));
        graphics.draw(mouth);
        graphics.drawLine((int) (size * 0.50), (int) (size * 0.54), (int) (size * 0.50), (int) (size * 0.70));
        graphics.drawLine((int) (size * 0.32), (int) (size * 0.62), (int) (size * 0.68), (int) (size * 0.62));
    }

    private static void paintLaugh(Graphics2D graphics, Integer size) {
        paintFaceBase(graphics, size);
        float stroke = Math.max(3f, size * 0.05f);
        graphics.setColor(new Color(51, 40, 28));
        graphics.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(new Arc2D.Double(size * 0.26, size * 0.34, size * 0.14, size * 0.10, 200, 140, Arc2D.OPEN));
        graphics.draw(new Arc2D.Double(size * 0.60, size * 0.34, size * 0.14, size * 0.10, 200, 140, Arc2D.OPEN));

        GeneralPath mouth = new GeneralPath();
        mouth.moveTo(size * 0.26, size * 0.55);
        mouth.curveTo(size * 0.36, size * 0.82, size * 0.64, size * 0.82, size * 0.74, size * 0.55);
        mouth.curveTo(size * 0.66, size * 0.47, size * 0.34, size * 0.47, size * 0.26, size * 0.55);
        mouth.closePath();
        graphics.setColor(new Color(103, 30, 30));
        graphics.fill(mouth);
        graphics.setColor(new Color(255, 255, 255));
        graphics.fillRect((int) (size * 0.33), (int) (size * 0.57), (int) (size * 0.34), (int) (size * 0.08));
        graphics.setColor(new Color(51, 40, 28));
        graphics.draw(mouth);
        graphics.setColor(new Color(74, 169, 255, 220));
        graphics.fill(oval(size, 0.18, 0.42, 0.10, 0.16));
        graphics.fill(oval(size, 0.72, 0.42, 0.10, 0.16));
    }

    private static void paintLove(Graphics2D graphics, Integer size) {
        paintFaceBase(graphics, size);
        graphics.setColor(new Color(51, 40, 28));
        graphics.setStroke(new BasicStroke(Math.max(3f, size * 0.05f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(new Arc2D.Double(size * 0.30, size * 0.44, size * 0.40, size * 0.28, 205, 130, Arc2D.OPEN));
        graphics.setPaint(new GradientPaint(0, 0, new Color(255, 77, 109), size, size, new Color(220, 38, 38)));
        graphics.fill(createHeart(size * 0.36, size * 0.38, size * 0.15, size * 0.13));
        graphics.fill(createHeart(size * 0.64, size * 0.38, size * 0.15, size * 0.13));
    }

    private static void paintWink(Graphics2D graphics, Integer size) {
        paintFaceBase(graphics, size);
        graphics.setColor(new Color(51, 40, 28));
        graphics.fill(oval(size, 0.35, 0.36, 0.06, 0.08));
        graphics.setStroke(new BasicStroke(Math.max(3f, size * 0.05f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(new Arc2D.Double(size * 0.58, size * 0.37, size * 0.12, size * 0.05, 200, 140, Arc2D.OPEN));
        graphics.draw(new Arc2D.Double(size * 0.30, size * 0.43, size * 0.40, size * 0.26, 205, 130, Arc2D.OPEN));
    }

    private static void paintCool(Graphics2D graphics, Integer size) {
        paintFaceBase(graphics, size);
        graphics.setColor(new Color(33, 41, 56));
        graphics.fillRoundRect((int) (size * 0.24), (int) (size * 0.34), (int) (size * 0.18), (int) (size * 0.12), 10, 10);
        graphics.fillRoundRect((int) (size * 0.58), (int) (size * 0.34), (int) (size * 0.18), (int) (size * 0.12), 10, 10);
        graphics.fillRect((int) (size * 0.41), (int) (size * 0.38), (int) (size * 0.18), (int) (size * 0.04));
        graphics.setStroke(new BasicStroke(Math.max(3f, size * 0.05f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(new Arc2D.Double(size * 0.30, size * 0.44, size * 0.40, size * 0.24, 205, 130, Arc2D.OPEN));
    }

    private static void paintKiss(Graphics2D graphics, Integer size) {
        paintFaceBase(graphics, size);
        graphics.setColor(new Color(51, 40, 28));
        graphics.fill(oval(size, 0.34, 0.36, 0.06, 0.08));
        graphics.setStroke(new BasicStroke(Math.max(3f, size * 0.05f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(new Arc2D.Double(size * 0.58, size * 0.37, size * 0.12, size * 0.05, 200, 140, Arc2D.OPEN));
        GeneralPath lips = new GeneralPath();
        lips.moveTo(size * 0.44, size * 0.58);
        lips.curveTo(size * 0.47, size * 0.53, size * 0.53, size * 0.53, size * 0.56, size * 0.58);
        lips.curveTo(size * 0.53, size * 0.63, size * 0.47, size * 0.63, size * 0.44, size * 0.58);
        lips.closePath();
        graphics.setPaint(new GradientPaint(0, 0, new Color(255, 116, 139), size, size, new Color(220, 38, 38)));
        graphics.fill(lips);
        graphics.fill(createHeart(size * 0.72, size * 0.42, size * 0.12, size * 0.10));
    }

    private static void paintWow(Graphics2D graphics, Integer size) {
        paintFaceBase(graphics, size);
        graphics.setColor(new Color(51, 40, 28));
        graphics.fill(oval(size, 0.35, 0.36, 0.06, 0.08));
        graphics.fill(oval(size, 0.59, 0.36, 0.06, 0.08));
        graphics.setStroke(new BasicStroke(Math.max(3f, size * 0.05f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(new Arc2D.Double(size * 0.28, size * 0.24, size * 0.16, size * 0.07, 15, 150, Arc2D.OPEN));
        graphics.draw(new Arc2D.Double(size * 0.56, size * 0.24, size * 0.16, size * 0.07, 15, 150, Arc2D.OPEN));
        graphics.setColor(new Color(84, 56, 36));
        graphics.fill(oval(size, 0.43, 0.54, 0.14, 0.20));
    }

    private static void paintCry(Graphics2D graphics, Integer size) {
        paintFaceBase(graphics, size);
        float stroke = Math.max(3f, size * 0.05f);
        graphics.setColor(new Color(51, 40, 28));
        graphics.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(new Arc2D.Double(size * 0.28, size * 0.39, size * 0.12, size * 0.08, 20, 140, Arc2D.OPEN));
        graphics.draw(new Arc2D.Double(size * 0.60, size * 0.39, size * 0.12, size * 0.08, 20, 140, Arc2D.OPEN));
        graphics.draw(new Arc2D.Double(size * 0.32, size * 0.60, size * 0.36, size * 0.18, 15, 150, Arc2D.OPEN));
        graphics.setColor(new Color(74, 169, 255, 220));
        graphics.fill(createTear(size * 0.34, size * 0.52, size * 0.09, size * 0.17));
        graphics.fill(createTear(size * 0.66, size * 0.52, size * 0.09, size * 0.17));
    }

    private static void paintAngry(Graphics2D graphics, Integer size) {
        paintWarmFaceBase(graphics, size, new Color(255, 176, 66), new Color(239, 68, 68));
        graphics.setColor(new Color(78, 24, 24));
        graphics.setStroke(new BasicStroke(Math.max(4f, size * 0.05f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawLine((int) (size * 0.28), (int) (size * 0.34), (int) (size * 0.41), (int) (size * 0.30));
        graphics.drawLine((int) (size * 0.72), (int) (size * 0.34), (int) (size * 0.59), (int) (size * 0.30));
        graphics.fill(oval(size, 0.34, 0.39, 0.06, 0.07));
        graphics.fill(oval(size, 0.60, 0.39, 0.06, 0.07));
        graphics.draw(new Arc2D.Double(size * 0.34, size * 0.62, size * 0.32, size * 0.12, 20, 140, Arc2D.OPEN));
    }

    private static void paintHeart(Graphics2D graphics, Integer size) {
        GeneralPath heart = createHeart(size * 0.50, size * 0.50, size * 0.55, size * 0.46);
        graphics.setPaint(new GradientPaint(0, 0, new Color(255, 124, 145), size, size, new Color(220, 38, 38)));
        graphics.fill(heart);
        graphics.setColor(new Color(185, 28, 28));
        graphics.setStroke(new BasicStroke(Math.max(3f, size * 0.04f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(heart);
        graphics.setColor(new Color(255, 255, 255, 120));
        graphics.fill(new Ellipse2D.Double(size * 0.30, size * 0.24, size * 0.12, size * 0.18));
    }

    private static void paintStar(Graphics2D graphics, Integer size) {
        GeneralPath star = createStar(size * 0.50, size * 0.50, size * 0.34, size * 0.15);
        graphics.setPaint(new GradientPaint(0, 0, new Color(255, 232, 130), size, size, new Color(245, 158, 11)));
        graphics.fill(star);
        graphics.setColor(new Color(217, 119, 6));
        graphics.setStroke(new BasicStroke(Math.max(3f, size * 0.04f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(star);
    }

    private static void paintFire(Graphics2D graphics, Integer size) {
        GeneralPath outerFlame = new GeneralPath();
        outerFlame.moveTo(size * 0.52, size * 0.10);
        outerFlame.curveTo(size * 0.72, size * 0.26, size * 0.85, size * 0.48, size * 0.72, size * 0.72);
        outerFlame.curveTo(size * 0.62, size * 0.92, size * 0.35, size * 0.94, size * 0.24, size * 0.74);
        outerFlame.curveTo(size * 0.12, size * 0.52, size * 0.22, size * 0.30, size * 0.42, size * 0.18);
        outerFlame.curveTo(size * 0.46, size * 0.28, size * 0.53, size * 0.32, size * 0.58, size * 0.36);
        outerFlame.curveTo(size * 0.58, size * 0.26, size * 0.56, size * 0.18, size * 0.52, size * 0.10);
        outerFlame.closePath();

        graphics.setPaint(new GradientPaint(0, 0, new Color(255, 166, 0), size, size, new Color(239, 68, 68)));
        graphics.fill(outerFlame);

        GeneralPath innerFlame = new GeneralPath();
        innerFlame.moveTo(size * 0.50, size * 0.28);
        innerFlame.curveTo(size * 0.60, size * 0.40, size * 0.64, size * 0.56, size * 0.56, size * 0.68);
        innerFlame.curveTo(size * 0.50, size * 0.80, size * 0.37, size * 0.80, size * 0.32, size * 0.68);
        innerFlame.curveTo(size * 0.28, size * 0.58, size * 0.32, size * 0.44, size * 0.44, size * 0.34);
        innerFlame.curveTo(size * 0.45, size * 0.42, size * 0.49, size * 0.47, size * 0.53, size * 0.52);
        innerFlame.curveTo(size * 0.54, size * 0.42, size * 0.53, size * 0.34, size * 0.50, size * 0.28);
        innerFlame.closePath();

        graphics.setPaint(new GradientPaint(0, 0, new Color(255, 245, 157), size, size, new Color(251, 146, 60)));
        graphics.fill(innerFlame);
    }

    private static void paintParty(Graphics2D graphics, Integer size) {
        paintFaceBase(graphics, size);
        graphics.setColor(new Color(51, 40, 28));
        graphics.fill(oval(size, 0.34, 0.38, 0.06, 0.08));
        graphics.fill(oval(size, 0.59, 0.38, 0.06, 0.08));
        graphics.setStroke(new BasicStroke(Math.max(3f, size * 0.05f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(new Arc2D.Double(size * 0.30, size * 0.44, size * 0.40, size * 0.24, 205, 130, Arc2D.OPEN));

        GeneralPath hat = new GeneralPath();
        hat.moveTo(size * 0.30, size * 0.26);
        hat.lineTo(size * 0.56, size * 0.10);
        hat.lineTo(size * 0.58, size * 0.30);
        hat.closePath();
        graphics.setPaint(new GradientPaint(0, 0, new Color(168, 85, 247), size, size, new Color(236, 72, 153)));
        graphics.fill(hat);
        graphics.setColor(new Color(124, 58, 237));
        graphics.draw(hat);
        graphics.setColor(new Color(34, 197, 94));
        graphics.fill(oval(size, 0.57, 0.07, 0.08, 0.08));
        graphics.setColor(new Color(59, 130, 246));
        graphics.fill(oval(size, 0.18, 0.22, 0.06, 0.06));
        graphics.setColor(new Color(236, 72, 153));
        graphics.fill(oval(size, 0.72, 0.22, 0.05, 0.05));
    }

    private static void paintFaceBase(Graphics2D graphics, int size) {
        paintWarmFaceBase(graphics, size, new Color(255, 242, 117), new Color(255, 188, 36));
    }

    private static void paintWarmFaceBase(Graphics2D graphics, int size, Color topColor, Color bottomColor) {
        double inset = size * 0.10;
        Ellipse2D face = new Ellipse2D.Double(inset, inset, size - inset * 2, size - inset * 2);
        graphics.setPaint(new GradientPaint(0, 0, topColor, size, size, bottomColor));
        graphics.fill(face);
        graphics.setColor(new Color(214, 145, 0));
        graphics.setStroke(new BasicStroke(Math.max(3f, size * 0.04f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(face);
        graphics.setColor(new Color(255, 255, 255, 120));
        graphics.fill(new Ellipse2D.Double(size * 0.24, size * 0.18, size * 0.16, size * 0.11));
    }

    private static Ellipse2D oval(int size, double x, double y, double width, double height) {
        return new Ellipse2D.Double(size * x, size * y, size * width, size * height);
    }

    private static GeneralPath createHeart(double centerX, double centerY, double width, double height) {
        GeneralPath heart = new GeneralPath();
        double top = centerY - height / 2.0;
        double bottom = centerY + height / 2.0;
        heart.moveTo(centerX, bottom);
        heart.curveTo(centerX + width * 0.55, centerY + height * 0.10, centerX + width * 0.55, top, centerX, top + height * 0.28);
        heart.curveTo(centerX - width * 0.55, top, centerX - width * 0.55, centerY + height * 0.10, centerX, bottom);
        heart.closePath();
        return heart;
    }

    private static GeneralPath createTear(double centerX, double centerY, double width, double height) {
        double top = centerY - height / 2.0;
        double bottom = centerY + height / 2.0;
        double left = centerX - width / 2.0;
        double right = centerX + width / 2.0;

        GeneralPath tear = new GeneralPath();
        tear.moveTo(centerX, top);
        tear.curveTo(right, centerY - height * 0.10, right, centerY + height * 0.20, centerX, bottom);
        tear.curveTo(left, centerY + height * 0.20, left, centerY - height * 0.10, centerX, top);
        tear.closePath();
        return tear;
    }

    private static GeneralPath createStar(double centerX, double centerY, double outerRadius, double innerRadius) {
        GeneralPath star = new GeneralPath();
        for (int pointIndex = 0; pointIndex < 10; pointIndex++) {
            double angle = Math.PI / 2 + pointIndex * Math.PI / 5;
            double radius = pointIndex % 2 == 0 ? outerRadius : innerRadius;
            double x = centerX + Math.cos(angle) * radius;
            double y = centerY - Math.sin(angle) * radius;
            if (pointIndex == 0) {
                star.moveTo(x, y);
            } else {
                star.lineTo(x, y);
            }
        }
        star.closePath();
        return star;
    }

    private static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private static String humanizeId(String id) {
        if (id == null || id.isBlank()) {
            return "Icon";
        }
        String safeId = id.trim().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(safeId.charAt(0)) + safeId.substring(1).replace('-', ' ');
    }

    public record EmojiIconSpec(String id, String displayName, String category, SourceType sourceType,
            String assetFileName) {
    }

    public enum SourceType {
        GENERATED,
        FILE
    }
}
