package com.github.sullyvahnn.flaskplugin.java.TreeTypeWidget;

import com.github.sullyvahnn.flaskplugin.java.ExpressionData.ExpressionData;
import com.github.sullyvahnn.flaskplugin.java.NormalTypeWidget.VariableTypeResolver;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TreeVariableTypeResolver extends VariableTypeResolver {
    private final Stack<ExpressionData> expressionStack = new Stack<>();
    private final Map<ExpressionData, List<ExpressionData>> expressionDependencyMap = new HashMap<>();
    private ExpressionData root;
    List<String> processedVariableNames;

    /**
     * Gets possible types for the element at the current caret position.
     * Overrides parent method to initialize and clear data structures.
     *
     * @param event The caret event containing position information
     * @return Map of expression data containing possible types, or null if an error occurred
     */

    public @Nullable Map<ExpressionData,List<ExpressionData>> getPossibleTreeTypes(CaretEvent event) {
        processedVariableNames = new ArrayList<>();
        expressionStack.clear();
        expressionDependencyMap.clear();
        root=null;
        super.getPossibleTypes(event);
        return expressionDependencyMap;
    }

    @Override
    protected void findDeclarationParameterType(@NotNull PyParameter identifier) {
        createConnection(new ExpressionData((PyExpression) identifier, identifier.getName()));
        super.findDeclarationParameterType(identifier);
        expressionStack.pop();
    }

    @Override
    protected boolean isEvaluateParameter(PsiElement element) {
        if (element instanceof PyReferenceExpression targetExpr) {
            // Get containing function
            PyFunction containingFunction = PsiTreeUtil.getParentOfType(targetExpr, PyFunction.class);
            if (containingFunction == null) {
                return false;
            }
            createConnection(new ExpressionData(targetExpr, targetExpr.getName()));
            super.isEvaluateParameter(element);
        }
        return false;
    }

    /**
     * Overrides the parent method to push the current identifier to the stack
     * and record dependencies between variables.
     *
     * @param identifier identifier under caret
     */
    @Override
    protected void findVariableAssignments(PsiElement identifier) {
        if (isError) return;
        ExpressionData current;
        if (identifier.getNextSibling() instanceof PyArgumentList) {
            current = new ExpressionData((PyExpression) identifier, ((PyExpression) identifier).getName() + "()");
        } else {
            current = new ExpressionData((PyExpression) identifier, identifier.getText());
        }
        if(root == null) root = current;

        createConnection(current);

        super.findVariableAssignments(identifier);

        // Pop from stack when we're done with this variable
        if (expressionStack.size() != 1) {
            expressionStack.pop();
        }
        collectedTypes.clear();
    }

    @Override
    protected void evaluateType(PyExpression expression) {
        if (isError) return;
        collectedTypes.clear();
        if(processedVariableNames.contains(expression.getText())) return;
        super.evaluateType(expression);
        addToDependencyMap();

    }

    private void addToDependencyMap() {
        if(expressionStack.isEmpty()) return;
        ExpressionData currentExpressionData = expressionStack.peek();
        // Get newly added expression data items
        List<ExpressionData> newExpressionData = collectedTypes;

        for (ExpressionData data : newExpressionData) {
            // Create an entry for the current expression if it doesn't exist
            ExpressionData key = findDependencyMapKey(currentExpressionData.type);
            if( key == null) {
                expressionDependencyMap.put(currentExpressionData, new ArrayList<>());
                expressionDependencyMap.get(currentExpressionData).add(data);
            } else {
                if(!checkIfAbsent(key, data)) return;
                expressionDependencyMap.get(key).add(data);
            }
            return;
        }
    }

    private boolean checkIfAbsent(ExpressionData key, ExpressionData value) {
        if(key == null && value == null) return true;
       for (ExpressionData data : expressionDependencyMap.get(key)) {
           if(data.lineNumber == value.lineNumber &&
                   Objects.equals(data.type, value.type)) {
               return false;
           }
           if(Objects.equals(data,value)) return false;
       }
       return true;
    }

    private ExpressionData findDependencyMapKey(String type) {
        for (ExpressionData expressionData : expressionDependencyMap.keySet()) {
            if(expressionData.type.equals(type)) {
                return expressionData;
            }
        }
        return null;
    }

    /**
     * creates connection between expressionData and latest value on expressionStack
     * @param expressionData expression we need to connect
     */
    private void createConnection(ExpressionData expressionData) {
//        if(ignoreNextConnection) return;
        if (!expressionStack.isEmpty()) {
            List<ExpressionData> copiedCollectedTypes = collectedTypes;
            collectedTypes = List.of(expressionData);
            addToDependencyMap();
            collectedTypes = copiedCollectedTypes;
        }
        processedVariableNames.add(expressionData.type);
        expressionStack.push(expressionData);
    }

    /**
     * special handling to None type
     * @param expression expression with None Type possition
     */
    @Override
    protected void addNoneType(PyExpression expression) {
        List<ExpressionData> copiedCollectedTypes = collectedTypes;
        collectedTypes.clear();
        super.addNoneType(expression);
        addToDependencyMap();
        collectedTypes = copiedCollectedTypes;
    }
    public ExpressionData getRoot() {
        return root;
    }
}