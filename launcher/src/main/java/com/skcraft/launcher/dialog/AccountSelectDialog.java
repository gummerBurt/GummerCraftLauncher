/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.dialog;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.gummer.launcher.dialog.OfflineLoginDialog;
import com.gummer.launcher.swing.SwingExtras;
import com.skcraft.concurrency.ObservableFuture;
import com.skcraft.concurrency.ProgressObservable;
import com.skcraft.concurrency.SettableProgress;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.auth.*;
import com.skcraft.launcher.persistence.Persistence;
import com.skcraft.launcher.swing.LinedBoxPanel;
import com.skcraft.launcher.swing.SwingHelper;
import com.skcraft.launcher.util.SharedLocale;
import com.skcraft.launcher.util.SwingExecutor;
import lombok.RequiredArgsConstructor;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.Callable;

public class AccountSelectDialog extends JDialog {
	private final JList<SavedSession> accountList;
	private final JButton loginButton = new JButton(SharedLocale.tr("accounts.play"), SwingHelper.createIcon(Launcher.class, "ui/icon/light/start.png", 21, 21));
	private final JButton cancelButton = new JButton(SharedLocale.tr("button.cancelsm"));
	private final JButton addMojangButton = new JButton(SharedLocale.tr("accounts.addMojang"));
	private final JButton addMicrosoftButton = new JButton(SharedLocale.tr("accounts.addMicrosoft"));
	private final JButton addOfflineButton = new JButton(SharedLocale.tr("accounts.addOffline"));
	private final JButton removeSelected = new JButton(SharedLocale.tr("accounts.removeSelected"));
	private final JButton offlineButton = new JButton(SharedLocale.tr("login.playOffline"));
    //private final LinedBoxPanel buttonsPanel = new LinedBoxPanel(true);

	private final Launcher launcher;
	private Session selected;

    // Define a uniform minimum button width
    int minButtonWidth = 230;
    int minButtonHeight = 35;

	public AccountSelectDialog(Window owner, Launcher launcher) {
		super(owner, ModalityType.DOCUMENT_MODAL);

		this.launcher = launcher;
		this.accountList = new JList<>(launcher.getAccounts());

		setTitle(SharedLocale.tr("accounts.title"));
		initComponents();
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setMinimumSize(new Dimension(350, 260));
		setResizable(false);
		pack();
		setLocationRelativeTo(owner);

		loginButton.requestFocusInWindow();
	}

