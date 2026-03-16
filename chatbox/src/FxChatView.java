import java.io.ByteArrayInputStream;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

// JavaFX message list view embedded into Swing via JFXPanel.
// Focused on chat bubbles + lightweight fade/slide-in animation.
public final class FxChatView {
    public enum Side {
        INCOMING,
        OUTGOING,
        SYSTEM
    }

    private static final double BUBBLE_RADIUS = 18;
    private static final double BUBBLE_MAX_WIDTH = 520;

    private static final String COLOR_BG = "#F9FBFF";
    private static final String COLOR_TEXT = "#1F2937";
    private static final String COLOR_MUTED = "#6B7280";
    private static final String COLOR_OUT_BG = "#0284C7";
    private static final String COLOR_IN_BG = "#E5E7EB";
    private static final String COLOR_SYS_BG = "#FFFFFF";
    private static final String COLOR_SYS_BORDER = "#D6DCE6";

    private static final int DEFAULT_ANIM_MS = 250;
    private static final int DEFAULT_SLIDE_PX = 10;

    private final JFXPanel panel = new JFXPanel();
    private ScrollPane scrollPane;
    private VBox messagesBox;

    public FxChatView() {
        Platform.runLater(this::initScene);
    }

    public JFXPanel getPanel() {
        return panel;
    }

    public void clear() {
        Platform.runLater(() -> {
            if (messagesBox != null) {
                messagesBox.getChildren().clear();
            }
            if (scrollPane != null) {
                scrollPane.setVvalue(1.0);
            }
        });
    }

    public void appendText(Side side, String metaText, String message) {
        String text = message == null ? "" : message.trim();
        if (text.isEmpty()) {
            return;
        }

        Platform.runLater(() -> {
            boolean pinned = isPinnedToBottom();
            Node node = buildTextMessage(side, metaText, text);
            addAnimated(node, pinned, DEFAULT_ANIM_MS, DEFAULT_SLIDE_PX);
        });
    }

    public void appendImage(Side side, String metaText, String caption, byte[] imageBytes, int maxWidth, int maxHeight) {
        if (imageBytes == null || imageBytes.length == 0) {
            return;
        }

        Platform.runLater(() -> {
            boolean pinned = isPinnedToBottom();
            Node node = buildImageMessage(side, metaText, caption, imageBytes, maxWidth, maxHeight);
            addAnimated(node, pinned, DEFAULT_ANIM_MS, DEFAULT_SLIDE_PX);
        });
    }

