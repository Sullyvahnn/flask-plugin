package com.github.sullyvahnn.flaskplugin.java.CaretListener;

import com.github.sullyvahnn.flaskplugin.java.ExpressionData.ExpressionData;
import com.github.sullyvahnn.flaskplugin.java.NormalTypeWidget.NormalTypeWidget;
import com.github.sullyvahnn.flaskplugin.java.NormalTypeWidget.VariableTypeResolver;
import com.github.sullyvahnn.flaskplugin.java.TreeTypeWidget.TreeTypeWidget;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import org.jetbrains.annotations.NotNull;

import java.util.*;

final class CaretPositionTracker implements ToolWindowManagerListener {
    private final Project project;
    // Store our listeners to avoid duplicates and to be able to remove them
    private final Map<Editor, CaretPositionListener> activeListeners = new HashMap<>();
    private final VariableTypeResolver  resolver = new VariableTypeResolver();

    CaretPositionTracker(Project project) {
        this.project = project;

        // Set up the file editor listeners when this tracker is created
        setupEditorListeners();
    }


    @Override
    public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
        // When tool windows change, the editor might have changed too
        // Re-register listeners for the current editor
        registerCaretListener(FileEditorManager.getInstance(project).getSelectedTextEditor());
    }

    private void setupEditorListeners() {
        // Subscribe to file editor events for this project
        project.getMessageBus().connect().subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                new FileEditorManagerListener() {
                    @Override
                    public void fileOpened(@NotNull FileEditorManager source, @NotNull com.intellij.openapi.vfs.VirtualFile file) {
                        registerCaretListener(source.getSelectedTextEditor());
                    }

                    @Override
                    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                        registerCaretListener(event.getManager().getSelectedTextEditor());
                    }
                }
        );

        // Register for the currently open editor (if any)
        registerCaretListener(FileEditorManager.getInstance(project).getSelectedTextEditor());
    }

    private void registerCaretListener(Editor editor) {
        if (editor == null) {
            return;
        }

        // If we already have a listener for this editor, no need to add a new one
        if (activeListeners.containsKey(editor)) {
            return;
        }

        // Create and add our custom caret listener
        CaretPositionListener listener = new CaretPositionListener();
        editor.getCaretModel().addCaretListener(listener);

        // Store the listener so we can remove it later if needed
        activeListeners.put(editor, listener);
    }

    // Clean up listeners when editors are closed
    private void removeCaretListener(Editor editor) {
        if (editor == null) {
            return;
        }

        CaretPositionListener listener = activeListeners.remove(editor);
        if (listener != null) {
            editor.getCaretModel().removeCaretListener(listener);
        }
    }

    private class CaretPositionListener implements CaretListener {

        @Override
        public void caretPositionChanged(@NotNull CaretEvent event) {
            List<ExpressionData> types = resolver.getPossibleTypes(event);

            // Run background task to get the possible types
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                // Use ReadAction to ensure PSI calls are made on the correct thread
                ApplicationManager.getApplication().runReadAction(() -> {
                    // Ensure the UI update happens on the EDT after the background task
                    ApplicationManager.getApplication().invokeLater(() -> {
                        // Now that we have the types, update the caret position widget
                        updateWidget(types, event, "NormalTypeWidget");
                        updateWidget(types, event, "TreeTypeWidget");
                    });
                });
            });
        }

        private void updateWidget(List<ExpressionData> types, CaretEvent event, String widgetName) {
            // Update the status bar widget with the new message
            StatusBar statusBar = WindowManager.getInstance().getStatusBar(
                    Objects.requireNonNull(event.getEditor().getProject()));
            if (statusBar != null) {
                if(widgetName.equals("NormalTypeWidget")) {
                    NormalTypeWidget widget = (NormalTypeWidget) statusBar.getWidget(widgetName);
                    if (widget == null) return;
                    widget.updateValue(types);
                    statusBar.updateWidget(widgetName);
                }
                if(widgetName.equals("TreeTypeWidget")) {
                    TreeTypeWidget widget = (TreeTypeWidget) statusBar.getWidget(widgetName);
                    if (widget == null) return;
                    widget.updateFromCaret(event);
                    statusBar.updateWidget(widgetName);
                }

            }
        }


    }

}