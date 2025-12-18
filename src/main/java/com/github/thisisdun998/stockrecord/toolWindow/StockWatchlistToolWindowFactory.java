package com.github.thisisdun998.stockrecord.toolWindow;

import com.github.thisisdun998.stockrecord.StockRecordIcons;
import com.github.thisisdun998.stockrecord.model.StockQuote;
import com.github.thisisdun998.stockrecord.model.StockIndex;
import com.github.thisisdun998.stockrecord.model.StockIndexQuote;
import com.github.thisisdun998.stockrecord.persistence.StockWatchlistStateService;
import com.github.thisisdun998.stockrecord.service.SinaStockService;
import com.github.thisisdun998.stockrecord.service.StockService;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.table.JBTable;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class StockWatchlistToolWindowFactory implements ToolWindowFactory {

    private static final String[] COLUMN_NAMES = {
            "股票名称", "股票代码", "当前价格", "涨跌幅(%)"
    };

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
//        toolWindow.setIcon(StockRecordIcons.TOOL_WINDOW);
        StockWatchlistPanel panel = new StockWatchlistPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel.getComponent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }

    private static final class StockWatchlistPanel {

        private final Project project;
        private final JPanel mainPanel;
        private final JBTable table;
        private final DefaultTableModel tableModel;
        private final StockService stockService = new SinaStockService();
        private ScheduledFuture<?> pollingTask;
        private ScheduledFuture<?> indexPollingTask;
        private boolean syncing = false;
        private JComboBox<StockIndex> indexComboBox;
        private JLabel indexCodeLabel;
        private JLabel indexPointsLabel;
        private JLabel indexChangeLabel;

        StockWatchlistPanel(Project project) {
            this.project = project;
            this.tableModel = new DefaultTableModel(COLUMN_NAMES, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            this.table = new JBTable(tableModel);
            this.table.setDefaultRenderer(Object.class, new ChangePercentRenderer());
            this.mainPanel = new JPanel(new BorderLayout());

            // 清理无效数据
            StockWatchlistStateService stateService = StockWatchlistStateService.getInstance(project);
            stateService.cleanupInvalidData();

            JComponent toolbar = createToolbar();
            JScrollPane scrollPane = new JBScrollPane(table);
            JComponent indexPanel = createIndexPanel();

            mainPanel.add(toolbar, BorderLayout.NORTH);
            mainPanel.add(scrollPane, BorderLayout.CENTER);
            mainPanel.add(indexPanel, BorderLayout.SOUTH);

            refreshData();
            startIndexPolling();
        }

        JComponent getComponent() {
            return mainPanel;
        }

        private JComponent createToolbar() {
            DefaultActionGroup actionGroup = new DefaultActionGroup();
            actionGroup.add(new RefreshAction());
            actionGroup.add(new ToggleSyncAction());
            actionGroup.add(new AddStockAction());
            actionGroup.add(new RemoveStockAction());

            ActionToolbar toolbar = ActionManager.getInstance()
                    .createActionToolbar("StockWatchlistToolbar", actionGroup, true);
            toolbar.setTargetComponent(mainPanel);
            return toolbar.getComponent();
        }

        private void refreshData() {
            StockWatchlistStateService stateService = StockWatchlistStateService.getInstance(project);
            List<StockWatchlistStateService.StockItemState> stocks = stateService.getStocks();
            List<String> codes = stocks.stream().map(s -> s.code).toList();

            if (codes.isEmpty()) {
                SwingUtilities.invokeLater(() -> tableModel.setRowCount(0));
                return;
            }

            AppExecutorUtil.getAppExecutorService().submit(() -> {
                List<StockQuote> quotes = stockService.getQuotes(codes);
                SwingUtilities.invokeLater(() -> {
                    tableModel.setRowCount(0);
                    for (StockQuote quote : quotes) {
                        // 优先显示本地自定义名称，其次使用接口返回名称
                        String displayName = stocks.stream()
                                .filter(s -> s.code.equals(quote.getCode()))
                                .map(s -> s.name)
                                .filter(n -> n != null && !n.isBlank())
                                .findFirst()
                                .orElse(quote.getName());

                        tableModel.addRow(new Object[]{
                                displayName,
                                quote.getCode(),
                                quote.getPrice(),
                                quote.getChangePercent()
                        });
                    }
                });
            });
        }

        private void startSync() {
            if (pollingTask != null && !pollingTask.isDone()) {
                return;
            }
            pollingTask = AppExecutorUtil.getAppScheduledExecutorService()
                    .scheduleWithFixedDelay(this::refreshData, 0, 3, TimeUnit.SECONDS);
        }

        private void stopSync() {
            if (pollingTask != null) {
                pollingTask.cancel(false);
                pollingTask = null;
            }
        }

        private JComponent createIndexPanel() {
            JPanel panel = new JPanel(new GridLayout(1, 4));
            panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            
            // 第一列：指数下拉框（对应股票名称列）
            indexComboBox = new JComboBox<>(StockIndex.values());
            
            // 第二列：指数代码
            indexCodeLabel = new JLabel("", SwingConstants.CENTER);
            
            // 第三列：当前点数
            indexPointsLabel = new JLabel("", SwingConstants.CENTER);
            
            // 第四列：涨跌幅
            indexChangeLabel = new JLabel("", SwingConstants.CENTER);
            
            panel.add(indexComboBox);
            panel.add(indexCodeLabel);
            panel.add(indexPointsLabel);
            panel.add(indexChangeLabel);
            
            indexComboBox.addActionListener(e -> restartIndexPolling());
            
            return panel;
        }

        private void startIndexPolling() {
            StockIndex index = (StockIndex) indexComboBox.getSelectedItem();
            if (index == null) {
                return;
            }
            indexPollingTask = AppExecutorUtil.getAppScheduledExecutorService()
                    .scheduleWithFixedDelay(() -> updateIndex(index), 0, 3, TimeUnit.SECONDS);
        }

        private void restartIndexPolling() {
            stopIndexPolling();
            startIndexPolling();
        }

        private void stopIndexPolling() {
            if (indexPollingTask != null) {
                indexPollingTask.cancel(false);
                indexPollingTask = null;
            }
        }

        private void updateIndex(StockIndex index) {
            StockIndexQuote quote = stockService.getIndexQuote(index);
            SwingUtilities.invokeLater(() -> {
                // 第二列：指数代码
                indexCodeLabel.setText(index.getSinaCode());
                
                // 第三列：当前点数
                indexPointsLabel.setText(String.format("%.2f", quote.getPoints()));
                
                // 第四列：涨跌幅
                String changeText = String.format("%.2f%%", quote.getChangePercent());
                indexChangeLabel.setText(changeText);
                
                // 涨跌幅颜色
                Color color;
                if (quote.getChange() > 0) {
                    color = new Color(0xCC0000); // 红色
                } else if (quote.getChange() < 0) {
                    color = new Color(0x008000); // 绿色
                } else {
                    color = UIManager.getColor("Label.foreground");
                }
                indexChangeLabel.setForeground(color);
            });
        }

        private final class RefreshAction extends AnAction {
            RefreshAction() {
                super("刷新", "刷新自选股行情", AllIcons.Actions.Refresh);
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                refreshData();
            }
        }

        private final class ToggleSyncAction extends ToggleAction {
            ToggleSyncAction() {
                super("同步/暂停", "开启或暂停自动同步", AllIcons.Actions.Pause);
            }

            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return syncing;
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                syncing = state;
                if (syncing) {
                    startSync();
                } else {
                    stopSync();
                }
            }
        }

        private final class AddStockAction extends AnAction {
            AddStockAction() {
                super("添加自选", "添加自选股票", AllIcons.General.Add);
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                StockSearchDialog dialog = new StockSearchDialog(project, stockService, StockWatchlistPanel.this::refreshData);
                dialog.show();
            }
        }

        private final class RemoveStockAction extends AnAction {
            RemoveStockAction() {
                super("删除自选", "删除选中的自选股票", AllIcons.General.Remove);
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                int selectedRow = table.getSelectedRow();
                if (selectedRow < 0) {
                    return;
                }
                StockWatchlistStateService stateService = StockWatchlistStateService.getInstance(project);
                stateService.removeStock(selectedRow);
                refreshData();
            }
        }

        private static final class ChangePercentRenderer extends DefaultTableCellRenderer {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (column == 3 && value instanceof Number number) {
                    double v = number.doubleValue();
                    if (v > 0) {
                        c.setForeground(new Color(0xCC0000));
                    } else if (v < 0) {
                        c.setForeground(new Color(0x008000));
                    } else {
                        c.setForeground(table.getForeground());
                    }
                    setText(String.format("%.2f", v));
                } else {
                    c.setForeground(table.getForeground());
                }
                return c;
            }
        }
    }
}
