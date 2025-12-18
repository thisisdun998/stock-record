package com.github.thisisdun998.stockrecord.toolWindow;

import com.github.thisisdun998.stockrecord.model.StockQuote;
import com.github.thisisdun998.stockrecord.persistence.StockWatchlistStateService;
import com.github.thisisdun998.stockrecord.service.StockService;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * 股票搜索对话框
 */
public class StockSearchDialog extends DialogWrapper {

    private static final String[] COLUMN_NAMES = {"股票代码", "股票名称", "操作"};
    
    private final Project project;
    private final StockService stockService;
    private final JTextField searchField;
    private final JBTable resultTable;
    private final DefaultTableModel tableModel;
    private final Runnable onStockAdded;

    public StockSearchDialog(@Nullable Project project, StockService stockService, Runnable onStockAdded) {
        super(project);
        this.project = project;
        this.stockService = stockService;
        this.onStockAdded = onStockAdded;
        
        this.searchField = new JTextField();
        this.tableModel = new DefaultTableModel(COLUMN_NAMES, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 2; // 只有操作列可编辑
            }
        };
        this.resultTable = new JBTable(tableModel);
        
        setTitle("搜索并添加股票");
        init();
        setupUI();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setPreferredSize(new Dimension(600, 400));
        
        // 搜索栏
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchPanel.add(new JLabel("搜索:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        JButton searchButton = new JButton("查询", AllIcons.Actions.Search);
        searchButton.addActionListener(e -> performSearch());
        searchPanel.add(searchButton, BorderLayout.EAST);
        
        // 结果表格
        JScrollPane scrollPane = new JBScrollPane(resultTable);
        
        panel.add(searchPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    private void setupUI() {
        // 搜索框实时过滤事件
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                performSearch();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                performSearch();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                performSearch();
            }
        });
        
        // 搜索框回车事件
        searchField.addActionListener(e -> performSearch());
        
        // 设置列宽
        resultTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        resultTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        resultTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        
        // 设置操作列的渲染器和编辑器
        resultTable.getColumn("操作").setCellRenderer(new ButtonRenderer());
        resultTable.getColumn("操作").setCellEditor(new ButtonEditor(new JCheckBox()));
        
        // 禁止列重排序
        resultTable.getTableHeader().setReorderingAllowed(false);
    }

    private void performSearch() {
        String keyword = searchField.getText();
        if (keyword == null || keyword.isBlank()) {
            // 清空搜索结果
            tableModel.setRowCount(0);
            return;
        }
        
        // 异步搜索
        AppExecutorUtil.getAppExecutorService().submit(() -> {
            try {
                List<StockQuote> results = stockService.searchStocks(keyword);
                SwingUtilities.invokeLater(() -> {
                    tableModel.setRowCount(0);
                    // 直接显示结果，不显示"未找到结果"
                    for (StockQuote quote : results) {
                        // 清理股票代码，移除.SZ/.SH/.XSHG/.XSHE等后缀
                        String cleanCode = cleanStockCode(quote.getCode());
                        
                        // 检查是否已在自选列表
                        StockWatchlistStateService stateService = StockWatchlistStateService.getInstance(project);
                        boolean isInWatchlist = stateService.containsStock(cleanCode);
                        String buttonText = isInWatchlist ? "删除" : "添加";
                        
                        tableModel.addRow(new Object[]{
                                cleanCode,
                                quote.getName(),
                                buttonText
                        });
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    tableModel.setRowCount(0);
                });
            }
        });
    }

    /**
     * 清理股票代码，移除各种后缀
     */
    private String cleanStockCode(String code) {
        if (code == null) {
            return "";
        }
        // 移除 .SZ, .SH, .XSHG, .XSHE 等后缀
        return code.replaceAll("\\.(SZ|SH|XSHG|XSHE)$", "");
    }

    /**
     * 按钮渲染器
     */
    private class ButtonRenderer extends DefaultTableCellRenderer {
        private final JButton button = new JButton();
        
        public ButtonRenderer() {
            button.setOpaque(true);
        }
        
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            String text = value != null ? value.toString() : "添加";
            button.setText(text);
            // 根据文本设置不同的图标
            if ("删除".equals(text)) {
                button.setIcon(AllIcons.General.Remove);
            } else {
                button.setIcon(AllIcons.General.Add);
            }
            return button;
        }
    }

    /**
     * 按钮编辑器
     */
    private class ButtonEditor extends DefaultCellEditor {
        private final JButton button;
        private String label;
        private int currentRow;

        public ButtonEditor(JCheckBox checkBox) {
            super(checkBox);
            button = new JButton();
            button.setOpaque(true);
            
            button.addActionListener(e -> {
                fireEditingStopped();
                addStock(currentRow);
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                                                     boolean isSelected, int row, int column) {
            label = (value == null) ? "添加" : value.toString();
            button.setText(label);
            // 根据文本设置不同的图标
            if ("删除".equals(label)) {
                button.setIcon(AllIcons.General.Remove);
            } else {
                button.setIcon(AllIcons.General.Add);
            }
            currentRow = row;
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            return label;
        }

        private void addStock(int row) {
            if (row < 0 || row >= tableModel.getRowCount()) {
                return;
            }
            
            String code = (String) tableModel.getValueAt(row, 0);
            String name = (String) tableModel.getValueAt(row, 1);
            String buttonText = (String) tableModel.getValueAt(row, 2);
            
            // 防止添加空数据或错误数据
            if (code == null || code.isBlank() || name == null || name.isBlank() || buttonText == null || buttonText.isBlank()) {
                return;
            }
            
            if (code != null && !code.isBlank()) {
                StockWatchlistStateService stateService = StockWatchlistStateService.getInstance(project);
                
                if ("删除".equals(buttonText)) {
                    // 从自选列表中删除
                    stateService.removeStockByCode(code);
                    // 更新按钮文本为"添加"
                    tableModel.setValueAt("添加", row, 2);
                } else {
                    // 添加到自选列表
                    stateService.addStock(name != null ? name : "", code);
                    // 更新按钮文本为"删除"
                    tableModel.setValueAt("删除", row, 2);
                }
                
                // 通知刷新自选列表
                if (onStockAdded != null) {
                    onStockAdded.run();
                }
            }
        }
    }

    @Override
    protected Action @Nullable [] createActions() {
        return new Action[]{getCancelAction()};
    }
}
