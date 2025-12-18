package com.github.thisisdun998.stockrecord.model;

public final class StockQuote {

    private final String name;
    private final String code;
    private final double price;
    private final double changePercent;

    public StockQuote(String name, String code, double price, double changePercent) {
        this.name = name;
        this.code = code;
        this.price = price;
        this.changePercent = changePercent;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public double getPrice() {
        return price;
    }

    public double getChangePercent() {
        return changePercent;
    }
}
