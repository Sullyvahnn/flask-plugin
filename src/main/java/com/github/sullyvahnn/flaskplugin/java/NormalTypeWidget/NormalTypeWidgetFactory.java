package com.github.sullyvahnn.flaskplugin.java.NormalTypeWidget;


import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

// 1. Widget Factory
public class NormalTypeWidgetFactory implements StatusBarWidgetFactory {
    private static final String WIDGET_ID = "CaretPositionWidget";


    @Override
    public @NotNull String getId() {
        return WIDGET_ID;
    }

    @Override
    public @Nls @NotNull String getDisplayName() {
        return "Variable Type Inspector";
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
        return true;
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        return new NormalTypeWidget(project);
    }

}

