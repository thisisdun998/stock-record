package com.github.thisisdun998.stockrecord.service;

import com.github.thisisdun998.stockrecord.model.StockIndex;
import com.github.thisisdun998.stockrecord.model.StockIndexQuote;
import com.github.thisisdun998.stockrecord.model.StockQuote;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 使用新浪财经 hq.sinajs.cn 接口获取实时行情数据。
 * <p>
 * 如需更换为其他数据源，可以在此处替换为真实 HTTP 请求实现。
 */
public final class SinaStockService implements StockService {

    private static final String BASE_URL = "http://hq.sinajs.cn/list=";
    private static final Charset GBK = Charset.forName("GBK");
    
    // 麦瑞API - 沪深两市股票列表接口
    // 注意：请到 https://www.mairui.club/gratis.html 申请免费licence替换下面的示例licence
    private static final String STOCK_LIST_API = "http://api.mairuiapi.com/hslt/list/";
    private static final String DEFAULT_LICENCE = "LICENCE-66D8-9F96-0C7F0FBCD073"; // 默认测试licence，建议更换
    
    // 股票列表缓存
    private static volatile List<StockInfo> cachedStockList = null;
    private static volatile long lastUpdateTime = 0;
    private static final long CACHE_DURATION = 24 * 60 * 60 * 1000; // 24小时缓存

    private static final class StockInfo {
        final String code;
        final String name;

        StockInfo(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }

    private static List<StockInfo> initStockList() {
        // 保留作为默认备用数据，API请求失败时使用
        List<StockInfo> list = new ArrayList<>();
        list.add(new StockInfo("sh600519", "贵州茅台"));
        list.add(new StockInfo("sh600036", "招商银行"));
        list.add(new StockInfo("sh601318", "中国平安"));
        list.add(new StockInfo("sz000858", "五粮液"));
        list.add(new StockInfo("sz000333", "美的集团"));
        list.add(new StockInfo("sz002594", "比亚迪"));
        list.add(new StockInfo("sh600887", "伊利股份"));
        list.add(new StockInfo("sz000651", "格力电器"));
        list.add(new StockInfo("sh601888", "中国中免"));
        list.add(new StockInfo("sz300750", "宁德时代"));
        return list;
    }

    /**
     * 从麦瑞API获取所有沪深股票列表
     */
    private List<StockInfo> fetchStockListFromAPI() {
        try {
            String url = STOCK_LIST_API + DEFAULT_LICENCE;
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return initStockList(); // 返回默认列表
            }

            // 解析JSON响应
            String body = response.body();
            List<StockInfo> stocks = parseStockListJson(body);
            
            if (!stocks.isEmpty()) {
                return stocks;
            }
        } catch (Exception e) {
            // 请求失败，记录日志但不抛异常
            System.err.println("获取股票列表失败: " + e.getMessage());
        }
        return initStockList(); // 返回默认列表
    }

    /**
     * 解析股票列表JSON
     */
    private List<StockInfo> parseStockListJson(String json) {
        List<StockInfo> result = new ArrayList<>();
        try {
            // JSON格式：[{"dm":"000001","mc":"平安银行","jys":"sz"},...]
            if (json == null || json.isBlank() || !json.startsWith("[")) {
                return result;
            }

            // 简单解析，不依赖第三方JSON库
            String[] items = json.substring(1, json.length() - 1).split("\\},\\{");
            for (String item : items) {
                item = item.replace("{", "").replace("}", "");
                String code = extractJsonValue(item, "dm");
                String name = extractJsonValue(item, "mc");
                String market = extractJsonValue(item, "jys");

                if (code != null && name != null && market != null) {
                    String fullCode = market.toLowerCase() + code;
                    result.add(new StockInfo(fullCode, name));
                }
            }
        } catch (Exception e) {
            System.err.println("解析股票列表JSON失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 提取JSON字段值
     */
    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) {
            return null;
        }
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end < 0) {
            return null;
        }
        return json.substring(start, end);
    }

