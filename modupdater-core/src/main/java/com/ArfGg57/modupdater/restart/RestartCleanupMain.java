package com.ArfGg57.modupdater.restart;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Standalone helper that deletes provided files then shows restart-required dialog. */
public class RestartCleanupMain {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) return;
        List<File> filesToDelete = readList(args[0]);
        String message = args.length > 1 ? args[1] : "Modpack update requires a restart.";
        deleteFiles(filesToDelete);
        SwingUtilities.invokeLater(() -> showDialog(filesToDelete, message));
    }
    private static List<File> readList(String listPath) throws Exception {
        List<File> files = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(listPath), StandardCharsets.UTF_8)) {
            String line; while ((line = reader.readLine()) != null) if (!line.trim().isEmpty()) files.add(new File(line.trim()));
        }
        return files;
    }
    private static void deleteFiles(List<File> files) { for (File f : files) if (f.exists()) try { Files.deleteIfExists(f.toPath()); } catch (Exception ex) { System.err.println("[RestartCleanup] Failed to delete " + f + ": " + ex); } }
    private static void showDialog(List<File> files, String message) {
        JFrame frame = new JFrame("ModUpdater"); frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); frame.setMinimumSize(new Dimension(420,260));
        JLabel headline = new JLabel(message); headline.setFont(headline.getFont().deriveFont(Font.BOLD,16f));
        JTextArea details = new JTextArea(); details.setEditable(false); details.setText("Removed files:\n" + format(files) + "\n\nPlease relaunch the game to finish the update.");
        JButton close = new JButton("Close"); close.addActionListener(e -> frame.dispose());
        JPanel content = new JPanel(new BorderLayout(8,8)); content.add(headline,BorderLayout.NORTH); content.add(new JScrollPane(details),BorderLayout.CENTER); content.add(close,BorderLayout.SOUTH);
        frame.setContentPane(content); frame.pack(); frame.setLocationRelativeTo(null); frame.setVisible(true);
        frame.addWindowListener(new WindowAdapter(){ @Override public void windowClosed(WindowEvent e){ System.exit(0);} });
    }
    private static String format(List<File> files){ if(files.isEmpty()) return "(none)"; StringBuilder sb=new StringBuilder(); for(File f:files) sb.append(" - ").append(f.getAbsolutePath()).append('\n'); return sb.toString(); }
}

