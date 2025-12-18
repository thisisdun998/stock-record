package com.github.thisisdun998.stockrecord.statusbar;

import com.github.thisisdun998.stockrecord.model.StockIndex;
import com.github.thisisdun998.stockrecord.model.StockIndexQuote;
import com.github.thisisdun998.stockrecord.service.SinaStockService;
import com.github.thisisdun998.stockrecord.service.StockService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class StockIndexStatusBarWidget implements CustomStatusBarWidget {

    private final Project project;
    private final JPanel panel;
    private final JComboBox<StockIndex> comboBox;
    private final JLabel label;
    private final StockService stockService = new SinaStockService();
    private ScheduledFuture<?> pollingTask;

    public StockIndexStatusBarWidget(Project project) {
        this.project = project;
        this.panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        this.comboBox = new JComboBox<>(StockIndex.values());
        this.label = new JLabel("——");

        panel.add(new JLabel("指数:"));
        panel.add(comboBox);
        panel.add(label);

        comboBox.addActionListener(e -> restartPolling());

        restartPolling();
    }

    private void restartPolling() {
        stopPolling();
        StockIndex index = (StockIndex) comboBox.getSelectedItem();
        if (index == null) {
            return;
        }
        pollingTask = AppExecutorUtil.getAppScheduledExecutorService()
                .scheduleWithFixedDelay(() -> updateIndex(index), 0, 3, TimeUnit.SECONDS);
    }

    private void stopPolling() {
        if (pollingTask != null) {
            pollingTask.cancel(false);
            pollingTask = null;
        }
    }

    private void updateIndex(StockIndex index) {
        StockIndexQuote quote = stockService.getIndexQuote(index);
        SwingUtilities.invokeLater(() -> {
            String text = String.format(
                    "%s %.2f  涨跌: %.2f  涨跌幅: %.2f%%",
                    quote.getDisplayName(),
                    quote.getPoints(),
                    quote.getChange(),
                    quote.getChangePercent()
            );
            label.setText(text);
            if (quote.getChange() > 0) {
                label.setForeground(new Color(0xCC0000));
            } else if (quote.getChange() < 0) {
                label.setForeground(new Color(0x008000));
            } else {
                label.setForeground(UIManager.getColor("Label.foreground"));
            }
        });
    }

    @Override
    public @NotNull String ID() {
        return "stockIndexStatusBarWidget";
    }

    @Override
    public @Nullable JComponent getComponent() {
        return panel;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
        // no-op
    }

    @Override
    public void dispose() {
        stopPolling();
    }
}
