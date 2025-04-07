package com.github.sullyvahnn.flaskplugin.java.TreeTypeWidget;

import com.github.sullyvahnn.flaskplugin.java.ExpressionData.ExpressionData;
import com.github.sullyvahnn.flaskplugin.java.NormalTypeWidget.NormalTypeWidget;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class TreeTypeWidget extends NormalTypeWidget {
    private Map<ExpressionData, List<ExpressionData>> currentTypeMap;
    private ExpressionData currentVariable;
    private final Set<String> processedTypes = new HashSet<>();

    public TreeTypeWidget(@NotNull Project project) {
        super(project);
    }
    /**
     * Updates the widget with type information based on caret position
     */

    public void updateTreeValue(Map<ExpressionData, List<ExpressionData>> currentTypeMap, ExpressionData root) {
        // Clear previous data
        processedTypes.clear();
        this.currentTypeMap = currentTypeMap; // Store the map reference

        if(currentTypeMap == null || currentTypeMap.isEmpty() || root == null) {
            typeLines.clear();
            typeCounts.clear();
            updateMessageString();
            return;
        }

        // Set the current variable
        currentVariable = root;

        // Extract all types recursively
        List<ExpressionData> allTypes = new ArrayList<>();
        // Add the root first
        allTypes.add(root);
        // Then collect all its dependencies
        collectTypesRecursively(currentVariable, allTypes);
        // Update the widget display
        super.updateValue(allTypes);
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
        DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
        JTree tree = new JTree(treeModel);

        // Customize tree appearance
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        renderer.setLeafIcon(AllIcons.Nodes.Type);
        renderer.setOpenIcon(AllIcons.Nodes.Variable);
        renderer.setClosedIcon(AllIcons.Nodes.Variable);
        tree.setCellRenderer(renderer);

        // Make row selection visible and enable single selection
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);

        // Enable row selection instead of just node selection
        tree.setRowHeight(22); // Make rows a bit taller for easier clicking

        // Add mouse listener to handle clicks on entire row
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // Get row at mouse position
                int row = tree.getRowForLocation(e.getX(), e.getY());

                if (row != -1) {
                    // Set selection to the row
                    tree.setSelectionRow(row);

                    // Get the selected node
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode)
                            tree.getLastSelectedPathComponent();

                    // Handle selection for highlighting
                    if (node != null) {
                        Object userObject = node.getUserObject();
                        if (userObject instanceof String selectedType) {
                            removeAllHighlights();
                            highlightLine(getLineNumber(selectedType)-1);
                        }
                    }

                    // Handle expand/collapse on double-click
                    if (e.getClickCount() == 2) {
                        TreePath path = tree.getPathForRow(row);
                        if (tree.isExpanded(path)) {
                            tree.collapsePath(path);
                        } else {
                            tree.expandPath(path);
                        }
                    }
                }
            }
        });

        // Expand all nodes
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }

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
            // Add each dependency as a separate node (no grouping)
            for (ExpressionData dep : dependencies) {
                // Add line number information to the node label
                String nodeLabel = dep.type;

                int lineNum = dep.lineNumber+1;
                nodeLabel += " (line " + lineNum + ")";

                DefaultMutableTreeNode typeNode = new DefaultMutableTreeNode(nodeLabel);
                parentNode.add(typeNode);

                // Continue building tree recursively if this is a variable with dependencies
                if (currentTypeMap.containsKey(dep)) {
                    buildTypeTreeNode(typeNode, dep);
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
        if(currentVariable == null) return;
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

    private int getLineNumber(String type) {
        String target = "line ";
        int index = type.indexOf(target); // Find "line " in the string

        if (index != -1) {
            // Extract everything after "line "
            String numberPart = type.substring(index + target.length()).replace(")", "").trim();
            try {
                return Integer.parseInt(numberPart);
            } catch (NumberFormatException e) {
                return -1;
            }
        } else {
            return -1;
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