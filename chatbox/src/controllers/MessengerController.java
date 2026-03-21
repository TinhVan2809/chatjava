package controllers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import core.DesktopApp;
import core.EmojiIconCatalog;
import core.EmojiIconCatalog.EmojiIconSpec;
import core.StoragePaths;
import core.UiResources;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import java.io.File;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import models.ClientSession;
import models.Conversation;
import models.Message;
import services.AudioCallManager;
import services.ChatClientListener;
import services.ChatClientService;
import views.cells.ConversationListCell;

public class MessengerController implements ChatClientListener {
    private static final String GROUP_CONVERSATION_ID = "group";
    private static final Pattern USERNAME_AT_END = Pattern.compile("\\(([^)]+)\\)\\s*$");
    private static final Pattern TIME_AND_BODY = Pattern.compile("^\\[(.+?)]\\s+(.*)$");
    private static final DateTimeFormatter SERVER_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FALLBACK_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    private static final String PHONE_ICON_PATH =
            "M6.62 10.79C8.06 13.62 10.38 15.94 13.21 17.38L15.41 15.18C15.69 14.9 16.08 14.82 16.43 14.93C17.55 15.3 18.75 15.5 20 15.5C20.55 15.5 21 15.95 21 16.5V20C21 20.55 20.55 21 20 21C10.61 21 3 13.39 3 4C3 3.45 3.45 3 4 3H7.5C8.05 3 8.5 3.45 8.5 4C8.5 5.25 8.7 6.45 9.07 7.57C9.18 7.92 9.1 8.31 8.82 8.59L6.62 10.79Z";
    private static final String VIDEO_ICON_PATH =
            "M17 10.5V7C17 6.45 16.55 6 16 6H4C3.45 6 3 6.45 3 7V17C3 17.55 3.45 18 4 18H16C16.55 18 17 17.55 17 17V13.5L21 17V7L17 10.5Z";
    private static final String SEARCH_ICON_PATH =
            "M15.5 14H14.71L14.43 13.73C15.41 12.59 16 11.11 16 9.5C16 5.91 13.09 3 9.5 3C5.91 3 3 5.91 3 9.5C3 13.09 5.91 16 9.5 16C11.11 16 12.59 15.41 13.73 14.43L14 14.71V15.5L19 20.49L20.49 19L15.5 14ZM9.5 14C7.01 14 5 11.99 5 9.5C5 7.01 7.01 5 9.5 5C11.99 5 14 7.01 14 9.5C14 11.99 11.99 14 9.5 14Z";
    private static final String EMOJI_ICON_PATH =
            "M12 22C6.48 22 2 17.52 2 12S6.48 2 12 2S22 6.48 22 12S17.52 22 12 22ZM8.5 11.5C9.33 11.5 10 10.83 10 10S9.33 8.5 8.5 8.5S7 9.17 7 10S7.67 11.5 8.5 11.5ZM15.5 11.5C16.33 11.5 17 10.83 17 10S16.33 8.5 15.5 8.5S14 9.17 14 10S14.67 11.5 15.5 11.5ZM12 18C14.33 18 16.31 16.54 17.11 14.5H6.89C7.69 16.54 9.67 18 12 18Z";
    private static final String IMAGE_ICON_PATH =
            "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z";
    private static final String FILE_ICON_PATH =
            "M16.5 6v11.5c0 2.21-1.79 4-4 4s-4-1.79-4-4V5a2.5 2.5 0 0 1 5 0v10.5c0 .55-.45 1-1 1s-1-.45-1-1V6H10v9.5a2.5 2.5 0 0 0 5 0V5c0-2.21-1.79-4-4-4S7 2.79 7 5v12.5c0 3.04 2.46 5.5 5.5 5.5s5.5-2.46 5.5-5.5V6h-1.5z";

    @FXML
    private BorderPane rootPane;
    @FXML
    private ListView<Conversation> conversationListView;
    @FXML
    private TextField searchField;
    @FXML
    private Label currentUserNameLabel;
    @FXML
    private Label currentUsernameLabel;
    @FXML
    private Label currentUserAvatarLabel;
    @FXML
    private ImageView currentUserAvatarImageView;
    @FXML
    private StackPane currentUserAvatarPane;
    @FXML
    private StackPane headerAvatarPane;
    @FXML
    private ImageView headerAvatarImageView;
    @FXML
    private Label headerAvatarLabel;
    @FXML
    private Label chatTitleLabel;
    @FXML
    private Label chatStatusLabel;
    @FXML
    private Circle chatStatusDot;
    @FXML
    private Label callStatusLabel;
    @FXML
    private Button searchMessagesButton;
    @FXML
    private Button callButton;
    @FXML
    private Button videoCallButton;
    @FXML
    private Button hangupButton;
    @FXML
    private ScrollPane messagesScrollPane;
    @FXML
    private VBox messagesContainer;
    @FXML
    private StackPane emptyStatePane;
    @FXML
    private Label emptyStateTitleLabel;
    @FXML
    private Label emptyStateBodyLabel;
    @FXML
    private Button newMessagesButton;
    @FXML
    private HBox typingIndicatorPane;
    @FXML
    private Label typingIndicatorTextLabel;
    @FXML
    private Circle typingDotOne;
    @FXML
    private Circle typingDotTwo;
    @FXML
    private Circle typingDotThree;
    @FXML
    private VBox messageSearchSidebar;
    @FXML
    private TextField messageSearchField;
    @FXML
    private Label messageSearchMetaLabel;
    @FXML
    private ListView<MessageSearchResult> messageSearchResultsListView;
    @FXML
    private HBox replyPreviewPane;
    @FXML
    private Label replyPreviewTitleLabel;
    @FXML
    private Label replyPreviewBodyLabel;
    @FXML
    private Button clearReplyButton;
    @FXML
    private TextField messageInputField;
    @FXML
    private Button sendImageButton;
    @FXML
    private Button sendEmojiButton;
    @FXML
    private Button sendFileButton;
    @FXML
    private Button sendButton;
    @FXML
    private VBox composerShell;
    @FXML
    private Button themeToggleButton;

    private final ObservableList<Conversation> conversations = FXCollections.observableArrayList();
    private final ObservableList<MessageSearchResult> messageSearchResults = FXCollections.observableArrayList();
    private FilteredList<Conversation> filteredConversations;
    private final Map<String, Conversation> conversationsById = new HashMap<>();
    private final Map<String, Image> avatarImagesByUsername = new HashMap<>();
    private final Map<Message, Node> renderedMessageNodes = new HashMap<>();
    private final Map<String, String> onlineUsers = new HashMap<>();
    private final Map<String, String> groupTypingUsers = new LinkedHashMap<>();
    private final Map<String, PauseTransition> typingExpiryTimers = new HashMap<>();
    private final PauseTransition localTypingIdleTimer = new PauseTransition(Duration.millis(1200));

    private DesktopApp app;
    private ChatClientService service;
    private AudioCallManager audioCallManager;
    private ClientSession session;
    private Conversation groupConversation;
    private Conversation activeConversation;
    private Conversation localTypingConversation;
    private boolean localTypingActive;
    private long lastTypingSentAt;
    private boolean shuttingDown;
    private boolean suppressConversationSelectionHandling;
    private Conversation replyConversation;
    private Message replyMessage;
    private Image currentUserAvatarImage;
    private ProfilePopupController profilePopupController;
    private Stage profilePopupStage;
    private CallState callState = CallState.IDLE;
    private String currentCallId = "";
    private String currentCallPeerUsername = "";
    private String currentCallPeerDisplayName = "";
    private Alert incomingCallAlert;
    private int pendingNewMessagesCount;
    private Message activeUnreadDividerMessage;
    private Node unreadDividerNode;
    private boolean suppressAutoReadStateClearing;
    private Popup emojiPickerPopup;
    private VBox emojiPickerRoot;
    private EmojiPickerController emojiPickerController;
    private Timeline typingDotsTimeline;

    @FXML
    private void initialize() {
        filteredConversations = new FilteredList<>(conversations, conversation -> true);
        conversationListView.setItems(filteredConversations);
        conversationListView.setCellFactory(listView -> new ConversationListCell());
        messageSearchResultsListView.setItems(messageSearchResults);
        messageSearchResultsListView.setCellFactory(listView -> createMessageSearchCell());
        messageSearchResultsListView.setPlaceholder(new Label("No matching messages yet."));
        conversationListView.getSelectionModel().selectedItemProperty()
                .addListener((observable, previous, current) -> {
                    if (suppressConversationSelectionHandling) {
                        return;
                    }
                    selectConversation(current);
                });

        searchField.textProperty().addListener((observable, oldValue, newValue) -> filterConversations(newValue));
        messageSearchField.textProperty().addListener((observable, oldValue, newValue) -> handleMessageSearchQueryChanged());
        messageSearchResultsListView.getSelectionModel().selectedItemProperty()
                .addListener((observable, previous, current) -> scrollToSearchResult(current));
        messageInputField.textProperty().addListener((observable, oldValue, newValue) -> handleTypingInputChange());
        messageInputField.setOnAction(event -> sendCurrentMessage());
        localTypingIdleTimer.setOnFinished(event -> stopTyping(true));

        messagesScrollPane.setFitToWidth(true);
        messagesScrollPane.vvalueProperty().addListener((observable, oldValue, newValue) -> handleMessagesScrollPositionChanged());
        messagesScrollPane.viewportBoundsProperty()
                .addListener((observable, oldBounds, newBounds) -> handleMessagesScrollPositionChanged());
        messagesContainer.heightProperty()
                .addListener((observable, oldHeight, newHeight) -> handleMessagesScrollPositionChanged());
        typingIndicatorPane.setManaged(false);
        typingIndicatorPane.setVisible(false);
        configureTypingIndicatorAnimation();
        newMessagesButton.setManaged(false);
        newMessagesButton.setVisible(false);
        replyPreviewPane.setManaged(false);
        replyPreviewPane.setVisible(false);
        callStatusLabel.setManaged(false);
        callStatusLabel.setVisible(false);
        messageSearchSidebar.setManaged(false);
        messageSearchSidebar.setVisible(false);
        hangupButton.setManaged(false);
        hangupButton.setVisible(false);
        currentUserAvatarImageView.setClip(new Circle(22, 22, 22));
        headerAvatarImageView.setClip(new Circle(22, 22, 22));
        currentUserAvatarImageView.imageProperty()
                .addListener((observable, oldImage, newImage) -> updateAvatarPaneStyle(currentUserAvatarPane, newImage));
        headerAvatarImageView.imageProperty()
                .addListener((observable, oldImage, newImage) -> updateAvatarPaneStyle(headerAvatarPane, newImage));
        configureSvgToolButton(sendImageButton, "Send image", IMAGE_ICON_PATH);
        configureSvgToolButton(sendEmojiButton, "Send icon", EMOJI_ICON_PATH);
        configureSvgToolButton(sendFileButton, "Send file", FILE_ICON_PATH);
    }

