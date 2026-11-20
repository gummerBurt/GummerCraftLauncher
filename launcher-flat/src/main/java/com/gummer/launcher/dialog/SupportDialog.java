/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import com.gummer.launcher.support.SupportMessage;
import com.gummer.launcher.support.SupportService;
import com.gummer.launcher.support.SupportState;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.swing.SwingHelper;
import lombok.extern.java.Log;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;

import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * 1.32: вікно «Підтримка». Спершу форма звернення (нік, тема, текст, контакт, лог), після відправки - чат цієї
 * розмови: відповіді адмінів з Chatwoot, можна дописувати. Поки вікно відкрите, нові повідомлення перевіряються
 * кожні {@link #POLL_MS} мс.
 */
@Log
public class SupportDialog extends JDialog {

    private static final int POLL_MS = 5000;
    private static SupportDialog open;

    private final SupportService service;
    private final Supplier<Instance> selectedInstance;
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    // форма
    private final JTextField nickField = new JTextField();
    private final JComboBox<String> topicBox = new JComboBox<String>();
    private final JTextArea textArea = new JTextArea(7, 40);
    private final JTextField contactField = new JTextField();
    private final JCheckBox logsCheck = new JCheckBox(tr("support.attachLogs"), true);
    private final JLabel formError = new JLabel(" ");
    private final JButton sendButton = new JButton(tr("support.send"));
    private final JButton toChatButton = new JButton();

    // чат
    private final JLabel chatTitle = new JLabel();
    /** 20.11.2026: «адміністрація вже відповіла» над чатом, коли звіт з вікна вильоту чекає, а відповідь не прочитана. */
    private final JTextArea chatBanner = wrapText("", false);
    /** 20.11.2026: текст і тема звіту з вікна вильоту, поки гравець читає відповідь (форма їх не губить). */
    private String pendingText;
    private String pendingTopic;
    private final JEditorPane chatPane = new JEditorPane("text/html", "");
    private final JTextArea replyArea = new JTextArea(3, 40);
    private final JButton replyButton = new JButton(tr("support.reply"));
    private final JLabel chatStatus = new JLabel(" ");
    private final Timer poll = new Timer(POLL_MS, e -> refreshChat());
    private boolean polling;
    private long shownMaxId = -1;

    /** 16.11.2026: для значка непрочитаних (SupportUnread): поки вікно відкрите, воно саме показує відповіді. */
    public static boolean isOpen() {
        return open != null && open.isDisplayable() && open.isVisible();
    }

    public static void showFor(Window owner, SupportService service, Supplier<Instance> selectedInstance) {
        if (open != null && open.isDisplayable()) {
            open.toFront();
            return;
        }
        open = new SupportDialog(owner, service, selectedInstance);
        open.setVisible(true);
    }

    /**
     * 1.32: нове звернення з готовою темою і текстом (з вікна помилки чи вильоту). Якщо вікно вже відкрите,
     * воно перемикається на форму з цим текстом. Лог і звіт про збій додаються галочкою, як завжди.
     */
    public static void showPrefilled(Window owner, SupportService service, Supplier<Instance> selectedInstance, String topic, String text) {
        if (open == null || !open.isDisplayable()) {
            open = new SupportDialog(owner, service, selectedInstance);
        }
        open.pendingTopic = topic;
        open.pendingText = text;
        open.showForm();
        open.setVisible(true);
        // 20.11.2026 (тікет #88: гравець тричі слав новий звіт про той самий виліт, не прочитавши відповідей): якщо на
        // поточну розмову є непрочитана відповідь, спершу чат з нею і поясненням; «Нове звернення» поверне цей звіт
        if (service.hasConversation()) {
            final SupportDialog d = open;
            new SwingWorker<Integer, Void>() {
                @Override
                protected Integer doInBackground() throws Exception {
                    return service.unreadReplies();
                }

                @Override
                protected void done() {
                    try {
                        if (get() > 0 && d.isDisplayable()) {
                            d.showChat();
                            d.chatBanner.setText(tr("support.unreadBeforeNew"));
                            d.chatBanner.setVisible(true);
                        }
                    } catch (Exception e) {
                        log.log(Level.INFO, "Support: could not check replies before a new report: " + e);
                    }
                }
            }.execute();
        }
        open.toFront();
        // 1.33: звіт про відключення приходить, поки гра на весь екран, а головне вікно сховане. Ненадовго «поверх усіх»,
        // щоб форма не загубилась за грою; далі звичайне вікно, яке можна сховати за інші.
        if (owner == null || !owner.isShowing()) {
            final SupportDialog d = open;
            d.setAlwaysOnTop(true);
            Timer off = new Timer(3000, e -> d.setAlwaysOnTop(false));
            off.setRepeats(false);
            off.start();
        }
    }

    private SupportDialog(Window owner, SupportService service, Supplier<Instance> selectedInstance) {
        super(owner, tr("support.title"), ModalityType.MODELESS);
        this.service = service;
        this.selectedInstance = selectedInstance;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        root.add(buildForm(), "form");
        root.add(buildChat(), "chat");
        setContentPane(root);
        // 16.11.2026: під розмір екрана і стиль вигляду лаунчера
        Dimension size = com.gummer.launcher.look.LookTheme.fitToScreen(this, 560, 640, 440, 420);
        setSize(size);
        setMinimumSize(new Dimension(Math.min(440, size.width), Math.min(420, size.height)));
        com.gummer.launcher.look.LookUi.primary(sendButton);
        com.gummer.launcher.look.LookUi.primary(replyButton);
        com.gummer.launcher.look.LookUi.decorate(this, owner);
        setLocationRelativeTo(owner);
        if (service.hasConversation()) {
            showChat();
        } else {
            showForm();
        }
    }

    @Override
    public void dispose() {
        poll.stop();
        super.dispose();
    }

    // ------------------------------------------------------------------ форма

    /** Текст, що переноситься по ширині вікна (JLabel з html-шириною в px ламався при масштабі Windows). */
    private static JTextArea wrapText(String text, boolean small) {
        JTextArea a = new JTextArea(text);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setEditable(false);
        a.setFocusable(false);
        a.setOpaque(false);
        a.setBorder(null);
        a.setFont(UIManager.getFont(small ? "small.font" : "Label.font"));
        a.setForeground(UIManager.getColor("Label.foreground"));
        return a;
    }

    private JPanel buildForm() {
        JPanel p = new JPanel(new MigLayout("insets 18 20 16 20, wrap 1, fillx", "[0:0:,grow,fill]", ""));
        JLabel title = new JLabel(tr("support.formTitle"));
        title.putClientProperty("FlatLaf.styleClass", "h2");
        p.add(title, "gapbottom 4");
        p.add(wrapText(tr("support.intro"), false), "gapbottom 8");

        p.add(new JLabel(tr("support.nick")));
        nickField.putClientProperty("JTextField.placeholderText", tr("support.nickHint"));
        p.add(nickField, "gapbottom 6");

        p.add(new JLabel(tr("support.topic")));
        for (String t : SupportService.TOPICS) topicBox.addItem(t);
        p.add(topicBox, "gapbottom 6");

        p.add(new JLabel(tr("support.text")));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        p.add(new JScrollPane(textArea), "h 120::, growy, gapbottom 6");

        p.add(new JLabel(tr("support.contact")));
        contactField.putClientProperty("JTextField.placeholderText", tr("support.contactHint"));
        p.add(contactField, "gapbottom 6");

        logsCheck.setToolTipText(tr("support.attachLogsTooltip"));
        p.add(logsCheck);
        JTextArea privacy = wrapText(tr("support.privacy"), true);
        p.add(privacy, "gapbottom 6");

        formError.setForeground(new Color(0xE0, 0x6C, 0x6C));
        p.add(formError);

        JPanel buttons = new JPanel(new MigLayout("insets 0, fillx", "[]push[]", ""));
        buttons.setOpaque(false);
        toChatButton.addActionListener(e -> showChat());
        buttons.add(toChatButton, "hidemode 3");
        sendButton.putClientProperty("FlatLaf.styleClass", "large");
        sendButton.addActionListener(e -> submit());
        buttons.add(sendButton, "cell 1 0");
        p.add(buttons, "gaptop 6");
        return p;
    }

    private void showForm() {
        poll.stop();
        SupportState s = service.getState();
        nickField.setText(service.defaultNick());
        topicBox.setSelectedItem(SupportService.defaultTopic(selectedInstance.get()));
        contactField.setText(s.contact != null ? s.contact : "");
        if (pendingTopic != null && SupportService.TOPICS.contains(pendingTopic)) topicBox.setSelectedItem(pendingTopic);
        textArea.setText(pendingText != null ? pendingText : "");
        textArea.setCaretPosition(0);
        formError.setText(" ");
        toChatButton.setVisible(service.hasConversation());
        toChatButton.setText(tr("support.backToChatAll"));
        cards.show(root, "form");
        SwingUtilities.invokeLater(textArea::requestFocusInWindow);
    }

    private void submit() {
        final String nick = nickField.getText().trim();
        final String topic = (String) topicBox.getSelectedItem();
        final String text = textArea.getText();
        final String contact = contactField.getText().trim();
        String err = SupportService.validate(nick, topic, text);
        if (err == null) err = service.rateLimitError();
        if (err != null) {
            formError.setText(tr(err));
            return;
        }
        final boolean logs = logsCheck.isSelected();
        final Instance selected = selectedInstance.get();
        formError.setText(tr("support.sending"));
        sendButton.setEnabled(false);
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return service.submit(nick, topic, text, contact, logs, selected);
            }

            @Override
            protected void done() {
                sendButton.setEnabled(true);
                try {
                    get();
                    pendingText = null;
                    pendingTopic = null;
                    shownMaxId = -1;
                    showChat();
                } catch (InterruptedException | ExecutionException e) {
                    log.log(Level.WARNING, "Support: submit failed", e);
                    formError.setText(failText(e));
                }
            }
        }.execute();
    }

    /**
     * Текст помилки надсилання за причиною. До 13.11.2026 будь-яка помилка була «Перевір інтернет», навіть коли інтернет є, а впав
     * сам лаунчер (старий exe, NoSuchMethodError) або сервер відповів кодом помилки: гравцю і нам це нічого не казало.
     */
    static String failText(Exception e) {
        Throwable c = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
        if (c instanceof com.gummer.launcher.support.SupportClient.SupportException) {
            return tr("support.sendFailedServer", ((com.gummer.launcher.support.SupportClient.SupportException) c).code);
        }
        if (c instanceof java.io.IOException || c instanceof InterruptedException) {
            return tr("support.sendFailed");
        }
        return tr("support.sendFailedBug", c.getClass().getSimpleName());
    }

    // ------------------------------------------------------------------ чат

    private JPanel buildChat() {
        JPanel p = new JPanel(new MigLayout("insets 16 18 14 18, wrap 1, fill", "[0:0:,grow,fill]", "[][][grow,fill][][][]"));
        chatTitle.putClientProperty("FlatLaf.styleClass", "h3");
        p.add(chatTitle);
        chatBanner.setOpaque(true);
        chatBanner.setBackground(new Color(0xFF, 0xC1, 0x07, 60));
        chatBanner.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        chatBanner.setVisible(false);
        p.add(chatBanner, "hidemode 3, gapbottom 4");
        JTextArea note = wrapText(tr("support.chatNote"), true);
        p.add(note, "gapbottom 4");

        chatPane.setEditable(false);
        chatPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        chatPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getURL() != null) {
                SwingHelper.openURL(e.getURL().toString(), this);
            }
        });
        p.add(new JScrollPane(chatPane), "grow, push");

        replyArea.setLineWrap(true);
        replyArea.setWrapStyleWord(true);
        replyArea.putClientProperty("JTextField.placeholderText", tr("support.replyHint"));
        p.add(new JScrollPane(replyArea), "h 60::, gaptop 6");
        p.add(chatStatus);

        JPanel buttons = new JPanel(new MigLayout("insets 0, fillx", "[]push[]", ""));
        buttons.setOpaque(false);
        JButton newButton = new JButton(tr("support.newRequest"));
        newButton.addActionListener(e -> showForm());
        buttons.add(newButton);
        replyButton.addActionListener(e -> reply());
        buttons.add(replyButton, "cell 1 0");
        p.add(buttons);
        return p;
    }

    private void showChat() {
        SupportState s = service.getState();
        chatTitle.setText(tr("support.chatTitleAll"));
        chatStatus.setText(" ");
        chatBanner.setVisible(false);
        cards.show(root, "chat");
        refreshChat();
        poll.start();
        SwingUtilities.invokeLater(replyArea::requestFocusInWindow);
    }

    private void refreshChat() {
        if (polling) return;
        polling = true;
        new SwingWorker<List<SupportMessage>, Void>() {
            @Override
            protected List<SupportMessage> doInBackground() throws Exception {
                return service.messages();
            }

            @Override
            protected void done() {
                polling = false;
                try {
                    render(get());
                } catch (InterruptedException | ExecutionException e) {
                    log.log(Level.INFO, "Support: could not load messages", e);
                    chatStatus.setText(tr("support.loadFailed"));
                }
            }
        }.execute();
    }

    private void render(List<SupportMessage> messages) {
        long max = 0;
        for (SupportMessage m : messages) max = Math.max(max, m.id);
        if (max == shownMaxId) return; // нічого нового: не перемальовуємо (не збиваємо прокрутку)
        shownMaxId = max;
        service.markSeen(max);
        chatStatus.setText(" ");

        SimpleDateFormat time = new SimpleDateFormat("dd.MM HH:mm");
        StringBuilder html = new StringBuilder("<html><body style=\"font-family:sans-serif; margin:6px\">");
        if (messages.isEmpty()) {
            html.append("<p style=\"color:#8a8f98\">").append(SwingHelper.htmlEscape(tr("support.noMessages"))).append("</p>");
        }
        int conv = -1;
        for (SupportMessage m : messages) {
            // 20.11.2026: спільний чат усіх звернень: перед кожним зверненням рядок з його номером і датою
            if (m.conversationId > 0 && m.conversationId != conv) {
                conv = m.conversationId;
                html.append("<div style=\"color:#8a8f98; margin:6px 0 8px 0; border-top:1px solid #4a4f58; padding-top:4px\">")
                        .append(SwingHelper.htmlEscape(tr("support.convSeparator", conv, time.format(new Date(m.createdAt)))))
                        .append("</div>");
            }
            boolean mine = m.isFromPlayer();
            String who = mine ? tr("support.you") : (m.senderName != null && !m.senderName.isEmpty() ? m.senderName : tr("support.admin"));
            String color = mine ? "#7fb2ff" : "#7fd99a";
            html.append("<div style=\"margin-bottom:10px\">")
                    .append("<span style=\"color:").append(color).append("; font-weight:bold\">").append(SwingHelper.htmlEscape(who)).append("</span>")
                    .append(" <span style=\"color:#8a8f98\">").append(time.format(new Date(m.createdAt))).append("</span><br>");
            if (m.content != null && !m.content.isEmpty()) {
                html.append(SwingHelper.htmlEscape(m.content).replace("\n", "<br>"));
            }
            int n = 1;
            for (String url : m.attachments) {
                if (m.content != null && !m.content.isEmpty() || n > 1) html.append("<br>");
                html.append("<a href=\"").append(SwingHelper.htmlEscape(url)).append("\">")
                        .append(SwingHelper.htmlEscape(tr("support.attachment", n++))).append("</a>");
            }
            html.append("</div>");
        }
        html.append("</body></html>");
        chatPane.setText(html.toString());
        SwingUtilities.invokeLater(() -> chatPane.setCaretPosition(chatPane.getDocument().getLength()));
    }

    private void reply() {
        final String text = replyArea.getText().trim();
        if (text.isEmpty()) return;
        replyButton.setEnabled(false);
        chatStatus.setText(tr("support.sending"));
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                service.send(text);
                return null;
            }

            @Override
            protected void done() {
                replyButton.setEnabled(true);
                try {
                    get();
                    replyArea.setText("");
                    chatStatus.setText(" ");
                    refreshChat();
                } catch (InterruptedException | ExecutionException e) {
                    log.log(Level.WARNING, "Support: reply failed", e);
                    chatStatus.setText(failText(e));
                }
            }
        }.execute();
    }
}
