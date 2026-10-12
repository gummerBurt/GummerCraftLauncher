/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.popup;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JDialog;

import org.jdesktop.animation.timing.Animator;
import org.jdesktop.animation.timing.TimingTarget;
import org.jdesktop.animation.timing.TimingTargetAdapter;

public class Notification extends javax.swing.JComponent {

    private JDialog dialog;
    private Animator animator;
    private Frame fram;
    private boolean showing;
    private Thread thread;
    private int animate = 10;
    private BufferedImage imageShadow;
    private int shadowSize = 6;
    private Type type;
    private Location location;
    public static int onscreen;
    private int gap = 5000;
    public String title = "Custom";

    public Notification(Frame fram, Type type, Location location, String message) throws InterruptedException {
        onscreen = onscreen + 1;
        this.fram = fram;
        this.type = type;
        this.location = location;
        initComponents();
        init(message, "none");
        initAnimator(onscreen);
    }

    public Notification(Frame fram, Type type, Location location, String message, String title) throws InterruptedException {
        onscreen = onscreen + 1;
        this.fram = fram;
        this.type = type;
        this.location = location;
        initComponents();
        init(message, title);
        initAnimator(onscreen);
    }

    private void init(String message, String title) {
        setBackground(new Color(255, 255, 255, 225));
        dialog = new JDialog(fram);
        dialog.setUndecorated(true);
        dialog.setFocusableWindowState(false);
        dialog.setBackground(new Color(0, 0, 0, 0));
        dialog.add(this);
        dialog.setSize(getPreferredSize());
        int defaultHeight = (int) getPreferredSize().getHeight();
        int defaultWidth = (int) getPreferredSize().getWidth();
        switch (type) {
            case SUCCESS:
                lbIcon.setIcon(new javax.swing.ImageIcon(
                    Objects.requireNonNull(getClass().getResource("/com/skcraft/launcher/ui/notification/success.png"))
                ));
                lbMessage.setText(title);
                break;
            case WARNING:
                lbIcon.setIcon(new javax.swing.ImageIcon(
                    Objects.requireNonNull(getClass().getResource("/com/skcraft/launcher/ui/notification/warning.png"))
                ));
                lbMessage.setText(title);
                break;
            default:
                lbIcon.setIcon(new javax.swing.ImageIcon(
                    Objects.requireNonNull(getClass().getResource("/com/skcraft/launcher/ui/notification/info.png"))
                ));
                lbMessage.setText(title);
                break;
        }
        lbMessageText.setText(message);

        if(title.equals("none")) {
            int stringWidth = lbMessageText.getPreferredSize().width;
            dialog.setSize(new Dimension((int) (stringWidth * 2.38), defaultHeight));
            lbMessageText.setFont(lbMessage.getFont().deriveFont(Font.BOLD, 20));
            lbMessageText.setForeground(new java.awt.Color(38, 38, 38));
            lbMessage.setVisible(false);
        }
    }

    private void initAnimator(int instance) {
        TimingTarget target = new TimingTargetAdapter() {
            private int x;
            private int top;
            private boolean top_to_bot;

            @Override
            public void timingEvent(float fraction) {
                if (showing) {
                    float alpha = 1f - fraction;
                    int y = (int) ((1f - fraction) * animate);
                    if (top_to_bot) {
                        dialog.setLocation(x, top + y + (instance - 1) * getHeight());
                    } else {
                        dialog.setLocation(x, top - y + (instance - 1) * getHeight());
                    }
                    dialog.setOpacity(alpha);
                } else {
                    int y = (int) (fraction * animate);
                    if (top_to_bot) {
                        dialog.setLocation(x, top + y + ((instance -1) * getHeight()));
                    } else {
                        dialog.setLocation(x, top - y + ((instance -1) * getHeight()));
                    }
                    dialog.setOpacity(fraction);
                }
            }

            @Override
            public void begin() {
                if (!showing) {
                    dialog.setOpacity(0f);
                    int margin = 15;
                    int y = 0;
                    switch (location) {
                        case TOP_CENTER:
                            // add 220 to account instanceTable
                            x = fram.getX() + (((fram.getWidth() + 220) - dialog.getWidth()) / 2);
                            y = fram.getY() + 30;
                            top_to_bot = true;
                            break;
                        case TOP_RIGHT:
                            x = fram.getX() + fram.getWidth() - dialog.getWidth() - margin;
                            y = fram.getY();
                            top_to_bot = true;
                            break;
                        case TOP_LEFT:
                            x = fram.getX() + margin;
                            y = fram.getY();
                            top_to_bot = true;
                            break;
                        case BOTTOM_CENTER:
                            x = fram.getX() + ((fram.getWidth() - dialog.getWidth()) / 2);
                            y = fram.getY() + fram.getHeight() - dialog.getHeight();
                            top_to_bot = false;
                            break;
                        case BOTTOM_RIGHT:
                            x = fram.getX() + fram.getWidth() - dialog.getWidth() - margin;
                            y = fram.getY() + fram.getHeight() - dialog.getHeight();
                            top_to_bot = false;
                            break;
                        case BOTTOM_LEFT:
                            x = fram.getX() + margin;
                            y = fram.getY() + fram.getHeight() - dialog.getHeight();
                            top_to_bot = false;
                            break;
                        default:
                            x = fram.getX() + ((fram.getWidth() - dialog.getWidth()) / 2);
                            y = fram.getY() + ((fram.getHeight() - dialog.getHeight()) / 2);
                            top_to_bot = true;
                            break;
                    }
                    top = y;
                    dialog.setLocation(x, y);
                    dialog.setVisible(true);
                }
            }

            @Override
            public void end() {
                showing = !showing;
                if (showing) {
                    thread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            sleep();
                            try {
                                closeNotification();
                            } catch (InterruptedException ex) {
                                Logger.getLogger(Notification.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    });
                    thread.start();
                } else {
                    dialog.dispose();
                }
            }
        };
        animator = new Animator(500, target);
        animator.setResolution(5);
    }