    public void setApp(DesktopApp app, ChatClientService service) {
        this.app = app;
        this.service = service;
        this.session = service.getSession();
        this.audioCallManager = new AudioCallManager((peerUsername, callId, audioBytes) -> {
            if (this.service != null) {
                this.service.sendPrivateCallAudio(peerUsername, callId, audioBytes);
            }
        });

        currentUserNameLabel.setText(session.getFullName());
        currentUsernameLabel.setText("@" + session.getUsername());
        currentUserAvatarLabel.setText(buildAvatar(session.getFullName()));
        loadCurrentUserAvatarFromDisk();
        applyCurrentUserAvatar(currentUserAvatarImage);
        cacheAvatarImage(session.getUsername(), currentUserAvatarImage);

        bootstrapConversations();
    }

    @FXML
    private void onSendMessage() {
        sendCurrentMessage();
    }

    @FXML
    private void onSendImage() {
        if (activeConversation == null || service == null) {
            return;
        }

        Path imagePath = promptForAttachment(true);
        if (imagePath == null) {
            return;
        }

        Conversation targetConversation = activeConversation;
        Thread sender = new Thread(() -> sendImageAttachment(targetConversation, imagePath),
                "fx-send-image-" + System.currentTimeMillis());
        sender.setDaemon(true);
        sender.start();
    }

    @FXML
    private void onSendEmoji() {
        if (activeConversation == null || service == null || rootPane.getScene() == null) {
            return;
        }

        toggleEmojiPicker();
    }

    @FXML
    private void onSendFile() {
        if (activeConversation == null || service == null) {
            return;
        }

        Path filePath = promptForAttachment(false);
        if (filePath == null) {
            return;
        }

        Conversation targetConversation = activeConversation;
        Thread sender = new Thread(() -> sendFileAttachment(targetConversation, filePath),
                "fx-send-file-" + System.currentTimeMillis());
        sender.setDaemon(true);
        sender.start();
    }

    @FXML
    private void onClearReply() {
        clearReply();
    }

    @FXML
    private void onDragOver(DragEvent event) {
        if (event.getGestureSource() != composerShell && event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
            if (!composerShell.getStyleClass().contains("drag-over")) {
                composerShell.getStyleClass().add("drag-over");
            }
        }
        event.consume();
    }

    @FXML
    private void onDragDropped(DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean success = false;
        if (db.hasFiles()) {
            List<File> files = db.getFiles();
            if (files != null) {
                for (File file : files) {
                    handleDroppedFile(file.toPath());
                }
            }
            success = true;
        }
        event.setDropCompleted(success);
        composerShell.getStyleClass().remove("drag-over");
        event.consume();
    }

    @FXML
    private void onDragExited(DragEvent event) {
        composerShell.getStyleClass().remove("drag-over");
        event.consume();
    }

    private void handleDroppedFile(Path path) {
        if (activeConversation == null || service == null || path == null) {
            return;
        }

        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean isImage = fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")
                || fileName.endsWith(".gif") || fileName.endsWith(".bmp") || fileName.endsWith(".webp");

        Conversation targetConversation = activeConversation;
        if (isImage) {
            Thread sender = new Thread(() -> sendImageAttachment(targetConversation, path), "fx-send-image-dragdrop-" + System.currentTimeMillis());
            sender.setDaemon(true);
            sender.start();
        } else {
            Thread sender = new Thread(() -> sendFileAttachment(targetConversation, path), "fx-send-file-dragdrop-" + System.currentTimeMillis());
            sender.setDaemon(true);
            sender.start();
        }
    }

    @FXML
    private void onOpenProfile() {
        showProfilePopup();
    }

    @FXML
    private void onLogout() {
        shutdown();
        app.logoutToAuth(session);
    }

    @FXML
    private void onToggleTheme() {
        if (rootPane.getStyleClass().contains("theme-dark")) {
            rootPane.getStyleClass().remove("theme-dark");
            themeToggleButton.setText("Dark");
            syncEmojiPickerTheme();
            return;
        }

        rootPane.getStyleClass().add("theme-dark");
        themeToggleButton.setText("Light");
        syncEmojiPickerTheme();
    }

    @FXML
    private void onStartCall() {
        if (activeConversation == null || activeConversation.isGroupConversation() || service == null) {
            return;
        }

        if (!currentCallId.isBlank()) {
            return;
        }

        if (!activeConversation.isOnline()) {
            appendLocalSystemMessage(activeConversation, activeConversation.getTitle() + " is offline right now.");
            return;
        }

        currentCallId = UUID.randomUUID().toString();
        currentCallPeerUsername = normalizeUsername(activeConversation.getPeerUsername());
        currentCallPeerDisplayName = activeConversation.getTitle();
        callState = CallState.OUTGOING_RINGING;
        service.sendPrivateCallInvite(currentCallPeerUsername, currentCallId);
        appendLocalSystemMessage(activeConversation, "Calling " + currentCallPeerDisplayName + "...");
        updateHeader(activeConversation);
    }

    @FXML
    private void onStartVideoCall() {
        if (activeConversation == null || activeConversation.isGroupConversation()) {
            return;
        }

        appendLocalSystemMessage(activeConversation, "Video call is not available yet in this build.");
    }

    @FXML
    private void onToggleMessageSearch() {
        boolean visible = !messageSearchSidebar.isVisible();
        setMessageSearchSidebarVisible(visible);
        if (visible) {
            handleMessageSearchQueryChanged();
            messageSearchField.requestFocus();
            messageSearchField.selectAll();
            return;
        }

        rerenderActiveConversationForSearchHighlight();
    }

    @FXML
    private void onHangupCall() {
        if (currentCallId.isBlank()) {
            return;
        }

        endCurrentCall(true, "Call ended.");
    }

    @FXML
    private void onJumpToLatestMessages() {
        scrollToBottomSoon();
    }

    private void configureTypingIndicatorAnimation() {
        typingDotsTimeline = new Timeline(
                dotFrame(Duration.ZERO,
                        dotState(typingDotOne, 0.38, 2.0, 0.94),
                        dotState(typingDotTwo, 0.30, 4.0, 0.90),
                        dotState(typingDotThree, 0.24, 5.5, 0.88)),
                dotFrame(Duration.millis(180),
                        dotState(typingDotOne, 1.0, -2.0, 1.0),
                        dotState(typingDotTwo, 0.34, 3.0, 0.92),
                        dotState(typingDotThree, 0.26, 5.0, 0.88)),
                dotFrame(Duration.millis(320),
                        dotState(typingDotOne, 0.42, 1.5, 0.95),
                        dotState(typingDotTwo, 1.0, -2.0, 1.0),
                        dotState(typingDotThree, 0.30, 3.0, 0.92)),
                dotFrame(Duration.millis(460),
                        dotState(typingDotOne, 0.30, 4.0, 0.90),
                        dotState(typingDotTwo, 0.44, 1.5, 0.95),
                        dotState(typingDotThree, 1.0, -2.0, 1.0)),
                dotFrame(Duration.millis(720),
                        dotState(typingDotOne, 0.38, 2.0, 0.94),
                        dotState(typingDotTwo, 0.30, 4.0, 0.90),
                        dotState(typingDotThree, 0.24, 5.5, 0.88)));
        typingDotsTimeline.setCycleCount(Animation.INDEFINITE);
        resetTypingDots();
    }

    private KeyFrame dotFrame(Duration at, KeyValue[]... groups) {
        List<KeyValue> values = new ArrayList<>();
        for (KeyValue[] group : groups) {
            values.addAll(Arrays.asList(group));
        }
        return new KeyFrame(at, values.toArray(KeyValue[]::new));
    }

    private KeyValue[] dotState(Circle dot, double opacity, double translateY, double scale) {
        return new KeyValue[] {
                new KeyValue(dot.opacityProperty(), opacity, Interpolator.EASE_BOTH),
                new KeyValue(dot.translateYProperty(), translateY, Interpolator.EASE_BOTH),
                new KeyValue(dot.scaleXProperty(), scale, Interpolator.EASE_BOTH),
                new KeyValue(dot.scaleYProperty(), scale, Interpolator.EASE_BOTH)
        };
    }

    private void setTypingIndicatorVisible(boolean visible) {
        typingIndicatorPane.setVisible(visible);
        typingIndicatorPane.setManaged(visible);
        if (!visible) {
            if (typingDotsTimeline != null) {
                typingDotsTimeline.stop();
            }
            resetTypingDots();
            return;
        }

        if (typingDotsTimeline != null && typingDotsTimeline.getStatus() != Animation.Status.RUNNING) {
            typingDotsTimeline.play();
        }
    }

    private void resetTypingDots() {
        resetTypingDot(typingDotOne, 0.38, 2.0, 0.94);
        resetTypingDot(typingDotTwo, 0.30, 4.0, 0.90);
        resetTypingDot(typingDotThree, 0.24, 5.5, 0.88);
    }

    private void resetTypingDot(Circle dot, double opacity, double translateY, double scale) {
        if (dot == null) {
            return;
        }

        dot.setOpacity(opacity);
        dot.setTranslateY(translateY);
        dot.setScaleX(scale);
        dot.setScaleY(scale);
    }

    private void toggleEmojiPicker() {
        EmojiIconCatalog.reloadCatalog();
        ensureEmojiPickerPopup();
        if (emojiPickerPopup == null) {
            return;
        }
        if (emojiPickerController != null) {
            emojiPickerController.refreshCatalog();
        }

        if (emojiPickerPopup.isShowing()) {
            hideEmojiPicker();
            return;
        }

        syncEmojiPickerTheme();
        Bounds buttonBounds = sendEmojiButton.localToScreen(sendEmojiButton.getBoundsInLocal());
        if (buttonBounds == null) {
            return;
        }

        emojiPickerRoot.applyCss();
        emojiPickerRoot.layout();
        double popupWidth = emojiPickerRoot.prefWidth(-1);
        double popupHeight = emojiPickerRoot.prefHeight(-1);
        double x = Math.max(12, buttonBounds.getMaxX() - popupWidth);
        double y = buttonBounds.getMinY() - popupHeight - 10;
        if (y < 12) {
            y = buttonBounds.getMaxY() + 10;
        }

        emojiPickerPopup.show(sendEmojiButton, x, y);
        sendEmojiButton.getStyleClass().add("composer-tool-button-active");
    }

