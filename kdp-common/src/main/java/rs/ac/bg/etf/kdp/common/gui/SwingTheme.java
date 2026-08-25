package rs.ac.bg.etf.kdp.common.gui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;

/** Shared visual settings for the three Swing applications. */
public final class SwingTheme {

    public static final Color BACKGROUND = new Color(244, 246, 249);
    public static final Color SURFACE = Color.WHITE;
    public static final Color PRIMARY = new Color(36, 92, 147);
    public static final Color SUCCESS = new Color(36, 122, 72);
    public static final Color DANGER = new Color(177, 55, 55);
    public static final Color MUTED_TEXT = new Color(88, 96, 105);

    private SwingTheme() {
    }

    public static void install() {
        try {
            for (UIManager.LookAndFeelInfo lookAndFeel : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(lookAndFeel.getName())) {
                    UIManager.setLookAndFeel(lookAndFeel.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {
            // The platform look and feel is a safe fallback.
        }

        Font controlFont = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        UIManager.put("Label.font", controlFont);
        UIManager.put("Button.font", controlFont.deriveFont(Font.BOLD));
        UIManager.put("TextField.font", controlFont);
        UIManager.put("TextArea.font", controlFont);
        UIManager.put("Table.font", controlFont);
        UIManager.put("Table.rowHeight", 28);
        UIManager.put("TabbedPane.font", controlFont);
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("OptionPane.messageFont", controlFont);
        UIManager.put("OptionPane.buttonFont", controlFont.deriveFont(Font.BOLD));
    }

    public static Border cardBorder(String title) {
        Border line = BorderFactory.createLineBorder(new Color(216, 221, 228));
        Border padding = BorderFactory.createEmptyBorder(12, 14, 14, 14);
        Border card = BorderFactory.createCompoundBorder(line, padding);
        return BorderFactory.createTitledBorder(
                card,
                title,
                0,
                0,
                new Font(Font.SANS_SERIF, Font.BOLD, 14),
                new Color(42, 48, 55));
    }

    public static void markPrimary(javax.swing.JButton button) {
        button.setBackground(PRIMARY);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setMargin(new Insets(7, 14, 7, 14));
    }

    public static void setAccessibleDescription(JComponent component, String description) {
        component.getAccessibleContext().setAccessibleDescription(description);
    }
}
