package models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;

public final class Conversation {
    private final String id;
    private final String peerUsername;
    private final boolean groupConversation;
    private final ObservableList<Message> messages = FXCollections.observableArrayList();

    private final StringProperty title = new SimpleStringProperty("");
    private final StringProperty subtitle = new SimpleStringProperty("");
    private final StringProperty timestamp = new SimpleStringProperty("");
    private final StringProperty statusText = new SimpleStringProperty("");
    private final StringProperty avatarText = new SimpleStringProperty("?");
    private final ObjectProperty<Image> avatarImage = new SimpleObjectProperty<>();
    private final BooleanProperty online = new SimpleBooleanProperty(false);
    private final IntegerProperty unreadCount = new SimpleIntegerProperty(0);
    private final Set<String> pendingSeenMessageIds = new LinkedHashSet<>();

    private String lastPreviewText = "";
    private boolean typing;
    private long lastActivityEpochMillis;

    public Conversation(String id, String peerUsername, boolean groupConversation, String title, String statusText) {
        this.id = id;
        this.peerUsername = peerUsername;
        this.groupConversation = groupConversation;
        this.title.set(title == null ? "" : title.trim());
        this.statusText.set(statusText == null ? "" : statusText.trim());
        this.avatarText.set(buildAvatarText(this.title.get(), groupConversation));
        this.subtitle.set(groupConversation ? "Room is ready" : "No messages yet");
    }

    public String getId() {
        return id;
    }

    public String getPeerUsername() {
        return peerUsername;
    }

    public boolean isGroupConversation() {
        return groupConversation;
    }

    public ObservableList<Message> getMessages() {
        return messages;
    }

    public StringProperty titleProperty() {
        return title;
    }

    public StringProperty subtitleProperty() {
        return subtitle;
    }

    public StringProperty timestampProperty() {
        return timestamp;
    }

    public StringProperty statusTextProperty() {
        return statusText;
    }

    public StringProperty avatarTextProperty() {
        return avatarText;
    }

    public ObjectProperty<Image> avatarImageProperty() {
        return avatarImage;
    }

    public BooleanProperty onlineProperty() {
        return online;
    }

    public IntegerProperty unreadCountProperty() {
        return unreadCount;
    }

    public String getTitle() {
        return title.get();
    }

    public String getSubtitle() {
        return subtitle.get();
    }

    public String getTimestamp() {
        return timestamp.get();
    }

    public String getStatusText() {
        return statusText.get();
    }

    public Image getAvatarImage() {
        return avatarImage.get();
    }

    public boolean isOnline() {
        return online.get();
    }

    public int getUnreadCount() {
        return unreadCount.get();
    }

    public long getLastActivityEpochMillis() {
        return lastActivityEpochMillis;
    }

    public void updateTitle(String value) {
        String safeValue = value == null ? "" : value.trim();
        title.set(safeValue);
        avatarText.set(buildAvatarText(safeValue, groupConversation));
    }

    public void updateStatusText(String value) {
        statusText.set(value == null ? "" : value.trim());
    }

    public void updateAvatarImage(Image image) {
        avatarImage.set(image);
    }

    public void setOnline(boolean value) {
        online.set(value);
    }

    public void addMessage(Message message) {
        if (message == null) {
            return;
        }

        messages.add(message);
        lastActivityEpochMillis = System.currentTimeMillis();
        updatePreviewFromMessage(message);
    }

    public void removeMessage(Message message) {
        if (message == null) {
            return;
        }

        if (!messages.remove(message)) {
            return;
        }

        refreshPreviewFromMessages();
    }

    public void showTyping(String text) {
        typing = true;
        subtitle.set(text == null || text.isBlank() ? "typing..." : text.trim());
    }

    public void clearTyping() {
        typing = false;
        if (lastPreviewText == null || lastPreviewText.isBlank()) {
            subtitle.set(groupConversation ? "Room is ready" : "No messages yet");
            return;
        }
        subtitle.set(lastPreviewText);
    }

    public boolean isTyping() {
        return typing;
    }

    public void incrementUnread() {
        unreadCount.set(unreadCount.get() + 1);
    }

    public void clearUnread() {
        unreadCount.set(0);
    }

    public void bumpSortOrder() {
        lastActivityEpochMillis = Math.max(lastActivityEpochMillis, System.currentTimeMillis());
    }

    public void queuePendingSeenMessage(String messageId) {
        String safeMessageId = messageId == null ? "" : messageId.trim();
        if (!safeMessageId.isEmpty()) {
            pendingSeenMessageIds.add(safeMessageId);
        }
    }

    public List<String> consumePendingSeenMessageIds() {
        List<String> pendingIds = new ArrayList<>(pendingSeenMessageIds);
        pendingSeenMessageIds.clear();
        return pendingIds;
    }

    public boolean markMessageReadById(String messageId) {
        String safeMessageId = messageId == null ? "" : messageId.trim();
        if (safeMessageId.isEmpty()) {
            return false;
        }

        for (Message message : messages) {
            if (!safeMessageId.equals(message.getId())) {
                continue;
            }

            message.markReadByPeer();
            return true;
        }

        return false;
    }

    public void seedPreview(String previewText, String timeText) {
        lastPreviewText = previewText == null ? "" : previewText.trim();
        if (!typing) {
            subtitle.set(lastPreviewText.isBlank()
                    ? (groupConversation ? "Room is ready" : "No messages yet")
                    : lastPreviewText);
        }
        timestamp.set(timeText == null ? "" : timeText.trim());
        if (!timestamp.get().isBlank()) {
            lastActivityEpochMillis = LocalDateTime.now().minusMinutes(1).atZone(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli();
        }
    }

    private static String buildAvatarText(String title, boolean groupConversation) {
        String safeTitle = title == null ? "" : title.trim();
        if (groupConversation) {
            return "GR";
        }
        if (safeTitle.isEmpty()) {
            return "?";
        }

        String[] parts = safeTitle.split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }

        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
    }

    private void updatePreviewFromMessage(Message message) {
        lastPreviewText = message.getPreviewText();
        if (!typing) {
            subtitle.set(lastPreviewText);
        }
        timestamp.set(message.getTimestamp());
    }

    private void refreshPreviewFromMessages() {
        if (messages.isEmpty()) {
            lastPreviewText = "";
            if (!typing) {
                subtitle.set(groupConversation ? "Room is ready" : "No messages yet");
            }
            timestamp.set("");
            return;
        }

        updatePreviewFromMessage(messages.get(messages.size() - 1));
    }
}
