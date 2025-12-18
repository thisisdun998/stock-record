package com.github.thisisdun998.stockrecord.model;

public final class StockIndexQuote {

    private final String displayName;
    private final double points;
    private final double change;
    private final double changePercent;

    public StockIndexQuote(String displayName, double points, double change, double changePercent) {
        this.displayName = displayName;
        this.points = points;
        this.change = change;
        this.changePercent = changePercent;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getPoints() {
        return points;
    }

    public double getChange() {
        return change;
    }

    public double getChangePercent() {
        return changePercent;
    }
}