    public void showNotification() {
        animator.start();
    }

    private void closeNotification() throws InterruptedException {
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
        if (animator.isRunning()) {
            if (!showing) {
                animator.stop();
                showing = true;
                animator.start();
            }
        } else {
            showing = true;
            animator.start();
        }
        if (onscreen > 0) {
            onscreen = onscreen - 1;
        }
    }

    private void sleep() {
        try {
            Thread.sleep(gap-1500);
        } catch (InterruptedException e) {
        }
    }

    @Override
    public void paint(Graphics grphcs) {
        Graphics2D g2 = (Graphics2D) grphcs.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(getBackground());
        g2.drawImage(imageShadow, 0, 0, null);
        int x = shadowSize;
        int y = shadowSize;
        int width = getWidth() - shadowSize * 2;
        int height = getHeight() - shadowSize * 2;
        int arc = 50;
        g2.fillRoundRect(x, y, width, height, arc, arc);
        g2.dispose();
        super.paint(grphcs);
    }

    @Override
    public void setBounds(int i, int i1, int i2, int i3) {
        super.setBounds(i, i1, i2, i3);
        createImageShadow();
    }

    private void createImageShadow() {
        imageShadow = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = imageShadow.createGraphics();
        g2.drawImage(createShadow(), 0, 0, null);
        g2.dispose();
    }

    private BufferedImage createShadow() {
        BufferedImage img = new BufferedImage(getWidth() - shadowSize * 2, getHeight() - shadowSize * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.fillRect(0, 0, img.getWidth(), img.getHeight());
        g2.dispose();
        return new ShadowRenderer(shadowSize, 0.3f, new Color(100, 100, 100)).createShadow(img);
    }

    @SuppressWarnings("unchecked")
    private void initComponents() {

        lbIcon = new javax.swing.JLabel();
        panel = new javax.swing.JPanel();
        lbMessage = new javax.swing.JLabel();
        lbMessageText = new javax.swing.JLabel();
        cmdClose = new javax.swing.JButton();

        lbIcon.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbIcon.setIcon(new javax.swing.ImageIcon(
            Objects.requireNonNull(getClass().getResource("/com/skcraft/launcher/ui/notification/success.png"))
        ));

        panel.setOpaque(false);

        lbMessage.setFont(lbMessage.getFont().deriveFont(Font.BOLD, 14));
        lbMessage.setForeground(new java.awt.Color(38, 38, 38));
        lbMessage.setText("none");

        lbMessageText.setForeground(new java.awt.Color(127, 127, 127));
        lbMessageText.setText("Message Text");

        javax.swing.GroupLayout panelLayout = new javax.swing.GroupLayout(panel);
        panel.setLayout(panelLayout);
        panelLayout.setHorizontalGroup(
            panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(panelLayout.createSequentialGroup()
                    .addContainerGap()
                    .addGroup(panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(lbMessage)
                        .addComponent(lbMessageText))
                    .addContainerGap(217, Short.MAX_VALUE))
        );
        panelLayout.setVerticalGroup(
            panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(panelLayout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(lbMessage)
                    .addGap(3, 3, 3)
                    .addComponent(lbMessageText)
                    .addContainerGap())
        );

        cmdClose.setIcon(new javax.swing.ImageIcon(
            Objects.requireNonNull(getClass().getResource("/com/skcraft/launcher/ui/notification/close.png"))
        ));
        cmdClose.setBorder(null);
        cmdClose.setContentAreaFilled(false);
        cmdClose.setFocusable(false);
        cmdClose.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdCloseActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addGap(20, 20, 20)
                    .addComponent(lbIcon)
                    .addGap(10, 10, 10)
                    .addComponent(panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(cmdClose)
                    .addGap(15, 15, 15))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addGap(10, 10, 10)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(cmdClose, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(lbIcon, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGap(10, 10, 10))
        );
    }

    private void cmdCloseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmdCloseActionPerformed
        try {
            closeNotification();
        } catch (InterruptedException ex) {
            Logger.getLogger(Notification.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_cmdCloseActionPerformed

    public static enum Type {
        SUCCESS, INFO, UPDATE, WARNING
    }

    public static enum Location {
        TOP_CENTER, TOP_RIGHT, TOP_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT, BOTTOM_LEFT, CENTER
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cmdClose;
    private javax.swing.JLabel lbIcon;
    private javax.swing.JLabel lbMessage;
    private javax.swing.JLabel lbMessageText;
    private javax.swing.JPanel panel;
    // End of variables declaration//GEN-END:variables
}
