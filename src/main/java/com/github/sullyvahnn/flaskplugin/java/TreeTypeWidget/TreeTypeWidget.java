package com.github.sullyvahnn.flaskplugin.java.TreeTypeWidget;

import com.github.sullyvahnn.flaskplugin.java.ExpressionData.ExpressionData;
import com.github.sullyvahnn.flaskplugin.java.NormalTypeWidget.NormalTypeWidget;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class TreeTypeWidget extends NormalTypeWidget {
    private final TreeVariableTypeResolver typeMapper;
    private Map<ExpressionData, List<ExpressionData>> currentTypeMap;
    private ExpressionData currentVariable;
    private final Set<String> processedTypes = new HashSet<>();

    public TreeTypeWidget(@NotNull Project project) {
        super(project);
        typeMapper = new TreeVariableTypeResolver();
    }
    /**
     * Updates the widget with type information based on caret position
     */
    public void updateFromCaret(CaretEvent event) {
        // Clear previous data
        processedTypes.clear();
        currentVariable = null;

        currentTypeMap = typeMapper.getVariablePossibleTypeTree(event);

        if (currentTypeMap != null && !currentTypeMap.isEmpty()) {
            // Get the current variable (first key in the map)
            currentVariable = currentTypeMap.keySet().iterator().next();

            // Extract all types recursively
            List<ExpressionData> allTypes = new ArrayList<>();
            collectTypesRecursively(currentVariable, allTypes);

            // Update the widget display
            updateValue(allTypes);
        } else {
            // Clear the widget if no types found
            typeLines.clear();
            typeCounts.clear();
            updateMessageString();
        }
    }

    /**
     * Recursively collects all types for a given variable
     */
    private void collectTypesRecursively(ExpressionData variable, List<ExpressionData> result) {
        if (variable == null || processedTypes.contains(variable.type)) {
            return;
        }

        // Mark this type as processed to avoid cycles
        processedTypes.add(variable.type);

        // Get dependencies for this variable
        List<ExpressionData> dependencies = currentTypeMap.get(variable);
        if (dependencies != null) {
            // Add all dependencies to the result
            result.addAll(dependencies);

            // Process each dependency recursively if it's a variable reference
            for (ExpressionData dep : dependencies) {
                if (currentTypeMap.containsKey(dep)) {
                    collectTypesRecursively(dep, result);
                }
            }
        }
    }

    @Override
    protected List<String> getTypeItems() {
        if (currentTypeMap == null || currentTypeMap.isEmpty()) {
            return null;
        }

        // Create a list with the root variable and special marker
        List<String> rootItem = new ArrayList<>();
        rootItem.add(currentVariable.type + " (root variable)");
        return rootItem;
    }

    @Override
    protected void handleClick(MouseEvent e) {
        if (currentTypeMap == null || currentVariable == null) {
            super.handleClick(e);
            return;
        }

        // Create tree view popup
        JTree tree = createTypeTree();
        JScrollPane scrollPane = new JScrollPane(tree);
        scrollPane.setPreferredSize(new Dimension(400, 300));

        // Create custom popup
        JBPopupFactory.getInstance()
                .createComponentPopupBuilder(scrollPane, tree)
                .setTitle("Type Hierarchy for " + currentVariable.type)
                .setResizable(true)
                .setMovable(true)
                .createPopup()
                .show(RelativePoint.fromScreen(new Point(e.getXOnScreen(), e.getYOnScreen())));
    }

    /**
     * Creates a JTree showing the hierarchical type structure
     */
    private JTree createTypeTree() {
        // Create root node
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(currentVariable.type);

        // Build tree recursively
        processedTypes.clear();
        buildTypeTreeNode(rootNode, currentVariable);

        // Create the tree
        JTree tree = new JTree(new DefaultTreeModel(rootNode));

        // Customize tree appearance
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        renderer.setLeafIcon(AllIcons.Nodes.Type);
        renderer.setOpenIcon(AllIcons.Nodes.Variable);
        renderer.setClosedIcon(AllIcons.Nodes.Variable);
        tree.setCellRenderer(renderer);

        // Expand all nodes
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }

        // Add selection listener to highlight references
        tree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (selectedNode != null) {
                String selectedType;
                Object userObject = selectedNode.getUserObject();
                if (userObject instanceof String) {
                    selectedType = (String) userObject;
                    highlightAllAssignments(selectedType);
                }
            }
        });

        return tree;
    }

    /**
     * Recursively builds the tree structure
     */
    private void buildTypeTreeNode(DefaultMutableTreeNode parentNode, ExpressionData variable) {
        if (variable == null || processedTypes.contains(variable.type)) {
            return;
        }

        // Mark as processed to avoid cycles
        processedTypes.add(variable.type);

        // Get dependencies
        List<ExpressionData> dependencies = currentTypeMap.get(variable);
        if (dependencies != null) {
            // Group dependencies by their type
            Map<String, List<ExpressionData>> groupedDeps = dependencies.stream()
                    .collect(Collectors.groupingBy(dep -> dep.type));

            // Add child nodes for each type
            for (Map.Entry<String, List<ExpressionData>> entry : groupedDeps.entrySet()) {
                String typeName = entry.getKey();
                int count = entry.getValue().size();

                // Create node with type name and count
                String nodeLabel = typeName;
                if (count > 1) {
                    nodeLabel += " (" + count + " occurrences)";
                }

                DefaultMutableTreeNode typeNode = new DefaultMutableTreeNode(nodeLabel);
                parentNode.add(typeNode);

                // Get a representative ExpressionData for this type
                ExpressionData representative = entry.getValue().get(0);

                // Continue building tree recursively if this is a variable with dependencies
                if (currentTypeMap.containsKey(representative)) {
                    buildTypeTreeNode(typeNode, representative);
                }
            }
        }
    }

    @Override
    public @NotNull String ID() {
        return "TreeTypeWidget";
    }

    @Override
    public @Nullable String getTooltipText() {
        if (currentTypeMap == null || currentTypeMap.isEmpty()) {
            return "No type hierarchy available";
        }
        return "Click to view type hierarchy for " + currentVariable.type;
    }

    @Override
    protected void updateMessageString() {
        if (currentTypeMap == null || currentTypeMap.isEmpty()) {
            message = "";
            return;
        }

        // Count total direct and indirect types
        int totalTypes = 0;
        int directTypes = 0;

        if (currentVariable != null) {
            List<ExpressionData> directDependencies = currentTypeMap.get(currentVariable);
            directTypes = directDependencies != null ? directDependencies.size() : 0;

            // Count total unique types in the entire hierarchy
            Set<String> uniqueTypes = new HashSet<>();
            for (List<ExpressionData> types : currentTypeMap.values()) {
                types.forEach(type -> uniqueTypes.add(type.type));
            }
            totalTypes = uniqueTypes.size();
        }

        // Build status message
        if (directTypes > 0) {
            message = currentVariable.type + ": " + directTypes + " direct / " + totalTypes + " total types";
        } else {
            message = currentVariable.type + ": No types found";
        }

        // Update the widget in the status bar
        if (getStatusBar() != null) {
            getStatusBar().updateWidget(ID());
        }
    }

    @Override
    public void dispose() {
        // Clean up any resources
        processedTypes.clear();
        if (currentTypeMap != null) {
            currentTypeMap.clear();
        }
        super.dispose();
    }
}