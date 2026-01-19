package com.arbitragepro;

import com.arbitragepro.api.ActiveTrade;
import com.arbitragepro.api.Recommendation;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

@Slf4j
public class ArbitrageProPanel extends PluginPanel {
    private final ArbitrageProPlugin plugin;

    // Components
    private final JLabel statusLabel;
    private final JButton loginButton;
    private final JButton getRecommendationButton;
    private final JPanel recommendationPanel;
    private final JPanel activeTradePanel;

    public ArbitrageProPanel(ArbitrageProPlugin plugin) {
        super(false);
        this.plugin = plugin;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(new EmptyBorder(0, 0, 10, 0));

        JLabel titleLabel = new JLabel("Arbitrage Pro");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        headerPanel.add(titleLabel, BorderLayout.NORTH);

        statusLabel = new JLabel("Not logged in");
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setForeground(Color.GRAY);
        headerPanel.add(statusLabel, BorderLayout.CENTER);

        add(headerPanel, BorderLayout.NORTH);

        // Main content panel
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        // Login button
        loginButton = new JButton("Login");
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginButton.addActionListener(e -> onLoginClick());
        contentPanel.add(loginButton);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Get Recommendation button
        getRecommendationButton = new JButton("Get Recommendation");
        getRecommendationButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        getRecommendationButton.setEnabled(false);
        getRecommendationButton.addActionListener(e -> plugin.fetchRecommendation());
        contentPanel.add(getRecommendationButton);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // Recommendation display panel
        recommendationPanel = new JPanel();
        recommendationPanel.setLayout(new BoxLayout(recommendationPanel, BoxLayout.Y_AXIS));
        recommendationPanel.setBorder(BorderFactory.createTitledBorder("Current Recommendation"));
        recommendationPanel.setVisible(false);
        contentPanel.add(recommendationPanel);

        // Active trade panel
        activeTradePanel = new JPanel();
        activeTradePanel.setLayout(new BoxLayout(activeTradePanel, BoxLayout.Y_AXIS));
        activeTradePanel.setBorder(BorderFactory.createTitledBorder("Active Trade"));
        activeTradePanel.setVisible(false);
        contentPanel.add(activeTradePanel);

        add(contentPanel, BorderLayout.CENTER);
    }

    private void onLoginClick() {
        // In a real implementation, this would open a login dialog
        // For now, it uses config values
        String email = plugin.config.email();
        String password = plugin.config.password();

        if (email.isEmpty() || password.isEmpty()) {
            showError("Please set email and password in plugin config");
            return;
        }

        loginButton.setEnabled(false);
        loginButton.setText("Logging in...");
        plugin.login(email, password);
    }

