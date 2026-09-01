package dev.learning.stockanalyzer.service;

import dev.learning.stockanalyzer.data.StockCodeUtils;
import dev.learning.stockanalyzer.data.StockDataService;
import dev.learning.stockanalyzer.data.StockQuote;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class DesktopTickerService {

    private static final Logger log = LoggerFactory.getLogger(DesktopTickerService.class);
    private static final Color RISE_COLOR = new Color(230, 45, 45);
    private static final Color FALL_COLOR = new Color(20, 170, 105);

    private final StockDataService stockDataService;
    private final MonitorLicenseService monitorLicenseService;
    private final ScheduledExecutorService refreshExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "desktop-stock-ticker");
        thread.setDaemon(true);
        return thread;
    });

    private volatile List<String> selectedCodes = List.of();
    private volatile JWindow window;
    private volatile ScheduledFuture<?> refreshTask;
    private volatile boolean userPositioned;
    private volatile int intervalSeconds = 5;

    public DesktopTickerService(StockDataService stockDataService,
                                MonitorLicenseService monitorLicenseService) {
        this.stockDataService = stockDataService;
        this.monitorLicenseService = monitorLicenseService;
    }

    public synchronized MonitorStatus start(List<String> codes, Integer requestedIntervalSeconds) {
        monitorLicenseService.assertMonitorAvailable();
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("当前运行环境不支持桌面悬浮窗");
        }
        if (codes == null || codes.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一只盯盘股票");
        }

        List<String> normalizedCodes = codes.stream()
                .map(StockCodeUtils::normalizeFullCode)
                .distinct()
                .limit(12)
                .toList();
        List<StockQuote> quotes = stockDataService.getQuotes(normalizedCodes);
        if (quotes.isEmpty()) {
            throw new IllegalStateException("暂时无法获取所选股票行情");
        }

        int normalizedInterval = normalizeIntervalSeconds(requestedIntervalSeconds);
        selectedCodes = normalizedCodes;
        intervalSeconds = normalizedInterval;
        render(quotes);
        if (refreshTask != null) refreshTask.cancel(false);
        refreshTask = refreshExecutor.scheduleWithFixedDelay(
                this::refreshSafely,
                normalizedInterval,
                normalizedInterval,
                TimeUnit.SECONDS);
        return status();
    }

    public synchronized MonitorStatus stop() {
        selectedCodes = List.of();
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
        disposeWindow();
        return status();
    }

    public MonitorStatus status() {
        JWindow currentWindow = window;
        return new MonitorStatus(
                currentWindow != null && currentWindow.isVisible(),
                selectedCodes,
                selectedCodes.size(),
                intervalSeconds);
    }

    private void refreshSafely() {
        try {
            List<String> codes = selectedCodes;
            if (codes.isEmpty()) return;
            monitorLicenseService.assertMonitorAvailable();
            List<StockQuote> quotes = stockDataService.getQuotes(codes);
            if (!quotes.isEmpty()) render(quotes);
        } catch (FeatureUnavailableException e) {
            log.info("桌面盯盘授权已失效，停止刷新行情");
            stop();
        } catch (Exception e) {
            log.warn("刷新桌面盯盘行情失败", e);
        }
    }

    private void render(List<StockQuote> quotes) {
        runOnEdt(() -> {
            if (window == null) {
                window = createWindow();
            }
            Container content = window.getContentPane();
            content.removeAll();

            JPanel tickerPanel = new JPanel();
            tickerPanel.setOpaque(false);
            tickerPanel.setLayout(new BoxLayout(tickerPanel, BoxLayout.Y_AXIS));
            tickerPanel.setBorder(new EmptyBorder(5, 8, 5, 8));

            MouseAdapter dragHandler = dragHandler(window);
            tickerPanel.addMouseListener(dragHandler);
            tickerPanel.addMouseMotionListener(dragHandler);

            for (StockQuote quote : quotes) {
                String sign = quote.changePercent() > 0 ? "+" : "";
                JLabel label = new JLabel("%s  %.2f  %s%.2f%%".formatted(
                        quote.name(), quote.currentPrice(), sign, quote.changePercent()));
                label.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
                label.setForeground(quote.changePercent() >= 0 ? RISE_COLOR : FALL_COLOR);
                label.setOpaque(false);
                label.setAlignmentX(Component.RIGHT_ALIGNMENT);
                label.setBorder(new EmptyBorder(2, 0, 2, 0));
                label.addMouseListener(dragHandler);
                label.addMouseMotionListener(dragHandler);
                tickerPanel.add(label);
            }

            content.add(tickerPanel);
            window.pack();
            if (!userPositioned) positionAtTopRight(window);
            window.setVisible(true);
            window.setAlwaysOnTop(true);
            window.revalidate();
            window.repaint();
        });
    }

    private JWindow createWindow() {
        GraphicsDevice device = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getDefaultScreenDevice();
        if (!device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
            throw new IllegalStateException("当前桌面不支持透明悬浮窗");
        }

        JWindow tickerWindow = new JWindow();
        tickerWindow.setBackground(new Color(0, 0, 0, 0));
        tickerWindow.setAlwaysOnTop(true);
        tickerWindow.setFocusableWindowState(false);
        tickerWindow.getRootPane().setOpaque(false);
        ((JComponent) tickerWindow.getContentPane()).setOpaque(false);
        return tickerWindow;
    }

    private MouseAdapter dragHandler(Window tickerWindow) {
        return new MouseAdapter() {
            private Point pressedAt;
            private Point windowAt;

            @Override
            public void mousePressed(MouseEvent event) {
                pressedAt = event.getLocationOnScreen();
                windowAt = tickerWindow.getLocation();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (pressedAt == null || windowAt == null) return;
                Point current = event.getLocationOnScreen();
                tickerWindow.setLocation(
                        windowAt.x + current.x - pressedAt.x,
                        windowAt.y + current.y - pressedAt.y);
                userPositioned = true;
            }
        };
    }

    private void positionAtTopRight(Window tickerWindow) {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration()
                .getBounds();
        tickerWindow.setLocation(
                screen.x + screen.width - tickerWindow.getWidth() - 28,
                screen.y + 32);
    }

    private void runOnEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(task);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("启动桌面盯盘被中断", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("启动桌面盯盘失败", cause);
        }
    }

    private void disposeWindow() {
        JWindow currentWindow = window;
        window = null;
        userPositioned = false;
        if (currentWindow != null) {
            SwingUtilities.invokeLater(() -> {
                currentWindow.setVisible(false);
                currentWindow.dispose();
            });
        }
    }

    @PreDestroy
    void shutdown() {
        stop();
        refreshExecutor.shutdownNow();
    }

    static int normalizeIntervalSeconds(Integer value) {
        int interval = value == null ? 5 : value;
        if (interval < 1 || interval > 300) {
            throw new IllegalArgumentException("盯盘刷新频率必须在 1 到 300 秒之间");
        }
        return interval;
    }

    public record MonitorStatus(boolean active, List<String> codes, int count, int intervalSeconds) {
    }
}
