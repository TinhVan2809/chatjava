package controllers;

import java.util.List;
import java.util.function.Consumer;
import core.EmojiIconCatalog;
import core.EmojiIconCatalog.EmojiIconSpec;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;

public class EmojiPickerController {
    @FXML
    private TextField searchField;
    @FXML
    private ChoiceBox<String> categoryChoiceBox;
    @FXML
    private FlowPane emojiGrid;
    @FXML
    private Label statusLabel;

    private Consumer<EmojiIconSpec> onEmojiSelected;

    @FXML
    private void initialize() {
        categoryChoiceBox.getSelectionModel().selectedItemProperty()
                .addListener((observable, previous, current) -> renderIcons());
        searchField.textProperty().addListener((observable, oldValue, newValue) -> renderIcons());
        refreshCatalog();
    }

    public void setOnEmojiSelected(Consumer<EmojiIconSpec> onEmojiSelected) {
        this.onEmojiSelected = onEmojiSelected;
    }

    public void refreshCatalog() {
        String previousCategory = categoryChoiceBox.getSelectionModel().getSelectedItem();
        categoryChoiceBox.getItems().setAll(EmojiIconCatalog.categories());
        if (previousCategory != null && categoryChoiceBox.getItems().contains(previousCategory)) {
            categoryChoiceBox.getSelectionModel().select(previousCategory);
        } else {
            categoryChoiceBox.getSelectionModel().selectFirst();
        }
        renderIcons();
    }

    private void renderIcons() {
        emojiGrid.getChildren().clear();

        String selectedCategory = categoryChoiceBox.getSelectionModel().getSelectedItem();
        String searchQuery = searchField.getText();
        List<EmojiIconSpec> icons = EmojiIconCatalog.filter(selectedCategory, searchQuery);
        for (EmojiIconSpec icon : icons) {
            emojiGrid.getChildren().add(createEmojiButton(icon));
        }

        if (icons.isEmpty()) {
            statusLabel.setText("No icons match this filter.");
            return;
        }

        statusLabel.setText(icons.size() + " icon(s)");
    }

    private Button createEmojiButton(EmojiIconSpec icon) {
        Button button = new Button();
        button.getStyleClass().add("emoji-picker-button");
        button.setText("");
        button.setGraphic(buildPreview(icon));
        button.setTooltip(new Tooltip(icon.displayName() + " - " + icon.category()));
        button.setOnAction(event -> emit(icon));
        return button;
    }

    private ImageView buildPreview(EmojiIconSpec icon) {
        ImageView imageView = new ImageView(EmojiIconCatalog.previewImage(icon.id()));
        imageView.setFitWidth(38);
        imageView.setFitHeight(38);
        imageView.setPreserveRatio(true);
        return imageView;
    }

    private void emit(EmojiIconSpec icon) {
        if (onEmojiSelected != null && icon != null) {
            onEmojiSelected.accept(icon);
        }
    }
}
