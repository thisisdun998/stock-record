package com.github.thisisdun998.stockrecord.service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 股票列表缓存服务
 * 每天定时更新股票列表缓存
 */
@Service(Service.Level.PROJECT)
public final class StockListCacheService implements Disposable {
    
    private final StockService stockService;
    private ScheduledFuture<?> scheduledTask;
    
    public StockListCacheService(Project project) {
        this.stockService = new SinaStockService();
        startScheduledUpdate();
    }
    
    /**
     * 启动定时任务，每天凌晨2点更新股票列表
     */
    private void startScheduledUpdate() {
        // 计算到凌晨2点的延迟时间
        long initialDelay = calculateInitialDelay();
        long period = 24 * 60 * 60; // 24小时（秒）
        
        scheduledTask = AppExecutorUtil.getAppScheduledExecutorService()
                .scheduleAtFixedRate(
                        this::updateStockListCache,
                        initialDelay,
                        period,
                        TimeUnit.SECONDS
                );
    }
    
    /**
     * 计算到凌晨2点的延迟时间（秒）
     */
    private long calculateInitialDelay() {
        long now = System.currentTimeMillis();
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 2);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        
        long targetTime = calendar.getTimeInMillis();
        if (targetTime <= now) {
            // 如果今天的2点已经过了，延迟到明天2点
            calendar.add(java.util.Calendar.DAY_OF_MONTH, 1);
            targetTime = calendar.getTimeInMillis();
        }
        
        return (targetTime - now) / 1000; // 转换为秒
    }
    
    /**
     * 更新股票列表缓存
     */
    private void updateStockListCache() {
        try {
            // 触发一次搜索，强制刷新缓存
            stockService.searchStocks("000001");
            System.out.println("股票列表缓存已更新: " + new java.util.Date());
        } catch (Exception e) {
            System.err.println("更新股票列表缓存失败: " + e.getMessage());
        }
    }
    
    /**
     * 手动触发更新
     */
    public void manualUpdate() {
        AppExecutorUtil.getAppExecutorService().submit(this::updateStockListCache);
    }
    
    @Override
    public void dispose() {
        if (scheduledTask != null && !scheduledTask.isDone()) {
            scheduledTask.cancel(false);
        }
    }
    
    public static StockListCacheService getInstance(Project project) {
        return project.getService(StockListCacheService.class);
    }
}