    private void initScene() {
        messagesBox = new VBox(8);
        messagesBox.setPadding(new Insets(12, 12, 12, 12));

        scrollPane = new ScrollPane(messagesBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPannable(true);
        scrollPane.setStyle("-fx-background-color: " + COLOR_BG + "; -fx-background: " + COLOR_BG + ";");

        StackPane root = new StackPane(scrollPane);
        root.setStyle("-fx-background-color: " + COLOR_BG + ";");

        Scene scene = new Scene(root);
        scene.setFill(Color.web(COLOR_BG));
        panel.setScene(scene);
    }

    private void addAnimated(Node node, boolean pinned, int durationMs, int slidePx) {
        if (messagesBox == null) {
            return;
        }

        node.setOpacity(0);
        node.setTranslateY(slidePx);
        messagesBox.getChildren().add(node);

        if (pinned) {
            scrollToBottomSoon();
        }

        FadeTransition fade = new FadeTransition(Duration.millis(durationMs), node);
        fade.setFromValue(0);
        fade.setToValue(1);

        TranslateTransition slide = new TranslateTransition(Duration.millis(durationMs), node);
        slide.setFromY(slidePx);
        slide.setToY(0);

        ParallelTransition animation = new ParallelTransition(fade, slide);
        animation.setOnFinished(event -> {
            node.setOpacity(1);
            node.setTranslateY(0);
            if (pinned) {
                scrollToBottomSoon();
            }
        });
        animation.play();
    }

    private void scrollToBottomSoon() {
        if (scrollPane == null) {
            return;
        }

        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    private boolean isPinnedToBottom() {
        if (scrollPane == null || messagesBox == null) {
            return true;
        }

        double contentHeight = messagesBox.getBoundsInLocal().getHeight();
        double viewportHeight = scrollPane.getViewportBounds().getHeight();
        if (contentHeight <= viewportHeight + 2) {
            return true;
        }

        return scrollPane.getVvalue() >= 0.97;
    }

    private Node buildTextMessage(Side side, String metaText, String message) {
        VBox wrapper = new VBox(4);
        wrapper.setFillWidth(true);

        String meta = metaText == null ? "" : metaText.trim();
        if (!meta.isEmpty()) {
            Label metaLabel = new Label(meta);
            metaLabel.setTextFill(Color.web(COLOR_MUTED));
            metaLabel.setStyle("-fx-font-size: 11px;");
            wrapper.getChildren().add(align(metaLabel, side));
        }

        Label bubble = new Label(message);
        bubble.setWrapText(true);
        bubble.setMaxWidth(BUBBLE_MAX_WIDTH);
        bubble.setTextFill(textColor(side));
        bubble.setStyle(bubbleStyle(side) + " -fx-font-size: 13px;");

        wrapper.getChildren().add(align(bubble, side));
        return wrapper;
    }

    private Node buildImageMessage(Side side, String metaText, String caption, byte[] imageBytes, int maxWidth, int maxHeight) {
        VBox wrapper = new VBox(4);
        wrapper.setFillWidth(true);

        String meta = metaText == null ? "" : metaText.trim();
        if (!meta.isEmpty()) {
            Label metaLabel = new Label(meta);
            metaLabel.setTextFill(Color.web(COLOR_MUTED));
            metaLabel.setStyle("-fx-font-size: 11px;");
            wrapper.getChildren().add(align(metaLabel, side));
        }

        VBox bubble = new VBox(8);
        bubble.setStyle(bubbleStyle(side));
        bubble.setPadding(new Insets(10, 12, 10, 12));

        String safeCaption = caption == null ? "" : caption.trim();
        if (!safeCaption.isEmpty()) {
            Label captionLabel = new Label(safeCaption);
            captionLabel.setWrapText(true);
            captionLabel.setMaxWidth(BUBBLE_MAX_WIDTH);
            captionLabel.setTextFill(textColor(side));
            captionLabel.setStyle("-fx-font-size: 12px;");
            bubble.getChildren().add(captionLabel);
        }

        Image image = new Image(new ByteArrayInputStream(imageBytes));
        if (image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0) {
            Label errorLabel = new Label("[Khong the doc anh]");
            errorLabel.setTextFill(textColor(side));
            errorLabel.setStyle("-fx-font-size: 12px;");
            bubble.getChildren().add(errorLabel);
        } else {
            ImageView view = new ImageView(image);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            view.setFitWidth(Math.max(1, maxWidth));
            view.setFitHeight(Math.max(1, maxHeight));
            bubble.getChildren().add(view);
        }

        wrapper.getChildren().add(align(bubble, side));
        return wrapper;
    }

    private HBox align(Node node, Side side) {
        HBox row = new HBox();
        row.setFillHeight(true);

        if (side == Side.SYSTEM) {
            row.setAlignment(Pos.TOP_CENTER);
            row.getChildren().add(node);
            return row;
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        if (side == Side.OUTGOING) {
            row.setAlignment(Pos.TOP_RIGHT);
            row.getChildren().addAll(spacer, node);
        } else {
            row.setAlignment(Pos.TOP_LEFT);
            row.getChildren().addAll(node, spacer);
        }

        return row;
    }

    private String bubbleStyle(Side side) {
        if (side == Side.OUTGOING) {
            return "-fx-background-color: " + COLOR_OUT_BG + ";"
                    + " -fx-background-radius: " + BUBBLE_RADIUS + ";"
                    + " -fx-padding: 10 12 10 12;";
        }

        if (side == Side.INCOMING) {
            return "-fx-background-color: " + COLOR_IN_BG + ";"
                    + " -fx-background-radius: " + BUBBLE_RADIUS + ";"
                    + " -fx-padding: 10 12 10 12;";
        }

        return "-fx-background-color: " + COLOR_SYS_BG + ";"
                + " -fx-background-radius: " + BUBBLE_RADIUS + ";"
                + " -fx-border-color: " + COLOR_SYS_BORDER + ";"
                + " -fx-border-radius: " + BUBBLE_RADIUS + ";"
                + " -fx-border-width: 1;"
                + " -fx-padding: 10 12 10 12;";
    }

    private Color textColor(Side side) {
        if (side == Side.OUTGOING) {
            return Color.WHITE;
        }
        if (side == Side.INCOMING) {
            return Color.web(COLOR_TEXT);
        }
        return Color.web(COLOR_MUTED);
    }
}
