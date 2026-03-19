package models;

import core.EmojiIconCatalog;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

public final class Message {
    public enum Kind {
        TEXT,
        IMAGE,
        FILE,
        SYSTEM
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    public static final String REPLY_BLOCK_START = "RUN_REPLY_BLOCK";
    public static final String REPLY_BLOCK_END = "END_REPLY_BLOCK";

    private final String id;
    private final String senderUsername;
    private final String senderDisplayName;
    private final Kind kind;
    private final String rawText;
    private final String text;
    private final String replyPreviewText;
    private final String attachmentFileName;
    private final String attachmentMimeType;
    private final byte[] attachmentBytes;
    private final long attachmentSizeBytes;
    private final boolean sentByCurrentUser;
    private final boolean systemMessage;
    private final LocalDateTime createdAt;
    private boolean readByPeer;

    private Message(String id, String senderUsername, String senderDisplayName, Kind kind, String text,
            String attachmentFileName, String attachmentMimeType, byte[] attachmentBytes, long attachmentSizeBytes,
            boolean sentByCurrentUser, boolean systemMessage, LocalDateTime createdAt) {
        ParsedContent parsedContent = kind == Kind.TEXT || systemMessage
                ? parseContent(text)
                : new ParsedContent("", "");
        this.id = id == null ? "" : id;
        this.senderUsername = senderUsername == null ? "" : senderUsername.trim();
        this.senderDisplayName = senderDisplayName == null ? "" : senderDisplayName.trim();
        this.kind = systemMessage ? Kind.SYSTEM : kind;
        this.rawText = text == null ? "" : text.trim();
        this.text = kind == Kind.TEXT || systemMessage ? parsedContent.bodyText() : "";
        this.replyPreviewText = kind == Kind.TEXT ? parsedContent.replyPreviewText() : "";
        this.attachmentFileName = attachmentFileName == null ? "" : attachmentFileName.trim();
        this.attachmentMimeType = attachmentMimeType == null ? "" : attachmentMimeType.trim();
        this.attachmentBytes = attachmentBytes == null || attachmentBytes.length == 0
                ? null
                : Arrays.copyOf(attachmentBytes, attachmentBytes.length);
        this.attachmentSizeBytes = attachmentSizeBytes >= 0
                ? attachmentSizeBytes
                : (this.attachmentBytes == null ? -1 : this.attachmentBytes.length);
        this.sentByCurrentUser = sentByCurrentUser;
        this.systemMessage = systemMessage;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }

    public static Message userMessage(String id, String senderUsername, String senderDisplayName, String text,
            boolean sentByCurrentUser, LocalDateTime createdAt) {
        return new Message(id, senderUsername, senderDisplayName, Kind.TEXT, text,
                "", "", null, -1, sentByCurrentUser, false, createdAt);
    }

    public static Message imageMessage(String id, String senderUsername, String senderDisplayName,
            String fileName, String mimeType, byte[] imageBytes, boolean sentByCurrentUser, LocalDateTime createdAt) {
        return new Message(id, senderUsername, senderDisplayName, Kind.IMAGE, "",
                fileName, mimeType, imageBytes, -1, sentByCurrentUser, false, createdAt);
    }

    public static Message fileMessage(String id, String senderUsername, String senderDisplayName,
            String fileName, String mimeType, byte[] fileBytes, long sizeBytes,
            boolean sentByCurrentUser, LocalDateTime createdAt) {
        return new Message(id, senderUsername, senderDisplayName, Kind.FILE, "",
                fileName, mimeType, fileBytes, sizeBytes, sentByCurrentUser, false, createdAt);
    }

    public static Message systemMessage(String text, LocalDateTime createdAt) {
        return new Message("", "", "System", Kind.SYSTEM, text,
                "", "", null, -1, false, true, createdAt);
    }

