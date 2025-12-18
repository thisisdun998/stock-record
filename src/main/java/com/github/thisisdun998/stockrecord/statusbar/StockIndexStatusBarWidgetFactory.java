package com.github.thisisdun998.stockrecord.statusbar;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class StockIndexStatusBarWidgetFactory implements StatusBarWidgetFactory {

    @Override
    public @NotNull String getId() {
        return "stockIndexStatusBarWidget";
    }

    @Override
    public @Nls @NotNull String getDisplayName() {
        return "指数行情";
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
        return true;
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        return new StockIndexStatusBarWidget(project);
    }

    @Override
    public boolean canBeEnabledOn(@NotNull com.intellij.openapi.wm.StatusBar statusBar) {
        return true;
    }
}