    public void onLoginSuccess() {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Logged in");
            statusLabel.setForeground(new Color(0, 150, 0));
            loginButton.setVisible(false);
            getRecommendationButton.setEnabled(true);
        });
    }

    public void onLoginError(String message) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Login failed");
            statusLabel.setForeground(Color.RED);
            loginButton.setEnabled(true);
            loginButton.setText("Login");
            showError(message);
        });
    }

    public void displayRecommendation(Recommendation rec) {
        SwingUtilities.invokeLater(() -> {
            recommendationPanel.removeAll();
            recommendationPanel.setVisible(true);

            addInfoRow(recommendationPanel, "Item", rec.getItem_name());
            addInfoRow(recommendationPanel, "Buy Price", formatGp(rec.getBuy_price()));
            addInfoRow(recommendationPanel, "Sell Price", formatGp(rec.getSell_price()));
            addInfoRow(recommendationPanel, "Quantity", String.valueOf(rec.getBuy_quantity()));
            addInfoRow(recommendationPanel, "Expected Profit", formatGp(rec.getExpected_profit()));
            addInfoRow(recommendationPanel, "ROI", String.format("%.2f%%", rec.getExpected_roi_percent()));
            addInfoRow(recommendationPanel, "ML Score", String.format("%.4f", rec.getMl_score()));
            addInfoRow(recommendationPanel, "GE Limit", String.valueOf(rec.getGe_limit()));

            JLabel instructionLabel = new JLabel("<html><div style='padding:10px; background:#f0f0f0;'>" +
                    "<b>Instructions:</b><br/>" +
                    "1. Go to GE<br/>" +
                    "2. Place BUY order at EXACT price<br/>" +
                    "3. Plugin will auto-track your trade<br/>" +
                    "</div></html>");
            instructionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            instructionLabel.setBorder(new EmptyBorder(5, 0, 0, 0));
            recommendationPanel.add(instructionLabel);

            recommendationPanel.revalidate();
            recommendationPanel.repaint();
        });
    }

    public void displayActiveTrade(ActiveTrade trade) {
        SwingUtilities.invokeLater(() -> {
            activeTradePanel.removeAll();
            activeTradePanel.setVisible(true);

            addInfoRow(activeTradePanel, "Item", trade.getItem_name());
            addInfoRow(activeTradePanel, "Status", trade.getStatus().toUpperCase());
            addInfoRow(activeTradePanel, "Buy Price", formatGp(trade.getBuy_price()));

            if (trade.getStatus().equals("bought")) {
                addInfoRow(activeTradePanel, "Sell Price", formatGp(trade.getSell_price()));
                addInfoRow(activeTradePanel, "Quantity", String.valueOf(trade.getBuy_quantity_filled()));

                JLabel sellLabel = new JLabel("<html><div style='padding:10px; background:#e8f5e9;'>" +
                        "<b style='color:green;'>READY TO SELL!</b><br/>" +
                        "Place SELL order at: <b>" + formatGp(trade.getSell_price()) + "</b>" +
                        "</div></html>");
                sellLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                sellLabel.setBorder(new EmptyBorder(5, 0, 0, 0));
                activeTradePanel.add(sellLabel);
            }

            activeTradePanel.revalidate();
            activeTradePanel.repaint();

            // Hide recommendation panel when there's an active trade
            recommendationPanel.setVisible(false);
        });
    }

    public void clearRecommendation() {
        SwingUtilities.invokeLater(() -> {
            recommendationPanel.setVisible(false);
            recommendationPanel.removeAll();
        });
    }

    public void clearActiveTrade() {
        SwingUtilities.invokeLater(() -> {
            activeTradePanel.setVisible(false);
            activeTradePanel.removeAll();
        });
    }

    public void showError(String message) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE)
        );
    }

    public void showNotification(String message) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, message, "Arbitrage Pro", JOptionPane.INFORMATION_MESSAGE)
        );
    }

    private void addInfoRow(JPanel panel, String label, String value) {
        JPanel row = new JPanel(new BorderLayout(5, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));

        JLabel labelComponent = new JLabel(label + ":");
        labelComponent.setFont(new Font("Arial", Font.BOLD, 12));

        JLabel valueComponent = new JLabel(value);
        valueComponent.setFont(new Font("Arial", Font.PLAIN, 12));

        row.add(labelComponent, BorderLayout.WEST);
        row.add(valueComponent, BorderLayout.EAST);

        panel.add(row);
    }

    private String formatGp(int amount) {
        if (amount >= 1_000_000) {
            return String.format("%.2fm gp", amount / 1_000_000.0);
        } else if (amount >= 1_000) {
            return String.format("%.1fk gp", amount / 1_000.0);
        } else {
            return amount + " gp";
        }
    }

    /**
     * Show price movement warning with option to proceed or cancel
     * Returns true if user wants to proceed anyway
     */
    public boolean showPriceMovedWarning(String message) {
        int result = JOptionPane.showConfirmDialog(
                this,
                message + "\n\nContinue anyway?",
                "⚠️ Price Alert",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        return result == JOptionPane.YES_OPTION;
    }

    /**
     * Show liquidity warning (non-blocking)
     */
    public void showLiquidityWarning(String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                "⚠️ Liquidity Warning",
                JOptionPane.WARNING_MESSAGE
        );
    }

    /**
     * Show API unavailable warning (non-blocking)
     */
    public void showAPIUnavailableWarning(String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                "⚠️ Validation Unavailable",
                JOptionPane.WARNING_MESSAGE
        );
    }
}
