package core;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Toolkit;
import java.awt.FlowLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.datatransfer.StringSelection;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.geom.RoundRectangle2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

// Cua so chinh cua client: gom man hinh auth va phong chat sau khi dang nhap.
public class ChatClientFrame extends JFrame {
    private static final String AUTH_CARD = "auth";
    private static final String CHAT_CARD = "chat";

    private final String host;
    private final int port;
    // Socket I/O duoc dung o nhieu thread nen can khoa khi doi tham chieu.
    private final Object connectionLock = new Object();

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);

    private final JTextField loginUsernameField = new JTextField();
    private final JPasswordField loginPasswordField = new JPasswordField();
    private final JButton loginButton = new ModernButton("Dang nhap", ButtonVariant.PRIMARY);

    private final JTextField registerNameField = new JTextField();
    private final JTextField registerUsernameField = new JTextField();
    private final JPasswordField registerPasswordField = new JPasswordField();
    private final JButton registerButton = new ModernButton("Dang ky", ButtonVariant.PRIMARY);

    private final JLabel authStatusLabel = new JLabel("Nhap thong tin de dang nhap hoac dang ky.");
    private final JLabel chatStatusLabel = new JLabel("Chua dang nhap.");
    private final JLabel accountLabel = new JLabel("Chua co tai khoan dang nhap.");
    // Hien thi trang thai "dang soan tin" cua nguoi khac trong phong chat chung.
    private final JLabel typingStatusLabel = new JLabel();
    // Fallback Swing message list (duoc giu lai de tai su dung cho private chat / khi JavaFX khong san sang).
    private final JPanel chatMessagesPanel = new JPanel();
    private JScrollPane chatMessagesScrollPane;
    // JavaFX message view (embedded vao Swing bang JFXPanel) de co animation Fade + Slide nhu Messenger.
    private FxChatView fxGroupView;
    private final JTextField inputField = new JTextField();
    private final JButton sendButton = new ModernButton("Gui", ButtonVariant.PRIMARY);
    private final JButton sendImageButton = new ModernButton("Gửi ảnh", ButtonVariant.GHOST);
    private final JButton sendFileButton = new ModernButton("Gửi file", ButtonVariant.GHOST);
    private final JButton logoutButton = new ModernButton("Đăng xuất", ButtonVariant.SECONDARY);
    // Danh sach nguoi dang online (server se gui cap nhat dinh ky theo su kien join/leave).
    private final DefaultListModel<String> onlineUsersModel = new DefaultListModel<>();
    private final JList<String> onlineUsersList = new JList<>(onlineUsersModel);
    private final JButton startPrivateChatButton = new ModernButton("Chat riêng", ButtonVariant.PRIMARY);
    // Tab trung tam: gom tab Chat, Online va cac tab PM duoc tao dong.
    private final JTabbedPane chatTabs = new JTabbedPane();
    private final Map<String, PrivateChatTab> privateChatTabs = new HashMap<>();
    private final JLabel profileNameValue = new JLabel();
    private final JLabel profileUsernameValue = new JLabel();

    private final Map<String, TypingEntry> groupTypingUsers = new HashMap<>();
    private Timer groupTypingSweepTimer;
    private Timer groupTypingIdleTimer;
    private boolean groupTypingActive;
    private long groupTypingLastSentAt;

    private static final Pattern USERNAME_IN_PARENS_AT_END = Pattern.compile("\\(([^)]+)\\)\\s*$");
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024; // 2MB
    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024; // 10MB
    private static final int CHAT_IMAGE_MAX_WIDTH = 420;
    private static final int CHAT_IMAGE_MAX_HEIGHT = 280;
    private static final int TYPING_IDLE_DELAY_MS = 1200;
    private static final int TYPING_REFRESH_MS = 900;
    private static final int TYPING_TTL_MS = 2500;
    private static final int TYPING_SWEEP_MS = 450;
    private static final int MESSAGE_ANIM_DURATION_MS = 250;
    private static final int MESSAGE_ANIM_SLIDE_PX = 10;
    private static final int MESSAGE_ANIM_TICK_MS = 16;

    // Theme mau sac va style co ban de giao dien trong sang va nhat quan.
    private static final class Theme {
        private static final Color BG = new Color(0xF3F6FB);
        private static final Color SURFACE = new Color(0xFFFFFF);
        private static final Color SURFACE_2 = new Color(0xF9FBFF);
        private static final Color BORDER = new Color(0xD6DCE6);
        private static final Color TEXT = new Color(0x1F2937);
        private static final Color MUTED_TEXT = new Color(0x6B7280);
        private static final Color ACCENT = new Color(0x0EA5E9);
        private static final Color ACCENT_DARK = new Color(0x0284C7);
        private static final Color ACCENT_SOFT = new Color(0xE0F2FE);
        private static final Color SUCCESS = new Color(0x059669);
        private static final Color DANGER = new Color(0xDC2626);

        private static final int RADIUS = 14;
        private static final Font UI_FONT = new Font("Segoe UI", Font.PLAIN, 13);

        private Theme() {
        }

        private static Font fontPlain(float size) {
            return UI_FONT.deriveFont(Font.PLAIN, size);
        }

        private static Font fontBold(float size) {
            return UI_FONT.deriveFont(Font.BOLD, size);
        }

        private static Border roundedFieldBorder() {
            Border outer = new RoundedBorder(BORDER, RADIUS);
            Border inner = BorderFactory.createEmptyBorder(10, 12, 10, 12);
            return BorderFactory.createCompoundBorder(outer, inner);
        }

        private static void styleTextField(JTextField field) {
            // Fail-fast neu field bi null (thuong do quen new JTextField truoc khi goi style/addField).
            if (field == null) {
                throw new IllegalArgumentException("TextField is null. Ensure it is initialized before styling/adding.");
            }
            field.setFont(fontPlain(13));
            field.setForeground(TEXT);
            field.setCaretColor(TEXT);
            field.setBackground(SURFACE);
            field.setBorder(roundedFieldBorder());
        }

        private static void styleSecondaryLabel(JLabel label) {
            label.setFont(fontPlain(13));
            label.setForeground(MUTED_TEXT);
        }

        private static void stylePrimaryLabel(JLabel label) {
            label.setFont(fontBold(14));
            label.setForeground(TEXT);
        }

        private static void styleTabs(JTabbedPane tabs) {
            tabs.setFont(fontBold(13));
            tabs.setUI(new ModernTabbedPaneUI());
            tabs.setOpaque(false);
            tabs.setBackground(SURFACE);
            tabs.setForeground(TEXT);
            tabs.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        }
    }

    private enum ButtonVariant {
        PRIMARY,
        SECONDARY,
        GHOST
    }

    private static final class ModernButton extends JButton {
        private final ButtonVariant variant;

        private ModernButton(String text, ButtonVariant variant) {
            super(text);
            this.variant = variant;
            setFont(Theme.fontBold(13));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setRolloverEnabled(true);
            setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
            updateForeground();
        }

        private void updateForeground() {
            if (variant == ButtonVariant.PRIMARY) {
                setForeground(Color.WHITE);
                return;
            }

            if (variant == ButtonVariant.SECONDARY) {
                setForeground(Theme.TEXT);
                return;
            }

            setForeground(Theme.ACCENT_DARK);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            boolean enabled = isEnabled();
            boolean pressed = getModel().isPressed();
            boolean hover = getModel().isRollover();

            Color fill = null;
            Color stroke = null;

            if (variant == ButtonVariant.PRIMARY) {
                if (!enabled) {
                    fill = new Color(Theme.ACCENT.getRGB() & 0x00FFFFFF | (0x66 << 24), true);
                } else if (pressed) {
                    fill = Theme.ACCENT_DARK;
                } else if (hover) {
                    fill = Theme.ACCENT_DARK;
                } else {
                    fill = Theme.ACCENT;
                }
            } else if (variant == ButtonVariant.SECONDARY) {
                fill = enabled ? Theme.SURFACE : new Color(0xF1F5F9);
                stroke = Theme.BORDER;
            } else {
                // GHOST
                if (hover && enabled) {
                    fill = Theme.ACCENT_SOFT;
                }
            }

            float arc = Theme.RADIUS;
            RoundRectangle2D.Float rect = new RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, arc, arc);

            if (fill != null) {
                g2.setColor(fill);
                g2.fill(rect);
            }

            if (stroke != null) {
                g2.setColor(stroke);
                g2.draw(rect);
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class RoundedPanel extends JPanel {
        private final int radius;

        private RoundedPanel(int radius) {
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, width, height, radius, radius);

            g2.setColor(Theme.BORDER);
            g2.drawRoundRect(0, 0, width - 1, height - 1, radius, radius);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class GradientPanel extends JPanel {
        private final Color from;
        private final Color to;
        private final int radius;

        private GradientPanel(Color from, Color to, int radius) {
            this.from = from;
            this.to = to;
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            g2.setPaint(new GradientPaint(0, 0, from, width, height, to));
            g2.fillRoundRect(0, 0, width, height, radius, radius);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class RoundedBorder implements Border {
        private final Color color;
        private final int radius;

        private RoundedBorder(Color color, int radius) {
            this.color = color;
            this.radius = radius;
        }

        @Override
        public Insets getBorderInsets(java.awt.Component c) {
            return new Insets(4, 6, 4, 6);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }

        @Override
        public void paintBorder(java.awt.Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2.dispose();
        }
    }

    private static final class ModernTabbedPaneUI extends BasicTabbedPaneUI {
        @Override
        protected void installDefaults() {
            super.installDefaults();
            tabAreaInsets = new Insets(10, 10, 8, 10);
            contentBorderInsets = new Insets(0, 0, 0, 0);
        }

        @Override
        protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
            // Remove the default content border to keep the UI clean.
        }

        @Override
        protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex,
                Rectangle iconRect, Rectangle textRect, boolean isSelected) {
            // No focus ring on tabs.
        }

        @Override
        protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h,
                boolean isSelected) {
            // No border; background handles the selected state.
        }

        @Override
        protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h,
                boolean isSelected) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color fill = isSelected ? Theme.ACCENT_SOFT : new Color(0, 0, 0, 0);
            g2.setColor(fill);
            g2.fillRoundRect(x + 2, y + 2, w - 4, h - 4, Theme.RADIUS, Theme.RADIUS);
            g2.dispose();
        }

        @Override
        protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
            return super.calculateTabHeight(tabPlacement, tabIndex, fontHeight) + 8;
        }

        @Override
        protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {
            return super.calculateTabWidth(tabPlacement, tabIndex, metrics) + 18;
        }
    }

    private volatile boolean authenticated;
    private volatile boolean manualDisconnect;
    private volatile String currentFullName;
    private volatile String currentUsername;

    private ImageIcon defaultAvatarIcon;
    private final Map<String, ImageIcon> avatarCache = new HashMap<>(); // Cache avatar cua user khac
    // Trang thai Reply cho Chat chung
    private JPanel groupReplyPanel;
    private JLabel groupReplyLabel;
    private String groupReplyContext = null; // Luu text dang duoc reply

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    // Khoi tao frame, dung UI va dua nguoi dung ve trang thai chua dang nhap.
    public ChatClientFrame(String host, int port) {
        this.host = host;
        this.port = port;

        applyThemeDefaults();
        loadDefaultAvatar();

        setTitle("Java Chat Box");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(620, 500));
        setSize(760, 560);
        setLocationRelativeTo(null);

        buildUi();
        setButtonIcons();
        bindEvents();
        resetToAuthState("Server: " + host + ":" + port, Theme.MUTED_TEXT);
    }

    private void loadDefaultAvatar() {
        File f = new File("lib", "user.png");
        if (!f.exists()) {
            f = new File("../lib", "user.png");
        }
        if (f.exists()) {
            try {
                BufferedImage img = ImageIO.read(f);
                if (img != null) {
                    defaultAvatarIcon = new ImageIcon(scaleToFit(img, 32, 32));
                }
            } catch (IOException e) {
                // Ignore if cannot read
            }
        }
    }

    // Ap theme co ban cho cac component duoc khoi tao tu field initializers.
    private void applyThemeDefaults() {
        Theme.styleTextField(loginUsernameField);
        Theme.styleTextField(loginPasswordField);
        Theme.styleTextField(registerNameField);
        Theme.styleTextField(registerUsernameField);
        Theme.styleTextField(registerPasswordField);

        Theme.styleTextField(inputField);
        configureMessagesPanel(chatMessagesPanel);

        Theme.stylePrimaryLabel(accountLabel);
        Theme.styleSecondaryLabel(chatStatusLabel);
        Theme.styleSecondaryLabel(authStatusLabel);
        Theme.styleSecondaryLabel(typingStatusLabel);
        typingStatusLabel.setFont(Theme.fontPlain(12));
        typingStatusLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        typingStatusLabel.setText(" ");
        typingStatusLabel.setVisible(false);

        Theme.styleTabs(chatTabs);

        profileNameValue.setFont(Theme.fontBold(18));
        profileNameValue.setForeground(Theme.TEXT);
        profileUsernameValue.setFont(Theme.fontPlain(14));
        profileUsernameValue.setForeground(Theme.MUTED_TEXT);

        onlineUsersList.setBackground(Theme.SURFACE_2);
        onlineUsersList.setForeground(Theme.TEXT);
        onlineUsersList.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        onlineUsersList.setFixedCellHeight(44);
        onlineUsersList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel();
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            label.setFont(Theme.fontPlain(13));

            String displayName = value == null ? "" : value.trim();
            label.setText(formatOnlineUserHtml(displayName));

            if (isSelected) {
                label.setBackground(Theme.ACCENT_SOFT);
                label.setForeground(Theme.TEXT);
            } else {
                label.setBackground(Theme.SURFACE_2);
                label.setForeground(Theme.TEXT);
            }

            return label;
        });
    }

    // Ghep 2 man hinh auth/chat vao cung mot frame bang CardLayout.
    private void buildUi() {
        contentPanel.setBackground(Theme.BG);
        contentPanel.add(createAuthPanel(), AUTH_CARD);
        contentPanel.add(createChatPanel(), CHAT_CARD);
        setContentPane(contentPanel);
        cardLayout.show(contentPanel, AUTH_CARD);
    }

    // Tao man hinh auth gom tieu de, thong tin server va 2 tab dang nhap/dang ky.
    private JPanel createAuthPanel() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(Theme.BG);

        RoundedPanel card = new RoundedPanel(Theme.RADIUS);
        card.setBackground(Theme.SURFACE);
        card.setLayout(new BorderLayout(0, 14));
        card.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        card.setPreferredSize(new Dimension(560, 420));

        GradientPanel heroPanel = new GradientPanel(Theme.ACCENT_DARK, Theme.ACCENT, Theme.RADIUS);
        heroPanel.setLayout(new BorderLayout());
        heroPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JLabel titleLabel = new JLabel("Java Realtime Chat");
        titleLabel.setFont(Theme.fontBold(24));
        titleLabel.setForeground(Color.WHITE);

        JLabel subtitleLabel = new JLabel("Ket noi toi " + host + ":" + port);
        subtitleLabel.setFont(Theme.fontPlain(13));
        subtitleLabel.setForeground(new Color(255, 255, 255, 220));

        JPanel heroTextPanel = new JPanel();
        heroTextPanel.setOpaque(false);
        heroTextPanel.setLayout(new BoxLayout(heroTextPanel, BoxLayout.Y_AXIS));
        heroTextPanel.add(titleLabel);
        heroTextPanel.add(Box.createVerticalStrut(6));
        heroTextPanel.add(subtitleLabel);
        heroPanel.add(heroTextPanel, BorderLayout.CENTER);

        card.add(heroPanel, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        Theme.styleTabs(tabs);
        // Boc tab auth vao JScrollPane de neu man hinh nho/DPI cao thi van scroll thay nut.
        tabs.addTab("Dang nhap", wrapAuthTab(createLoginTab()));
        tabs.addTab("Dang ky", wrapAuthTab(createRegisterTab()));
        card.add(tabs, BorderLayout.CENTER);

        authStatusLabel.setBorder(BorderFactory.createEmptyBorder(10, 6, 0, 6));
        card.add(authStatusLabel, BorderLayout.SOUTH);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.insets = new Insets(24, 24, 24, 24);
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;
        root.add(card, gbc);

        return root;
    }

    // Tab dang nhap chi can username va password.
    private JPanel createLoginTab() {
        JPanel panel = createFormPanel();
        addField(panel, 0, "Username", loginUsernameField);
        addField(panel, 1, "Password", loginPasswordField);

        GridBagConstraints buttonConstraints = new GridBagConstraints();
        buttonConstraints.gridx = 1;
        buttonConstraints.gridy = 2;
        buttonConstraints.anchor = GridBagConstraints.EAST;
        buttonConstraints.insets = new Insets(18, 0, 0, 0);
        panel.add(loginButton, buttonConstraints);

        return panel;
    }

    // Tab dang ky them ho ten va mot dong huong dan dieu kien du lieu.
    private JPanel createRegisterTab() {
        JPanel panel = createFormPanel();
        addField(panel, 0, "Ho ten", registerNameField);
        addField(panel, 1, "Username", registerUsernameField);
        addField(panel, 2, "Password", registerPasswordField);

        GridBagConstraints hintConstraints = new GridBagConstraints();
        hintConstraints.gridx = 1;
        hintConstraints.gridy = 3;
        hintConstraints.weightx = 1.0;
        hintConstraints.anchor = GridBagConstraints.WEST;
        hintConstraints.insets = new Insets(6, 0, 0, 0);
        JLabel hintLabel = new JLabel("Ho ten 2-60 ky tu, username 3-20 ky tu, password toi thieu 6 ky tu.");
        Theme.styleSecondaryLabel(hintLabel);
        panel.add(hintLabel, hintConstraints);

        GridBagConstraints buttonConstraints = new GridBagConstraints();
        buttonConstraints.gridx = 1;
        buttonConstraints.gridy = 4;
        buttonConstraints.anchor = GridBagConstraints.EAST;
        buttonConstraints.insets = new Insets(18, 0, 0, 0);
        panel.add(registerButton, buttonConstraints);

        return panel;
    }

    // Form chung dung GridBagLayout de can chinh label/input gon gang.
    private JPanel createFormPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(18, 10, 18, 10));
        return panel;
    }

    // Tao JScrollPane nhe cho tab auth (khong border) de tranh mat nut khi khung nho.
    private JScrollPane wrapAuthTab(JPanel content) {
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    // Them mot dong gom label va o nhap vao vi tri row chi dinh.
    private void addField(JPanel panel, int row, String labelText, JTextField field) {
        // Swing se NPE khi add component null; kiem tra som de biet ro vi sao.
        if (panel == null) {
            throw new IllegalArgumentException("Panel is null in addField(row=" + row + ", label=" + labelText + ").");
        }
        if (field == null) {
            throw new IllegalArgumentException("Field is null in addField(row=" + row + ", label=" + labelText + ").");
        }
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(8, 0, 8, 12);
        JLabel label = new JLabel(labelText);
        label.setFont(Theme.fontBold(12));
        label.setForeground(Theme.MUTED_TEXT);
        panel.add(label, labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1.0;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(8, 0, 8, 0);
        field.setColumns(22);
        panel.add(field, fieldConstraints);
    }

    // Tao giao dien phong chat sau khi auth thanh cong.
    private JPanel createChatPanel() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        RoundedPanel card = new RoundedPanel(Theme.RADIUS);
        card.setBackground(Theme.SURFACE);
        card.setLayout(new BorderLayout(14, 14));
        card.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JPanel headerPanel = new JPanel(new BorderLayout(12, 12));
        headerPanel.setOpaque(false);

        JPanel labelsPanel = new JPanel();
        labelsPanel.setOpaque(false);
        labelsPanel.setLayout(new BoxLayout(labelsPanel, BoxLayout.Y_AXIS));
        labelsPanel.add(accountLabel);
        labelsPanel.add(Box.createVerticalStrut(4));
        labelsPanel.add(chatStatusLabel);

        headerPanel.add(labelsPanel, BorderLayout.CENTER);
        headerPanel.add(logoutButton, BorderLayout.EAST);
        card.add(headerPanel, BorderLayout.NORTH);

        if (chatTabs.getTabCount() == 0) {
            chatTabs.addTab("Chat", createChatTab());
            chatTabs.addTab("Online", createOnlineTab());
            chatTabs.addTab("Ca nhan", createProfileTab());
        }

        card.add(chatTabs, BorderLayout.CENTER);
        root.add(card, BorderLayout.CENTER);

        setChatControlsEnabled(false);
        return root;
    }

    // Tab chat: khung tin nhan va o nhap tin.
    private JPanel createChatTab() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setOpaque(false);

        if (fxGroupView == null) {
            fxGroupView = new FxChatView();
        }

        javax.swing.JComponent fxPanel = fxGroupView.getPanel();
        fxPanel.setBorder(new RoundedBorder(Theme.BORDER, Theme.RADIUS));
        fxPanel.setBackground(Theme.SURFACE_2);
        fxPanel.setOpaque(true);
        panel.add(fxPanel, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout(8, 8));
        inputPanel.setOpaque(false);
        
        // Init panel reply cho chat chung
        groupReplyPanel = createReplyPreviewPanel(e -> clearGroupReply());
        inputPanel.add(groupReplyPanel, BorderLayout.NORTH);
        
        inputPanel.add(inputField, BorderLayout.CENTER);

        JPanel actionsPanel = new JPanel();
        actionsPanel.setOpaque(false);
        actionsPanel.setLayout(new BoxLayout(actionsPanel, BoxLayout.X_AXIS));
        actionsPanel.add(sendFileButton);
        actionsPanel.add(Box.createHorizontalStrut(8));
        actionsPanel.add(sendImageButton);
        actionsPanel.add(Box.createHorizontalStrut(8));
        actionsPanel.add(sendButton);
        inputPanel.add(actionsPanel, BorderLayout.EAST);

        JPanel bottomPanel = new JPanel(new BorderLayout(0, 6));
        bottomPanel.setOpaque(false);
        bottomPanel.add(typingStatusLabel, BorderLayout.NORTH);
        bottomPanel.add(inputPanel, BorderLayout.CENTER);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    // Tao panel hien thi noi dung dang duoc tra loi (Reply Preview)
    private JPanel createReplyPreviewPanel(ActionListener onCancel) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 0));
        panel.setVisible(false); // An mac dinh

        JLabel label = new JLabel();
        label.setFont(Theme.fontPlain(11));
        label.setForeground(Theme.MUTED_TEXT);
        
        // Ve duong gach doc ben trai de bieu thi quote
        JLabel border = new JLabel(" ");
        border.setOpaque(true);
        border.setBackground(Theme.ACCENT);
        border.setPreferredSize(new Dimension(3, 0));

        JButton closeBtn = new ModernButton("×", ButtonVariant.GHOST);
        closeBtn.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        closeBtn.setFont(Theme.fontBold(16));
        closeBtn.addActionListener(onCancel);

        panel.add(border, BorderLayout.WEST);
        panel.add(label, BorderLayout.CENTER);
        panel.add(closeBtn, BorderLayout.EAST);
        
        // Luu tham chieu label vao ClientProperty hoac field neu can, 
        // nhung o day ta se gan vao field class (groupReplyLabel) hoac tra ve.
        // De don gian, ta set truc tiep component con ra ngoai
        if (this.groupReplyLabel == null) {
             this.groupReplyLabel = label;
        }
        
        return panel;
    }

    private void setGroupReply(String senderName, String message) {
        this.groupReplyContext = "Replying to " + senderName + ": " + message;
        if (groupReplyLabel != null) {
            // Gioi han do dai hien thi
            String preview = message.length() > 50 ? message.substring(0, 50) + "..." : message;
            groupReplyLabel.setText("<html><b>Đang trả lời " + senderName + ":</b><br>" + preview + "</html>");
            groupReplyPanel.setVisible(true);
            inputField.requestFocusInWindow();
        }
    }

    private void clearGroupReply() {
        this.groupReplyContext = null;
        if (groupReplyPanel != null) {
            groupReplyPanel.setVisible(false);
        }
    }

    // Tab online: hien thi danh sach nguoi dang online nhan tu server.
    private JPanel createOnlineTab() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setOpaque(false);

        JLabel titleLabel = new JLabel("Nguoi dang online");
        titleLabel.setFont(Theme.fontBold(14));
        titleLabel.setForeground(Theme.TEXT);
        JLabel hintLabel = new JLabel("Double click hoac bam 'Chat rieng' de gui tin nhan rieng.");
        Theme.styleSecondaryLabel(hintLabel);

        JPanel headerPanel = new JPanel();
        headerPanel.setOpaque(false);
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.add(titleLabel);
        headerPanel.add(Box.createVerticalStrut(4));
        headerPanel.add(hintLabel);
        panel.add(headerPanel, BorderLayout.NORTH);

        onlineUsersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        onlineUsersList.setFont(Theme.fontPlain(13));
        panel.add(createScrollPane(onlineUsersList, Theme.SURFACE_2), BorderLayout.CENTER);

        JPanel footerPanel = new JPanel(new BorderLayout(8, 8));
        footerPanel.setOpaque(false);
        footerPanel.add(startPrivateChatButton, BorderLayout.EAST);
        panel.add(footerPanel, BorderLayout.SOUTH);

        startPrivateChatButton.setEnabled(false);

        return panel;
    }

    // Tab profile: hien thi thong tin ca nhan co ban.
    private JPanel createProfileTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        RoundedPanel card = new RoundedPanel(Theme.RADIUS);
        card.setBackground(Theme.SURFACE_2);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(32, 48, 32, 48));

        JLabel iconLabel = new JLabel("👤");
        iconLabel.setFont(Theme.UI_FONT.deriveFont(48f));
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        iconLabel.setForeground(Theme.ACCENT);

        JLabel lblName = new JLabel("Ho va ten");
        Theme.styleSecondaryLabel(lblName);
        lblName.setAlignmentX(Component.CENTER_ALIGNMENT);

        profileNameValue.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblUser = new JLabel("Username");
        Theme.styleSecondaryLabel(lblUser);
        lblUser.setAlignmentX(Component.CENTER_ALIGNMENT);

        profileUsernameValue.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton changeAvatarBtn = new ModernButton("Đổi Avatar", ButtonVariant.SECONDARY);
        changeAvatarBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        changeAvatarBtn.addActionListener(e -> {
            Path p = promptForImagePath();
            if (p != null) {
                new Thread(() -> uploadAvatar(p)).start();
            }
        });

        // Them cache avatar cua chinh minh vao cache ban dau neu co
        if (currentUsername != null && avatarCache.containsKey(currentUsername)) {
            // Logic update UI preview avatar o day neu can
        }

        card.add(iconLabel);
        card.add(Box.createVerticalStrut(16));
        card.add(lblName);
        card.add(Box.createVerticalStrut(4));
        card.add(profileNameValue);
        card.add(Box.createVerticalStrut(16));
        card.add(lblUser);
        card.add(Box.createVerticalStrut(4));
        card.add(profileUsernameValue);
        card.add(Box.createVerticalStrut(16));
        card.add(changeAvatarBtn);

        panel.add(card);
        return panel;
    }

    private void uploadAvatar(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length > MAX_IMAGE_BYTES) {
                appendMessage("[He thong] Anh qua lon (>2MB).");
                return;
            }
            // Resize nho lai truoc khi gui de nhe server
            ImageIcon scaled = createScaledImageIcon(bytes, 64, 64); 
            // Convert lai sang byte array (đơn giản hóa: gửi ảnh gốc nếu nhỏ, ở đây ta gửi bytes gốc check size)
            // De toi uu: nen compress lai tu BufferedImage. O day ta gui bytes goc.
            
            sendProtocolMessageBytes("UPDATE_AVATAR", bytes);
            
        } catch (IOException e) {
            appendMessage("[He thong] Loi doc file: " + e.getMessage());
        }
    }

    private void updateLocalAvatarCache(String username, byte[] data) {
        try {
            // Resize ve kich thuoc chuan cua avatar chat (32x32)
            ImageIcon icon = createScaledImageIcon(data, 32, 32);
            avatarCache.put(username, icon);
            if (fxGroupView != null) {
                fxGroupView.updateUserAvatar(username, data);
            }
        } catch (IOException e) {
            // ignore bad image
        }
    }

    // Tao JScrollPane voi border tron va mau nen dong bo theo theme.
    private JScrollPane createScrollPane(java.awt.Component view, Color viewportBg) {
        JScrollPane scrollPane = new JScrollPane(view);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(true);
        scrollPane.getViewport().setBackground(viewportBg);
        scrollPane.setBorder(new RoundedBorder(Theme.BORDER, Theme.RADIUS));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    // Noi cac su kien UI voi xu ly nghiep vu tuong ung.
    private void bindEvents() {
        loginButton.addActionListener(event -> submitLogin());
        registerButton.addActionListener(event -> submitRegister());
        loginPasswordField.addActionListener(event -> submitLogin());
        registerPasswordField.addActionListener(event -> submitRegister());
        sendButton.addActionListener(event -> sendCurrentMessage());
        sendImageButton.addActionListener(event -> chooseAndSendGroupImage());
        sendFileButton.addActionListener(event -> chooseAndSendGroupFile());
        inputField.addActionListener(event -> sendCurrentMessage());
        inputField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                handleGroupTypingInputChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                handleGroupTypingInputChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                handleGroupTypingInputChanged();
            }
        });
        logoutButton.addActionListener(event -> logout());
        startPrivateChatButton.addActionListener(event -> openPrivateChatFromSelection());

        onlineUsersList.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updatePrivateChatButtonState();
            }
        });

        onlineUsersList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1 && event.getClickCount() == 2) {
                    openPrivateChatFromSelection();
                }
            }
        });

        chatTabs.addChangeListener(e -> {
            Component selected = chatTabs.getSelectedComponent();
            // When a tab is selected, check if it's a private chat and process any unread messages.
            for (PrivateChatTab tab : privateChatTabs.values()) {
                if (tab.panel == selected) {
                    tab.setUnread(false);
                    tab.processReadReceipts();
                }
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                manualDisconnect = true;
                disconnectConnection();
            }
        });
    }

    private void setButtonIcons() {
        // For the main chat panel
        setupIconButton(this.sendImageButton, "send_image.png", "📷", "Gửi ảnh", 16);
        setupIconButton(this.sendFileButton, "send_file.png", "📎", "Gửi file", 16);
    }

    private void setupIconButton(JButton button, String filename, String fallbackText, String tooltip, int size) {
        button.setToolTipText(tooltip);
        // Canh lai padding de nut trong vuong van va dong bo chieu cao
        button.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        File file = new File("lib", filename);
        if (!file.exists()) {
            file = new File("../lib", filename);
        }

        if (file.exists()) {
            try {
                ImageIcon originalIcon = new ImageIcon(file.getAbsolutePath());
                Image scaledImage = originalIcon.getImage().getScaledInstance(size, size, java.awt.Image.SCALE_SMOOTH);
                button.setIcon(new ImageIcon(scaledImage));
                button.setText(""); // Co icon thi xoa text
            } catch (Exception e) {
                button.setIcon(null);
                button.setText(fallbackText); // Fallback khi co loi
            }
        } else {
            button.setIcon(null);
            button.setText(fallbackText); // Fallback khi khong tim thay file
        }
    }
    // Cap nhat trang thai enable/disable cua nut "Chat rieng" theo user dang duoc chon.
    private void updatePrivateChatButtonState() {
        String selectedUsername = extractSelectedOnlineUsername();
        boolean enabled = authenticated
                && selectedUsername != null
                && (currentUsername == null || !selectedUsername.equalsIgnoreCase(currentUsername));
        startPrivateChatButton.setEnabled(enabled);
    }

    // Mo tab chat rieng dua tren user dang duoc chon trong danh sach online.
    private void openPrivateChatFromSelection() {
        String selectedDisplayName = onlineUsersList.getSelectedValue();
        String selectedUsername = extractSelectedOnlineUsername();
        if (selectedDisplayName == null || selectedUsername == null) {
            return;
        }

        if (currentUsername != null && selectedUsername.equalsIgnoreCase(currentUsername)) {
            appendMessage("[He thong] Khong the chat rieng voi chinh minh.");
            return;
        }

        SwingUtilities.invokeLater(() -> getOrCreatePrivateChatTab(selectedUsername, selectedDisplayName, true));
    }

    // Lay username tu dong dang duoc chon trong online list (duoc format: "Full Name (username)").
    private String extractSelectedOnlineUsername() {
        String selected = onlineUsersList.getSelectedValue();
        if (selected == null) {
            return null;
        }

        Matcher matcher = USERNAME_IN_PARENS_AT_END.matcher(selected);
        if (!matcher.find()) {
            return null;
        }

        String extracted = matcher.group(1).trim().toLowerCase(Locale.ROOT);
        return extracted.isEmpty() ? null : extracted;
    }

    private String formatOnlineUserHtml(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "";
        }

        Matcher matcher = USERNAME_IN_PARENS_AT_END.matcher(displayName);
        if (!matcher.find()) {
            return "<html><div style='font-weight:700'>" + escapeHtml(displayName) + "</div></html>";
        }

        String username = matcher.group(1).trim();
        String fullName = displayName.substring(0, matcher.start()).trim();
        if (fullName.isEmpty()) {
            fullName = displayName.trim();
        }

        return "<html>"
                + "<div style='font-weight:700'>" + escapeHtml(fullName) + "</div>"
                + "<div style='color:#6B7280; font-size:11px'>@" + escapeHtml(username) + "</div>"
                + "</html>";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    // Lay (hoac tao moi) tab chat rieng voi 1 username, sau do co the chuyen sang tab do.
    private PrivateChatTab getOrCreatePrivateChatTab(String otherUsername, String otherDisplayName, boolean switchToTab) {
        String normalizedUsername = otherUsername == null ? "" : otherUsername.trim().toLowerCase(Locale.ROOT);
        if (normalizedUsername.isEmpty()) {
            return null;
        }

        PrivateChatTab existing = privateChatTabs.get(normalizedUsername);
        if (existing != null) {
            existing.updatePeerDisplayName(otherDisplayName);
            if (switchToTab) {
                chatTabs.setSelectedComponent(existing.panel);
            }
            return existing;
        }

        PrivateChatTab created = new PrivateChatTab(normalizedUsername, otherDisplayName);
        privateChatTabs.put(normalizedUsername, created);
        // Add tab voi tieu de null, sau do set custom component de hien thi badge
        chatTabs.addTab(null, created.panel);
        int index = chatTabs.indexOfComponent(created.panel);
        chatTabs.setTabComponentAt(index, created.tabHeaderPanel);

        if (switchToTab) {
            chatTabs.setSelectedComponent(created.panel);
        }

        return created;
    }

    // Xoa tab chat rieng khoi UI va khoi map quan ly.
    private void closePrivateChatTab(String otherUsername) {
        String normalizedUsername = otherUsername == null ? "" : otherUsername.trim().toLowerCase(Locale.ROOT);
        PrivateChatTab tab = privateChatTabs.remove(normalizedUsername);
        if (tab == null) {
            return;
        }

        int tabIndex = chatTabs.indexOfComponent(tab.panel);
        if (tabIndex >= 0) {
            chatTabs.removeTabAt(tabIndex);
        }
    }

    // Don dep tat ca tab PM (duoc goi khi logout/mat ket noi).
    private void clearPrivateChatTabs() {
        for (String username : privateChatTabs.keySet().toArray(new String[0])) {
            closePrivateChatTab(username);
        }
    }

    private static final Pattern CHAT_LINE_WITH_TIME = Pattern.compile("^\\[([^\\]]+)\\]\\s*(.*)$");
    private static final int CHAT_BUBBLE_RADIUS = 18;
    private static final int CHAT_BUBBLE_MAX_WIDTH = 520;

    private enum MessageSide {
        INCOMING,
        OUTGOING,
        SYSTEM
    }

    private static final class MessageRowPanel extends JPanel {
        private MessageRowPanel() {
            super(new BorderLayout());
            setOpaque(false);
        }

        @Override
        public Dimension getMaximumSize() {
            Dimension pref = getPreferredSize();
            return new Dimension(Integer.MAX_VALUE, pref.height);
        }
    }

    private static final class BubblePanel extends JPanel {
        private final Color fill;
        private final Color stroke;
        private final int radius;

        private BubblePanel(Color fill, Color stroke, int radius) {
            super(new BorderLayout());
            this.fill = fill;
            this.stroke = stroke;
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            g2.setColor(fill);
            g2.fillRoundRect(0, 0, width, height, radius, radius);

            if (stroke != null) {
                g2.setColor(stroke);
                g2.drawRoundRect(0, 0, width - 1, height - 1, radius, radius);
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class AnimatedRowPanel extends JPanel {
        private final int durationMs;
        private final int fromY;
        private final int toY;

        private float alpha;
        private int translateY;
        private int extraBottom;
        private long startMs;
        private Timer timer;

        private AnimatedRowPanel(Component content, int durationMs, int fromY, int toY) {
            super(new BorderLayout());
            this.durationMs = Math.max(1, durationMs);
            this.fromY = fromY;
            this.toY = toY;
            this.alpha = 0f;
            this.translateY = fromY;
            this.extraBottom = Math.max(0, fromY);
            setOpaque(false);
            add(content, BorderLayout.CENTER);
        }

        private void play(Runnable onFinished) {
            if (timer != null) {
                timer.stop();
            }

            startMs = System.currentTimeMillis();
            timer = new Timer(MESSAGE_ANIM_TICK_MS, event -> {
                float t = (System.currentTimeMillis() - startMs) / (float) durationMs;
                if (t >= 1f) {
                    t = 1f;
                }

                // Ease-out cubic: nhanh luc dau, cham dan ve cuoi.
                float eased = 1f - (float) Math.pow(1f - t, 3);
                alpha = eased;
                translateY = Math.round(fromY + (toY - fromY) * eased);
                repaint();

                if (t >= 1f) {
                    timer.stop();
                    alpha = 1f;
                    translateY = toY;
                    extraBottom = 0;
                    revalidate(); // shrink height once (avoid layout work per frame)
                    repaint();
                    if (onFinished != null) {
                        onFinished.run();
                    }
                }
            });
            timer.setRepeats(true);
            timer.start();
        }

        @Override
        public Dimension getMaximumSize() {
            Dimension pref = getPreferredSize();
            return new Dimension(Integer.MAX_VALUE, pref.height);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(d.width, d.height + extraBottom);
        }

        @Override
        public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate(0, translateY);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            super.paint(g2);
            g2.dispose();
        }
    }

    private static final class TypingEntry {
        private final String displayName;
        private final long lastSeenMs;

        private TypingEntry(String displayName, long lastSeenMs) {
            this.displayName = displayName;
            this.lastSeenMs = lastSeenMs;
        }
    }

    private void configureMessagesPanel(JPanel panel) {
        if (panel == null) {
            return;
        }

        panel.setOpaque(false);
        if (!(panel.getLayout() instanceof BoxLayout)) {
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        }
        if (panel.getBorder() == null) {
            panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        }
    }

    private void clearMessages(JPanel panel) {
        if (panel == null) {
            return;
        }

        Runnable task = () -> {
            panel.removeAll();
            panel.revalidate();
            panel.repaint();
        };

        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    // Xoa tin nhan phong chat chung (uu tien JavaFX view neu dang dung).
    private void clearGroupMessages() {
        if (fxGroupView != null) {
            fxGroupView.clear();
            return;
        }

        clearMessages(chatMessagesPanel);
    }

    private boolean isCurrentUser(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        if (currentUsername == null || currentUsername.isBlank()) {
            return false;
        }
        return username.trim().equalsIgnoreCase(currentUsername.trim());
    }

    private String extractUsernameFromDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }

        Matcher matcher = USERNAME_IN_PARENS_AT_END.matcher(displayName);
        if (!matcher.find()) {
            return null;
        }

        String extracted = matcher.group(1).trim();
        return extracted.isEmpty() ? null : extracted;
    }

    private String formatMessageMeta(String time, String displayName) {
        String safeTime = time == null ? "" : time.trim();
        String safeName = displayName == null ? "" : displayName.trim();

        if (!safeTime.isEmpty() && !safeName.isEmpty()) {
            return safeTime + "  " + safeName;
        }
        if (!safeTime.isEmpty()) {
            return safeTime;
        }
        if (!safeName.isEmpty()) {
            return safeName;
        }
        return null;
    }

    // Append tin nhan vao phong chat chung. Uu tien render bang JavaFX (FxChatView) de co animation.
    private void appendGroupText(FxChatView.Side side, String metaText, String message, String username) {
        if (fxGroupView != null) {
            fxGroupView.appendText(side, metaText, message, username);
            return;
        }

        // Fallback ve Swing bubble (neu JavaFX chua duoc khoi tao).
        MessageSide swingSide = MessageSide.SYSTEM;
        if (side == FxChatView.Side.OUTGOING) {
            swingSide = MessageSide.OUTGOING;
        } else if (side == FxChatView.Side.INCOMING) {
            swingSide = MessageSide.INCOMING;
        }
        // Note: JavaFX view hien tai chua ho tro nut Reply, fallback Swing se co
        appendTextBubble(chatMessagesPanel, chatMessagesScrollPane, swingSide, metaText, message, new JComponent[0]);
    }

    private void appendGroupText(FxChatView.Side side, String metaText, String message) {
        appendGroupText(side, metaText, message, null);
    }
    

    private void appendGroupLine(String rawLine) {
        String line = rawLine == null ? "" : rawLine.trim();
        if (line.isEmpty()) {
            return;
        }

        // Tin nhan he thong hoac loi local (khong co format "[time] name: msg").
        if (line.startsWith("[He thong]")) {
            appendGroupText(FxChatView.Side.SYSTEM, null, line);
            return;
        }

        Matcher timeMatch = CHAT_LINE_WITH_TIME.matcher(line);
        if (timeMatch.matches()) {
            String time = timeMatch.group(1).trim();
            String rest = timeMatch.group(2) == null ? "" : timeMatch.group(2).trim();

            if (rest.startsWith("[He thong]")) {
                appendGroupText(FxChatView.Side.SYSTEM, null, "[" + time + "] " + rest);
                return;
            }

            int sep = rest.indexOf(": ");
            if (sep > 0 && sep + 2 <= rest.length()) {
                String display = rest.substring(0, sep).trim();
                String message = rest.substring(sep + 2).trim();
                if (message.isEmpty()) {
                    return;
                }

                String username = extractUsernameFromDisplayName(display);
                if (username != null) {
                    clearGroupTypingUser(username);
                }
                boolean outgoing = isCurrentUser(username);
                FxChatView.Side side = outgoing ? FxChatView.Side.OUTGOING : FxChatView.Side.INCOMING;
                String meta = outgoing ? formatMessageMeta(time, null) : formatMessageMeta(time, display);
                // Pass username de FxChatView tim avatar
                appendGroupText(side, meta, message, username);
                return;
            }
        }

        // Fallback: hien thi nhu thong bao he thong.
        appendGroupText(FxChatView.Side.SYSTEM, null, line);
    }

    private JLabel createMetaLabel(String metaText, MessageSide side) {
        JLabel label = new JLabel(metaText == null ? "" : metaText);
        label.setFont(Theme.fontPlain(11));
        label.setForeground(Theme.MUTED_TEXT);
        label.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));

        if (side == MessageSide.OUTGOING) {
            label.setHorizontalAlignment(SwingConstants.RIGHT);
            label.setAlignmentX(Component.RIGHT_ALIGNMENT);
        } else if (side == MessageSide.INCOMING) {
            label.setHorizontalAlignment(SwingConstants.LEFT);
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
        } else {
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setAlignmentX(Component.CENTER_ALIGNMENT);
        }

        return label;
    }

    private BubblePanel createBubblePanel(MessageSide side) {
        if (side == MessageSide.OUTGOING) {
            return new BubblePanel(Theme.ACCENT_DARK, null, CHAT_BUBBLE_RADIUS);
        }
        if (side == MessageSide.INCOMING) {
            return new BubblePanel(new Color(0xE5E7EB), null, CHAT_BUBBLE_RADIUS);
        }
        // SYSTEM
        return new BubblePanel(Theme.SURFACE, Theme.BORDER, CHAT_BUBBLE_RADIUS);
    }

    private Color textColorFor(MessageSide side) {
        if (side == MessageSide.OUTGOING) {
            return Color.WHITE;
        }
        if (side == MessageSide.INCOMING) {
            return Theme.TEXT;
        }
        return Theme.MUTED_TEXT;
    }

    private JTextArea createBubbleTextArea(String text, MessageSide side, float fontSize) {
        JTextArea area = new JTextArea(text == null ? "" : text);
        area.setFont(Theme.fontPlain(fontSize));
        area.setForeground(textColorFor(side));
        area.setOpaque(false);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(null);
        area.setFocusable(false);
        area.setColumns(26);
        return area;
    }

    // Cap nhat method de nhan vao thong tin reply
    private JPanel buildMessageRow(MessageSide side, String metaText, JComponent messageContent, 
                                   String rawMessageContent, String senderName, Runnable onReply, 
                                   JComponent... extras) {
        MessageRowPanel row = new MessageRowPanel();
        row.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));

        if (metaText != null && !metaText.isBlank()) {
            JLabel meta = createMetaLabel(metaText, side);
            stack.add(meta);
            stack.add(Box.createVerticalStrut(4));
        }

        // Tao container ngang de chua Bubble + Nut Options (3 cham)
        JPanel bubbleRow = new JPanel(new FlowLayout(
                side == MessageSide.OUTGOING ? FlowLayout.RIGHT : 
                side == MessageSide.INCOMING ? FlowLayout.LEFT : FlowLayout.CENTER, 
                0, 0));
        bubbleRow.setOpaque(false);
        
        // Nut 3 cham (Option Menu)
        JButton optionsBtn = null;
        if (side != MessageSide.SYSTEM) {
            optionsBtn = new ModernButton("⋮", ButtonVariant.GHOST);
            optionsBtn.setFont(Theme.fontBold(16));
            optionsBtn.setForeground(Theme.MUTED_TEXT);
            optionsBtn.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            optionsBtn.setToolTipText("Tùy chọn");
            
            // Tao Popup Menu
            JPopupMenu popup = new JPopupMenu();
            
            // Menu Item: Tra loi
            JMenuItem replyItem = new JMenuItem("Trả lời");
            replyItem.addActionListener(e -> {
                if (onReply != null) onReply.run();
            });
            
            // Menu Item: Copy
            JMenuItem copyItem = new JMenuItem("Copy");
            copyItem.addActionListener(e -> {
                if (rawMessageContent != null) {
                    StringSelection selection = new StringSelection(rawMessageContent);
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
                }
            });

            // Menu Item: Xoa (Client side visual only)
            JMenuItem deleteItem = new JMenuItem("Xóa (ở phía tôi)");
            deleteItem.addActionListener(e -> {
                 row.setVisible(false);
                 row.removeAll();
            });

            popup.add(replyItem);
            popup.add(copyItem);
            popup.addSeparator();
            popup.add(deleteItem);
            
            final JButton btnRef = optionsBtn;
            optionsBtn.addActionListener(e -> popup.show(btnRef, 0, btnRef.getHeight()));
        }

        // Sap xep vi tri Bubble va Nut tuy theo Side
        if (side == MessageSide.OUTGOING) {
            if (optionsBtn != null) bubbleRow.add(optionsBtn);
            bubbleRow.add(messageContent);
        } else {
            bubbleRow.add(messageContent);
            if (optionsBtn != null) bubbleRow.add(optionsBtn);
        }

        // Canh chinh cho stack
        bubbleRow.setAlignmentX(side == MessageSide.OUTGOING
                ? Component.RIGHT_ALIGNMENT
                : side == MessageSide.INCOMING ? Component.LEFT_ALIGNMENT : Component.CENTER_ALIGNMENT);
        
        stack.add(bubbleRow);

        if (extras != null) {
            for (JComponent extra : extras) {
                if (extra == null) {
                    continue;
                }
                if (side == MessageSide.OUTGOING) {
                    extra.setAlignmentX(Component.RIGHT_ALIGNMENT);
                } else if (side == MessageSide.INCOMING) {
                    extra.setAlignmentX(Component.LEFT_ALIGNMENT);
                } else {
                    extra.setAlignmentX(Component.CENTER_ALIGNMENT);
                }
                stack.add(extra);
            }
        }

        if (side == MessageSide.OUTGOING) {
            row.add(stack, BorderLayout.EAST);
        } else if (side == MessageSide.INCOMING) {
            JPanel container = new JPanel(new BorderLayout());
            container.setOpaque(false);

            ImageIcon avatarToUse = defaultAvatarIcon;
            
            // Thu lay avatar tu cache dua tren senderName (thuong la "Ten (username)")
            // Hoac neu senderName chinh la username (trong PrivateChatTab)
            String userKey = extractUsernameFromDisplayName(senderName);
            if (userKey == null) userKey = senderName; // Fallback cho private chat
            
            if (userKey != null && avatarCache.containsKey(userKey.toLowerCase())) {
                avatarToUse = avatarCache.get(userKey.toLowerCase());
            }

            if (avatarToUse != null) {
                JLabel avatarLabel = new JLabel(avatarToUse);
                avatarLabel.setVerticalAlignment(SwingConstants.TOP);
                avatarLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
                container.add(avatarLabel, BorderLayout.WEST);
            }
            container.add(stack, BorderLayout.CENTER);
            row.add(container, BorderLayout.WEST);
        } else {
            row.add(stack, BorderLayout.CENTER);
        }

        return row;
    }

    private void appendRow(JPanel listPanel, JScrollPane scrollPane, JPanel row, boolean forceScroll) {
        if (listPanel == null || row == null) {
            return;
        }

        boolean pinnedToBottom = forceScroll || isScrollNearBottom(scrollPane);

        AnimatedRowPanel animated = null;
        Component toAdd = row;
        if (MESSAGE_ANIM_DURATION_MS > 0) {
            animated = new AnimatedRowPanel(row, MESSAGE_ANIM_DURATION_MS, MESSAGE_ANIM_SLIDE_PX, 0);
            toAdd = animated;
        }

        listPanel.add(toAdd);
        listPanel.add(Box.createVerticalStrut(8));
        listPanel.revalidate();
        listPanel.repaint();

        if (pinnedToBottom) {
            scrollToBottom(scrollPane);
        }

        if (animated != null) {
            boolean shouldScrollOnFinish = pinnedToBottom;
            AnimatedRowPanel runner = animated;
            runner.play(() -> {
                if (shouldScrollOnFinish) {
                    scrollToBottom(scrollPane);
                }
            });
        }
    }

    private boolean isScrollNearBottom(JScrollPane scrollPane) {
        if (scrollPane == null) {
            return true;
        }

        JScrollBar bar = scrollPane.getVerticalScrollBar();
        if (bar == null) {
            return true;
        }

        int value = bar.getValue();
        int extent = bar.getModel().getExtent();
        int max = bar.getMaximum();
        return value + extent >= max - 24;
    }

    private void scrollToBottom(JScrollPane scrollPane) {
        if (scrollPane == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = scrollPane.getVerticalScrollBar();
            if (bar != null) {
                bar.setValue(bar.getMaximum());
            }
        });
    }

    private void appendTextBubble(JPanel listPanel, JScrollPane scrollPane, MessageSide side, String metaText, String message,
            JComponent... extras) {
        String text = message == null ? "" : message.trim();
        if (text.isEmpty() || listPanel == null) {
            return;
        }

        Runnable task = () -> {
            boolean forceScroll = (side == MessageSide.OUTGOING);
            JComponent messageContent = parseAndBuildMessageBubble(text, side);

            // Xac dinh ten nguoi gui va action reply
            String senderName = "Unknown";
            Runnable replyAction = null;
            
            if (metaText != null && !metaText.isBlank()) {
                 // Meta thuong co dang "[Time] Name" hoac "Name"
                 senderName = metaText; // Don gian hoa, lay ca string
            }
            
            // Check xem dang o tab rieng hay chung de gan action reply phu hop
            if (side != MessageSide.SYSTEM) {
                final String finalName = senderName;
                replyAction = () -> {
                    // Mac dinh fallback ve group reply, PrivateTab se override bang cach tu goi buildRow rieng hoac 
                    // ta se xu ly trong PrivateChatTab.append...
                    setGroupReply(finalName, text);
                };
            }

            JPanel row = buildMessageRow(side, metaText, messageContent, text, senderName, replyAction, extras);
            appendRow(listPanel, scrollPane, row, forceScroll);
        };

        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    // Ham helper de tao bong bong chat (hoac container reply) cho Swing
    private JComponent parseAndBuildMessageBubble(String text, MessageSide side) {
        String startMarker = "RUN_REPLY_BLOCK";
        String endMarker = "END_REPLY_BLOCK";
        
        int start = text.indexOf(startMarker);
        int end = text.indexOf(endMarker);
        
        if (start >= 0 && end > start) {
            String quote = text.substring(start + startMarker.length(), end).trim();
            String reply = text.substring(end + endMarker.length()).trim();
            
            JPanel container = new JPanel();
            container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
            container.setOpaque(false);
            
            // Quote Bubble (White background, Gray text)
            BubblePanel quoteBubble = new BubblePanel(Color.WHITE, new Color(0xD6DCE6), 12); // Radius 12
            quoteBubble.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            JTextArea quoteArea = createBubbleTextArea(quote, side, 12);
            quoteArea.setForeground(Color.GRAY);
            quoteBubble.add(quoteArea, BorderLayout.CENTER);
            // Align left relative to the container stack
            quoteBubble.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            // Reply Bubble (Standard style)
            BubblePanel replyBubble = createBubblePanel(side);
            replyBubble.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
            replyBubble.add(createBubbleTextArea(reply, side, 13), BorderLayout.CENTER);
            replyBubble.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            container.add(quoteBubble);
            container.add(Box.createVerticalStrut(4)); // Spacing
            container.add(replyBubble);
            
            return container;
        } else {
             // Normal message
             BubblePanel bubble = createBubblePanel(side);
             bubble.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
             bubble.add(createBubbleTextArea(text, side, 13), BorderLayout.CENTER);
             return bubble;
        }
    }

    private void appendImageBubble(JPanel listPanel, JScrollPane scrollPane, MessageSide side, String metaText, String caption,
            ImageIcon icon) {
        if (icon == null || listPanel == null) {
            return;
        }

        Runnable task = () -> {
            boolean forceScroll = (side == MessageSide.OUTGOING);
            BubblePanel bubble = createBubblePanel(side);
            bubble.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

            JPanel content = new JPanel();
            content.setOpaque(false);
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

            String safeCaption = caption == null ? "" : caption.trim();
            if (!safeCaption.isEmpty()) {
                JTextArea captionArea = createBubbleTextArea(safeCaption, side, 12);
                captionArea.setColumns(22);
                captionArea.setAlignmentX(Component.LEFT_ALIGNMENT);
                content.add(captionArea);
                content.add(Box.createVerticalStrut(8));
            }

            JLabel imageLabel = new JLabel(icon);
            imageLabel.setOpaque(false);
            imageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(imageLabel);

            bubble.add(content, BorderLayout.CENTER);
            bubble.setMaximumSize(new Dimension(Math.min(CHAT_BUBBLE_MAX_WIDTH, CHAT_IMAGE_MAX_WIDTH + 64), Integer.MAX_VALUE));

            JPanel row = buildMessageRow(side, metaText, bubble, "[Hình ảnh]", "User", null); // Tam thoi null action cho anh
            appendRow(listPanel, scrollPane, row, forceScroll);
        };

        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private boolean parseTypingState(String value) {
        if (value == null) {
            return false;
        }

        String trimmed = value.trim();
        return "1".equals(trimmed) || "true".equalsIgnoreCase(trimmed) || "start".equalsIgnoreCase(trimmed);
    }

    private void handleGroupTypingInputChanged() {
        String text = inputField.getText();
        boolean hasText = text != null && !text.trim().isEmpty();

        if (!hasText) {
            stopGroupTyping(true);
            return;
        }

        boolean canSend;
        synchronized (connectionLock) {
            canSend = writer != null;
        }

        if (!authenticated || !canSend) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!groupTypingActive || now - groupTypingLastSentAt >= TYPING_REFRESH_MS) {
            sendProtocolMessage("TYPING", "1");
            groupTypingActive = true;
            groupTypingLastSentAt = now;
        }

        if (groupTypingIdleTimer == null) {
            groupTypingIdleTimer = new Timer(TYPING_IDLE_DELAY_MS, event -> stopGroupTyping(true));
            groupTypingIdleTimer.setRepeats(false);
        }
        groupTypingIdleTimer.restart();
    }

    private void stopGroupTyping(boolean sendStop) {
        if (groupTypingIdleTimer != null) {
            groupTypingIdleTimer.stop();
        }

        if (!groupTypingActive) {
            return;
        }

        groupTypingActive = false;
        groupTypingLastSentAt = 0;

        if (!sendStop) {
            return;
        }

        boolean canSend;
        synchronized (connectionLock) {
            canSend = writer != null;
        }

        if (!authenticated || !canSend) {
            return;
        }

        sendProtocolMessage("TYPING", "0");
    }

    private void resetGroupTypingIndicators() {
        Runnable task = () -> {
            groupTypingUsers.clear();
            typingStatusLabel.setVisible(false);
            typingStatusLabel.setText(" ");

            if (groupTypingSweepTimer != null) {
                groupTypingSweepTimer.stop();
            }
            if (groupTypingIdleTimer != null) {
                groupTypingIdleTimer.stop();
            }

            groupTypingActive = false;
            groupTypingLastSentAt = 0;
        };

        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private void clearGroupTypingUser(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            if (groupTypingUsers.remove(normalized) != null) {
                updateGroupTypingLabel();
            }
        });
    }

    private void handleIncomingGroupTyping(String fromUsername, String fromDisplayName, String state) {
        String normalized = fromUsername == null ? "" : fromUsername.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return;
        }

        if (isCurrentUser(normalized)) {
            return;
        }

        boolean typing = parseTypingState(state);
        String displayName = fromDisplayName == null || fromDisplayName.isBlank() ? normalized : fromDisplayName.trim();
        long now = System.currentTimeMillis();

        SwingUtilities.invokeLater(() -> {
            if (typing) {
                groupTypingUsers.put(normalized, new TypingEntry(displayName, now));
            } else {
                groupTypingUsers.remove(normalized);
            }
            updateGroupTypingLabel();
        });
    }

    private void handleIncomingPrivateTyping(String fromUsername, String fromDisplayName, String state) {
        String normalized = fromUsername == null ? "" : fromUsername.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return;
        }

        if (isCurrentUser(normalized)) {
            return;
        }

        boolean typing = parseTypingState(state);
        String displayName = fromDisplayName == null || fromDisplayName.isBlank() ? normalized : fromDisplayName.trim();

        SwingUtilities.invokeLater(() -> {
            PrivateChatTab tab = privateChatTabs.get(normalized);
            if (tab != null) {
                tab.updatePeerDisplayName(displayName);
                tab.setPeerTyping(typing, displayName);
            }
        });
    }

    private void pruneGroupTypingUsers() {
        long now = System.currentTimeMillis();
        groupTypingUsers.entrySet().removeIf(entry -> now - entry.getValue().lastSeenMs > TYPING_TTL_MS);
    }

    private void updateGroupTypingLabel() {
        pruneGroupTypingUsers();

        if (groupTypingUsers.isEmpty()) {
            typingStatusLabel.setVisible(false);
            typingStatusLabel.setText(" ");
            if (groupTypingSweepTimer != null) {
                groupTypingSweepTimer.stop();
            }
            return;
        }

        typingStatusLabel.setVisible(true);
        typingStatusLabel.setText(buildGroupTypingLabelText());

        if (groupTypingSweepTimer == null) {
            groupTypingSweepTimer = new Timer(TYPING_SWEEP_MS, event -> updateGroupTypingLabel());
            groupTypingSweepTimer.setRepeats(true);
        }
        if (!groupTypingSweepTimer.isRunning()) {
            groupTypingSweepTimer.start();
        }
    }

    private String buildGroupTypingLabelText() {
        int count = groupTypingUsers.size();
        if (count <= 0) {
            return " ";
        }

        if (count == 1) {
            TypingEntry entry = groupTypingUsers.values().iterator().next();
            String name = entry == null ? "Someone" : entry.displayName;
            return name + " Đoạn soạn tin...";
        }

        int shown = 0;
        StringBuilder sb = new StringBuilder(64);
        for (TypingEntry entry : groupTypingUsers.values()) {
            if (shown >= 2) {
                break;
            }
            if (shown > 0) {
                sb.append(", ");
            }
            sb.append(entry == null ? "Someone" : entry.displayName);
            shown++;
        }

        if (count <= shown) {
            sb.append(" Đoạn soạn tin...");
            return sb.toString();
        }

        sb.append(" va ").append(count - shown).append(" nguoi khac dang soan tin...");
        return sb.toString();
    }

    // Hoi nguoi dung co muon luu file nhan duoc khong, neu co thi mo Save dialog.
    private void promptToSaveReceivedFile(String fromDisplayName, String fileName, byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0) {
            return;
        }

        final String safeFrom = fromDisplayName == null || fromDisplayName.isBlank() ? "Unknown" : fromDisplayName.trim();
        final String safeFileName = sanitizeFileName(fileName);
        final long sizeBytes = fileBytes.length;

        SwingUtilities.invokeLater(() -> {
            String message = safeFrom + " gui file:\n"
                    + safeFileName + "\n"
                    + "Dung luong: " + formatBytes(sizeBytes) + "\n\n"
                    + "Ban muon luu file nay khong?";

            int choice = JOptionPane.showConfirmDialog(
                    this,
                    message,
                    "Nhan file",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (choice != JOptionPane.YES_OPTION) {
                return;
            }

            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Luu file");
            chooser.setApproveButtonText("Luu");
            chooser.setSelectedFile(new File(safeFileName));

            int saveResult = chooser.showSaveDialog(this);
            if (saveResult != JFileChooser.APPROVE_OPTION) {
                return;
            }

            File target = chooser.getSelectedFile();
            if (target == null) {
                return;
            }

            Path targetPath = target.toPath();
            if (Files.exists(targetPath)) {
                int overwrite = JOptionPane.showConfirmDialog(
                        this,
                        "File da ton tai. Ban co muon ghi de khong?",
                        "Xac nhan",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);

                if (overwrite != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            Thread writerThread = new Thread(() -> {
                try {
                    Files.write(targetPath, fileBytes);
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            this,
                            "Da luu file: " + targetPath.toAbsolutePath(),
                            "Da luu",
                            JOptionPane.INFORMATION_MESSAGE));
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            this,
                            "Khong the luu file: " + ex.getMessage(),
                            "Loi",
                            JOptionPane.ERROR_MESSAGE));
                }
            }, "save-received-file");

            writerThread.setDaemon(true);
            writerThread.start();
        });
    }

    // Sanitize file name de hien thi/luu tren Windows (loai bo ky tu cam, khong cho path).
    private String sanitizeFileName(String value) {
        String name = value == null ? "" : value.trim();
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }

        if (name.isBlank()) {
            name = "file";
        }

        StringBuilder sb = new StringBuilder(name.length());
        String forbidden = "<>:\"/\\\\|?*";
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 32 || forbidden.indexOf(c) >= 0) {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }

        String sanitized = sb.toString();
        while (sanitized.endsWith(" ") || sanitized.endsWith(".")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }

        if (sanitized.isBlank()) {
            sanitized = "file";
        }

        if (sanitized.length() > 160) {
            sanitized = sanitized.substring(0, 160);
        }

        return sanitized;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.1f KB", kb);
        }

        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.ROOT, "%.1f MB", mb);
        }

        double gb = mb / 1024.0;
        return String.format(Locale.ROOT, "%.2f GB", gb);
    }

    private long parseLongOrDefault(String value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    // Doc du lieu tu form dang nhap, kiem tra co ban roi gui len server.
    private void submitLogin() {
        String username = loginUsernameField.getText().trim();
        String password = new String(loginPasswordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            updateAuthStatus("Hay nhap day du username va password.", Theme.DANGER);
            return;
        }

        authenticate("LOGIN", null, username, password);
    }

    // Doc du lieu tu form dang ky, kiem tra co ban roi gui len server.
    private void submitRegister() {
        String fullName = registerNameField.getText().trim().replaceAll("\\s+", " ");
        String username = registerUsernameField.getText().trim();
        String password = new String(registerPasswordField.getPassword());

        if (fullName.isEmpty() || username.isEmpty() || password.isEmpty()) {
            updateAuthStatus("Hay nhap day du ho ten, username va password.", Theme.DANGER);
            return;
        }

        if (fullName.length() < 2 || fullName.length() > 60) {
            updateAuthStatus("Ho ten phai dai tu 2 den 60 ky tu.", Theme.DANGER);
            return;
        }

        // Normalize text hien thi trong field cho giong validate o server.
        registerNameField.setText(fullName);

        authenticate("REGISTER", fullName, username, password);
    }

    // Thuc hien auth o background thread de giao dien Swing khong bi dung.
    private void authenticate(String command, String fullName, String username, String password) {
        setAuthControlsEnabled(false);
        updateAuthStatus("Dang ket noi toi server...", Theme.MUTED_TEXT);

        Thread authThread = new Thread(() -> {
            try {
                // Moi lan auth se tao ket noi moi de tranh trang thai cu con ton tai.
                openConnection();

                if ("REGISTER".equals(command)) {
                    sendProtocolMessage(command, fullName, username, password);
                } else {
                    sendProtocolMessage(command, username, password);
                }

                String responseLine = readResponseLine();
                ChatProtocol.Command response = ChatProtocol.decode(responseLine);

                if ("AUTH_OK".equals(response.name()) && response.hasFields(2)) {
                    currentFullName = response.field(0);
                    currentUsername = response.field(1);
                    authenticated = true;
                    manualDisconnect = false;
                    showChatState();
                    startListening();
                    return;
                }

                String message = extractResponseMessage(response, "Dang nhap that bai.");
                updateAuthStatus(message, Theme.DANGER);
                disconnectConnection();
            } catch (IOException ex) {
                updateAuthStatus("Khong the ket noi toi server: " + ex.getMessage(), Theme.DANGER);
                disconnectConnection();
            } catch (IllegalArgumentException ex) {
                updateAuthStatus(ex.getMessage(), Theme.DANGER);
                disconnectConnection();
            } finally {
                if (!authenticated) {
                    setAuthControlsEnabled(true);
                }
            }
        }, "auth-" + command.toLowerCase());

        authThread.setDaemon(true);
        authThread.start();
    }

    // Lay thong diep loi/phan hoi tu protocol neu server gui kem noi dung.
    private String extractResponseMessage(ChatProtocol.Command response, String fallback) {
        if (response.hasFields(1)) {
            return response.field(0);
        }
        return fallback;
    }

    // Chuyen tu man hinh auth sang man hinh chat va cap nhat thong tin tai khoan.
    private void showChatState() {
        SwingUtilities.invokeLater(() -> {
            clearGroupMessages();
            resetGroupTypingIndicators();
            onlineUsersModel.clear();
            clearPrivateChatTabs();
            accountLabel.setText("Dang nhap voi: " + currentFullName + " (" + currentUsername + ")");
            profileNameValue.setText(currentFullName);
            profileUsernameValue.setText("@" + currentUsername);
            chatStatusLabel.setText("Da ket noi toi " + host + ":" + port);
            chatStatusLabel.setForeground(Theme.SUCCESS);
            setTitle("Java Chat Box - " + currentFullName);
            setChatControlsEnabled(true);
            cardLayout.show(contentPanel, CHAT_CARD);
            chatTabs.setSelectedIndex(0);
            inputField.requestFocusInWindow();
        });
    }

    // Bat mot thread rieng de lang nghe tin nhan real-time tu server.
    private void startListening() {
        BufferedReader activeReader;
        synchronized (connectionLock) {
            activeReader = reader;
        }

        Thread listenerThread = new Thread(() -> listenForMessages(activeReader), "chat-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    // Vong lap doc protocol message cho den khi mat ket noi hoac nguoi dung thoat.
    private void listenForMessages(BufferedReader activeReader) {
        try {
            String line;
            while ((line = activeReader.readLine()) != null) {
                ChatProtocol.Command command = ChatProtocol.decode(line);
                if ("ONLINE".equals(command.name())) {
                    updateOnlineUsers(command.fieldsText());
                    continue;
                }

                if ("IMG".equals(command.name()) && command.hasFields(6)) {
                    handleIncomingGroupImage(
                            command.field(0),
                            command.field(1),
                            command.field(2),
                            command.field(3),
                            command.field(4),
                            command.fieldBytes(5));
                    continue;
                }

                if ("FILE".equals(command.name()) && command.hasFields(6)) {
                    handleIncomingGroupFile(
                            command.field(0),
                            command.field(1),
                            command.field(2),
                            command.field(3),
                            command.field(4),
                            command.fieldBytes(5));
                    continue;
                }

                if ("TYPING".equals(command.name()) && command.hasFields(3)) {
                    handleIncomingGroupTyping(command.field(0), command.field(1), command.field(2));
                    continue;
                }

                if ("PM".equals(command.name()) && command.hasFields(4)) {
                    handleIncomingPrivateMessage(command.field(0), command.field(1), command.field(2), command.field(3));
                    continue;
                }

                if ("PM_TYPING".equals(command.name()) && command.hasFields(3)) {
                    handleIncomingPrivateTyping(command.field(0), command.field(1), command.field(2));
                    continue;
                }

                if ("PM_FILE".equals(command.name()) && command.hasFields(5)) {
                    handleIncomingPrivateFile(
                            command.field(0),
                            command.field(1),
                            command.field(2),
                            command.field(3),
                            command.fieldBytes(4));
                    continue;
                }

                if ("PM_IMG".equals(command.name()) && command.hasFields(5)) {
                    handleIncomingPrivateImage(
                            command.field(0),
                            command.field(1),
                            command.field(2),
                            command.field(3),
                            command.fieldBytes(4));
                    continue;
                }

                if ("PM_SENT".equals(command.name()) && command.hasFields(4)) {
                    handleOutgoingPrivateMessage(command.field(0), command.field(1), command.field(2), command.field(3));
                    continue;
                }

                if ("PM_FILE_SENT".equals(command.name()) && command.hasFields(5)) {
                    handleOutgoingPrivateFile(
                            command.field(0),
                            command.field(1),
                            command.field(2),
                            command.field(3),
                            command.field(4));
                    continue;
                }

                if ("PM_IMG_SENT".equals(command.name()) && command.hasFields(5)) {
                    handleOutgoingPrivateImage(
                            command.field(0),
                            command.field(1),
                            command.field(2),
                            command.field(3),
                            command.fieldBytes(4));
                    continue;
                }

                if ("PM_READ".equals(command.name()) && command.hasFields(2)) {
                    handlePrivateMessageRead(command.field(0), command.field(1));
                    continue;
                }

                if ("PM_ERROR".equals(command.name()) && command.hasFields(2)) {
                    handlePrivateMessageError(command.field(0), command.field(1));
                    continue;
                }

                if ("CHAT".equals(command.name()) && command.hasFields(1)) {
                    appendMessage(command.field(0));
                    continue;
                }

                if ("INFO".equals(command.name()) && command.hasFields(1)) {
                    appendMessage("[He thong] " + command.field(0));
                }
                
                if ("USER_AVATAR".equals(command.name()) && command.hasFields(2)) {
                    updateLocalAvatarCache(command.field(0), command.fieldBytes(1));
                    continue;
                }
            }
        } catch (IOException ex) {
            if (authenticated && !manualDisconnect) {
                appendMessage("[He thong] Mat ket noi: " + ex.getMessage());
            }
        } catch (IllegalArgumentException ex) {
            if (authenticated && !manualDisconnect) {
                appendMessage("[He thong] Phan hoi server khong hop le.");
            }
        } finally {
            boolean shouldReset = authenticated && !manualDisconnect;
            authenticated = false;
            disconnectConnection();

            if (shouldReset) {
                resetToAuthState("Mat ket noi voi server. Hay dang nhap lai.", Theme.DANGER);
            }
        }
    }

    // Cap nhat danh sach online tren EDT (Swing thread).
    private void updateOnlineUsers(String[] users) {
        SwingUtilities.invokeLater(() -> {
            onlineUsersModel.clear();
            if (users == null) {
                updatePrivateChatButtonState();
                return;
            }

            for (String user : users) {
                String value = user == null ? "" : user.trim();
                if (!value.isEmpty()) {
                    onlineUsersModel.addElement(value);
                }
            }

            updatePrivateChatButtonState();
        });
    }

    // Anh gui trong phong chat chung: server se broadcast IMG toi tat ca client.
    private void handleIncomingGroupImage(String time, String fromUsername, String fromDisplayName, String fileName,
            String mimeType, byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return;
        }

        if (imageBytes.length > MAX_IMAGE_BYTES) {
            appendMessage("[He thong] Anh nhan duoc vuot qua gioi han.");
            return;
        }

        String safeTime = time == null ? "" : time.trim();
        String normalizedUsername = fromUsername == null ? "" : fromUsername.trim();
        String displayName = fromDisplayName == null || fromDisplayName.isBlank() ? normalizedUsername : fromDisplayName.trim();
        if (displayName.isBlank()) {
            displayName = "Unknown";
        }

        String safeFileName = fileName == null ? "" : fileName.trim();
        if (safeFileName.isEmpty()) {
            safeFileName = "image";
        }

        clearGroupTypingUser(normalizedUsername);
        boolean fromSelf = isCurrentUser(normalizedUsername);
        String metaText = formatMessageMeta(safeTime, fromSelf ? null : displayName);

        if (fxGroupView != null) {
            fxGroupView.appendImage(
                    fromSelf ? FxChatView.Side.OUTGOING : FxChatView.Side.INCOMING,
                    metaText,
                    "Gui anh: " + safeFileName,
                    imageBytes,
                    CHAT_IMAGE_MAX_WIDTH,
                    CHAT_IMAGE_MAX_HEIGHT,
                    normalizedUsername);
            return;
        }

        // Fallback Swing bubble (truong hop JavaFX khong san sang).
        ImageIcon icon;
        try {
            icon = createScaledImageIcon(imageBytes, CHAT_IMAGE_MAX_WIDTH, CHAT_IMAGE_MAX_HEIGHT);
        } catch (IOException ex) {
            appendMessage("[He thong] Khong the hien thi anh tu " + displayName + ".");
            return;
        }

        appendImageBubble(chatMessagesPanel, chatMessagesScrollPane,
                fromSelf ? MessageSide.OUTGOING : MessageSide.INCOMING,
                metaText,
                "Gui anh: " + safeFileName,
                icon);
    }

    // File gui trong phong chat chung: server se broadcast FILE toi tat ca client.
    private void handleIncomingGroupFile(String time, String fromUsername, String fromDisplayName, String fileName,
            String mimeType, byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0) {
            return;
        }

        if (fileBytes.length > MAX_FILE_BYTES) {
            appendMessage("[He thong] File nhan duoc vuot qua gioi han.");
            return;
        }

        String safeTime = time == null ? "" : time.trim();
        String normalizedFrom = fromUsername == null ? "" : fromUsername.trim().toLowerCase(Locale.ROOT);
        String displayName = fromDisplayName == null || fromDisplayName.isBlank() ? normalizedFrom : fromDisplayName.trim();
        if (displayName.isBlank()) {
            displayName = "Unknown";
        }

        String safeFileName = fileName == null ? "" : fileName.trim();
        if (safeFileName.isEmpty()) {
            safeFileName = "file";
        }

        String sizeText = formatBytes(fileBytes.length);

        boolean fromSelf = currentUsername != null
                && !currentUsername.isBlank()
                && normalizedFrom.equals(currentUsername.trim().toLowerCase(Locale.ROOT));

        clearGroupTypingUser(normalizedFrom);
        String messageText = "Gui file: " + safeFileName + " (" + sizeText + ")";
        if (fromSelf) {
            appendGroupText(FxChatView.Side.OUTGOING, formatMessageMeta(safeTime, null), messageText, normalizedFrom);
            return;
        }

        appendGroupText(FxChatView.Side.INCOMING, formatMessageMeta(safeTime, displayName), messageText, normalizedFrom);
        promptToSaveReceivedFile(displayName, safeFileName, fileBytes);
    }

    // Tin nhan rieng (PM) tu nguoi khac gui toi.
    private void handleIncomingPrivateMessage(String fromUsername, String fromDisplayName, String messageId, String message) {
        String normalizedUsername = fromUsername == null ? "" : fromUsername.trim().toLowerCase(Locale.ROOT);
        if (normalizedUsername.isEmpty()) {
            appendMessage("[He thong] Tin nhan rieng khong hop le.");
            return;
        }

        String displayName = fromDisplayName == null || fromDisplayName.isBlank() ? normalizedUsername : fromDisplayName.trim();
        String text = message == null ? "" : message.trim();
        if (text.isEmpty()) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            PrivateChatTab tab = getOrCreatePrivateChatTab(normalizedUsername, displayName, false);
            if (tab != null) {
                tab.appendIncoming(displayName, messageId, text);
            }
        });
    }

    // File gui rieng (PM_FILE) tu nguoi khac gui toi.
    private void handleIncomingPrivateFile(String fromUsername, String fromDisplayName, String fileName, String mimeType,
            byte[] fileBytes) {
        String normalizedUsername = fromUsername == null ? "" : fromUsername.trim().toLowerCase(Locale.ROOT);
        if (normalizedUsername.isEmpty()) {
            appendMessage("[He thong] File rieng khong hop le.");
            return;
        }

        if (fileBytes == null || fileBytes.length == 0) {
            return;
        }

        if (fileBytes.length > MAX_FILE_BYTES) {
            handlePrivateMessageError(normalizedUsername, "File vuot qua gioi han.");
            return;
        }

        final String displayName = fromDisplayName == null || fromDisplayName.isBlank()
                ? normalizedUsername
                : fromDisplayName.trim();
        final String safeFileName = fileName == null || fileName.trim().isEmpty()
                ? "file"
                : fileName.trim();
        final long sizeBytes = fileBytes.length;

        SwingUtilities.invokeLater(() -> {
            PrivateChatTab tab = getOrCreatePrivateChatTab(normalizedUsername, displayName, false);
            if (tab != null) {
                tab.appendIncomingFile(displayName, safeFileName, sizeBytes);
            }
        });

        promptToSaveReceivedFile(displayName, safeFileName, fileBytes);
    }

    // Anh gui rieng (PM_IMG) tu nguoi khac gui toi.
    private void handleIncomingPrivateImage(String fromUsername, String fromDisplayName, String fileName, String mimeType,
            byte[] imageBytes) {
        String normalizedUsername = fromUsername == null ? "" : fromUsername.trim().toLowerCase(Locale.ROOT);
        if (normalizedUsername.isEmpty()) {
            appendMessage("[He thong] Anh rieng khong hop le.");
            return;
        }

        if (imageBytes == null || imageBytes.length == 0) {
            return;
        }

        if (imageBytes.length > MAX_IMAGE_BYTES) {
            handlePrivateMessageError(normalizedUsername, "Anh vuot qua gioi han.");
            return;
        }

        final String displayName = fromDisplayName == null || fromDisplayName.isBlank()
                ? normalizedUsername
                : fromDisplayName.trim();
        final String safeFileName = fileName == null || fileName.trim().isEmpty()
                ? "image"
                : fileName.trim();

        ImageIcon icon;
        try {
            icon = createScaledImageIcon(imageBytes, CHAT_IMAGE_MAX_WIDTH, CHAT_IMAGE_MAX_HEIGHT);
        } catch (IOException ex) {
            handlePrivateMessageError(normalizedUsername, "Khong the hien thi anh.");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            PrivateChatTab tab = getOrCreatePrivateChatTab(normalizedUsername, displayName, false);
            if (tab != null) {
                tab.appendIncomingImage(displayName, safeFileName, icon);
            }
        });
    }

    // Xac nhan tu server: PM cua minh da duoc gui thanh cong.
    private void handleOutgoingPrivateMessage(String toUsername, String toDisplayName, String messageId, String message) {
        String normalizedUsername = toUsername == null ? "" : toUsername.trim().toLowerCase(Locale.ROOT);
        if (normalizedUsername.isEmpty()) {
            return;
        }

        String displayName = toDisplayName == null || toDisplayName.isBlank() ? normalizedUsername : toDisplayName.trim();
        String text = message == null ? "" : message.trim();
        if (text.isEmpty()) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            PrivateChatTab tab = getOrCreatePrivateChatTab(normalizedUsername, displayName, false);
            if (tab != null) {
                tab.appendOutgoing(displayName, messageId, text);
            }
        });
    }

    // Xac nhan tu server: file PM cua minh da duoc gui thanh cong (PM_FILE_SENT).
    private void handleOutgoingPrivateFile(String toUsername, String toDisplayName, String fileName, String mimeType,
            String sizeText) {
        String normalizedUsername = toUsername == null ? "" : toUsername.trim().toLowerCase(Locale.ROOT);
        if (normalizedUsername.isEmpty()) {
            return;
        }

        final String displayName = toDisplayName == null || toDisplayName.isBlank()
                ? normalizedUsername
                : toDisplayName.trim();
        final String safeFileName = fileName == null || fileName.trim().isEmpty()
                ? "file"
                : fileName.trim();
        final long sizeBytes = parseLongOrDefault(sizeText, -1);

        SwingUtilities.invokeLater(() -> {
            PrivateChatTab tab = getOrCreatePrivateChatTab(normalizedUsername, displayName, false);
            if (tab != null) {
                tab.appendOutgoingFile(displayName, safeFileName, sizeBytes);
            }
        });
    }

    // Xac nhan tu server: anh PM cua minh da duoc gui thanh cong (PM_IMG_SENT).
    private void handleOutgoingPrivateImage(String toUsername, String toDisplayName, String fileName, String mimeType,
            byte[] imageBytes) {
        String normalizedUsername = toUsername == null ? "" : toUsername.trim().toLowerCase(Locale.ROOT);
        if (normalizedUsername.isEmpty()) {
            return;
        }

        if (imageBytes == null || imageBytes.length == 0) {
            return;
        }

        if (imageBytes.length > MAX_IMAGE_BYTES) {
            handlePrivateMessageError(normalizedUsername, "Anh vuot qua gioi han.");
            return;
        }

        final String displayName = toDisplayName == null || toDisplayName.isBlank()
                ? normalizedUsername
                : toDisplayName.trim();
        final String safeFileName = fileName == null || fileName.trim().isEmpty()
                ? "image"
                : fileName.trim();

        ImageIcon icon;
        try {
            icon = createScaledImageIcon(imageBytes, CHAT_IMAGE_MAX_WIDTH, CHAT_IMAGE_MAX_HEIGHT);
        } catch (IOException ex) {
            handlePrivateMessageError(normalizedUsername, "Khong the hien thi anh.");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            PrivateChatTab tab = getOrCreatePrivateChatTab(normalizedUsername, displayName, false);
            if (tab != null) {
                tab.appendOutgoingImage(displayName, safeFileName, icon);
            }
        });
    }

    // Server thong bao tin nhan cua minh da duoc doc.
    private void handlePrivateMessageRead(String byUsername, String messageId) {
        String normalizedUsername = byUsername == null ? "" : byUsername.trim().toLowerCase(Locale.ROOT);
        if (normalizedUsername.isEmpty() || messageId == null || messageId.isBlank()) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            // Find the tab corresponding to the user who read the message
            PrivateChatTab tab = privateChatTabs.get(normalizedUsername);
            if (tab != null) {
                tab.markAsRead(messageId);
            }
        });
    }

    // Loi khi gui PM (nguoi nhan offline, sai du lieu, ...).
    private void handlePrivateMessageError(String toUsername, String errorMessage) {
        String normalizedUsername = toUsername == null ? "" : toUsername.trim().toLowerCase(Locale.ROOT);
        String text = errorMessage == null ? "" : errorMessage.trim();
        String messageText = text.isEmpty() ? "Gui tin nhan rieng that bai." : text;

        if (normalizedUsername.isEmpty()) {
            appendMessage("[He thong] " + messageText);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            PrivateChatTab tab = privateChatTabs.get(normalizedUsername);
            if (tab != null) {
                tab.appendSystem(messageText);
                return;
            }

            appendMessage("[He thong] PM toi " + normalizedUsername + ": " + messageText);
        });
    }

    // Gui tin nhan chat hien tai len server neu nguoi dung da dang nhap.
    private void sendCurrentMessage() {
        String message = inputField.getText().trim();
        if (message.isEmpty()) {
            return;
        }

        if (!authenticated) {
            appendMessage("[He thong] Ban can dang nhap truoc.");
            return;
        }

        // Neu dang reply, dinh kem context vao tin nhan
        String finalMessage = message;
        if (groupReplyContext != null) {
            finalMessage = "RUN_REPLY_BLOCK\n" + groupReplyContext + "\nEND_REPLY_BLOCK\n" + message;
            clearGroupReply();
        }

        sendProtocolMessage("MSG", finalMessage);
        stopGroupTyping(true);
        inputField.setText("");
        inputField.requestFocusInWindow();
    }

    // Mo file chooser de chon anh va gui vao phong chat chung.
    private void chooseAndSendGroupImage() {
        if (!authenticated) {
            appendMessage("[He thong] Ban can dang nhap truoc.");
            return;
        }

        Path imagePath = promptForImagePath();
        if (imagePath == null) {
            return;
        }

        Thread sender = new Thread(() -> sendImageToServer(null, imagePath), "send-group-image");
        sender.setDaemon(true);
        sender.start();
    }

    // Mo file chooser de chon anh va gui rieng toi 1 user (PM_IMG).
    private void chooseAndSendPrivateImage(String toUsername) {
        if (!authenticated) {
            appendMessage("[He thong] Ban can dang nhap truoc.");
            return;
        }

        String normalized = toUsername == null ? "" : toUsername.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            appendMessage("[He thong] Khong tim thay nguoi nhan.");
            return;
        }

        Path imagePath = promptForImagePath();
        if (imagePath == null) {
            return;
        }

        Thread sender = new Thread(() -> sendImageToServer(normalized, imagePath), "send-private-image-" + normalized);
        sender.setDaemon(true);
        sender.start();
    }

    // Hien JFileChooser de nguoi dung chon file anh.
    private Path promptForImagePath() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chon anh de gui");
        chooser.setApproveButtonText("Gui");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(false);
        chooser.setAcceptAllFileFilterUsed(true);
        chooser.setFileFilter(new FileNameExtensionFilter("Image files (*.png, *.jpg, *.jpeg, *.gif, *.bmp)",
                "png", "jpg", "jpeg", "gif", "bmp"));

        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        File selectedFile = chooser.getSelectedFile();
        return selectedFile == null ? null : selectedFile.toPath();
    }

    // Doc file anh, validate co ban, sau do gui len server. toUsername = null => group IMG.
    private void sendImageToServer(String toUsername, Path imagePath) {
        if (imagePath == null) {
            return;
        }

        boolean canSend;
        synchronized (connectionLock) {
            canSend = writer != null;
        }

        if (!authenticated || !canSend) {
            appendMessage("[He thong] Chua ket noi toi server.");
            return;
        }

        String fileName = imagePath.getFileName() == null ? "image" : imagePath.getFileName().toString();
        if (fileName.isBlank()) {
            fileName = "image";
        }
        if (fileName.length() > 120) {
            fileName = fileName.substring(0, 120);
        }

        try {
            long fileSize = Files.size(imagePath);
            if (fileSize > MAX_IMAGE_BYTES) {
                appendMessage("[He thong] Anh vuot qua gioi han " + (MAX_IMAGE_BYTES / (1024 * 1024)) + "MB.");
                return;
            }
        } catch (IOException ex) {
            appendMessage("[He thong] Khong doc duoc thong tin file anh: " + ex.getMessage());
            return;
        }

        byte[] imageBytes;
        try {
            imageBytes = Files.readAllBytes(imagePath);
        } catch (IOException ex) {
            appendMessage("[He thong] Khong doc duoc anh: " + ex.getMessage());
            return;
        }

        if (imageBytes.length == 0) {
            appendMessage("[He thong] File anh rong.");
            return;
        }

        // Double check (race condition) neu file bi thay doi trong luc doc.
        if (imageBytes.length > MAX_IMAGE_BYTES) {
            appendMessage("[He thong] Anh vuot qua gioi han " + (MAX_IMAGE_BYTES / (1024 * 1024)) + "MB.");
            return;
        }

        // Kiem tra file co phai anh hop le khong (ImageIO.read tra ve null neu khong doc duoc).
        if (!isValidImageBytes(imageBytes)) {
            appendMessage("[He thong] File khong phai anh hop le (hoac dinh dang khong duoc ho tro).");
            return;
        }

        String mimeType = detectImageMimeType(imagePath, fileName);

        if (toUsername == null) {
            sendProtocolMessageBytes("IMG", utf8(fileName), utf8(mimeType), imageBytes);
        } else {
            sendProtocolMessageBytes("PM_IMG", utf8(toUsername), utf8(fileName), utf8(mimeType), imageBytes);
        }
    }

    // Mo file chooser de chon file bat ky va gui vao phong chat chung.
    private void chooseAndSendGroupFile() {
        if (!authenticated) {
            appendMessage("[He thong] Ban can dang nhap truoc.");
            return;
        }

        Path filePath = promptForFilePath();
        if (filePath == null) {
            return;
        }

        Thread sender = new Thread(() -> sendFileToServer(null, filePath), "send-group-file");
        sender.setDaemon(true);
        sender.start();
    }

    // Mo file chooser de chon file va gui rieng toi 1 user (PM_FILE).
    private void chooseAndSendPrivateFile(String toUsername) {
        if (!authenticated) {
            appendMessage("[He thong] Ban can dang nhap truoc.");
            return;
        }

        String normalized = toUsername == null ? "" : toUsername.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            appendMessage("[He thong] Khong tim thay nguoi nhan.");
            return;
        }

        Path filePath = promptForFilePath();
        if (filePath == null) {
            return;
        }

        Thread sender = new Thread(() -> sendFileToServer(normalized, filePath), "send-private-file-" + normalized);
        sender.setDaemon(true);
        sender.start();
    }

    // Hien JFileChooser de nguoi dung chon file bat ky.
    private Path promptForFilePath() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chon file de gui");
        chooser.setApproveButtonText("Gui");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(false);
        chooser.setAcceptAllFileFilterUsed(true);

        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        File selectedFile = chooser.getSelectedFile();
        return selectedFile == null ? null : selectedFile.toPath();
    }

    // Doc file va gui len server. toUsername = null => group FILE.
    private void sendFileToServer(String toUsername, Path filePath) {
        if (filePath == null) {
            return;
        }

        boolean canSend;
        synchronized (connectionLock) {
            canSend = writer != null;
        }

        if (!authenticated || !canSend) {
            appendMessage("[He thong] Chua ket noi toi server.");
            return;
        }

        String fileName = filePath.getFileName() == null ? "file" : filePath.getFileName().toString();
        if (fileName.isBlank()) {
            fileName = "file";
        }
        if (fileName.length() > 160) {
            fileName = fileName.substring(0, 160);
        }

        try {
            long fileSize = Files.size(filePath);
            if (fileSize > MAX_FILE_BYTES) {
                appendMessage("[He thong] File vuot qua gioi han " + (MAX_FILE_BYTES / (1024 * 1024)) + "MB.");
                return;
            }
        } catch (IOException ex) {
            appendMessage("[He thong] Khong doc duoc thong tin file: " + ex.getMessage());
            return;
        }

        byte[] fileBytes;
        try {
            fileBytes = Files.readAllBytes(filePath);
        } catch (IOException ex) {
            appendMessage("[He thong] Khong doc duoc file: " + ex.getMessage());
            return;
        }

        if (fileBytes.length == 0) {
            appendMessage("[He thong] File rong.");
            return;
        }

        if (fileBytes.length > MAX_FILE_BYTES) {
            appendMessage("[He thong] File vuot qua gioi han " + (MAX_FILE_BYTES / (1024 * 1024)) + "MB.");
            return;
        }

        String mimeType = detectFileMimeType(filePath, fileName);

        if (toUsername == null) {
            sendProtocolMessageBytes("FILE", utf8(fileName), utf8(mimeType), fileBytes);
        } else {
            sendProtocolMessageBytes("PM_FILE", utf8(toUsername), utf8(fileName), utf8(mimeType), fileBytes);
        }
    }

    private String detectFileMimeType(Path path, String fileName) {
        try {
            String probed = Files.probeContentType(path);
            if (probed != null && !probed.isBlank()) {
                return probed.trim();
            }
        } catch (IOException ignored) {
        }

        return "application/octet-stream";
    }

    private static byte[] utf8(String value) {
        return (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
    }

    private boolean isValidImageBytes(byte[] imageBytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(imageBytes)) != null;
        } catch (IOException ex) {
            return false;
        }
    }

    // Tao ImageIcon da scale de hien thi trong chat (giu ty le, gioi han kich thuoc).
    private ImageIcon createScaledImageIcon(byte[] imageBytes, int maxWidth, int maxHeight) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (image == null) {
            throw new IOException("Unsupported image");
        }
        BufferedImage scaled = scaleToFit(image, maxWidth, maxHeight);
        return new ImageIcon(scaled);
    }

    private BufferedImage scaleToFit(BufferedImage source, int maxWidth, int maxHeight) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= 0 || height <= 0) {
            return source;
        }

        double scaleW = maxWidth <= 0 ? 1.0 : (double) maxWidth / (double) width;
        double scaleH = maxHeight <= 0 ? 1.0 : (double) maxHeight / (double) height;
        double scale = Math.min(1.0, Math.min(scaleW, scaleH));

        if (scale >= 1.0) {
            return source;
        }

        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scaled.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        g2.dispose();

        return scaled;
    }

    private String detectImageMimeType(Path path, String fileName) {
        try {
            String probed = Files.probeContentType(path);
            if (probed != null && probed.toLowerCase(Locale.ROOT).startsWith("image/")) {
                return probed;
            }
        } catch (IOException ignored) {
        }

        String lower = (fileName == null ? "" : fileName).toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        return "application/octet-stream";
    }

    // Chu dong dong phien chat, bao server biet va dua UI ve man auth.
    private void logout() {
        manualDisconnect = true;
        authenticated = false;
        sendProtocolMessage("QUIT");
        disconnectConnection();
        resetToAuthState("Da dang xuat khoi phong chat.", Theme.SUCCESS);
    }

    // Cap nhat dong trang thai o man hinh auth tren EDT.
    private void updateAuthStatus(String message, Color color) {
        SwingUtilities.invokeLater(() -> {
            authStatusLabel.setText(message);
            authStatusLabel.setForeground(color);
        });
    }

    // Dua toan bo giao dien ve trang thai chua dang nhap.
    private void resetToAuthState(String message, Color color) {
        SwingUtilities.invokeLater(() -> {
            setTitle("Java Chat Box");
            authStatusLabel.setText(message);
            authStatusLabel.setForeground(color);
            chatStatusLabel.setText("Chua dang nhap.");
            chatStatusLabel.setForeground(Theme.MUTED_TEXT);
            accountLabel.setText("Chua co tai khoan dang nhap.");
            clearGroupMessages();
            resetGroupTypingIndicators();
            onlineUsersModel.clear();
            clearPrivateChatTabs();
            inputField.setText("");
            setChatControlsEnabled(false);
            setAuthControlsEnabled(true);
            cardLayout.show(contentPanel, AUTH_CARD);
        });
    }

    // Them mot dong van ban vao khung chat va tu dong cuon xuong cuoi.
    private void appendMessage(String message) {
        appendGroupLine(message);
    }

    // Khoa/mo cac control auth de tranh nguoi dung bam lap khi dang xu ly.
    private void setAuthControlsEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            loginUsernameField.setEnabled(enabled);
            loginPasswordField.setEnabled(enabled);
            loginButton.setEnabled(enabled);
            registerNameField.setEnabled(enabled);
            registerUsernameField.setEnabled(enabled);
            registerPasswordField.setEnabled(enabled);
            registerButton.setEnabled(enabled);
        });
    }

    // Khoa/mo o nhap chat va nut gui/dang xuat theo trang thai dang nhap.
    private void setChatControlsEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            inputField.setEnabled(enabled);
            sendButton.setEnabled(enabled);
            sendImageButton.setEnabled(enabled);
            sendFileButton.setEnabled(enabled);
            logoutButton.setEnabled(enabled);
            if (!enabled) {
                startPrivateChatButton.setEnabled(false);
            } else {
                updatePrivateChatButtonState();
            }
        });
    }

    // Tao ket noi socket moi toi server va gan reader/writer cho client.
    private void openConnection() throws IOException {
        disconnectConnection();

        Socket newSocket = new Socket();
        newSocket.connect(new InetSocketAddress(host, port), 5000);

        BufferedReader newReader = new BufferedReader(
                new InputStreamReader(newSocket.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter newWriter = new PrintWriter(
                new OutputStreamWriter(newSocket.getOutputStream(), StandardCharsets.UTF_8), true);

        synchronized (connectionLock) {
            socket = newSocket;
            reader = newReader;
            writer = newWriter;
        }
    }

    // Doc dong phan hoi dau tien cua server, thuong la AUTH_OK hoac AUTH_ERROR.
    private String readResponseLine() throws IOException {
        BufferedReader currentReader;
        synchronized (connectionLock) {
            currentReader = reader;
        }

        if (currentReader == null) {
            throw new IOException("Chua co ket noi toi server.");
        }

        String line = currentReader.readLine();
        if (line == null) {
            throw new IOException("Server dong ket noi.");
        }

        return line;
    }

    // Dong goi lenh theo protocol roi gui qua socket neu ket noi dang ton tai.
    private void sendProtocolMessage(String command, String... fields) {
        PrintWriter currentWriter;
        synchronized (connectionLock) {
            currentWriter = writer;
        }

        if (currentWriter != null) {
            currentWriter.println(ChatProtocol.encode(command, fields));
        }
    }

    // Gui protocol message co field dang byte[] (dung cho gui anh).
    private void sendProtocolMessageBytes(String command, byte[]... fields) {
        PrintWriter currentWriter;
        synchronized (connectionLock) {
            currentWriter = writer;
        }

        if (currentWriter != null) {
            currentWriter.println(ChatProtocol.encodeBytes(command, fields));
        }
    }

    // Dong toan bo tai nguyen mang hien tai va xoa tham chieu de tranh dung lai.
    private void disconnectConnection() {
        BufferedReader currentReader;
        PrintWriter currentWriter;
        Socket currentSocket;

        synchronized (connectionLock) {
            currentReader = reader;
            currentWriter = writer;
            currentSocket = socket;
            reader = null;
            writer = null;
            socket = null;
        }

        closeQuietly(currentReader);

        if (currentWriter != null) {
            currentWriter.close();
        }

        if (currentSocket != null) {
            try {
                currentSocket.close();
            } catch (IOException ignored) {
                // Ignore close errors during shutdown.
            }
        }
    }

    // Tien ich dong BufferedReader ma khong nem them loi ra ngoai.
    private void closeQuietly(BufferedReader currentReader) {
        if (currentReader == null) {
            return;
        }

        try {
            currentReader.close();
        } catch (IOException ignored) {
            // Ignore close errors during shutdown.
        }
    }

    // Tab chat rieng: gui/nhan PM 1-1 voi mot user cu the.
    private final class PrivateChatTab {
        private final String peerUsername;
        // Custom tab header components
        private final JPanel tabHeaderPanel;
        private final JLabel tabTitleLabel;
        private final JLabel badgeLabel;
        private final JPanel panel = new JPanel(new BorderLayout(12, 12));
          // Reply components cho Private Chat
        private final JPanel replyPreviewPanel;
        private JLabel replyLabel;
        private String replyContext = null;
        
        private final JLabel titleLabel = new JLabel();
        private final JPanel messagesPanel = new JPanel();
        private JScrollPane messagesScrollPane;
        private final JTextField input = new JTextField();
        private final JButton sendButton = new ModernButton("Gửi", ButtonVariant.PRIMARY);
        private final JButton sendImageButton = new ModernButton("", ButtonVariant.GHOST);
        private final JButton sendFileButton = new ModernButton("", ButtonVariant.GHOST);
        private final JButton closeButton = new ModernButton("Dong", ButtonVariant.SECONDARY);
        private final JLabel peerTypingLabel = new JLabel();

        private Timer localTypingIdleTimer;
        private boolean localTypingActive;
        private long localTypingLastSentAt;
        private Timer peerTypingTimeoutTimer;

        // Map<messageId, statusLabel> for outgoing messages
        private final Map<String, JLabel> outgoingMessageStatuses = new HashMap<>();
        private final List<String> unreadMessageIds = new ArrayList<>();

        private String peerDisplayName;

        private PrivateChatTab(String peerUsername, String peerDisplayName) {
            this.peerUsername = peerUsername;

            // Init custom tab header (Ten + Red Dot)
            tabHeaderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            tabHeaderPanel.setOpaque(false);
            tabTitleLabel = new JLabel();
            tabTitleLabel.setFont(Theme.fontBold(13));
            badgeLabel = new JLabel("●");
            badgeLabel.setForeground(Theme.DANGER);
            badgeLabel.setFont(Theme.fontBold(14)); // To hon mot chut de de nhin
            badgeLabel.setVisible(false);
            tabHeaderPanel.add(tabTitleLabel);
            tabHeaderPanel.add(badgeLabel);

            updatePeerDisplayName(peerDisplayName);
            Theme.styleSecondaryLabel(peerTypingLabel);
            peerTypingLabel.setFont(Theme.fontPlain(12));
            peerTypingLabel.setText(" ");
            peerTypingLabel.setVisible(false);

            panel.setOpaque(false);
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel headerPanel = new JPanel(new BorderLayout(8, 8));
            headerPanel.setOpaque(false);
            titleLabel.setFont(Theme.fontBold(14));
            titleLabel.setForeground(Theme.TEXT);
            headerPanel.add(titleLabel, BorderLayout.CENTER);
            headerPanel.add(closeButton, BorderLayout.EAST);
            panel.add(headerPanel, BorderLayout.NORTH);

            // Init Reply Panel
            replyPreviewPanel = createReplyPreviewPanel(e -> clearReply());
            // Hack: Lay label ra tu panel vua tao (vi createReplyPreviewPanel gan vao this.groupReplyLabel cua outer class)
            // Ta can custom lai ham createReply hoa set thu cong.
            // De don gian: ta lay component thu 1 (index 1) la JLabel
            this.replyLabel = (JLabel) replyPreviewPanel.getComponent(1);

            configureMessagesPanel(messagesPanel);
            messagesScrollPane = createScrollPane(messagesPanel, Theme.SURFACE_2);
            panel.add(messagesScrollPane, BorderLayout.CENTER);

            JPanel inputPanel = new JPanel(new BorderLayout(8, 8));
            inputPanel.setOpaque(false);
            
            inputPanel.add(replyPreviewPanel, BorderLayout.NORTH);
            
            Theme.styleTextField(input);
            inputPanel.add(input, BorderLayout.CENTER);

            JPanel actionsPanel = new JPanel();
            actionsPanel.setOpaque(false);
            actionsPanel.setLayout(new BoxLayout(actionsPanel, BoxLayout.X_AXIS));
            actionsPanel.add(sendFileButton);
            actionsPanel.add(Box.createHorizontalStrut(8));
            actionsPanel.add(sendImageButton);
            actionsPanel.add(Box.createHorizontalStrut(8));
            actionsPanel.add(sendButton);
            inputPanel.add(actionsPanel, BorderLayout.EAST);

            JPanel bottomPanel = new JPanel(new BorderLayout(0, 6));
            bottomPanel.setOpaque(false);
            bottomPanel.add(peerTypingLabel, BorderLayout.NORTH);
            bottomPanel.add(inputPanel, BorderLayout.CENTER);
            panel.add(bottomPanel, BorderLayout.SOUTH);

            sendButton.addActionListener(event -> send());
            sendImageButton.addActionListener(event -> chooseAndSendPrivateImage(peerUsername));
            sendFileButton.addActionListener(event -> chooseAndSendPrivateFile(peerUsername));
            input.addActionListener(event -> send());
            input.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    handleLocalTypingInputChanged();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    handleLocalTypingInputChanged();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    handleLocalTypingInputChanged();
                }
            });
            closeButton.addActionListener(event -> {
                stopLocalTyping(true);
                closePrivateChatTab(peerUsername);
            });

            setButtonIcons();
            setEnabled(true);
        }

        private void setButtonIcons() {
            setupIconButton(sendImageButton, "send_image.png", "📷", "Gửi ảnh", 16);
            setupIconButton(sendFileButton, "send_file.png", "📎", "Gửi file", 16);
        }

        private String getTabTitle() {
            return "PM: " + peerUsername;
        }

        private void updatePeerDisplayName(String value) {
            String cleaned = value == null || value.isBlank() ? peerUsername : value.trim();
            peerDisplayName = cleaned;
            tabTitleLabel.setText(cleaned);
            titleLabel.setText("Chat rieng voi: " + cleaned);
        }

        private void setEnabled(boolean enabled) {
            input.setEnabled(enabled);
            sendButton.setEnabled(enabled);
            sendImageButton.setEnabled(enabled);
            sendFileButton.setEnabled(enabled);
            closeButton.setEnabled(true);
        }

        private void setUnread(boolean unread) {
            badgeLabel.setVisible(unread);
            tabHeaderPanel.repaint();
        }

        private void setReply(String displayName, String message) {
            this.replyContext = "Trả lời " + displayName + ": " + message;
            String preview = message.length() > 50 ? message.substring(0, 50) + "..." : message;
            this.replyLabel.setText("<html><b>Đang trả lời " + displayName + ":</b><br>" + preview + "</html>");
            this.replyPreviewPanel.setVisible(true);
            this.input.requestFocusInWindow();
        }

        private void clearReply() {
            this.replyContext = null;
            this.replyPreviewPanel.setVisible(false);
        }

        private void send() {
            String message = input.getText().trim();
            if (message.isEmpty()) {
                return;
            }

            if (!authenticated) {
                appendSystem("Ban can dang nhap truoc.");
                return;
            }

            boolean canSend;
            synchronized (connectionLock) {
                canSend = writer != null;
            }

            if (!canSend) {
                appendSystem("Chua ket noi toi server.");
                return;
            }

            // Format tin nhan neu co reply
            String finalMessage = message;
            if (replyContext != null) {
                // Format dong bo voi Group Chat de su dung chung logic parse
                finalMessage = "RUN_REPLY_BLOCK\n" + replyContext + "\nEND_REPLY_BLOCK\n" + message;
                clearReply();
            }

            String messageId = UUID.randomUUID().toString();
            sendProtocolMessage("PM", peerUsername, messageId, finalMessage);
            stopLocalTyping(true);
            input.setText("");
            input.requestFocusInWindow();
        }

        private void appendIncoming(String fromDisplayName, String messageId, String message) {
            setPeerTyping(false, null);
            unreadMessageIds.add(messageId);
            
            // Custom appendTextBubble call de gan action reply cho tab rieng
            Runnable onReply = () -> setReply(fromDisplayName, message);
            
            appendBubbleWithReply(messagesPanel, messagesScrollPane, MessageSide.INCOMING, null, message, fromDisplayName, onReply);
            
            if (chatTabs.getSelectedComponent() != this.panel) {
                setUnread(true);
            }
            processReadReceipts();
        }
        
        private void appendBubbleWithReply(JPanel listPanel, JScrollPane scrollPane, MessageSide side, 
                                           String metaText, String message, String senderName, Runnable onReply, 
                                           JComponent... extras) {
             Runnable task = () -> {
                boolean forceScroll = (side == MessageSide.OUTGOING);
                JComponent messageContent = parseAndBuildMessageBubble(message, side);
                JPanel row = buildMessageRow(side, metaText, messageContent, message, senderName, onReply, extras);
                appendRow(listPanel, scrollPane, row, forceScroll);
            };
            if (SwingUtilities.isEventDispatchThread()) task.run(); else SwingUtilities.invokeLater(task);
        }

        private void appendIncomingImage(String fromDisplayName, String fileName, ImageIcon icon) {
            setPeerTyping(false, null);
            appendImageBubble(messagesPanel, messagesScrollPane, MessageSide.INCOMING, null, "Gửi ảnh: " + fileName, icon);
            if (chatTabs.getSelectedComponent() != this.panel) {
                setUnread(true);
            }
        }

        private void appendOutgoingImage(String toDisplayName, String fileName, ImageIcon icon) {
            appendImageBubble(messagesPanel, messagesScrollPane, MessageSide.OUTGOING, null, "Gửi ảnh: " + fileName, icon);
        }

        private void appendIncomingFile(String fromDisplayName, String fileName, long sizeBytes) {
            String suffix = sizeBytes >= 0 ? " (" + formatBytes(sizeBytes) + ")" : "";
            setPeerTyping(false, null);
            appendTextBubble(messagesPanel, messagesScrollPane, MessageSide.INCOMING, null, "Gửi file: " + fileName + suffix,
                    new JComponent[0]);
            if (chatTabs.getSelectedComponent() != this.panel) {
                setUnread(true);
            }
        }

        private void appendOutgoingFile(String toDisplayName, String fileName, long sizeBytes) {
            String suffix = sizeBytes >= 0 ? " (" + formatBytes(sizeBytes) + ")" : "";
            appendTextBubble(messagesPanel, messagesScrollPane, MessageSide.OUTGOING, null, "Gửi file: " + fileName + suffix,
                    new JComponent[0]);
        }

        private void appendSystem(String message) {
            appendTextBubble(messagesPanel, messagesScrollPane, MessageSide.SYSTEM, null, "[Hệ thống] " + message,
                    new JComponent[0]);
        }

        private void appendOutgoing(String toDisplayName, String messageId, String message) {
            JLabel statusLabel = createStatusLabel("Đã gửi");
            outgoingMessageStatuses.put(messageId, statusLabel);
            appendTextBubble(messagesPanel, messagesScrollPane, MessageSide.OUTGOING, null, message, statusLabel); // Outgoing khong can reply chinh minh
        }

        private JLabel createStatusLabel(String text) {
            JLabel label = new JLabel(text);
            label.setFont(Theme.fontPlain(10));
            label.setForeground(Theme.MUTED_TEXT);
            label.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
            return label;
        }

        private void markAsRead(String messageId) {
            JLabel statusLabel = outgoingMessageStatuses.get(messageId);
            if (statusLabel != null) {
                statusLabel.setText("Đã xem");
            }
        }

        private void processReadReceipts() {
            if (chatTabs.getSelectedComponent() != this.panel || unreadMessageIds.isEmpty()) {
                return;
            }

            // Send a "seen" receipt for all unread messages in this now-active tab.
            for (String msgId : unreadMessageIds) {
                sendProtocolMessage("PM_SEEN", peerUsername, msgId);
            }
            unreadMessageIds.clear();
        }

        private void handleLocalTypingInputChanged() {
            String text = input.getText();
            boolean hasText = text != null && !text.trim().isEmpty();

            if (!hasText) {
                stopLocalTyping(true);
                return;
            }

            if (!authenticated) {
                return;
            }

            boolean canSend;
            synchronized (connectionLock) {
                canSend = writer != null;
            }

            if (!canSend) {
                return;
            }

            long now = System.currentTimeMillis();
            if (!localTypingActive || now - localTypingLastSentAt >= TYPING_REFRESH_MS) {
                sendProtocolMessage("PM_TYPING", peerUsername, "1");
                localTypingActive = true;
                localTypingLastSentAt = now;
            }

            if (localTypingIdleTimer == null) {
                localTypingIdleTimer = new Timer(TYPING_IDLE_DELAY_MS, event -> stopLocalTyping(true));
                localTypingIdleTimer.setRepeats(false);
            }
            localTypingIdleTimer.restart();
        }

        private void stopLocalTyping(boolean sendStop) {
            if (localTypingIdleTimer != null) {
                localTypingIdleTimer.stop();
            }

            if (!localTypingActive) {
                return;
            }

            localTypingActive = false;
            localTypingLastSentAt = 0;

            if (!sendStop) {
                return;
            }

            boolean canSend;
            synchronized (connectionLock) {
                canSend = writer != null;
            }

            if (!authenticated || !canSend) {
                return;
            }

            sendProtocolMessage("PM_TYPING", peerUsername, "0");
        }

        private void setPeerTyping(boolean typing, String fromDisplayName) {
            if (typing) {
                String name = fromDisplayName == null || fromDisplayName.isBlank()
                        ? peerDisplayName
                        : fromDisplayName.trim();
                peerTypingLabel.setText(name + " Đoạn soạn tin...");
                peerTypingLabel.setVisible(true);

                if (peerTypingTimeoutTimer == null) {
                    peerTypingTimeoutTimer = new Timer(TYPING_TTL_MS, event -> setPeerTyping(false, null));
                    peerTypingTimeoutTimer.setRepeats(false);
                }
                peerTypingTimeoutTimer.restart();
                return;
            }

            peerTypingLabel.setVisible(false);
            peerTypingLabel.setText(" ");
            if (peerTypingTimeoutTimer != null) {
                peerTypingTimeoutTimer.stop();
            }
        }
    }
}