    private void ensureEmojiPickerPopup() {
        if (emojiPickerPopup != null) {
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(UiResources.fxml("views/EmojiPicker.fxml"));
            VBox popupRoot = loader.load();
            EmojiPickerController controller = loader.getController();
            controller.setOnEmojiSelected(this::handleEmojiSelected);
            controller.refreshCatalog();
            String stylesheet = UiResources.stylesheet("css/MessengerStyle.css");
            if (!stylesheet.isBlank()) {
                popupRoot.getStylesheets().add(stylesheet);
            }

            emojiPickerRoot = popupRoot;
            emojiPickerController = controller;
            emojiPickerPopup = new Popup();
            emojiPickerPopup.setAutoHide(true);
            emojiPickerPopup.setHideOnEscape(true);
            emojiPickerPopup.getContent().add(popupRoot);
            emojiPickerPopup.setOnHidden(event -> sendEmojiButton.getStyleClass().remove("composer-tool-button-active"));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load emoji picker.", ex);
        }
    }

    private void syncEmojiPickerTheme() {
        if (emojiPickerRoot == null) {
            return;
        }

        emojiPickerRoot.getStyleClass().remove("theme-dark");
        if (rootPane.getStyleClass().contains("theme-dark")) {
            emojiPickerRoot.getStyleClass().add("theme-dark");
        }
    }

    private void hideEmojiPicker() {
        if (emojiPickerPopup != null) {
            emojiPickerPopup.hide();
        }
    }

    private void handleEmojiSelected(EmojiIconSpec icon) {
        hideEmojiPicker();
        if (icon == null || activeConversation == null || service == null) {
            return;
        }

        Conversation targetConversation = activeConversation;
        Thread sender = new Thread(() -> sendEmojiAttachment(targetConversation, icon),
                "fx-send-icon-" + icon.id() + "-" + System.currentTimeMillis());
        sender.setDaemon(true);
        sender.start();
    }

    public void shutdown() {
        if (shuttingDown) {
            return;
        }

        shuttingDown = true;
        stopTyping(true);
        if (!currentCallId.isBlank()) {
            endCurrentCall(true, "Call ended.");
        }
        audioCallManager.shutdown();
        hideEmojiPicker();
        setTypingIndicatorVisible(false);
        if (profilePopupStage != null) {
            profilePopupStage.close();
        }
        if (service != null) {
            service.disconnect();
        }
    }

    @Override
    public void onGroupChatLine(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            return;
        }

        ParsedGroupMessage parsed = parseGroupMessage(rawLine);
        if (parsed == null) {
            return;
        }

        if (!parsed.systemMessage() && !parsed.senderUsername().isBlank()) {
            groupTypingUsers.remove(parsed.senderUsername());
            stopExpiryTimer("group:" + parsed.senderUsername());
            if (groupTypingUsers.isEmpty()) {
                groupConversation.clearTyping();
            } else {
                groupConversation.showTyping(buildGroupTypingText());
            }
        }