    /**
     * 获取股票列表（带缓存）
     */
    private List<StockInfo> getStockList() {
        long now = System.currentTimeMillis();
        
        // 检查缓存是否过期
        if (cachedStockList == null || (now - lastUpdateTime) > CACHE_DURATION) {
            synchronized (SinaStockService.class) {
                // 双重检查
                if (cachedStockList == null || (now - lastUpdateTime) > CACHE_DURATION) {
                    cachedStockList = fetchStockListFromAPI();
                    lastUpdateTime = now;
                }
            }
        }
        
        return cachedStockList;
    }

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public List<StockQuote> getQuotes(List<String> codes) {
        List<StockQuote> result = new ArrayList<>();
        if (codes == null || codes.isEmpty()) {
            return result;
        }

        Map<String, String> sinaCodeToUserCode = new HashMap<>();
        List<String> sinaCodes = new ArrayList<>();
        for (String userCode : codes) {
            String sinaCode = toSinaCode(userCode);
            if (sinaCode != null) {
                sinaCodes.add(sinaCode);
                sinaCodeToUserCode.put(sinaCode, userCode);
            }
        }

        if (sinaCodes.isEmpty()) {
            return result;
        }

        String url = BASE_URL + String.join(",", sinaCodes);
        try {
            String body = sendRequest(url);
            if (body == null || body.isBlank()) {
                return result;
            }
            String[] lines = body.split("\\n");
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                ParsedQuote parsed = parseStockLine(line);
                if (parsed == null) {
                    continue;
                }
                String userCode = sinaCodeToUserCode.get(parsed.sinaCode);
                if (userCode == null) {
                    userCode = parsed.sinaCode;
                }
                result.add(new StockQuote(parsed.name, userCode, parsed.price, parsed.changePercent));
            }
        } catch (Exception e) {
            // 请求失败时，不抛异常，以免影响 IDE 使用
            // 可以根据需要增加日志记录
        }
        return result;
    }

    @Override
    public StockIndexQuote getIndexQuote(StockIndex index) {
        String url = BASE_URL + index.getSinaCode();
        try {
            String body = sendRequest(url);
            if (body == null || body.isBlank()) {
                return new StockIndexQuote(index.getDisplayName(), 0.0, 0.0, 0.0);
            }
            String[] lines = body.split("\\n");
            if (lines.length == 0) {
                return new StockIndexQuote(index.getDisplayName(), 0.0, 0.0, 0.0);
            }
            ParsedIndex parsed = parseIndexLine(lines[0]);
            if (parsed == null) {
                return new StockIndexQuote(index.getDisplayName(), 0.0, 0.0, 0.0);
            }
            return new StockIndexQuote(parsed.name, parsed.points, parsed.change, parsed.changePercent);
        } catch (Exception e) {
            return new StockIndexQuote(index.getDisplayName(), 0.0, 0.0, 0.0);
        }
    }

    private String sendRequest(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .header("Referer", "https://finance.sina.com.cn")
                .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            return null;
        }
        return new String(response.body(), GBK);
    }

    private static final class ParsedQuote {
        final String sinaCode;
        final String name;
        final double price;
        final double changePercent;

        ParsedQuote(String sinaCode, String name, double price, double changePercent) {
            this.sinaCode = sinaCode;
            this.name = name;
            this.price = price;
            this.changePercent = changePercent;
        }
    }

    private static final class ParsedIndex {
        final String name;
        final double points;
        final double change;
        final double changePercent;

        ParsedIndex(String name, double points, double change, double changePercent) {
            this.name = name;
            this.points = points;
            this.change = change;
            this.changePercent = changePercent;
        }
    }

    private ParsedQuote parseStockLine(String line) {
        int codeStart = line.indexOf("_str_");
        int eqIndex = line.indexOf("=");
        int firstQuote = line.indexOf('"');
        int lastQuote = line.lastIndexOf('"');
        if (codeStart < 0 || eqIndex < 0 || firstQuote < 0 || lastQuote <= firstQuote) {
            return null;
        }
        String sinaCode = line.substring(codeStart + "_str_".length(), eqIndex).trim();
        String inner = line.substring(firstQuote + 1, lastQuote);
        String[] parts = inner.split(",");
        if (parts.length < 4) {
            return null;
        }
        String name = parts[0];
        try {
            double yesterday = Double.parseDouble(parts[2]);
            double price = Double.parseDouble(parts[3]);
            double change = price - yesterday;
            double changePercent = yesterday == 0.0 ? 0.0 : (change / yesterday * 100.0);
            return new ParsedQuote(sinaCode, name, round(price), round(changePercent));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ParsedIndex parseIndexLine(String line) {
        int firstQuote = line.indexOf('"');
        int lastQuote = line.lastIndexOf('"');
        if (firstQuote < 0 || lastQuote <= firstQuote) {
            return null;
        }
        String inner = line.substring(firstQuote + 1, lastQuote);
        String[] parts = inner.split(",");
        if (parts.length < 4) {
            return null;
        }
        String name = parts[0];
        try {
            double points = Double.parseDouble(parts[1]);
            double change = Double.parseDouble(parts[2]);
            double changePercent = Double.parseDouble(parts[3]);
            return new ParsedIndex(name, round(points), round(change), round(changePercent));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @Override
    public List<StockQuote> searchStocks(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }
        
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT).trim();
        List<StockInfo> stockList = getStockList(); // 使用缓存的股票列表
        
        List<StockInfo> matches = stockList.stream()
                .filter(stock -> {
                    // 支持按名称搜索
                    if (stock.name.toLowerCase(Locale.ROOT).contains(lowerKeyword)) {
                        return true;
                    }
                    // 支持按完整代码搜索（sh600519）
                    if (stock.code.toLowerCase(Locale.ROOT).contains(lowerKeyword)) {
                        return true;
                    }
                    // 支持按纯数字代码搜索（600519）
                    String pureCode = stock.code.replaceAll("[^0-9]", "");
                    if (pureCode.contains(lowerKeyword)) {
                        return true;
                    }
                    return false;
                })
                .limit(100)  // 限制搜索结果数量
                .toList();
        
        if (matches.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 不查询实时行情，直接返回基本信息，提高搜索速度
        return matches.stream()
                .map(s -> new StockQuote(s.name, s.code, 0.0, 0.0))
                .toList();
    }

    private String toSinaCode(String userCode) {
        if (userCode == null) {
            return null;
        }
        String c = userCode.trim().toLowerCase(Locale.ROOT);
        if (c.isEmpty()) {
            return null;
        }
        if (c.startsWith("sh") || c.startsWith("sz") || c.startsWith("hk")) {
            return c;
        }
        int dot = c.indexOf('.');
        if (dot > 0 && dot < c.length() - 1) {
            String digits = c.substring(0, dot);
            String suffix = c.substring(dot + 1);
            if (!digits.chars().allMatch(Character::isDigit)) {
                return null;
            }
            if ("sh".equals(suffix)) {
                return "sh" + digits;
            } else if ("sz".equals(suffix)) {
                return "sz" + digits;
            } else if ("hk".equals(suffix)) {
                return "hk" + digits;
            }
        }
        if (c.matches("\\d{6}")) {
            char first = c.charAt(0);
            String prefix = (first == '6') ? "sh" : "sz";
            return prefix + c;
        }
        if (c.matches("\\d{5}")) {
            return "hk" + c;
        }
        return null;
    }
}
