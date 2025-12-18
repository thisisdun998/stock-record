package com.github.thisisdun998.stockrecord.service;

import com.github.thisisdun998.stockrecord.model.StockIndex;
import com.github.thisisdun998.stockrecord.model.StockIndexQuote;
import com.github.thisisdun998.stockrecord.model.StockQuote;

import java.util.List;

public interface StockService {

    List<StockQuote> getQuotes(List<String> codes);

    StockIndexQuote getIndexQuote(StockIndex index);

    /**
     * 搜索股票，支持按代码或名称模糊查询
     * @param keyword 搜索关键词
     * @return 匹配的股票列表
     */
    List<StockQuote> searchStocks(String keyword);
}
