package dev.learning.fashionagent.account;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.springframework.stereotype.Service;

@Service
public class NativeDirectoryPicker {

    public synchronized String choose(String initialDirectory) {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("当前运行环境没有桌面界面，无法打开文件夹选择窗口");
        }
        AtomicReference<String> selected = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> selected.set(showChooser(initialDirectory)));
            return selected.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("文件夹选择已中断", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException("无法打开文件夹选择窗口：" + cause.getMessage(), cause);
        }
    }

    private String showChooser(String initialDirectory) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        JFileChooser chooser = new JFileChooser(resolveInitialDirectory(initialDirectory));
        chooser.setDialogTitle("选择 Atelier Flow 文件夹");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setMultiSelectionEnabled(false);
        JFrame owner = new JFrame();
        owner.setUndecorated(true);
        owner.setAlwaysOnTop(true);
        owner.setSize(1, 1);
        owner.setLocationRelativeTo(null);
        try {
            owner.setVisible(true);
            if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) return null;
            File directory = chooser.getSelectedFile();
            return directory == null ? null : directory.toPath().toAbsolutePath().normalize().toString();
        } finally {
            owner.dispose();
        }
    }

    private File resolveInitialDirectory(String configured) {
        if (configured != null && !configured.isBlank()) {
            try {
                Path path = Path.of(configured.trim()).toAbsolutePath().normalize();
                if (Files.isDirectory(path)) return path.toFile();
                Path parent = path.getParent();
                if (parent != null && Files.isDirectory(parent)) return parent.toFile();
            } catch (RuntimeException ignored) {}
        }
        return Path.of(System.getProperty("user.home", ".")).toAbsolutePath().normalize().toFile();
    }
}