    public String getId() {
        return id;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public String getSenderDisplayName() {
        return senderDisplayName;
    }

    public Kind getKind() {
        return kind;
    }

    public String getText() {
        return text;
    }

    public String getRawText() {
        return rawText;
    }

    public boolean hasReplyPreview() {
        return !replyPreviewText.isBlank();
    }

    public String getReplyPreviewText() {
        return replyPreviewText;
    }

    public String getCopyText() {
        if (isAttachment()) {
            return getSummaryText();
        }

        if (!hasReplyPreview()) {
            return text;
        }

        if (text.isBlank()) {
            return replyPreviewText;
        }

        return replyPreviewText + System.lineSeparator() + text;
    }

    public boolean isImageAttachment() {
        return kind == Kind.IMAGE;
    }

    public boolean isFileAttachment() {
        return kind == Kind.FILE;
    }

    public boolean isAttachment() {
        return isImageAttachment() || isFileAttachment();
    }

    public boolean isEmojiIcon() {
        return isImageAttachment() && EmojiIconCatalog.isEmojiIconFile(getAttachmentFileName());
    }

    public String getEmojiIconLabel() {
        if (!isEmojiIcon()) {
            return "";
        }
        return EmojiIconCatalog.displayNameFromFileName(getAttachmentFileName());
    }

    public String getAttachmentFileName() {
        if (!attachmentFileName.isBlank()) {
            return attachmentFileName;
        }
        return isImageAttachment() ? "image" : "file";
    }

    public String getAttachmentMimeType() {
        return attachmentMimeType;
    }

    public byte[] getAttachmentBytes() {
        return attachmentBytes == null ? null : Arrays.copyOf(attachmentBytes, attachmentBytes.length);
    }

    public boolean hasAttachmentBytes() {
        return attachmentBytes != null && attachmentBytes.length > 0;
    }

    public long getAttachmentSizeBytes() {
        if (attachmentSizeBytes >= 0) {
            return attachmentSizeBytes;
        }
        return attachmentBytes == null ? -1 : attachmentBytes.length;
    }

    public boolean isSentByCurrentUser() {
        return sentByCurrentUser;
    }

    public boolean isSystemMessage() {
        return systemMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isReadByPeer() {
        return readByPeer;
    }

    public void markReadByPeer() {
        readByPeer = true;
    }

    public String getTimestamp() {
        return TIME_FORMAT.format(createdAt);
    }

    public String getPreviewText() {
        if (systemMessage) {
            return text;
        }

        String summaryText = getSummaryText();
        if (sentByCurrentUser) {
            return "You: " + summaryText;
        }

        return summaryText;
    }

    public String getSummaryText() {
        if (systemMessage) {
            return text;
        }
        if (isEmojiIcon()) {
            return "Icon: " + getEmojiIconLabel();
        }
        if (kind == Kind.IMAGE) {
            return "Image: " + getAttachmentFileName();
        }
        if (kind == Kind.FILE) {
            return "File: " + getAttachmentFileName();
        }
        return text;
    }

    public String getReplySourceText() {
        return getSummaryText();
    }

    public static String composeReplyMessage(String replyPreviewText, String messageText) {
        String safeReplyPreview = replyPreviewText == null ? "" : replyPreviewText.trim();
        String safeMessageText = messageText == null ? "" : messageText.trim();
        if (safeReplyPreview.isBlank()) {
            return safeMessageText;
        }
        if (safeMessageText.isBlank()) {
            return safeReplyPreview;
        }

        return REPLY_BLOCK_START
                + System.lineSeparator()
                + safeReplyPreview
                + System.lineSeparator()
                + REPLY_BLOCK_END
                + System.lineSeparator()
                + safeMessageText;
    }

    private static ParsedContent parseContent(String rawText) {
        String safeText = rawText == null ? "" : rawText.trim();
        if (safeText.isEmpty()) {
            return new ParsedContent("", "");
        }

        int start = safeText.indexOf(REPLY_BLOCK_START);
        int end = safeText.indexOf(REPLY_BLOCK_END);
        if (start < 0 || end <= start) {
            return new ParsedContent(safeText, "");
        }

        String replyPreview = safeText.substring(start + REPLY_BLOCK_START.length(), end).trim();
        String body = safeText.substring(end + REPLY_BLOCK_END.length()).trim();
        return new ParsedContent(body, replyPreview);
    }

    private record ParsedContent(String bodyText, String replyPreviewText) {
    }
}
