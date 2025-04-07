package com.github.sullyvahnn.flaskplugin.java.NormalTypeWidget;

import com.github.sullyvahnn.flaskplugin.java.ExpressionData.ExpressionData;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A status bar widget for IntelliJ IDEA that displays type information and allows users
 * to visualize where different types are used in the code.
 * <p>
 * This widget shows type information in the status bar, and allows users to click on it
 * to see a list of detected types. When a type is selected, all lines containing that type
 * are highlighted in the editor.
 */
public class NormalTypeWidget extends EditorBasedWidget
        implements StatusBarWidget.TextPresentation {

    /** Message displayed in the status bar */
    protected String message;

    /** Threshold for number of types above which a warning icon is shown */
    protected final int typesCountWarning;

    /** List of active highlights in the editor */
    private final List<RangeHighlighter> activeHighlighters = new ArrayList<>();

    /** Mouse listener to remove highlights when user clicks elsewhere */
    private final EditorMouseListener mouseListener;

    /** Map of type names to their occurrence counts */
    protected final Map<String, Integer> typeCounts = new HashMap<>();

    /** Map of type names to the line numbers where they appear */
    protected final Map<String, List<Integer>> typeLines = new HashMap<>();

    /** Tracks the editor where the mouse listener is currently attached */
    private Editor currentListenerEditor = null;

    /**
     * Creates a new NormalTypeWidget for the specified project.
     *
     * @param project The IntelliJ project this widget belongs to
     */
    public NormalTypeWidget(@NotNull Project project) {
        super(project);
        typesCountWarning = 3;
        message = "";

        // Create a mouse listener to remove highlight when user clicks
        mouseListener = new EditorMouseListener() {
            @Override
            public void mouseClicked(@NotNull EditorMouseEvent event) {
                removeAllHighlights();
            }
        };
    }

    /**
     * Returns the text to be displayed in the status bar.
     *
     * @return The widget text
     */
    @Override
    public @NotNull String getText() {
        return message;
    }

    /**
     * Returns the widget alignment in the status bar.
     *
     * @return Alignment value (0 means left alignment)
     */
    @Override
    public float getAlignment() {
        return 0; // Left alignment
    }

    /**
     * Returns the tooltip text to be shown when hovering over the widget.
     *
     * @return The tooltip text or null if no tooltip is needed
     */
    @Override
    public @Nullable String getTooltipText() {
        if (typeCounts.isEmpty()) {
            return "No types available";
        }
        return "Click to view " + typeCounts.size() + " types";
    }

    /**
     * Returns the unique identifier for this widget.
     *
     * @return The widget ID
     */
    @Override
    public @NotNull String ID() {
        return "NormalTypeWidget";
    }

    /**
     * Returns the widget presentation.
     *
     * @return This widget as a presentation
     */
    @Override
    public WidgetPresentation getPresentation() {
        return this;
    }

    /**
     * Called when the widget is installed on the status bar.
     *
     * @param statusBar The status bar
     */
    @Override
    public void install(@NotNull StatusBar statusBar) {
        super.install(statusBar);
    }

    /**
     * Returns a consumer that handles mouse clicks on the widget.
     *
     * @return A consumer for mouse events
     */
    @Nullable
    @Override
    public Consumer<MouseEvent> getClickConsumer() {
        return this::handleClick;
    }

    /**
     * Creates a list of type items to be displayed in the popup.
     *
     * @return A list of formatted strings representing types and their occurrences,
     *         or null if no types are available
     */
    protected List<String> getTypeItems() {
        if (typeCounts.isEmpty()) {
            return null;
        }

        // Create a list of type names for the popup with better formatting
        List<String> typeItems = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
            String typeMessage = entry.getKey() + " (" + entry.getValue() + " occurrences)";
            typeItems.add(typeMessage);
        }
        return typeItems;
    }

    /**
     * Handles mouse clicks on the widget by showing a popup with available types.
     *
     * @param e The mouse event
     */
    protected void handleClick(MouseEvent e) {
        List<String> typeItems = getTypeItems();
        if (typeItems == null) return;

        // Create and show the popup
        ListPopup popup = JBPopupFactory.getInstance().createListPopup(
                new BaseListPopupStep<>("Available Types", typeItems) {
                    @Override
                    public PopupStep onChosen(String selectedValue, boolean finalChoice) {
                        // Extract the type name without the count
                        String typeName = selectedValue.substring(0, selectedValue.indexOf(" ("));
                        highlightAllAssignments(typeName);
                        return FINAL_CHOICE;
                    }

                    @Override
                    public Icon getIconFor(String value) {
                        return null; // You can provide icons for each item if needed
                    }
                });

        popup.show(RelativePoint.fromScreen(new Point(e.getXOnScreen(), e.getYOnScreen())));
    }

    /**
     * Gets the currently active editor.
     *
     * @return The current editor or null if none is active
     */
    protected Editor getCurrentEditor() {
        return FileEditorManager.getInstance(getProject()).getSelectedTextEditor();
    }

    /**
     * Highlights all lines where the selected type appears.
     *
     * @param selectedType The type to highlight
     */
    protected void highlightAllAssignments(String selectedType) {
        // Remove previous highlights if they exist
        removeAllHighlights();

        // Extract the actual type name from the selection (remove the count and parentheses)
        String actualType = selectedType;
        if (selectedType.contains(" (")) {
            actualType = selectedType.substring(0, selectedType.indexOf(" ("));
        }

        // Get the lines to highlight for this type
        List<Integer> linesToHighlight = typeLines.get(actualType);

        if (linesToHighlight != null) {
            for (int line : linesToHighlight) {
                highlightLine(line);
            }
        }
    }

    /**
     * Highlights a specific line in the editor.
     *
     * @param lineNumber The line number to highlight (0-based)
     */
    protected void highlightLine(Integer lineNumber) {
        Editor editor = getCurrentEditor();
        if (editor == null) return;
        if(lineNumber<0) return;

        // Create text attributes for highlighting with a softer color
        TextAttributes attributes = getHighlightAttributes();

        // Get line start offset
        int startOffset = editor.getDocument().getLineStartOffset(lineNumber);

        // Get line end offset
        int endOffset;
        if (lineNumber + 1 < editor.getDocument().getLineCount()) {
            endOffset = editor.getDocument().getLineStartOffset(lineNumber + 1);
        } else {
            endOffset = editor.getDocument().getTextLength();
        }

        // Add highlight for the entire line
        RangeHighlighter highlighter = editor.getMarkupModel().addRangeHighlighter(
                startOffset, endOffset, HighlighterLayer.SELECTION, attributes,
                com.intellij.openapi.editor.markup.HighlighterTargetArea.EXACT_RANGE);

        // Add the highlighter to our tracked list
        activeHighlighters.add(highlighter);

        // Scroll to the highlighted line - only for the first one we highlight
        if (activeHighlighters.size() == 1) {
            editor.getScrollingModel().scrollTo(new LogicalPosition(lineNumber, 0),
                    com.intellij.openapi.editor.ScrollType.CENTER);
        }

        // Add mouse listener to remove highlights on next click
        // Only add it once to avoid multiple listeners
        safelyAttachMouseListener();
    }

    /**
     * Creates and returns the text attributes for highlighting.
     *
     * @return TextAttributes with appropriate colors for the highlight
     */
    protected TextAttributes getHighlightAttributes() {
        // Default attributes - light blue highlight with lower opacity
        TextAttributes attributes = new TextAttributes();
        attributes.setBackgroundColor(new JBColor(new Color(173, 216, 230, 100), new Color(173, 216, 230, 100)));
        return attributes;
    }

    /**
     * Removes all active highlights from the editor.
     */
    protected void removeAllHighlights() {
        Editor editor = getCurrentEditor();
        safelyDetachMouseListener();
        if (editor != null) {
            // Remove all tracked highlighters
            for (RangeHighlighter highlighter : activeHighlighters) {
                if (highlighter != null && highlighter.isValid()) {
                    editor.getMarkupModel().removeHighlighter(highlighter);
                }
            }

            // Clear the list
            activeHighlighters.clear();
        }
    }

    /**
     * Safely attaches the mouse listener to the current editor.
     * Ensures we don't add multiple listeners to the same editor.
     */
    private void safelyAttachMouseListener() {
        Editor editor = getCurrentEditor();
        if (editor == null) return;

        // First ensure any previous listener is removed
        safelyDetachMouseListener();

        try {
            // Add the listener and track this editor
            editor.addEditorMouseListener(mouseListener);
            currentListenerEditor = editor;
        } catch (Exception e) {
            // Just log the error, but don't crash
            currentListenerEditor = null;
        }
    }

    /**
     * Safely detaches the mouse listener from the current editor.
     * Handles potential exceptions during detachment.
     */
    private void safelyDetachMouseListener() {
        // Only attempt removal if we have tracked a previous attachment
        if (currentListenerEditor != null) {
            try {
                currentListenerEditor.removeEditorMouseListener(mouseListener);
            } catch (Exception e) {
                // Ignore exception, we're cleaning up anyway
            } finally {
                // Always reset our tracking
                currentListenerEditor = null;
            }
        }
    }

    /**
     * Cleans up resources when the widget is disposed.
     */
    @Override
    public void dispose() {
        removeAllHighlights();
        super.dispose();
    }

    /**
     * Updates the widget with new expression data.
     * Processes the data to count occurrences of each type and associate them with line numbers.
     *
     * @param expressionDataList List of expression data to analyze
     */
    public void updateValue(List<ExpressionData> expressionDataList) {
        typeLines.clear();
        typeCounts.clear();

        // Count occurrences of each type and collect line numbers
        for (ExpressionData data : expressionDataList) {
            // Increment count for this type
            typeCounts.put(data.type.trim(), typeCounts.getOrDefault(data.type.trim(), 0) + 1);

            // Add this line number to the type's list
            typeLines.computeIfAbsent(data.type.trim(), k -> new ArrayList<>()).add(data.lineNumber);
        }

        updateMessageString();
    }

    /**
     * Updates the message string displayed in the status bar based on current type counts.
     * Shows a warning icon if the number of types exceeds the warning threshold.
     */
    protected void updateMessageString() {
        if (typeCounts.isEmpty()) {
            message = "";
            return;
        }

        // Build the message string
        StringBuilder sb = new StringBuilder();

        // Add a warning indicator if needed
        if (typeCounts.size() >= typesCountWarning) {
            message = "⚠️ Types: " + typeCounts.size();
        } else {
            sb.append("Types: ");
            int count = 0;
            int displayLimit = 2; // Show at most 2 types in status bar

            for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
                if (count > 0) {
                    sb.append(", ");
                }
                sb.append(entry.getKey()).append(" (").append(entry.getValue()).append(")");
                count++;

                if (count >= displayLimit) {
                    break;
                }
            }

            if (typeCounts.size() > displayLimit) {
                sb.append(" (+").append(typeCounts.size() - displayLimit).append(" more)");
            }

            message = sb.toString();
        }

        // Update the widget in the status bar
        if (getStatusBar() != null) {
            getStatusBar().updateWidget(ID());
        }
    }
}