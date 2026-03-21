package controllers;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import models.Message;

public class MessageBubbleController {
    @FXML
    private HBox root;
    @FXML
    private Region leadingSpacer;
    @FXML
    private Region trailingSpacer;
    @FXML
    private StackPane avatarPane;
    @FXML
    private ImageView avatarImageView;
    @FXML
    private Label avatarLabel;
    @FXML
    private VBox contentBox;
    @FXML
    private Label metaLabel;
    @FXML
    private HBox bubbleRow;
    @FXML
    private VBox bubbleStack;
    @FXML
    private TextFlow replyPreviewFlow;
    @FXML
    private TextFlow bubbleFlow;
    @FXML
    private VBox imageAttachmentPane;
    @FXML
    private ImageView attachmentImageView;
    @FXML
    private Label imageAttachmentNameLabel;
    @FXML
    private Label imageAttachmentMetaLabel;
    @FXML
    private Button imageSaveButton;
    @FXML
    private HBox fileAttachmentPane;
    @FXML
    private Label fileAttachmentNameLabel;
    @FXML
    private Label fileAttachmentMetaLabel;
    @FXML
    private Button fileSaveButton;
    @FXML
    private Label statusLabel;
    @FXML
    private Button actionsButton;

    private Message message;
    private Runnable onReplyAction;
    private Runnable onDeleteAction;
    private final ContextMenu actionsMenu = new ContextMenu();
    private final MenuItem replyMenuItem = new MenuItem("Trả lời");
    private final MenuItem copyMenuItem = new MenuItem("Copy");
    private final MenuItem deleteMenuItem = new MenuItem("Xóa");

    @FXML
    private void initialize() {
        avatarImageView.setClip(new Circle(17, 17, 17));
        avatarImageView.imageProperty().addListener((observable, oldImage, newImage) -> updateAvatarPaneStyle());
        replyMenuItem.setOnAction(event -> {
            if (onReplyAction != null) {
                onReplyAction.run();
            }
        });
        copyMenuItem.setOnAction(event -> copyCurrentMessage());
        deleteMenuItem.setOnAction(event -> {
            if (onDeleteAction != null) {
                onDeleteAction.run();
            }
        });
        actionsMenu.getItems().setAll(replyMenuItem, copyMenuItem, deleteMenuItem);
    }

    public void setMessage(Message message) {
        setMessage(message, false, null, "");
    }

    public void setMessage(Message message, boolean privateConversation) {
        setMessage(message, privateConversation, null, "");
    }

    public void setMessage(Message message, boolean privateConversation, Image avatarImage) {
        setMessage(message, privateConversation, avatarImage, "");
    }

