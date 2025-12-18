package com.github.thisisdun998.stockrecord.model;

public enum StockIndex {

    SHANGHAI("上证指数", "s_sh000001"),
    SHENZHEN("深证指数", "s_sz399001"),
    CHINEXT("创业板指", "s_sz399006"),
    HSTECH("恒生科技", "s_hkHSTECH");

    private final String displayName;
    private final String sinaCode;

    StockIndex(String displayName, String sinaCode) {
        this.displayName = displayName;
        this.sinaCode = sinaCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSinaCode() {
        return sinaCode;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