        appendMessage(groupConversation, parsed.message(), true);
        updateTypingIndicator();
    }

    @Override
    public void onOnlineUsers(List<String> displayUsers) {
        onlineUsers.clear();
        int totalOnline = 0;

        for (String displayUser : displayUsers) {
            totalOnline++;
            String username = extractUsername(displayUser);
            if (username == null || username.equalsIgnoreCase(session.getUsername())) {
                continue;
            }

            onlineUsers.put(username, displayUser == null ? username : displayUser.trim());
            Conversation conversation = getOrCreatePrivateConversation(username, displayUser);
            conversation.setOnline(true);
            conversation.updateStatusText("Online");
            if (conversation.getMessages().isEmpty()) {
                conversation.seedPreview("Start a conversation", "");
            }
        }

        for (Conversation conversation : new ArrayList<>(conversations)) {
            if (conversation.isGroupConversation() || conversation.getPeerUsername() == null) {
                continue;
            }

            boolean online = onlineUsers.containsKey(conversation.getPeerUsername());
            conversation.setOnline(online);
            conversation.updateStatusText(online ? "Online" : "Offline");
        }

        if (!currentCallPeerUsername.isBlank() && !onlineUsers.containsKey(currentCallPeerUsername)) {
            endCurrentCall(false, resolveCallPeerDisplayName(currentCallPeerUsername, currentCallPeerDisplayName)
                    + " went offline.");
        }

        groupConversation.updateStatusText(formatOnlineCount(totalOnline));
        resortConversations();
        updateHeader(activeConversation);
        conversationListView.refresh();
    }

    @Override
    public void onGroupTyping(String fromUsername, String fromDisplayName, boolean typing) {
        if (fromUsername == null || fromUsername.isBlank() || fromUsername.equalsIgnoreCase(session.getUsername())) {
            return;
        }

        String displayName = cleanDisplayName(fromDisplayName, fromUsername);
        String timerKey = "group:" + fromUsername;

        if (typing) {
            groupTypingUsers.put(fromUsername, displayName);
            groupConversation.showTyping(buildGroupTypingText());
            restartExpiryTimer(timerKey, () -> {
                groupTypingUsers.remove(fromUsername);
                groupConversation.clearTyping();
                updateTypingIndicator();
                conversationListView.refresh();
            });
        } else {
            groupTypingUsers.remove(fromUsername);
            stopExpiryTimer(timerKey);
            groupConversation.clearTyping();
        }

        if (!groupTypingUsers.isEmpty()) {
            groupConversation.showTyping(buildGroupTypingText());
        } else {
            groupConversation.clearTyping();
        }

        updateTypingIndicator();
        conversationListView.refresh();
    }

    @Override
    public void onGroupImage(String time, String fromUsername, String fromDisplayName, String fileName, String mimeType,
            byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return;
        }

        clearIncomingGroupTyping(fromUsername);
        appendMessage(groupConversation, Message.imageMessage(
                UUID.randomUUID().toString(),
                normalizeUsername(fromUsername),
                cleanDisplayName(fromDisplayName, fromUsername),
                normalizeAttachmentName(fileName, "image", 120),
                mimeType,
                imageBytes,
                isCurrentUser(fromUsername),
                parseServerTime(time)), true);
        updateTypingIndicator();
    }

    @Override
    public void onGroupFile(String time, String fromUsername, String fromDisplayName, String fileName, String mimeType,
            byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0) {
            return;
        }

        clearIncomingGroupTyping(fromUsername);
        appendMessage(groupConversation, Message.fileMessage(
                UUID.randomUUID().toString(),
                normalizeUsername(fromUsername),
                cleanDisplayName(fromDisplayName, fromUsername),
                normalizeAttachmentName(fileName, "file", 160),
                mimeType,
                fileBytes,
                fileBytes.length,
                isCurrentUser(fromUsername),
                parseServerTime(time)), true);
        updateTypingIndicator();
    }

    @Override
    public void onPrivateMessage(String fromUsername, String fromDisplayName, String messageId, String message) {
        Conversation conversation = getOrCreatePrivateConversation(fromUsername, fromDisplayName);
        stopConversationTyping(conversation);
        Message privateMessage = Message.userMessage(
                messageId,
                fromUsername,
                cleanDisplayName(fromDisplayName, fromUsername),
                message,
                false,
                LocalDateTime.now());
        appendMessage(conversation, privateMessage, true);

        if (conversation.equals(activeConversation)) {
            sendSeenReceipt(conversation, messageId);
            return;
        }

        conversation.queuePendingSeenMessage(messageId);
    }

    @Override
    public void onPrivateMessageSent(String toUsername, String toDisplayName, String messageId, String message) {
        Conversation conversation = getOrCreatePrivateConversation(toUsername, toDisplayName);
        stopConversationTyping(conversation);
        appendMessage(conversation, Message.userMessage(
                messageId,
                session.getUsername(),
                session.getFullName(),
                message,
                true,
                LocalDateTime.now()), true);
    }

    @Override
    public void onPrivateImage(String fromUsername, String fromDisplayName, String fileName, String mimeType,
            byte[] imageBytes) {
        Conversation conversation = getOrCreatePrivateConversation(fromUsername, fromDisplayName);
        stopConversationTyping(conversation);
        appendMessage(conversation, Message.imageMessage(
                UUID.randomUUID().toString(),
                normalizeUsername(fromUsername),
                cleanDisplayName(fromDisplayName, fromUsername),
                normalizeAttachmentName(fileName, "image", 120),
                mimeType,
                imageBytes,
                false,
                LocalDateTime.now()), true);
    }

    @Override
    public void onPrivateImageSent(String toUsername, String toDisplayName, String fileName, String mimeType,
            byte[] imageBytes) {
        Conversation conversation = getOrCreatePrivateConversation(toUsername, toDisplayName);
        stopConversationTyping(conversation);
        appendMessage(conversation, Message.imageMessage(
                UUID.randomUUID().toString(),
                session.getUsername(),
                session.getFullName(),
                normalizeAttachmentName(fileName, "image", 120),
                mimeType,
                imageBytes,
                true,
                LocalDateTime.now()), true);
    }

    @Override
    public void onPrivateFile(String fromUsername, String fromDisplayName, String fileName, String mimeType,
            byte[] fileBytes) {
        Conversation conversation = getOrCreatePrivateConversation(fromUsername, fromDisplayName);
        stopConversationTyping(conversation);
        appendMessage(conversation, Message.fileMessage(
                UUID.randomUUID().toString(),
                normalizeUsername(fromUsername),
                cleanDisplayName(fromDisplayName, fromUsername),
                normalizeAttachmentName(fileName, "file", 160),
                mimeType,
                fileBytes,
                fileBytes == null ? -1 : fileBytes.length,
                false,
                LocalDateTime.now()), true);
    }

    @Override
    public void onPrivateFileSent(String toUsername, String toDisplayName, String fileName, String mimeType,
            long sizeBytes) {
        Conversation conversation = getOrCreatePrivateConversation(toUsername, toDisplayName);
        stopConversationTyping(conversation);
        appendMessage(conversation, Message.fileMessage(
                UUID.randomUUID().toString(),
                session.getUsername(),
                session.getFullName(),
                normalizeAttachmentName(fileName, "file", 160),
                mimeType,
                null,
                sizeBytes,
                true,
                LocalDateTime.now()), true);
    }

    @Override
    public void onPrivateRead(String fromUsername, String messageId) {
        String normalizedUsername = fromUsername == null ? "" : fromUsername.trim().toLowerCase(Locale.ROOT);
        Conversation conversation = conversationsById.get(normalizedUsername);
        if (conversation == null) {
            return;
        }

        if (!conversation.markMessageReadById(messageId)) {
            return;
        }

        if (conversation.equals(activeConversation)) {
            renderConversation(conversation);
            updateTypingIndicator();
        }
        conversationListView.refresh();
    }

    @Override
    public void onPrivateTyping(String fromUsername, String fromDisplayName, boolean typing) {
        Conversation conversation = getOrCreatePrivateConversation(fromUsername, fromDisplayName);
        String timerKey = "pm:" + fromUsername;

        if (typing) {
            conversation.showTyping(cleanDisplayName(fromDisplayName, fromUsername) + " đang soạn tin...");
            restartExpiryTimer(timerKey, () -> {
                conversation.clearTyping();
                updateTypingIndicator();
                conversationListView.refresh();
            });
        } else {
            stopConversationTyping(conversation);
            stopExpiryTimer(timerKey);
        }

        updateTypingIndicator();
        conversationListView.refresh();
    }

    @Override
    public void onPrivateSystemMessage(String peerUsername, String message) {
        if (peerUsername == null || peerUsername.isBlank()) {
            appendMessage(groupConversation, Message.systemMessage(message, LocalDateTime.now()), true);
            return;
        }

        Conversation conversation = getOrCreatePrivateConversation(peerUsername, onlineUsers.get(peerUsername));
        appendMessage(conversation, Message.systemMessage(message, LocalDateTime.now()), true);
    }

    @Override
    public void onPrivateCallInvite(String fromUsername, String fromDisplayName, String callId) {
        Conversation conversation = getOrCreatePrivateConversation(fromUsername, fromDisplayName);
        appendLocalSystemMessage(conversation, cleanDisplayName(fromDisplayName, fromUsername) + " is calling you.");
        conversationListView.getSelectionModel().select(conversation);

        if (!currentCallId.isBlank()) {
            service.sendPrivateCallDecline(fromUsername, callId, "Nguoi dung dang ban.");
            return;
        }

        currentCallId = callId == null ? "" : callId.trim();
        currentCallPeerUsername = normalizeUsername(fromUsername);
        currentCallPeerDisplayName = cleanDisplayName(fromDisplayName, fromUsername);
        callState = CallState.INCOMING_RINGING;
        updateHeader(activeConversation);
        showIncomingCallPrompt(conversation, currentCallPeerUsername, currentCallPeerDisplayName, currentCallId);
    }

    @Override
    public void onPrivateCallRinging(String fromUsername, String fromDisplayName, String callId) {
        if (!matchesCurrentCall(fromUsername, callId)) {
            return;
        }

        callState = CallState.OUTGOING_RINGING;
        currentCallPeerDisplayName = cleanDisplayName(fromDisplayName, fromUsername);
        appendLocalSystemMessage(getOrCreatePrivateConversation(fromUsername, fromDisplayName), "Ringing...");
        updateHeader(activeConversation);
    }

    @Override
    public void onPrivateCallAccepted(String fromUsername, String fromDisplayName, String callId) {
        if (!matchesCurrentCall(fromUsername, callId)) {
            return;
        }

        currentCallPeerDisplayName = cleanDisplayName(fromDisplayName, fromUsername);
        startAudioCall(getOrCreatePrivateConversation(fromUsername, fromDisplayName), currentCallPeerUsername, currentCallPeerDisplayName, callId);
    }

    @Override
    public void onPrivateCallDeclined(String fromUsername, String fromDisplayName, String callId, String reason) {
        if (!matchesCurrentCall(fromUsername, callId)) {
            return;
        }

        closeIncomingCallAlert();
        String message = reason == null || reason.isBlank() ? "Call was declined." : reason.trim();
        appendLocalSystemMessage(getOrCreatePrivateConversation(fromUsername, fromDisplayName), message);
        clearCallState();
        updateHeader(activeConversation);
    }

    @Override
    public void onPrivateCallEnded(String fromUsername, String callId) {
        if (!matchesCurrentCall(fromUsername, callId)) {
            return;
        }

        closeIncomingCallAlert();
        endCurrentCall(false, "Call ended by " + resolveCallPeerDisplayName(fromUsername, fromUsername) + ".");
    }

    @Override
    public void onPrivateCallAudio(String fromUsername, String callId, byte[] audioBytes) {
        audioCallManager.handleIncomingAudio(fromUsername, callId, audioBytes);
    }

    @Override
    public void onUserAvatarUpdated(String username, byte[] avatarBytes) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername.isBlank()) {
            return;
        }

        Image image = buildImageFromBytes(avatarBytes);
        if (image == null) {
            return;
        }

        cacheAvatarImage(normalizedUsername, image);

        if (normalizeUsername(session.getUsername()).equals(normalizedUsername)) {
            currentUserAvatarImage = image;
            applyCurrentUserAvatar(image);
            if (profilePopupController != null) {
                profilePopupController.setAvatarImage(image);
                profilePopupController.setStatus("Avatar updated successfully.", false);
            }
        }

        Conversation conversation = conversationsById.get(normalizedUsername);
        if (conversation != null) {
            conversation.updateAvatarImage(image);
        }

        boolean shouldRerenderActiveConversation = activeConversation != null
                && (activeConversation.isGroupConversation()
                        || normalizedUsername.equals(normalizeUsername(activeConversation.getPeerUsername())));
        if (shouldRerenderActiveConversation) {
            renderConversation(activeConversation);
            updateHeader(activeConversation);
            updateTypingIndicator();
        }
        conversationListView.refresh();
    }

    @Override
    public void onConnectionClosed(String message) {
        if (shuttingDown) {
            return;
        }

        shutdown();
        app.showAuthView(session.getHost(), session.getPort(), message, true);
    }

    private void bootstrapConversations() {
        groupConversation = new Conversation(GROUP_CONVERSATION_ID, null, true, "Community Room", "0 people online");
        conversations.add(groupConversation);
        conversationsById.put(groupConversation.getId(), groupConversation);

        groupConversation.addMessage(Message.systemMessage(
                "Welcome back, " + session.getFullName() + ".",
                LocalDateTime.now().minusMinutes(2)));
        groupConversation.addMessage(Message.systemMessage(
                "Choose a person from the sidebar to start a private conversation.",
                LocalDateTime.now().minusMinutes(1)));

        resortConversations();
        conversationListView.getSelectionModel().select(groupConversation);
    }

    private void filterConversations(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filteredConversations.setPredicate(conversation -> {
            if (normalized.isEmpty()) {
                return true;
            }

            return conversation.getTitle().toLowerCase(Locale.ROOT).contains(normalized)
                    || conversation.getSubtitle().toLowerCase(Locale.ROOT).contains(normalized)
                    || (conversation.getPeerUsername() != null
                            && conversation.getPeerUsername().toLowerCase(Locale.ROOT).contains(normalized));
        });
    }

    private void selectConversation(Conversation conversation) {
        if (conversation == null) {
            return;
        }

        stopTyping(true);
        hideEmojiPicker();
        if (activeConversation != null && !activeConversation.equals(conversation)) {
            clearReply();
        }
        prepareUnreadDividerForConversation(conversation);
        activeConversation = conversation;
        activeConversation.clearUnread();
        flushPendingSeenReceipts(activeConversation);
        clearPendingNewMessagesIndicator();
        boolean shouldScrollToBottom = activeUnreadDividerMessage == null;
        renderConversation(activeConversation, shouldScrollToBottom);
        if (!shouldScrollToBottom) {
            scrollToUnreadDividerSoon();
        }
        updateHeader(activeConversation);
        updateTypingIndicator();
        refreshMessageSearchResults();
        messageInputField.requestFocus();
        conversationListView.refresh();
    }

    private void renderConversation(Conversation conversation) {
        renderConversation(conversation, true);
    }

    private void renderConversation(Conversation conversation, boolean scrollToBottom) {
        suppressAutoReadStateClearing = true;
        messagesContainer.getChildren().clear();
        renderedMessageNodes.clear();
        unreadDividerNode = null;

        if (conversation.getMessages().isEmpty()) {
            emptyStatePane.setVisible(true);
            emptyStatePane.setManaged(true);
            emptyStateTitleLabel.setText(conversation.isGroupConversation()
                    ? "No messages yet"
                    : "Start a new conversation");
            emptyStateBodyLabel.setText(conversation.isGroupConversation()
                    ? "The room is ready. Your first message will show up here."
                    : "Send a message to " + conversation.getTitle() + " to begin.");
            refreshMessageSearchResults();
            javafx.application.Platform.runLater(() -> suppressAutoReadStateClearing = false);
            return;
        }

        emptyStatePane.setVisible(false);
        emptyStatePane.setManaged(false);
        for (Message message : conversation.getMessages()) {
            if (shouldRenderUnreadDivider(conversation, message)) {
                unreadDividerNode = createUnreadDividerNode();
                messagesContainer.getChildren().add(unreadDividerNode);
            }
            Node node = createMessageNode(conversation, message, false);
            renderedMessageNodes.put(message, node);
            messagesContainer.getChildren().add(node);
        }
        if (scrollToBottom) {
            scrollToBottomSoon();
        }
        refreshMessageSearchResults();
        javafx.application.Platform.runLater(() -> suppressAutoReadStateClearing = false);
    }

    private void appendMessage(Conversation conversation, Message message, boolean animate) {
        if (conversation == null || message == null) {
            return;
        }

        conversation.addMessage(message);
        conversation.clearTyping();
        resortConversations();

        boolean selectedConversation = conversation.equals(activeConversation);
        if (!selectedConversation) {
            conversation.incrementUnread();
            conversationListView.refresh();
            return;
        }

        boolean pinnedToBottom = isPinnedToBottom();
        double preservedScrollOffset = pinnedToBottom ? -1 : currentScrollOffset();
        boolean shouldMarkUnreadBoundary = !pinnedToBottom
                && !message.isSentByCurrentUser()
                && !message.isSystemMessage()
                && activeUnreadDividerMessage == null;
        emptyStatePane.setVisible(false);
        emptyStatePane.setManaged(false);
        if (shouldMarkUnreadBoundary) {
            activeUnreadDividerMessage = message;
            unreadDividerNode = createUnreadDividerNode();
            messagesContainer.getChildren().add(unreadDividerNode);
        }
        Node node = createMessageNode(conversation, message, animate);
        renderedMessageNodes.put(message, node);
        messagesContainer.getChildren().add(node);
        if (pinnedToBottom || message.isSentByCurrentUser()) {
            scrollToBottomSoon();
        } else {
            restoreScrollOffsetSoon(preservedScrollOffset);
            if (!message.isSystemMessage()) {
                incrementPendingNewMessagesIndicator();
            }
        }
        updateHeader(conversation);
        refreshMessageSearchResults();
        conversationListView.refresh();
    }

    private Node createMessageNode(Conversation conversation, Message message, boolean animate) {
        try {
            FXMLLoader loader = new FXMLLoader(UiResources.fxml("views/MessageBubble.fxml"));
            HBox node = loader.load();
            MessageBubbleController controller = loader.getController();
            controller.setMessage(
                    message,
                    conversation != null && !conversation.isGroupConversation(),
                    resolveMessageAvatar(message),
                    currentMessageHighlightQuery());
            controller.setActions(
                    () -> beginReply(conversation, message),
                    () -> deleteMessageLocally(conversation, message));
            if (animate) {
                controller.playEntrance();
            }
            return node;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load message bubble.", ex);
        }
    }

    private void sendCurrentMessage() {
        if (activeConversation == null) {
            return;
        }

        String text = messageInputField.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        String finalMessage = text;
        if (replyConversation != null && replyConversation.equals(activeConversation) && replyMessage != null) {
            finalMessage = Message.composeReplyMessage(buildReplyContext(replyMessage), text);
        }

        if (activeConversation.isGroupConversation()) {
            service.sendGroupMessage(finalMessage);
        } else {
            service.sendPrivateMessage(activeConversation.getPeerUsername(), UUID.randomUUID().toString(), finalMessage);
        }

        messageInputField.clear();
        clearReply();
        stopTyping(true);
    }

    private void handleTypingInputChange() {
        if (activeConversation == null) {
            return;
        }

        String currentText = messageInputField.getText().trim();
        if (currentText.isEmpty()) {
            stopTyping(true);
            return;
        }

        if (localTypingConversation != null && !localTypingConversation.equals(activeConversation)) {
            stopTyping(true);
        }

        long now = System.currentTimeMillis();
        if (!localTypingActive || now - lastTypingSentAt > 900) {
            localTypingConversation = activeConversation;
            localTypingActive = true;
            lastTypingSentAt = now;
            if (activeConversation.isGroupConversation()) {
                service.sendGroupTyping(true);
            } else {
                service.sendPrivateTyping(activeConversation.getPeerUsername(), true);
            }
        }

        localTypingIdleTimer.playFromStart();
    }

    private void stopTyping(boolean sendStop) {
        localTypingIdleTimer.stop();
        if (!localTypingActive || localTypingConversation == null) {
            return;
        }

        if (sendStop) {
            if (localTypingConversation.isGroupConversation()) {
                service.sendGroupTyping(false);
            } else if (localTypingConversation.getPeerUsername() != null) {
                service.sendPrivateTyping(localTypingConversation.getPeerUsername(), false);
            }
        }

        localTypingActive = false;
        lastTypingSentAt = 0;
        localTypingConversation = null;
    }

    private void updateHeader(Conversation conversation) {
        if (conversation == null) {
            chatTitleLabel.setText("No conversation selected");
            chatStatusLabel.setText("");
            chatStatusDot.setVisible(false);
            headerAvatarLabel.setText("?");
            applyHeaderAvatar(null, "?");
            updateCallControls(null);
            return;
        }

        applyHeaderAvatar(conversation.getAvatarImage(), conversation.avatarTextProperty().get());
        chatTitleLabel.setText(conversation.getTitle());
        chatStatusLabel.setText(conversation.getStatusText());
        chatStatusDot.setVisible(!conversation.isGroupConversation() && conversation.isOnline());
        updateCallControls(conversation);
    }

    private void updateCallControls(Conversation conversation) {
        boolean hasConversation = conversation != null;
        boolean privateConversation = conversation != null && !conversation.isGroupConversation();
        boolean callActive = !currentCallId.isBlank() && callState != CallState.IDLE;
        boolean samePeer = privateConversation
                && normalizeUsername(conversation.getPeerUsername()).equals(currentCallPeerUsername);

        String statusText = buildCallStatusText(privateConversation, samePeer);
        callStatusLabel.setText(statusText);
        callStatusLabel.setVisible(!statusText.isBlank());
        callStatusLabel.setManaged(callStatusLabel.isVisible());

        boolean showCallButton = privateConversation && !callActive;
        callButton.setVisible(showCallButton);
        callButton.setManaged(showCallButton);
        callButton.setDisable(!showCallButton || !conversation.isOnline());

        boolean showVideoCallButton = privateConversation && !callActive;
        videoCallButton.setVisible(showVideoCallButton);
        videoCallButton.setManaged(showVideoCallButton);
        videoCallButton.setDisable(!showVideoCallButton || !conversation.isOnline());

        boolean showHangupButton = callActive;
        hangupButton.setVisible(showHangupButton);
        hangupButton.setManaged(showHangupButton);

        searchMessagesButton.setVisible(hasConversation);
        searchMessagesButton.setManaged(hasConversation);
    }

    private String buildCallStatusText(boolean privateConversation, boolean samePeer) {
        if (currentCallId.isBlank() || callState == CallState.IDLE) {
            return "";
        }

        String peerDisplayName = resolveCallPeerDisplayName(currentCallPeerUsername, currentCallPeerDisplayName);
        return switch (callState) {
            case OUTGOING_RINGING -> samePeer || !privateConversation
                    ? "Calling..."
                    : "Calling " + peerDisplayName;
            case INCOMING_RINGING -> samePeer || !privateConversation
                    ? "Incoming call"
                    : "Incoming call from " + peerDisplayName;
            case IN_CALL -> samePeer || !privateConversation
                    ? "Voice call active"
                    : "In call with " + peerDisplayName;
            case IDLE -> "";
        };
    }

    private void setMessageSearchSidebarVisible(boolean visible) {
        messageSearchSidebar.setVisible(visible);
        messageSearchSidebar.setManaged(visible);
        searchMessagesButton.getStyleClass().remove("search-toggle-button-active");
        if (visible) {
            searchMessagesButton.getStyleClass().add("search-toggle-button-active");
            return;
        }

        messageSearchResultsListView.getSelectionModel().clearSelection();
    }

    private void handleMessageSearchQueryChanged() {
        if (activeConversation == null) {
            refreshMessageSearchResults();
            return;
        }

        rerenderActiveConversationForSearchHighlight();
    }

    private String currentMessageHighlightQuery() {
        if (!messageSearchSidebar.isVisible()) {
            return "";
        }

        String query = messageSearchField.getText();
        if (query == null) {
            return "";
        }

        String normalizedQuery = query.trim();
        return normalizedQuery.isBlank() ? "" : normalizedQuery;
    }

    private void rerenderActiveConversationForSearchHighlight() {
        if (activeConversation == null) {
            refreshMessageSearchResults();
            return;
        }

        double currentVvalue = messagesScrollPane.getVvalue();
        renderConversation(activeConversation, false);
        javafx.application.Platform.runLater(() -> {
            messagesScrollPane.layout();
            messagesScrollPane.setVvalue(currentVvalue);
        });
    }

    private void refreshMessageSearchResults() {
        messageSearchResults.clear();

        if (!messageSearchSidebar.isVisible()) {
            return;
        }

        if (activeConversation == null) {
            messageSearchMetaLabel.setText("Choose a conversation to search.");
            return;
        }

        String query = messageSearchField.getText() == null ? "" : messageSearchField.getText().trim();
        if (query.isBlank()) {
            messageSearchMetaLabel.setText("Type to search in " + activeConversation.getTitle() + ".");
            return;
        }

        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        for (Message message : activeConversation.getMessages()) {
            String searchableText = extractSearchableText(message);
            if (searchableText.isBlank() || !searchableText.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                continue;
            }

            messageSearchResults.add(new MessageSearchResult(
                    message,
                    resolveSearchSender(message),
                    message.getTimestamp(),
                    buildSearchSnippet(searchableText, query)));
        }

        if (messageSearchResults.isEmpty()) {
            messageSearchMetaLabel.setText("No results for \"" + query + "\".");
            return;
        }

        messageSearchMetaLabel.setText(messageSearchResults.size() + " result(s) in " + activeConversation.getTitle() + ".");
    }

    private ListCell<MessageSearchResult> createMessageSearchCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(MessageSearchResult item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                Label topLine = new Label(item.sender() + "  •  " + item.timestamp());
                topLine.getStyleClass().add("message-search-result-topline");

                Label snippetLabel = new Label(item.snippet());
                snippetLabel.setWrapText(true);
                snippetLabel.getStyleClass().add("message-search-result-snippet");

                VBox card = new VBox(topLine, snippetLabel);
                card.getStyleClass().add("message-search-result-card");
                setText(null);
                setGraphic(card);
            }
        };
    }

    private void scrollToSearchResult(MessageSearchResult result) {
        if (result == null || activeConversation == null) {
            return;
        }

        Node messageNode = renderedMessageNodes.get(result.message());
        if (messageNode == null) {
            return;
        }

        scrollNodeIntoViewSoon(messageNode, 18);
    }

    private void scrollToUnreadDividerSoon() {
        if (unreadDividerNode == null) {
            return;
        }

        scrollNodeIntoViewSoon(unreadDividerNode, 16);
    }

    private void scrollNodeIntoViewSoon(Node targetNode, double topPadding) {
        if (targetNode == null) {
            return;
        }

        javafx.application.Platform.runLater(() -> {
            double contentHeight = messagesContainer.getBoundsInLocal().getHeight();
            double viewportHeight = messagesScrollPane.getViewportBounds().getHeight();
            double maxScroll = Math.max(0, contentHeight - viewportHeight);
            if (maxScroll <= 0) {
                messagesScrollPane.setVvalue(0);
                return;
            }

            double targetY = Math.max(0, targetNode.getBoundsInParent().getMinY() - topPadding);
            messagesScrollPane.setVvalue(Math.min(1.0, targetY / maxScroll));
        });
    }

    private void handleMessagesScrollPositionChanged() {
        if (suppressAutoReadStateClearing) {
            return;
        }
        if (isPinnedToBottom()) {
            clearPendingNewMessagesIndicator();
        }
        if (isAtScrollableBottom()) {
            clearUnreadDividerMarker();
        }
    }

    private double currentScrollOffset() {
        double contentHeight = messagesContainer.getBoundsInLocal().getHeight();
        double viewportHeight = messagesScrollPane.getViewportBounds().getHeight();
        double maxScroll = Math.max(0, contentHeight - viewportHeight);
        if (maxScroll <= 0) {
            return 0;
        }
        return maxScroll * messagesScrollPane.getVvalue();
    }

    private void restoreScrollOffsetSoon(double scrollOffset) {
        if (scrollOffset < 0) {
            return;
        }

        javafx.application.Platform.runLater(() -> {
            messagesScrollPane.layout();
            double contentHeight = messagesContainer.getBoundsInLocal().getHeight();
            double viewportHeight = messagesScrollPane.getViewportBounds().getHeight();
            double maxScroll = Math.max(0, contentHeight - viewportHeight);
            if (maxScroll <= 0) {
                messagesScrollPane.setVvalue(0);
                return;
            }

            messagesScrollPane.setVvalue(Math.min(1.0, scrollOffset / maxScroll));
        });
    }

    private void incrementPendingNewMessagesIndicator() {
        pendingNewMessagesCount++;
        String labelText = pendingNewMessagesCount <= 1
                ? "Tin nhắn mới ↓"
                : pendingNewMessagesCount + " tin nhắn mới ↓";
        newMessagesButton.setText(labelText);
        newMessagesButton.setManaged(true);
        newMessagesButton.setVisible(true);
    }

    private void clearPendingNewMessagesIndicator() {
        pendingNewMessagesCount = 0;
        newMessagesButton.setManaged(false);
        newMessagesButton.setVisible(false);
        newMessagesButton.setText("Tin nhắn mới ↓");
    }

    private void prepareUnreadDividerForConversation(Conversation conversation) {
        clearUnreadDividerMarker();
        activeUnreadDividerMessage = resolveUnreadDividerMessage(conversation);
    }

    private Message resolveUnreadDividerMessage(Conversation conversation) {
        if (conversation == null) {
            return null;
        }

        int unreadCount = conversation.getUnreadCount();
        if (unreadCount <= 0) {
            return null;
        }

        List<Message> messages = conversation.getMessages();
        if (messages.isEmpty()) {
            return null;
        }

        int dividerIndex = Math.max(0, messages.size() - unreadCount);
        if (dividerIndex >= messages.size()) {
            return null;
        }

        return messages.get(dividerIndex);
    }

    private boolean shouldRenderUnreadDivider(Conversation conversation, Message message) {
        return conversation != null
                && conversation.equals(activeConversation)
                && unreadDividerNode == null
                && activeUnreadDividerMessage != null
                && activeUnreadDividerMessage.equals(message);
    }

    private Node createUnreadDividerNode() {
        try {
            return new FXMLLoader(UiResources.fxml("views/UnreadDivider.fxml")).load();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load unread divider.", ex);
        }
    }

    private void clearUnreadDividerMarker() {
        activeUnreadDividerMessage = null;
        if (unreadDividerNode != null) {
            messagesContainer.getChildren().remove(unreadDividerNode);
            unreadDividerNode = null;
        }
    }

    private String extractSearchableText(Message message) {
        if (message == null) {
            return "";
        }
        if (message.isAttachment()) {
            return message.getSummaryText();
        }
        if (message.isSystemMessage()) {
            return message.getText();
        }

        return message.getCopyText();
    }

    private String resolveSearchSender(Message message) {
        if (message == null) {
            return "Unknown";
        }
        if (message.isSentByCurrentUser()) {
            return "You";
        }
        if (message.getSenderDisplayName() != null && !message.getSenderDisplayName().isBlank()) {
            return message.getSenderDisplayName();
        }
        if (message.getSenderUsername() != null && !message.getSenderUsername().isBlank()) {
            return message.getSenderUsername();
        }
        return "Unknown";
    }

    private String buildSearchSnippet(String text, String query) {
        String safeText = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        if (safeText.isBlank()) {
            return "";
        }

        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.isBlank()) {
            return safeText;
        }

        String lowerText = safeText.toLowerCase(Locale.ROOT);
        String lowerQuery = safeQuery.toLowerCase(Locale.ROOT);
        int matchIndex = lowerText.indexOf(lowerQuery);
        if (matchIndex < 0) {
            return safeText.length() <= 120 ? safeText : safeText.substring(0, 120) + "...";
        }

        int start = Math.max(0, matchIndex - 28);
        int end = Math.min(safeText.length(), matchIndex + safeQuery.length() + 56);
        String snippet = safeText.substring(start, end).trim();
        if (start > 0) {
            snippet = "..." + snippet;
        }
        if (end < safeText.length()) {
            snippet = snippet + "...";
        }
        return snippet;
    }

    private void updateTypingIndicator() {
        String text = "";

        if (activeConversation == null) {
            typingIndicatorTextLabel.setText("");
            setTypingIndicatorVisible(false);
            return;
        }

        if (activeConversation.isGroupConversation()) {
            if (!groupTypingUsers.isEmpty()) {
                text = buildGroupTypingText();
            }
        } else if (activeConversation.isTyping()) {
            text = activeConversation.getSubtitle();
        }

        boolean visible = !text.isBlank();
        typingIndicatorTextLabel.setText(text);
        setTypingIndicatorVisible(visible);
    }

    private Conversation getOrCreatePrivateConversation(String username, String displayName) {
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        Conversation existing = conversationsById.get(normalizedUsername);
        if (existing != null) {
            if (displayName != null && !displayName.isBlank()) {
                existing.updateTitle(cleanDisplayName(displayName, normalizedUsername));
            }
            existing.updateAvatarImage(lookupAvatarImage(normalizedUsername));
            return existing;
        }

        String title = cleanDisplayName(displayName, normalizedUsername);
        Conversation conversation = new Conversation(normalizedUsername, normalizedUsername, false, title, "Offline");
        conversation.seedPreview("Start a conversation", "");
        conversation.setOnline(onlineUsers.containsKey(normalizedUsername));
        conversation.updateStatusText(conversation.isOnline() ? "Online" : "Offline");
        conversation.updateAvatarImage(lookupAvatarImage(normalizedUsername));

        conversationsById.put(normalizedUsername, conversation);
        conversations.add(conversation);
        resortConversations();
        return conversation;
    }

    private void stopConversationTyping(Conversation conversation) {
        if (conversation == null) {
            return;
        }

        conversation.clearTyping();
        if (conversation.getPeerUsername() != null) {
            stopExpiryTimer("pm:" + conversation.getPeerUsername());
        }
    }

    private void beginReply(Conversation conversation, Message message) {
        if (conversation == null || message == null || message.isSystemMessage()) {
            return;
        }

        replyConversation = conversation;
        replyMessage = message;
        replyPreviewTitleLabel.setText("Dang tra loi " + resolveReplyAuthor(message));
        replyPreviewBodyLabel.setText(buildReplyPreviewText(message));
        replyPreviewPane.setManaged(true);
        replyPreviewPane.setVisible(true);
        messageInputField.requestFocus();
    }

    private void clearReply() {
        replyConversation = null;
        replyMessage = null;
        replyPreviewPane.setManaged(false);
        replyPreviewPane.setVisible(false);
        replyPreviewTitleLabel.setText("");
        replyPreviewBodyLabel.setText("");
    }

    private void deleteMessageLocally(Conversation conversation, Message message) {
        if (conversation == null || message == null) {
            return;
        }

        conversation.removeMessage(message);
        if (message.equals(replyMessage)) {
            clearReply();
        }
        if (message.equals(activeUnreadDividerMessage)) {
            clearUnreadDividerMarker();
        }

        if (conversation.equals(activeConversation)) {
            renderConversation(conversation);
            updateTypingIndicator();
        }
        refreshMessageSearchResults();
        conversationListView.refresh();
    }

    private void sendImageAttachment(Conversation conversation, Path imagePath) {
        if (conversation == null || imagePath == null || service == null) {
            return;
        }

        String fileName = normalizeAttachmentName(pathFileName(imagePath), "image", 120);
        byte[] imageBytes = readAttachmentBytes(imagePath, MAX_IMAGE_BYTES, "Image", conversation);
        if (imageBytes == null) {
            return;
        }

        if (!isValidImage(imageBytes)) {
            appendLocalSystemMessage(conversation, "Selected file is not a valid image.");
            return;
        }

        String mimeType = detectMimeType(imagePath, fileName, "image/");
        if (conversation.isGroupConversation()) {
            service.sendGroupImage(fileName, mimeType, imageBytes);
            return;
        }

        service.sendPrivateImage(conversation.getPeerUsername(), fileName, mimeType, imageBytes);
    }

    private void sendEmojiAttachment(Conversation conversation, EmojiIconSpec icon) {
        if (conversation == null || icon == null || service == null) {
            return;
        }

        byte[] imageBytes = EmojiIconCatalog.pngBytes(icon.id(), 160);
        if (imageBytes == null || imageBytes.length == 0) {
            appendLocalSystemMessage(conversation, "Unable to generate icon.");
            return;
        }
        if (imageBytes.length > MAX_IMAGE_BYTES) {
            appendLocalSystemMessage(conversation, "Icon exceeds the image size limit.");
            return;
        }

        String fileName = EmojiIconCatalog.fileName(icon.id());
        String mimeType = "image/png";
        if (conversation.isGroupConversation()) {
            service.sendGroupImage(fileName, mimeType, imageBytes);
            return;
        }

        service.sendPrivateImage(conversation.getPeerUsername(), fileName, mimeType, imageBytes);
    }

    private void sendFileAttachment(Conversation conversation, Path filePath) {
        if (conversation == null || filePath == null || service == null) {
            return;
        }

        String fileName = normalizeAttachmentName(pathFileName(filePath), "file", 160);
        byte[] fileBytes = readAttachmentBytes(filePath, MAX_FILE_BYTES, "File", conversation);
        if (fileBytes == null) {
            return;
        }

        String mimeType = detectMimeType(filePath, fileName, "application/");
        if (conversation.isGroupConversation()) {
            service.sendGroupFile(fileName, mimeType, fileBytes);
            return;
        }

        service.sendPrivateFile(conversation.getPeerUsername(), fileName, mimeType, fileBytes);
    }

    private void showProfilePopup() {
        if (rootPane.getScene() == null) {
            return;
        }

        if (profilePopupStage != null && profilePopupStage.isShowing()) {
            profilePopupStage.requestFocus();
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(UiResources.fxml("views/ProfilePopup.fxml"));
            VBox root = loader.load();
            ProfilePopupController controller = loader.getController();
            controller.setProfile(session.getFullName(), session.getUsername(), buildAvatar(session.getFullName()));
            controller.setAvatarImage(currentUserAvatarImage);
            controller.setOnAvatarSelected(this::startAvatarUpload);
            if (rootPane.getStyleClass().contains("theme-dark")) {
                root.getStyleClass().add("theme-dark");
            }

            javafx.scene.Scene scene = new javafx.scene.Scene(root);
            String stylesheet = UiResources.stylesheet("css/MessengerStyle.css");
            if (!stylesheet.isBlank()) {
                scene.getStylesheets().setAll(stylesheet);
            }

            Stage stage = new Stage(StageStyle.UTILITY);
            stage.initOwner(rootPane.getScene().getWindow());
            stage.initModality(Modality.WINDOW_MODAL);
            stage.setResizable(false);
            stage.setTitle("Profile");
            stage.setScene(scene);
            stage.setOnHidden(event -> {
                profilePopupStage = null;
                profilePopupController = null;
            });

            profilePopupController = controller;
            profilePopupStage = stage;
            stage.show();
        } catch (IOException ex) {
            appendLocalSystemMessage(groupConversation, "Unable to open profile popup.");
        }
    }

    private void startAvatarUpload(Path imagePath) {
        if (imagePath == null || service == null) {
            return;
        }

        if (profilePopupController != null) {
            profilePopupController.setStatus("Uploading avatar...", false);
        }

        Thread worker = new Thread(() -> {
            byte[] avatarBytes = encodeAvatarAsPng(imagePath);
            if (avatarBytes == null) {
                return;
            }

            service.sendAvatarUpdate(avatarBytes);
        }, "fx-avatar-upload");
        worker.setDaemon(true);
        worker.start();
    }

    private void flushPendingSeenReceipts(Conversation conversation) {
        if (conversation == null || conversation.isGroupConversation()) {
            return;
        }

        for (String messageId : conversation.consumePendingSeenMessageIds()) {
            sendSeenReceipt(conversation, messageId);
        }
    }

    private void sendSeenReceipt(Conversation conversation, String messageId) {
        if (conversation == null || conversation.isGroupConversation()) {
            return;
        }

        String peerUsername = conversation.getPeerUsername();
        String safeMessageId = messageId == null ? "" : messageId.trim();
        if (peerUsername == null || peerUsername.isBlank() || safeMessageId.isEmpty()) {
            return;
        }

        service.sendPrivateSeen(peerUsername, safeMessageId);
    }

    private boolean matchesCurrentCall(String fromUsername, String callId) {
        return !currentCallId.isBlank()
                && currentCallId.equals(callId == null ? "" : callId.trim())
                && normalizeUsername(fromUsername).equals(currentCallPeerUsername);
    }

    private void showIncomingCallPrompt(Conversation conversation, String fromUsername, String fromDisplayName, String callId) {
        closeIncomingCallAlert();

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(rootPane.getScene() == null ? null : rootPane.getScene().getWindow());
        alert.setTitle("Incoming call");
        alert.setHeaderText(resolveCallPeerDisplayName(fromUsername, fromDisplayName) + " đang gọi cho bạn.");
        alert.setContentText("Bạn có muốn trả lời cuộc gọi?");

        ButtonType acceptButton = new ButtonType("Chấp nhận", ButtonBar.ButtonData.OK_DONE);
        ButtonType declineButton = new ButtonType("Từ chối", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(acceptButton, declineButton);
        alert.setOnHidden(event -> {
            if (incomingCallAlert == alert) {
                incomingCallAlert = null;
            }
        });
        incomingCallAlert = alert;

        ButtonType result = alert.showAndWait().orElse(declineButton);
        if (!matchesCurrentCall(fromUsername, callId)) {
            return;
        }

        if (result == acceptButton) {
            service.sendPrivateCallAccept(fromUsername, callId);
            startAudioCall(conversation, fromUsername, fromDisplayName, callId);
            return;
        }

        service.sendPrivateCallDecline(fromUsername, callId, resolveCallPeerDisplayName(fromUsername, fromDisplayName) + " đã từ chối cuộc gọi.");
        appendLocalSystemMessage(conversation, "Bạn đã từ chối cuộc gọi.");
        clearCallState();
        updateHeader(activeConversation);
    }

    private void closeIncomingCallAlert() {
        if (incomingCallAlert != null) {
            incomingCallAlert.close();
            incomingCallAlert = null;
        }
    }

    private void startAudioCall(Conversation conversation, String peerUsername, String peerDisplayName, String callId) {
        currentCallId = callId == null ? "" : callId.trim();
        currentCallPeerUsername = normalizeUsername(peerUsername);
        currentCallPeerDisplayName = resolveCallPeerDisplayName(peerUsername, peerDisplayName);
        callState = CallState.IN_CALL;

        if (!audioCallManager.startCall(currentCallPeerUsername, currentCallId)) {
            appendLocalSystemMessage(conversation, "Unable to access microphone or speakers for the call.");
            if (service != null && !currentCallId.isBlank() && !currentCallPeerUsername.isBlank()) {
                service.sendPrivateCallEnd(currentCallPeerUsername, currentCallId);
            }
            clearCallState();
            updateHeader(activeConversation);
            return;
        }

        appendLocalSystemMessage(conversation, "Voice call connected with " + currentCallPeerDisplayName + ".");
        if (activeConversation == null || activeConversation.isGroupConversation()
                || !currentCallPeerUsername.equals(normalizeUsername(activeConversation.getPeerUsername()))) {
            conversationListView.getSelectionModel().select(conversation);
        } else {
            updateHeader(activeConversation);
        }
    }

    private void endCurrentCall(boolean notifyPeer, String message) {
        Conversation conversation = currentCallPeerUsername.isBlank()
                ? activeConversation
                : getOrCreatePrivateConversation(currentCallPeerUsername, currentCallPeerDisplayName);

        if (notifyPeer && service != null && !currentCallPeerUsername.isBlank() && !currentCallId.isBlank()) {
            service.sendPrivateCallEnd(currentCallPeerUsername, currentCallId);
        }

        audioCallManager.stop();
        closeIncomingCallAlert();
        if (message != null && !message.isBlank() && conversation != null) {
            appendLocalSystemMessage(conversation, message);
        }
        clearCallState();
        updateHeader(activeConversation);
    }

    private void clearCallState() {
        audioCallManager.stop();
        callState = CallState.IDLE;
        currentCallId = "";
        currentCallPeerUsername = "";
        currentCallPeerDisplayName = "";
    }

    private String resolveCallPeerDisplayName(String username, String fallbackDisplayName) {
        String normalizedUsername = normalizeUsername(username);
        Conversation conversation = conversationsById.get(normalizedUsername);
        if (conversation != null && !conversation.getTitle().isBlank()) {
            return conversation.getTitle();
        }

        String safeDisplayName = fallbackDisplayName == null ? "" : fallbackDisplayName.trim();
        if (!safeDisplayName.isBlank()) {
            return safeDisplayName;
        }
        return normalizedUsername.isBlank() ? "Unknown" : normalizedUsername;
    }

    private void restartExpiryTimer(String key, Runnable action) {
        PauseTransition timer = typingExpiryTimers.computeIfAbsent(key, unused -> new PauseTransition(Duration.seconds(2.5)));
        timer.stop();
        timer.setOnFinished(event -> action.run());
        timer.playFromStart();
    }

    private void stopExpiryTimer(String key) {
        PauseTransition timer = typingExpiryTimers.remove(key);
        if (timer != null) {
            timer.stop();
        }
    }

    private String buildGroupTypingText() {
        List<String> names = new ArrayList<>(groupTypingUsers.values());
        if (names.isEmpty()) {
            return "";
        }
        if (names.size() == 1) {
            return names.get(0) + " đang soạn tin...";
        }
        return names.size() + " people are typing...";
    }

    private String formatOnlineCount(int totalOnline) {
        if (totalOnline == 1) {
            return "1 đang online";
        }
        return totalOnline + " đang online";
    }

    private ParsedGroupMessage parseGroupMessage(String rawLine) {
        String line = rawLine == null ? "" : rawLine.trim();
        if (line.isEmpty()) {
            return null;
        }

        if (line.startsWith("[System]") || line.startsWith("[He thong]")) {
            return new ParsedGroupMessage(Message.systemMessage(line, LocalDateTime.now()), true, "");
        }

        Matcher matcher = TIME_AND_BODY.matcher(line);
        if (!matcher.matches()) {
            return new ParsedGroupMessage(Message.systemMessage(line, LocalDateTime.now()), true, "");
        }

        String timeText = matcher.group(1).trim();
        String body = matcher.group(2) == null ? "" : matcher.group(2).trim();
        if (body.startsWith("[He thong]")) {
            return new ParsedGroupMessage(Message.systemMessage("[" + timeText + "] " + body, parseServerTime(timeText)), true, "");
        }

        int separator = body.indexOf(": ");
        if (separator <= 0) {
            return new ParsedGroupMessage(Message.systemMessage(line, parseServerTime(timeText)), true, "");
        }

        String displayName = body.substring(0, separator).trim();
        String messageText = body.substring(separator + 2).trim();
        String username = extractUsername(displayName);
        String cleanName = cleanDisplayName(displayName, username);
        boolean sentByCurrentUser = username != null && username.equalsIgnoreCase(session.getUsername());

        Message message = Message.userMessage(
                UUID.randomUUID().toString(),
                username == null ? "" : username,
                sentByCurrentUser ? session.getFullName() : cleanName,
                messageText,
                sentByCurrentUser,
                parseServerTime(timeText));

        return new ParsedGroupMessage(message, false, username == null ? "" : username);
    }

    private LocalDateTime parseServerTime(String timeText) {
        if (timeText == null || timeText.isBlank()) {
            return LocalDateTime.now();
        }

        try {
            return LocalDateTime.of(LocalDate.now(), LocalTime.parse(timeText.trim(), SERVER_TIME));
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.of(LocalDate.now(), LocalTime.parse(timeText.trim(), FALLBACK_TIME));
        } catch (DateTimeParseException ignored) {
        }

        return LocalDateTime.now();
    }

    private void resortConversations() {
        Conversation selected = activeConversation;
        suppressConversationSelectionHandling = true;
        try {
            FXCollections.sort(conversations, (left, right) -> {
                if (left.isGroupConversation() && !right.isGroupConversation()) {
                    return -1;
                }
                if (!left.isGroupConversation() && right.isGroupConversation()) {
                    return 1;
                }

                int byActivity = Long.compare(right.getLastActivityEpochMillis(), left.getLastActivityEpochMillis());
                if (byActivity != 0) {
                    return byActivity;
                }

                return left.getTitle().compareToIgnoreCase(right.getTitle());
            });

            if (selected != null) {
                conversationListView.getSelectionModel().select(selected);
            }
        } finally {
            suppressConversationSelectionHandling = false;
        }
    }

    private boolean isPinnedToBottom() {
        double contentHeight = messagesContainer.getBoundsInLocal().getHeight();
        double viewportHeight = messagesScrollPane.getViewportBounds().getHeight();
        if (contentHeight <= viewportHeight + 4) {
            return true;
        }
        return messagesScrollPane.getVvalue() >= 0.96;
    }

    private boolean isAtScrollableBottom() {
        double contentHeight = messagesContainer.getBoundsInLocal().getHeight();
        double viewportHeight = messagesScrollPane.getViewportBounds().getHeight();
        if (contentHeight <= viewportHeight + 4) {
            return false;
        }
        return messagesScrollPane.getVvalue() >= 0.96;
    }

    private void scrollToBottomSoon() {
        javafx.application.Platform.runLater(() -> {
            messagesScrollPane.layout();
            messagesScrollPane.setVvalue(1.0);
            clearPendingNewMessagesIndicator();
            clearUnreadDividerMarker();
        });
    }

    private String extractUsername(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }

        Matcher matcher = USERNAME_AT_END.matcher(displayName.trim());
        if (!matcher.find()) {
            return displayName.trim().toLowerCase(Locale.ROOT);
        }
        return matcher.group(1).trim().toLowerCase(Locale.ROOT);
    }

    private String cleanDisplayName(String displayName, String username) {
        if (displayName == null || displayName.isBlank()) {
            return username == null ? "Unknown" : username;
        }

        String value = displayName.trim();
        Matcher matcher = USERNAME_AT_END.matcher(value);
        if (matcher.find()) {
            value = value.substring(0, matcher.start()).trim();
        }
        return value.isBlank() ? (username == null ? "Unknown" : username) : value;
    }

    private String buildAvatar(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }

        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.ROOT);
        }
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }

    private void cacheAvatarImage(String username, Image image) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername.isBlank() || image == null || image.isError()) {
            return;
        }

        avatarImagesByUsername.put(normalizedUsername, image);
    }

    private Image lookupAvatarImage(String username) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername.isBlank()) {
            return null;
        }

        return avatarImagesByUsername.get(normalizedUsername);
    }

    private Image resolveMessageAvatar(Message message) {
        if (message == null || message.isSystemMessage()) {
            return null;
        }

        return lookupAvatarImage(message.getSenderUsername());
    }

    private String buildReplyContext(Message message) {
        return "Tra loi " + resolveReplyAuthor(message) + ": " + buildReplyPreviewText(message);
    }

    private String buildReplyPreviewText(Message message) {
        String content = message.getReplySourceText();
        if (content.length() <= 80) {
            return content;
        }
        return content.substring(0, 80) + "...";
    }

    private String resolveReplyAuthor(Message message) {
        if (message.getSenderDisplayName() != null && !message.getSenderDisplayName().isBlank()) {
            return message.getSenderDisplayName();
        }
        if (message.getSenderUsername() != null && !message.getSenderUsername().isBlank()) {
            return message.getSenderUsername();
        }
        return "Unknown";
    }

    private void clearIncomingGroupTyping(String fromUsername) {
        String normalizedUsername = normalizeUsername(fromUsername);
        if (normalizedUsername.isBlank()) {
            return;
        }

        groupTypingUsers.remove(normalizedUsername);
        stopExpiryTimer("group:" + normalizedUsername);
        if (groupTypingUsers.isEmpty()) {
            groupConversation.clearTyping();
        } else {
            groupConversation.showTyping(buildGroupTypingText());
        }
    }

    private Path promptForAttachment(boolean imageOnly) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(imageOnly ? "Choose an image" : "Choose a file");
        chooser.getExtensionFilters().clear();
        if (imageOnly) {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"));
        } else {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All Files", "*.*"));
        }

        java.io.File selectedFile = chooser.showOpenDialog(rootPane.getScene().getWindow());
        return selectedFile == null ? null : selectedFile.toPath();
    }

    private void configureSvgToolButton(Button button, String tooltipText, String svgPathContent) {
        if (button == null) {
            return;
        }

        button.setText("");
        button.setTooltip(new Tooltip(tooltipText));

        SVGPath icon = new SVGPath();
        icon.setContent(svgPathContent);
        icon.getStyleClass().add("composer-tool-icon");
        icon.setScaleX(0.74);
        icon.setScaleY(0.74);
        button.setGraphic(icon);
    }

    private void configureHeaderIconButton(Button button, String tooltipText, String svgPathContent, double rotateDegrees) {
        if (button == null) {
            return;
        }

        button.setText("");
        button.setTooltip(new Tooltip(tooltipText));

        SVGPath icon = new SVGPath();
        icon.setContent(svgPathContent);
        icon.getStyleClass().add("header-action-icon");
        icon.setScaleX(0.78);
        icon.setScaleY(0.78);
        icon.setRotate(rotateDegrees);
        button.setGraphic(icon);
    }

    private byte[] readAttachmentBytes(Path filePath, int maxBytes, String label, Conversation conversation) {
        try {
            if (!Files.exists(filePath)) {
                appendLocalSystemMessage(conversation, label + " file no longer exists.");
                return null;
            }

            long sizeBytes = Files.size(filePath);
            if (sizeBytes <= 0) {
                appendLocalSystemMessage(conversation, label + " file is empty.");
                return null;
            }
            if (sizeBytes > maxBytes) {
                appendLocalSystemMessage(conversation,
                        label + " exceeds the limit of " + (maxBytes / (1024 * 1024)) + "MB.");
                return null;
            }

            byte[] bytes = Files.readAllBytes(filePath);
            if (bytes.length == 0) {
                appendLocalSystemMessage(conversation, label + " file is empty.");
                return null;
            }
            if (bytes.length > maxBytes) {
                appendLocalSystemMessage(conversation,
                        label + " exceeds the limit of " + (maxBytes / (1024 * 1024)) + "MB.");
                return null;
            }
            return bytes;
        } catch (IOException ex) {
            appendLocalSystemMessage(conversation, "Unable to read " + label.toLowerCase(Locale.ROOT) + ": " + ex.getMessage());
            return null;
        }
    }

    private byte[] encodeAvatarAsPng(Path imagePath) {
        try (InputStream inputStream = Files.newInputStream(imagePath)) {
            BufferedImage bufferedImage = ImageIO.read(inputStream);
            if (bufferedImage == null) {
                updateProfilePopupStatus("Selected file is not a valid image.", true);
                return null;
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            if (!ImageIO.write(bufferedImage, "png", outputStream)) {
                updateProfilePopupStatus("Unable to convert avatar image.", true);
                return null;
            }

            byte[] avatarBytes = outputStream.toByteArray();
            if (avatarBytes.length == 0) {
                updateProfilePopupStatus("Avatar image is empty.", true);
                return null;
            }
            if (avatarBytes.length > MAX_IMAGE_BYTES) {
                updateProfilePopupStatus("Avatar exceeds the limit of 2MB.", true);
                return null;
            }

            return avatarBytes;
        } catch (IOException ex) {
            updateProfilePopupStatus("Unable to read avatar: " + ex.getMessage(), true);
            return null;
        }
    }

    private void appendLocalSystemMessage(Conversation conversation, String text) {
        javafx.application.Platform.runLater(() -> appendMessage(
                conversation == null ? groupConversation : conversation,
                Message.systemMessage(text, LocalDateTime.now()),
                true));
    }

    private boolean isValidImage(byte[] imageBytes) {
        try (InputStream inputStream = new ByteArrayInputStream(imageBytes)) {
            return ImageIO.read(inputStream) != null;
        } catch (IOException ex) {
            return false;
        }
    }

    private String detectMimeType(Path path, String fileName, String expectedPrefix) {
        try {
            String probed = Files.probeContentType(path);
            if (probed != null && !probed.isBlank()) {
                return probed.trim();
            }
        } catch (IOException ignored) {
        }

        String lowerName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (expectedPrefix.startsWith("image/")) {
            if (lowerName.endsWith(".png")) {
                return "image/png";
            }
            if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
                return "image/jpeg";
            }
            if (lowerName.endsWith(".gif")) {
                return "image/gif";
            }
            if (lowerName.endsWith(".bmp")) {
                return "image/bmp";
            }
            if (lowerName.endsWith(".webp")) {
                return "image/webp";
            }
            return "image/png";
        }

        return "application/octet-stream";
    }

    private String pathFileName(Path path) {
        if (path == null || path.getFileName() == null) {
            return "";
        }
        return path.getFileName().toString();
    }

    private String normalizeAttachmentName(String value, String fallback, int maxLength) {
        String safeValue = value == null ? "" : value.trim();
        if (safeValue.isBlank()) {
            safeValue = fallback;
        }
        if (safeValue.length() > maxLength) {
            return safeValue.substring(0, maxLength);
        }
        return safeValue;
    }

    private boolean isCurrentUser(String username) {
        return normalizeUsername(username).equals(normalizeUsername(session.getUsername()));
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private void loadCurrentUserAvatarFromDisk() {
        try {
            Path avatarPath = StoragePaths.avatarDirectory().resolve(session.getUsername() + ".png");
            if (!Files.exists(avatarPath)) {
                currentUserAvatarImage = null;
                return;
            }

            currentUserAvatarImage = buildImageFromBytes(Files.readAllBytes(avatarPath));
        } catch (IOException ignored) {
            currentUserAvatarImage = null;
        }
    }

    private Image buildImageFromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        Image image = new Image(new ByteArrayInputStream(bytes));
        if (image.isError()) {
            return null;
        }
        return image;
    }

    private void applyCurrentUserAvatar(Image image) {
        boolean hasImage = image != null && !image.isError();
        currentUserAvatarImageView.setImage(image);
        currentUserAvatarImageView.setManaged(hasImage);
        currentUserAvatarImageView.setVisible(hasImage);
        currentUserAvatarLabel.setManaged(!hasImage);
        currentUserAvatarLabel.setVisible(!hasImage);
    }

    private void applyHeaderAvatar(Image image, String fallbackText) {
        boolean hasImage = image != null && !image.isError();
        headerAvatarImageView.setImage(image);
        headerAvatarImageView.setManaged(hasImage);
        headerAvatarImageView.setVisible(hasImage);
        headerAvatarLabel.setText((fallbackText == null || fallbackText.isBlank()) ? "?" : fallbackText.trim());
        headerAvatarLabel.setManaged(!hasImage);
        headerAvatarLabel.setVisible(!hasImage);
    }

    private void updateAvatarPaneStyle(StackPane avatarPane, Image image) {
        if (avatarPane == null) {
            return;
        }

        boolean hasImage = image != null && !image.isError();
        if (hasImage) {
            if (!avatarPane.getStyleClass().contains("avatar-has-image")) {
                avatarPane.getStyleClass().add("avatar-has-image");
            }
            return;
        }

        avatarPane.getStyleClass().remove("avatar-has-image");
    }

    private void updateProfilePopupStatus(String message, boolean error) {
        javafx.application.Platform.runLater(() -> {
            if (profilePopupController != null) {
                profilePopupController.setStatus(message, error);
            }
        });
    }

    private enum CallState {
        IDLE,
        OUTGOING_RINGING,
        INCOMING_RINGING,
        IN_CALL
    }

    private record MessageSearchResult(Message message, String sender, String timestamp, String snippet) {
    }

    private record ParsedGroupMessage(Message message, boolean systemMessage, String senderUsername) {
    }
}