    public void setMessage(Message message, boolean privateConversation, Image avatarImage, String highlightQuery) {
        this.message = message;
        root.getStyleClass().removeAll("message-row-incoming", "message-row-outgoing", "message-row-system");
        bubbleFlow.getStyleClass().removeAll("bubble-incoming", "bubble-outgoing", "bubble-system");

        bubbleFlow.getChildren().clear();
        bubbleFlow.setMaxWidth(420);
        bubbleFlow.setManaged(false);
        bubbleFlow.setVisible(false);
        replyPreviewFlow.setManaged(false);
        replyPreviewFlow.setVisible(false);
        replyPreviewFlow.getChildren().clear();
        imageAttachmentPane.setManaged(false);
        imageAttachmentPane.setVisible(false);
        imageAttachmentPane.getStyleClass().remove("emoji-sticker-pane");
        imageAttachmentNameLabel.setText("");
        imageAttachmentMetaLabel.setText("");
        imageAttachmentNameLabel.setManaged(true);
        imageAttachmentNameLabel.setVisible(true);
        imageAttachmentMetaLabel.setManaged(true);
        imageAttachmentMetaLabel.setVisible(true);
        attachmentImageView.setImage(null);
        attachmentImageView.setFitWidth(340);
        fileAttachmentPane.setManaged(false);
        fileAttachmentPane.setVisible(false);
        fileAttachmentNameLabel.setText("");
        fileAttachmentMetaLabel.setText("");
        imageSaveButton.setManaged(false);
        imageSaveButton.setVisible(false);
        statusLabel.setManaged(false);
        statusLabel.setVisible(false);
        statusLabel.setText("");
        avatarImageView.setImage(null);
        avatarImageView.setManaged(false);
        avatarImageView.setVisible(false);
        avatarLabel.setManaged(true);
        avatarLabel.setVisible(true);
        actionsMenu.hide();
        imageAttachmentPane.getStyleClass().removeAll("attachment-bubble-outgoing", "attachment-bubble-incoming");
        fileAttachmentPane.getStyleClass().removeAll("file-attachment-bubble-outgoing", "file-attachment-bubble-incoming");

        if (message.isSystemMessage()) {
            root.setAlignment(Pos.CENTER);
            contentBox.setAlignment(Pos.CENTER);
            root.getStyleClass().add("message-row-system");
            bubbleFlow.getStyleClass().add("bubble-system");
            applyHighlightedText(bubbleFlow, message.getText(), highlightQuery, "message-bubble-text", "message-bubble-highlight");
            bubbleFlow.setManaged(true);
            bubbleFlow.setVisible(true);
            avatarPane.setManaged(false);
            avatarPane.setVisible(false);
            leadingSpacer.setManaged(false);
            trailingSpacer.setManaged(false);
            metaLabel.setManaged(false);
            metaLabel.setVisible(false);
            actionsButton.setManaged(false);
            actionsButton.setVisible(false);
            bubbleRow.getChildren().setAll(bubbleStack);
            return;
        }

        String senderDisplayName = message.getSenderDisplayName().isBlank()
                ? message.getSenderUsername()
                : message.getSenderDisplayName();
        metaLabel.setText(message.getTimestamp() + "  " + senderDisplayName);
        metaLabel.setManaged(true);
        metaLabel.setVisible(true);
        configureReplyPreview(message, highlightQuery);
        actionsButton.setManaged(true);
        actionsButton.setVisible(true);
        avatarPane.setManaged(!message.isSentByCurrentUser());
        avatarPane.setVisible(!message.isSentByCurrentUser());
        configureAvatar(avatarImage, senderDisplayName);
        replyMenuItem.setDisable(false);
        deleteMenuItem.setDisable(false);
        copyMenuItem.setDisable(message.getCopyText().isBlank());
        configureContent(message, highlightQuery);

        if (message.isSentByCurrentUser()) {
            root.setAlignment(Pos.TOP_RIGHT);
            contentBox.setAlignment(Pos.TOP_RIGHT);
            root.getStyleClass().add("message-row-outgoing");
            leadingSpacer.setManaged(true);
            trailingSpacer.setManaged(false);
            avatarPane.setManaged(false);
            avatarPane.setVisible(false);
            bubbleRow.setAlignment(Pos.TOP_RIGHT);
            bubbleRow.getChildren().setAll(actionsButton, bubbleStack);
            applyOutgoingStyles(message);
            applyPrivateMessageStatus(message, privateConversation);
        } else {
            root.setAlignment(Pos.TOP_LEFT);
            contentBox.setAlignment(Pos.TOP_LEFT);
            root.getStyleClass().add("message-row-incoming");
            leadingSpacer.setManaged(false);
            trailingSpacer.setManaged(true);
            bubbleRow.setAlignment(Pos.TOP_LEFT);
            bubbleRow.getChildren().setAll(bubbleStack, actionsButton);
            applyIncomingStyles(message);
        }
    }

    public void setActions(Runnable onReplyAction, Runnable onDeleteAction) {
        this.onReplyAction = onReplyAction;
        this.onDeleteAction = onDeleteAction;
        replyMenuItem.setDisable(onReplyAction == null);
        deleteMenuItem.setDisable(onDeleteAction == null);
    }

    public void playEntrance() {
        root.setOpacity(0);
        root.setTranslateY(10);

        FadeTransition fade = new FadeTransition(Duration.millis(220), root);
        fade.setFromValue(0);
        fade.setToValue(1);

        TranslateTransition slide = new TranslateTransition(Duration.millis(220), root);
        slide.setFromY(10);
        slide.setToY(0);

        new ParallelTransition(fade, slide).play();
    }

    private void configureAvatar(Image avatarImage, String senderDisplayName) {
        boolean hasImage = avatarImage != null && !avatarImage.isError();
        avatarImageView.setImage(avatarImage);
        avatarImageView.setManaged(hasImage);
        avatarImageView.setVisible(hasImage);
        avatarLabel.setText(buildAvatarText(senderDisplayName));
        avatarLabel.setManaged(!hasImage);
        avatarLabel.setVisible(!hasImage);
    }

