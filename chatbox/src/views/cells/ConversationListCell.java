package views.cells;

import java.io.IOException;

import controllers.ChatItemController;
import core.UiResources;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ListCell;
import javafx.scene.layout.StackPane;
import models.Conversation;

public class ConversationListCell extends ListCell<Conversation> {
    private FXMLLoader loader;
    private StackPane root;
    private ChatItemController controller;
    private boolean widthListenerInstalled;

    @Override
    protected void updateItem(Conversation item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            if (controller != null) {
                controller.bind(null);
            }
            setText(null);
            setGraphic(null);
            return;
        }

        ensureLoaded();
        installWidthListener();
        applyWidth(getListView() == null ? getWidth() : getListView().getWidth());
        controller.bind(item);
        setText(null);
        setGraphic(root);
    }

    private void ensureLoaded() {
        if (loader != null) {
            return;
        }

        try {
            loader = new FXMLLoader(UiResources.fxml("views/ChatItem.fxml"));
            root = loader.load();
            controller = loader.getController();
            root.setMaxWidth(Double.MAX_VALUE);
            installWidthListener();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load chat item cell.", ex);
        }
    }

    private void installWidthListener() {
        if (widthListenerInstalled || getListView() == null) {
            return;
        }

        getListView().widthProperty().addListener((observable, oldWidth, newWidth) -> applyWidth(newWidth.doubleValue()));
        widthListenerInstalled = true;
    }

    private void applyWidth(double listWidth) {
        if (root == null) {
            return;
        }

        double availableWidth = Math.max(0, listWidth - 44);
        root.setPrefWidth(availableWidth);
        root.setMaxWidth(availableWidth);
    }
}
