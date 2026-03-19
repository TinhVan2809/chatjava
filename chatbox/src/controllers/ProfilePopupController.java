package controllers;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class ProfilePopupController {
    @FXML
    private Button closeButton;
    @FXML
    private StackPane avatarPane;
    @FXML
    private ImageView avatarImageView;
    @FXML
    private Label avatarFallbackLabel;
    @FXML
    private Label fullNameLabel;
    @FXML
    private Label usernameLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Button updateAvatarButton;

    private Consumer<Path> onAvatarSelected;

    @FXML
    private void initialize() {
        avatarImageView.setClip(new Circle(48, 48, 48));
        avatarImageView.imageProperty().addListener((observable, oldImage, newImage) -> updateAvatarPaneStyle());
    }

    public void setProfile(String fullName, String username, String initials) {
        fullNameLabel.setText(fullName == null ? "" : fullName.trim());
        usernameLabel.setText(username == null || username.isBlank() ? "" : "@" + username.trim());
        avatarFallbackLabel.setText(initials == null || initials.isBlank() ? "?" : initials.trim());
    }

    public void setAvatarImage(Image image) {
        boolean hasImage = image != null && !image.isError();
        avatarImageView.setImage(image);
        avatarImageView.setManaged(hasImage);
        avatarImageView.setVisible(hasImage);
        avatarFallbackLabel.setManaged(!hasImage);
        avatarFallbackLabel.setVisible(!hasImage);
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

    public void setStatus(String message, boolean error) {
        String text = message == null ? "" : message.trim();
        statusLabel.setText(text.isBlank() ? "Choose a square image for the best result." : text);
        statusLabel.getStyleClass().removeAll("profile-popup-note-error", "profile-popup-note-info");
        statusLabel.getStyleClass().add(error ? "profile-popup-note-error" : "profile-popup-note-info");
    }

    public void setOnAvatarSelected(Consumer<Path> onAvatarSelected) {
        this.onAvatarSelected = onAvatarSelected;
    }

    @FXML
    private void onChooseAvatar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose avatar image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"));

        File selectedFile = chooser.showOpenDialog(updateAvatarButton.getScene().getWindow());
        if (selectedFile == null || onAvatarSelected == null) {
            return;
        }

        onAvatarSelected.accept(selectedFile.toPath());
    }

    @FXML
    private void onClose() {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        if (stage != null) {
            stage.close();
        }
    }
}