    private void updateAvatarPaneStyle() {
        boolean hasImage = avatarImageView.getImage() != null && !avatarImageView.getImage().isError();
        if (hasImage) {
            if (!avatarPane.getStyleClass().contains("avatar-has-image")) {
                avatarPane.getStyleClass().add("avatar-has-image");
            }
            return;
        }

        avatarPane.getStyleClass().remove("avatar-has-image");
    }

    private static String buildAvatarText(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "?";
        }

        String[] parts = displayName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }

        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
    }

    private void applyPrivateMessageStatus(Message message, boolean privateConversation) {
        if (!privateConversation || !message.isReadByPeer()) {
            return;
        }

        statusLabel.setText("\u0110\u00e3 xem");
        statusLabel.setManaged(true);
        statusLabel.setVisible(true);
    }

    private void configureReplyPreview(Message message) {
        configureReplyPreview(message, "");
    }

    private void configureReplyPreview(Message message, String highlightQuery) {
        if (!message.hasReplyPreview()) {
            return;
        }

        applyHighlightedText(replyPreviewFlow, message.getReplyPreviewText(), highlightQuery, "reply-preview-text", "reply-preview-highlight");
        replyPreviewFlow.setManaged(true);
        replyPreviewFlow.setVisible(true);
    }

    private void configureContent(Message message, String highlightQuery) {
        if (message.isImageAttachment()) {
            configureImageAttachment(message);
            return;
        }

        if (message.isFileAttachment()) {
            configureFileAttachment(message);
            return;
        }

        applyHighlightedText(bubbleFlow, message.getText(), highlightQuery, "message-bubble-text", "message-bubble-highlight");
        bubbleFlow.setManaged(true);
        bubbleFlow.setVisible(true);
    }

    private void applyOutgoingStyles(Message message) {
        if (message.isEmojiIcon()) {
            ensureImagePaneStyle("emoji-sticker-pane");
            return;
        }
        if (message.isImageAttachment()) {
            imageAttachmentPane.getStyleClass().add("attachment-bubble-outgoing");
            return;
        }
        if (message.isFileAttachment()) {
            fileAttachmentPane.getStyleClass().add("file-attachment-bubble-outgoing");
            return;
        }

        bubbleFlow.getStyleClass().add("bubble-outgoing");
    }

    private void applyIncomingStyles(Message message) {
        if (message.isEmojiIcon()) {
            ensureImagePaneStyle("emoji-sticker-pane");
            return;
        }
        if (message.isImageAttachment()) {
            imageAttachmentPane.getStyleClass().add("attachment-bubble-incoming");
            return;
        }
        if (message.isFileAttachment()) {
            fileAttachmentPane.getStyleClass().add("file-attachment-bubble-incoming");
            return;
        }

        bubbleFlow.getStyleClass().add("bubble-incoming");
    }

    private void configureImageAttachment(Message message) {
        byte[] imageBytes = message.getAttachmentBytes();
        if (imageBytes == null || imageBytes.length == 0) {
            applyHighlightedText(bubbleFlow, "[Image unavailable]", "", "message-bubble-text", "message-bubble-highlight");
            bubbleFlow.setManaged(true);
            bubbleFlow.setVisible(true);
            return;
        }

        Image image = new Image(new ByteArrayInputStream(imageBytes));
        if (image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0) {
            applyHighlightedText(bubbleFlow, "[Image unavailable]", "", "message-bubble-text", "message-bubble-highlight");
            bubbleFlow.setManaged(true);
            bubbleFlow.setVisible(true);
            return;
        }

        attachmentImageView.setImage(image);
        if (message.isEmojiIcon()) {
            attachmentImageView.setFitWidth(Math.min(92, image.getWidth()));
            imageAttachmentNameLabel.setManaged(false);
            imageAttachmentNameLabel.setVisible(false);
            imageAttachmentMetaLabel.setManaged(false);
            imageAttachmentMetaLabel.setVisible(false);
            imageSaveButton.setManaged(false);
            imageSaveButton.setVisible(false);
            ensureImagePaneStyle("emoji-sticker-pane");
        } else {
            attachmentImageView.setFitWidth(Math.min(340, image.getWidth()));
            imageAttachmentNameLabel.setText(message.getAttachmentFileName());
            imageAttachmentMetaLabel.setText("Image  " + formatBytes(message.getAttachmentSizeBytes()));
            imageSaveButton.setManaged(true);
            imageSaveButton.setVisible(true);
        }
        imageAttachmentPane.setManaged(true);
        imageAttachmentPane.setVisible(true);
    }

    private void ensureImagePaneStyle(String styleClass) {
        if (styleClass == null || styleClass.isBlank()) {
            return;
        }
        if (!imageAttachmentPane.getStyleClass().contains(styleClass)) {
            imageAttachmentPane.getStyleClass().add(styleClass);
        }
    }

    private void configureFileAttachment(Message message) {
        fileAttachmentNameLabel.setText(message.getAttachmentFileName());
        String metaText = message.getAttachmentMimeType().isBlank()
                ? formatBytes(message.getAttachmentSizeBytes())
                : message.getAttachmentMimeType() + "  " + formatBytes(message.getAttachmentSizeBytes());
        fileAttachmentMetaLabel.setText(metaText.trim());
        boolean savable = message.hasAttachmentBytes();
        fileSaveButton.setManaged(savable);
        fileSaveButton.setVisible(savable);
        fileAttachmentPane.setManaged(true);
        fileAttachmentPane.setVisible(true);
    }

    private void applyHighlightedText(TextFlow textFlow, String text, String highlightQuery, String baseClass, String highlightClass) {
        textFlow.getChildren().clear();

        String safeText = text == null ? "" : text;
        String safeQuery = highlightQuery == null ? "" : highlightQuery.trim();
        if (safeText.isEmpty()) {
            return;
        }

        if (safeQuery.isEmpty()) {
            Text plainText = new Text(safeText);
            plainText.getStyleClass().add(baseClass);
            textFlow.getChildren().add(plainText);
            return;
        }

        String lowerText = safeText.toLowerCase(Locale.ROOT);
        String lowerQuery = safeQuery.toLowerCase(Locale.ROOT);
        int startIndex = 0;

        while (startIndex < safeText.length()) {
            int matchIndex = lowerText.indexOf(lowerQuery, startIndex);
            if (matchIndex < 0) {
                Text remainingText = new Text(safeText.substring(startIndex));
                remainingText.getStyleClass().add(baseClass);
                textFlow.getChildren().add(remainingText);
                break;
            }

            if (matchIndex > startIndex) {
                Text beforeMatch = new Text(safeText.substring(startIndex, matchIndex));
                beforeMatch.getStyleClass().add(baseClass);
                textFlow.getChildren().add(beforeMatch);
            }

            Text highlightedText = new Text(safeText.substring(matchIndex, matchIndex + safeQuery.length()));
            highlightedText.getStyleClass().addAll(baseClass, highlightClass);
            textFlow.getChildren().add(highlightedText);
            startIndex = matchIndex + safeQuery.length();
        }
    }

    @FXML
    private void onShowActions() {
        if (!actionsButton.isVisible()) {
            return;
        }

        if (actionsMenu.isShowing()) {
            actionsMenu.hide();
            return;
        }

        actionsMenu.show(actionsButton, Side.BOTTOM, 0, 6);
    }

    private void copyCurrentMessage() {
        if (message == null) {
            return;
        }

        String copyText = message.getCopyText();
        if (copyText.isBlank()) {
            return;
        }

        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(copyText);
        Clipboard.getSystemClipboard().setContent(clipboardContent);
    }

    @FXML
    private void onSaveImageAttachment() {
        saveAttachment();
    }

    @FXML
    private void onSaveFileAttachment() {
        saveAttachment();
    }

    private void saveAttachment() {
        if (message == null || !message.isAttachment() || !message.hasAttachmentBytes()) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save attachment");
        chooser.setInitialFileName(message.getAttachmentFileName());
        File selectedFile = chooser.showSaveDialog(actionsButton.getScene().getWindow());
        if (selectedFile == null) {
            return;
        }

        try {
            Path outputPath = selectedFile.toPath();
            Files.write(outputPath, message.getAttachmentBytes());
        } catch (IOException ignored) {
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) {
            return "";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }

        double kilobytes = bytes / 1024.0;
        if (kilobytes < 1024) {
            return String.format("%.1f KB", kilobytes);
        }

        double megabytes = kilobytes / 1024.0;
        return String.format("%.1f MB", megabytes);
    }
}
