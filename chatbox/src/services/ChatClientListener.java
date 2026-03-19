package services;

import java.util.List;

public interface ChatClientListener {
    void onGroupChatLine(String rawLine);

    void onOnlineUsers(List<String> displayUsers);

    void onGroupTyping(String fromUsername, String fromDisplayName, boolean typing);

    void onGroupImage(String time, String fromUsername, String fromDisplayName, String fileName, String mimeType,
            byte[] imageBytes);

    void onGroupFile(String time, String fromUsername, String fromDisplayName, String fileName, String mimeType,
            byte[] fileBytes);

    void onPrivateMessage(String fromUsername, String fromDisplayName, String messageId, String message);

    void onPrivateMessageSent(String toUsername, String toDisplayName, String messageId, String message);

    void onPrivateImage(String fromUsername, String fromDisplayName, String fileName, String mimeType, byte[] imageBytes);

    void onPrivateImageSent(String toUsername, String toDisplayName, String fileName, String mimeType, byte[] imageBytes);

    void onPrivateFile(String fromUsername, String fromDisplayName, String fileName, String mimeType, byte[] fileBytes);

    void onPrivateFileSent(String toUsername, String toDisplayName, String fileName, String mimeType, long sizeBytes);

    void onPrivateRead(String fromUsername, String messageId);

    void onPrivateTyping(String fromUsername, String fromDisplayName, boolean typing);

    void onPrivateSystemMessage(String peerUsername, String message);

    void onUserAvatarUpdated(String username, byte[] avatarBytes);

    void onPrivateCallInvite(String fromUsername, String fromDisplayName, String callId);

    void onPrivateCallRinging(String fromUsername, String fromDisplayName, String callId);

    void onPrivateCallAccepted(String fromUsername, String fromDisplayName, String callId);

    void onPrivateCallDeclined(String fromUsername, String fromDisplayName, String callId, String reason);

    void onPrivateCallEnded(String fromUsername, String callId);

    void onPrivateCallAudio(String fromUsername, String callId, byte[] audioBytes);

    void onConnectionClosed(String message);
}
