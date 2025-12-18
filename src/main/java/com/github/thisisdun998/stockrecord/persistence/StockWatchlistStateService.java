package com.github.thisisdun998.stockrecord.persistence;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@State(
        name = "StockWatchlistState",
        storages = @Storage("stock_watchlist.xml")
)
public final class StockWatchlistStateService implements PersistentStateComponent<StockWatchlistStateService.State> {

    public static final class State {
        @Tag("stocks")
        public List<StockItemState> stocks = new ArrayList<>();
    }

    @Tag("stock")
    public static final class StockItemState {
        @Attribute("name")
        public String name;

        @Attribute("code")
        public String code; // 支持 A 股 / 港股代码

        public StockItemState() {
        }

        public StockItemState(String name, String code) {
            this.name = name;
            this.code = code;
        }
    }

    private State state = new State();

    public static StockWatchlistStateService getInstance(Project project) {
        return project.getService(StockWatchlistStateService.class);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public List<StockItemState> getStocks() {
        return Collections.unmodifiableList(state.stocks);
    }

    public void addStock(String name, String code) {
        // 数据验证，拒绝无效数据
        if (code == null || code.isBlank() || 
            code.contains(".SZ") || code.contains(".SH") ||
            name == null || name.isBlank() || name.equals("未找到结果")) {
            return;
        }
        
        state.stocks.add(new StockItemState(name, code));
    }

    public void removeStock(int index) {
        if (index >= 0 && index < state.stocks.size()) {
            state.stocks.remove(index);
        }
    }

    /**
     * 根据股票代码删除
     */
    public void removeStockByCode(String code) {
        if (code == null) {
            return;
        }
        state.stocks.removeIf(stock -> code.equals(stock.code));
    }

    /**
     * 检查股票是否已在自选列表中
     */
    public boolean containsStock(String code) {
        if (code == null) {
            return false;
        }
        return state.stocks.stream().anyMatch(stock -> code.equals(stock.code));
    }

    /**
     * 清理无效数据（移除包含.SZ/.SH后缀的错误代码和空值）
     */
    public void cleanupInvalidData() {
        state.stocks.removeIf(stock -> 
            stock.code == null || 
            stock.code.isBlank() || 
            stock.code.contains(".SZ") || 
            stock.code.contains(".SH") ||
            stock.name == null ||
            stock.name.isBlank() ||
            stock.name.equals("未找到结果")
        );
    }
}
