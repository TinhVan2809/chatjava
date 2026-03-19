package views.cells;

import java.io.IOException;

import controllers.MessageBubbleController;
import core.UiResources;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import models.Message;

public class MessageListCell extends ListCell<Message> {
    private FXMLLoader loader;
    private HBox root;
    private MessageBubbleController controller;

    @Override
    protected void updateItem(Message item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            return;
        }

        ensureLoaded();
        controller.setMessage(item);
        setText(null);
        setGraphic(root);
    }

    private void ensureLoaded() {
        if (loader != null) {
            return;
        }

        try {
            loader = new FXMLLoader(UiResources.fxml("views/MessageBubble.fxml"));
            root = loader.load();
            controller = loader.getController();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load message bubble cell.", ex);
        }
    }
}
