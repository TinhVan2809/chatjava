package controllers;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import models.Conversation;

public class ChatItemController {
    @FXML
    private StackPane rootPane;
    @FXML
    private StackPane avatarPane;
    @FXML
    private ImageView avatarImageView;
    @FXML
    private Label avatarLabel;
    @FXML
    private Label titleLabel;
    @FXML
    private Label subtitleLabel;
    @FXML
    private Label timeLabel;
    @FXML
    private Label unreadBadgeLabel;
    @FXML
    private Circle statusDot;

    private Conversation conversation;

    @FXML
    private void initialize() {
        rootPane.setMaxWidth(Double.MAX_VALUE);
        avatarImageView.setClip(new Circle(22, 22, 22));
        avatarImageView.imageProperty().addListener((observable, oldImage, newImage) -> updateAvatarPaneStyle());
        titleLabel.setMinWidth(0);
        subtitleLabel.setMinWidth(0);
        titleLabel.setWrapText(false);
        subtitleLabel.setWrapText(false);
        timeLabel.setWrapText(false);
        titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        subtitleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        timeLabel.setTextOverrun(OverrunStyle.CLIP);
    }

    public void bind(Conversation conversation) {
        unbind();
        this.conversation = conversation;
        if (conversation == null) {
            return;
        }

        avatarImageView.imageProperty().bind(conversation.avatarImageProperty());
        avatarImageView.visibleProperty().bind(Bindings.isNotNull(conversation.avatarImageProperty()));
        avatarImageView.managedProperty().bind(avatarImageView.visibleProperty());
        avatarLabel.textProperty().bind(conversation.avatarTextProperty());
        avatarLabel.visibleProperty().bind(Bindings.isNull(conversation.avatarImageProperty()));
        avatarLabel.managedProperty().bind(avatarLabel.visibleProperty());
        titleLabel.textProperty().bind(conversation.titleProperty());
        subtitleLabel.textProperty().bind(conversation.subtitleProperty());
        timeLabel.textProperty().bind(conversation.timestampProperty());

        unreadBadgeLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            int unreadCount = conversation.getUnreadCount();
            return unreadCount > 99 ? "99+" : String.valueOf(unreadCount);
        }, conversation.unreadCountProperty()));
        unreadBadgeLabel.visibleProperty().bind(conversation.unreadCountProperty().greaterThan(0));
        unreadBadgeLabel.managedProperty().bind(unreadBadgeLabel.visibleProperty());

        statusDot.visibleProperty().bind(conversation.onlineProperty());
        statusDot.managedProperty().bind(statusDot.visibleProperty());

        rootPane.getStyleClass().removeAll("chat-item-group", "chat-item-direct");
        rootPane.getStyleClass().add(conversation.isGroupConversation() ? "chat-item-group" : "chat-item-direct");
        updateAvatarPaneStyle();
    }

    private void unbind() {
        if (conversation == null) {
            return;
        }

        avatarImageView.imageProperty().unbind();
        avatarImageView.visibleProperty().unbind();
        avatarImageView.managedProperty().unbind();
        avatarLabel.textProperty().unbind();
        avatarLabel.visibleProperty().unbind();
        avatarLabel.managedProperty().unbind();
        titleLabel.textProperty().unbind();
        subtitleLabel.textProperty().unbind();
        timeLabel.textProperty().unbind();
        unreadBadgeLabel.textProperty().unbind();
        unreadBadgeLabel.visibleProperty().unbind();
        unreadBadgeLabel.managedProperty().unbind();
        statusDot.visibleProperty().unbind();
        statusDot.managedProperty().unbind();
    }

    private void updateAvatarPaneStyle() {
        boolean hasImage = conversation != null && conversation.getAvatarImage() != null;
        if (hasImage) {
            if (!avatarPane.getStyleClass().contains("avatar-has-image")) {
                avatarPane.getStyleClass().add("avatar-has-image");
            }
            return;
        }

        avatarPane.getStyleClass().remove("avatar-has-image");
    }
}
