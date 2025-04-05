package com.github.sullyvahnn.flaskplugin.java.TreeTypeWidget;

import com.github.sullyvahnn.flaskplugin.java.ExpressionData.ExpressionData;
import com.github.sullyvahnn.flaskplugin.java.NormalTypeWidget.VariableTypeResolver;
import com.intellij.openapi.editor.event.CaretEvent;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TreeVariableTypeResolver extends VariableTypeResolver {
    private final Map<String, ExpressionData> expressionDataMap = new HashMap<>();
    private final Map<ExpressionData, List<ExpressionData>> variableDependencyMap = new HashMap<>();

    /**
     * Gets the variable dependency map for the expression at the caret.
     * The map contains the current variable as the key and all its dependencies as values.
     *
     * @param event The caret event
     * @return A map of variable dependencies with their types, or null if there's an error
     */
    public @Nullable Map<ExpressionData, List<ExpressionData>> getVariablePossibleTypeTree(CaretEvent event) {
        List<ExpressionData> types = super.getPossibleTypes(event);
        if (isError || types == null || types.isEmpty()) {
            return null;
        }

        // Clear maps for new analysis
        expressionDataMap.clear();
        variableDependencyMap.clear();

        // Get the variable name at the caret position
        String variableName = element.getText();
        if (variableName == null || variableName.isEmpty()) {
            return null;
        }

        // Process the current variable
        buildDependencyMap(variableName);

        return variableDependencyMap;
    }

    /**
     * Recursively builds the dependency map for a variable
     *
     * @param variableName The name of the variable to analyze
     */
    private void buildDependencyMap(String variableName) {
        // Create an ExpressionData for the current variable if it doesn't exist
        if (!expressionDataMap.containsKey(variableName)) {
            expressionDataMap.put(variableName, new ExpressionData((PyExpression) element, variableName));
        }

        ExpressionData currentVariable = expressionDataMap.get(variableName);

        // Initialize list of dependencies if not already present
        if (!variableDependencyMap.containsKey(currentVariable)) {
            variableDependencyMap.put(currentVariable, new ArrayList<>());
        }

        // Find all assignments for this variable
        findVariableAssignments(currentVariable, variableName);
    }

    /**
     * Finds all assignments for a variable and tracks dependencies
     *
     * @param currentVariable The ExpressionData for the current variable
     * @param variableName The name of the variable to find assignments for
     */
    private void findVariableAssignments(ExpressionData currentVariable, String variableName) {
        if (scope == null) return;

        scope.getContainingFile().accept(new PyRecursiveElementVisitor() {
            @Override
            public void visitPyAssignmentStatement(@NotNull PyAssignmentStatement assignment) {
                super.visitPyAssignmentStatement(assignment);

                PyExpression[] targets = assignment.getTargets();
                PyExpression assignedValue = assignment.getAssignedValue();
                if (assignedValue == null) return;

                // Check if this assignment targets our variable
                for (PyExpression target : targets) {
                    if (Objects.equals(target.getName(), variableName)) {
                        // Process the assigned value
                        processAssignedValue(currentVariable, assignedValue);
                    }
                }
            }
        });
    }

    /**
     * Process the assigned value and update the dependency map
     *
     * @param currentVariable The current variable's ExpressionData
     * @param assignedValue The expression assigned to the variable
     */
    private void processAssignedValue(ExpressionData currentVariable, PyExpression assignedValue) {
        if (assignedValue instanceof PyReferenceExpression) {
            // If assigned value is another variable, track dependency
            String referenceName = assignedValue.getName();
            if (referenceName != null && !referenceName.isEmpty()) {
                // Create dependency ExpressionData
                ExpressionData dependency = new ExpressionData(assignedValue, referenceName);

                // Add dependency to the map
                variableDependencyMap.get(currentVariable).add(dependency);

                // Add to expressionDataMap if not already present
                expressionDataMap.putIfAbsent(referenceName, dependency);

                // Recursively process this dependency if not already processed
                if (!variableDependencyMap.containsKey(dependency)) {
                    variableDependencyMap.put(dependency, new ArrayList<>());
                    buildDependencyMap(referenceName);
                }
            }
        } else {
            // For non-variable assignments, determine their type
            evaluateTypeAndAddToDependencyMap(currentVariable, assignedValue);
        }
    }

    /**
     * Evaluates the type of an expression and adds it to the dependency map
     *
     * @param currentVariable The current variable's ExpressionData
     * @param expression The expression to evaluate
     */
    private void evaluateTypeAndAddToDependencyMap(ExpressionData currentVariable, PyExpression expression) {
        // Temporary store for collected types
        List<ExpressionData> tempTypes = new ArrayList<>(collectedTypes);
        collectedTypes.clear();

        // Use parent class's method to evaluate the type
        evaluateType(expression);

        // Add all determined types to the dependency map
        if (!collectedTypes.isEmpty()) {
            variableDependencyMap.get(currentVariable).addAll(collectedTypes);
        }

        // Restore the previous state
        collectedTypes.clear();
        collectedTypes.addAll(tempTypes);
    }

    /**
     * Overridden to enhance variable tracking
     */
    @Override
    protected void evaluateType(PyExpression expr) {
        if (expr instanceof PyReferenceExpression) {
            // If the expression is a variable reference, handle it specially
            String referenceName = expr.getName();
            if (referenceName != null && !referenceName.isEmpty()) {
                ExpressionData reference = new ExpressionData(expr, referenceName);
                collectedTypes.add(reference);

                // Recursively process this reference if not already in our map
                if (!variableDependencyMap.containsKey(reference)) {
                    expressionDataMap.putIfAbsent(referenceName, reference);
                    variableDependencyMap.put(reference, new ArrayList<>());
                    buildDependencyMap(referenceName);
                }
                return;
            }
        }

        // Use the parent implementation for other expression types
        super.evaluateType(expr);
    }
}