    private void initComponents() {
        setLayout(new BorderLayout());

        addMicrosoftButton.putClientProperty("FlatLaf.styleClass", "large");
        addOfflineButton.putClientProperty("FlatLaf.styleClass", "large");
        removeSelected.putClientProperty("FlatLaf.styleClass", "large");
        loginButton.setFont(loginButton.getFont().deriveFont(Font.BOLD, 24));
        loginButton.setHorizontalTextPosition(SwingConstants.LEFT);
        loginButton.setVerticalTextPosition(SwingConstants.CENTER);
        loginButton.setMargin(new Insets(0, 11, 1, 7));
        loginButton.setForeground(Color.WHITE);
        loginButton.setIconTextGap(5);

        // --- Account List Setup ---
        accountList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        accountList.setLayoutOrientation(JList.VERTICAL);
        accountList.setVisibleRowCount(0);
        accountList.setCellRenderer(new AccountRenderer());
        accountList.setFocusable(false);

        // --- Tune Buttons ---
        setButtonSize(addOfflineButton);
        setButtonSize(addMojangButton);
        setButtonSize(addMicrosoftButton);
        setButtonSize(removeSelected);

        // --- Account List Panel ---
        JScrollPane accountPane = new JScrollPane(accountList);
        accountPane.setPreferredSize(new Dimension(230, 225));
        accountPane.setAlignmentX(CENTER_ALIGNMENT);

        // Panel for removeSelected button (placed under the account list)
        JPanel removePanel = new JPanel(new BorderLayout());
        removeSelected.setAlignmentX(CENTER_ALIGNMENT);
        removePanel.add(removeSelected, BorderLayout.CENTER);
        removePanel.setBorder(BorderFactory.createEmptyBorder(13, 0, 0, 0));

        // Combine account list and remove button
        JPanel accountPanel = new JPanel(new BorderLayout());
        accountPanel.add(accountPane, BorderLayout.CENTER);
        accountPanel.add(removePanel, BorderLayout.SOUTH);

        // Create the label for the right column
        //JPanel headerPanel = new JPanel(new MigLayout("fill,debug, insets 0", "push[align center]push[align center]push[align center]push", ""));
        JPanel headerPanel = new JPanel(new MigLayout("fill, insets 0", "push[align center]push[align center]push[align center]push", ""));
        JLabel headerPanelLabel1 = new JLabel(SharedLocale.tr("accounts.header.select"));
        JLabel headerPanelLabel2 = new JLabel(SharedLocale.tr("accounts.header.or"));
        JLabel headerPanelLabel3 = new JLabel(SharedLocale.tr("accounts.header.create"));
        headerPanelLabel1.putClientProperty("FlatLaf.styleClass", "semibold");
        headerPanelLabel2.putClientProperty("FlatLaf.styleClass", "semibold");
        headerPanelLabel3.putClientProperty("FlatLaf.styleClass", "semibold");
        headerPanelLabel1.setFont(headerPanelLabel1.getFont().deriveFont(Font.PLAIN, 19));
        headerPanelLabel2.setFont(headerPanelLabel2.getFont().deriveFont(Font.PLAIN, 19));
        headerPanelLabel3.setFont(headerPanelLabel3.getFont().deriveFont(Font.PLAIN, 19));
        headerPanelLabel1.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 0));
        headerPanelLabel2.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 7));
        headerPanelLabel3.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 32));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        headerPanel.add(headerPanelLabel1);
        headerPanel.add(headerPanelLabel2);
        headerPanel.add(headerPanelLabel3);

        // Create a top panel for the label and the add buttons
        JPanel topRightPanel = new JPanel();
        topRightPanel.setLayout(new BoxLayout(topRightPanel, BoxLayout.Y_AXIS));
        topRightPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        topRightPanel.add(addOfflineButton);
        topRightPanel.add(Box.createVerticalStrut(13));
        topRightPanel.add(addMicrosoftButton);
        // Uncomment if needed:
        // topRightPanel.add(Box.createVerticalStrut(10));
        // topRightPanel.add(addMojangButton);

        // Create the login button panel for the bottom part
        JPanel loginButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0)) {
            @Override
            public Dimension getMaximumSize() {
                // Restrict vertical expansion to the preferred height.
                Dimension pref = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, pref.height);
            }
        };
        loginButtonPanel.setOpaque(false);
        loginButtonPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        loginButtonPanel.add(loginButton);

        // Create the overall right column using BorderLayout.
        JPanel rightColumn = new JPanel(new BorderLayout());
        rightColumn.add(topRightPanel, BorderLayout.NORTH);
        rightColumn.add(loginButtonPanel, BorderLayout.SOUTH);

        // --- Main Container ---
        // Create a container that holds the left and right columns side by side.
        JPanel listAndLoginContainer = new JPanel();
        listAndLoginContainer.setLayout(new BoxLayout(listAndLoginContainer, BoxLayout.X_AXIS));
        listAndLoginContainer.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        listAndLoginContainer.add(accountPanel); // Left column: accountPanel (already built)
        listAndLoginContainer.add(Box.createHorizontalStrut(14)); // Optionally, add a small horizontal gap
        listAndLoginContainer.add(rightColumn); // Right column: login buttons (with add buttons on top and login button at bottom)
        //add(listAndLoginContainer, BorderLayout.CENTER); // Add the main container to the dialog.
        add(headerPanel, BorderLayout.NORTH); // Add the main container to the dialog.
        add(listAndLoginContainer, BorderLayout.SOUTH); // Add the main container to the dialog.

        // set selection to login button
        getRootPane().setDefaultButton(loginButton);

		loginButton.addActionListener(ev -> attemptExistingLogin(accountList.getSelectedValue()));
		cancelButton.addActionListener(ev -> dispose());

		addOfflineButton.addActionListener(ev -> {
			Session newSession = OfflineLoginDialog.show(this, launcher);

 			if (newSession != null) {
				boolean accountExists = false;

				// Iterate through existing accounts using getSize() and getElementAt()
				AccountList accountList = launcher.getAccounts();
				for (int i = 0; i < accountList.getSize(); i++) {
					SavedSession savedSession = accountList.getElementAt(i);
					if (savedSession.getUsername().equals(newSession.getName())
							&& savedSession.getType().equals(UserType.LEGACY)) {
						// If the account already exists, update it
						launcher.getAccounts().update(newSession.toSavedSession());
						accountExists = true;
						System.out.println("Account already exists, updating.");
						break;
					}
				}

				// If the account does not exist, add it as a new session
				if (!accountExists) {
					launcher.getAccounts().add(newSession.toSavedSession());
					System.out.println("Adding a new account.");
				}

 				setResult(newSession);
 			}
 		});

		addMojangButton.addActionListener(ev -> {
			Session newSession = LoginDialog.showLoginRequest(this, launcher);

			if (newSession != null) {
				launcher.getAccounts().update(newSession.toSavedSession());
				setResult(newSession);
			}
		});

		addMicrosoftButton.addActionListener(ev -> attemptMicrosoftLogin(SharedLocale.tr("login.microsoft.seeBrowser")));

		offlineButton.addActionListener(ev ->
				setResult(new OfflineSession(launcher.getProperties().getProperty("offlinePlayerName"))));

		removeSelected.addActionListener(ev -> {
			if (accountList.getSelectedValue() != null) {
				boolean confirmed = SwingHelper.confirmDialog(this, SharedLocale.tr("accounts.confirmForget"),
						SharedLocale.tr("accounts.confirmForgetTitle"));

				if (confirmed) {
					launcher.getAccounts().remove(accountList.getSelectedValue());
				}
			}
		});

		accountList.setSelectedIndex(0);
	}

	@Override
	public void dispose() {
		accountList.setModel(new DefaultListModel<>());
		super.dispose();
	}

	public static Session showAccountRequest(Window owner, Launcher launcher) {
		AccountSelectDialog dialog = new AccountSelectDialog(owner, launcher);
		dialog.setVisible(true);

		if (dialog.selected != null && dialog.selected.isOnline()) {
			launcher.getAccounts().update(dialog.selected.toSavedSession());
		}

		Persistence.commitAndForget(launcher.getAccounts());

		return dialog.selected;
	}

	private void setResult(Session result) {
		this.selected = result;
		dispose();
	}

	private void attemptMicrosoftLogin(String status) {
		SettableProgress progress = new SettableProgress(status, -1);

		ListenableFuture<?> future = launcher.getExecutor().submit(() -> {
			Session newSession = launcher.getMicrosoftLogin().login(() ->
					progress.set(SharedLocale.tr("login.loggingInStatus"), -1));

			if (newSession != null) {
				launcher.getAccounts().update(newSession.toSavedSession());
				setResult(newSession);
			}

			return null;
		});

		ProgressDialog.showProgress(this, future, progress,
				SharedLocale.tr("login.loggingInTitle"), status);
		SwingHelper.addErrorDialogCallback(this, future);
	}

	private void attemptExistingLogin(SavedSession session) {
		if (session == null) return;

		// local (offline) accounts have no session to restore: start them directly
		if (accountList.getSelectedValue() != null) {
			if (session.getType().equals(UserType.LEGACY)) {
				setResult(new OfflineSession(session.getUsername()));
				return;
			}
		}

		LoginService loginService = launcher.getLoginService(session.getType());
		RestoreSessionCallable callable = new RestoreSessionCallable(loginService, session);

		ObservableFuture<Session> future = new ObservableFuture<>(launcher.getExecutor().submit(callable), callable);
		Futures.addCallback(future, new FutureCallback<Session>() {
			@Override
			public void onSuccess(Session result) {
				setResult(result);
			}

			@Override
			public void onFailure(Throwable t) {
				if (t instanceof AuthenticationException && ((AuthenticationException) t).isInvalidatedSession()) {
					// Just need to log in again
					relogin(session, t.getLocalizedMessage());
				} else {
					SwingHelper.showErrorDialog(AccountSelectDialog.this, t.getLocalizedMessage(), SharedLocale.tr("errorTitle"), t);
				}
			}
		}, SwingExecutor.INSTANCE);

		ProgressDialog.showProgress(this, future, SharedLocale.tr("login.loggingInTitle"),
				SharedLocale.tr("login.loggingInStatus"));
	}

	/**
	 * Re-login to an expired session
	 */
	private void relogin(SavedSession session, String message) {
		if (session.getType() == UserType.MICROSOFT) {
			this.attemptMicrosoftLogin(message);
		} else {
			LoginDialog.ReloginDetails details = new LoginDialog.ReloginDetails(session.getUsername(),
					SharedLocale.tr("login.relogin", message));
			Session newSession = LoginDialog.showLoginRequest(AccountSelectDialog.this, launcher, details);

			launcher.getAccounts().update(newSession.toSavedSession());
			setResult(newSession);
		}
	}

	@RequiredArgsConstructor
	private static class RestoreSessionCallable implements Callable<Session>, ProgressObservable {
		private final LoginService service;
		private final SavedSession session;

		@Override
		public Session call() throws Exception {
			return service.restore(session);
		}

		@Override
		public String getStatus() {
			return SharedLocale.tr("accounts.refreshingStatus");
		}

		@Override
		public double getProgress() {
			return -1;
		}
	}

    private static class AccountRenderer extends JPanel implements ListCellRenderer<SavedSession> {
        private final JLabel nameLabel;   // label for username (with avatar)
        private final JLabel typeIconLabel; // label for the account type icon

        // define type icons
        private final static Icon microsoftType = SwingHelper.createIcon(Launcher.class, "ui/icon/color/ms.png");
        private final static Icon mojangType = SwingHelper.createIcon(Launcher.class, "ui/icon/color/mj.png");
        private final static Icon localType = SwingHelper.createIcon(Launcher.class, "ui/icon/light/user.png");

        public AccountRenderer() {
            setLayout(new BorderLayout(5, 5)); // add spacing between components

            nameLabel = new JLabel();
            typeIconLabel = new JLabel();

            nameLabel.setHorizontalAlignment(SwingConstants.LEFT);
            typeIconLabel.setHorizontalAlignment(SwingConstants.RIGHT);

            typeIconLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8)); // add padding on the right

            add(nameLabel, BorderLayout.CENTER); // username with avatar
            add(typeIconLabel, BorderLayout.EAST); // account type icon at the end
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends SavedSession> list, SavedSession value, int index, boolean isSelected, boolean cellHasFocus) {
            // set username text
            nameLabel.setText(value.getUsername());

            // set avatar image
            if (value.getAvatarImage() != null) {
                nameLabel.setIcon(new ImageIcon(value.getAvatarImage())); // avatar (existing)
            } else {
                nameLabel.setIcon(SwingHelper.createIcon(Launcher.class, "default_skin.png", 32, 32));
            }

            // set icon based on value.getType()
            Icon typeIcon = null;
            if (value.getType() == UserType.MICROSOFT) {
                typeIcon = microsoftType;
            } else if (value.getType() == UserType.MOJANG) {
                typeIcon = mojangType;
            } else {
                typeIcon = localType;
            }
            typeIconLabel.setIcon(typeIcon);

            // apply selection background and foreground
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
                nameLabel.setForeground(list.getSelectionForeground());
                typeIconLabel.setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
                nameLabel.setForeground(list.getForeground());
                typeIconLabel.setForeground(list.getForeground());
            }

            setOpaque(true);
            return this;
        }
    }

    // helper method to set uniform size
    private void setButtonSize(JButton button) {
        Dimension size = new Dimension(minButtonWidth, minButtonHeight);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, minButtonHeight)); // Allows expansion only horizontally
    }
}
