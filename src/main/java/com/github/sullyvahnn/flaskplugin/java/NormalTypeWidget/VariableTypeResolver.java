package com.github.sullyvahnn.flaskplugin.java.NormalTypeWidget;

import com.github.sullyvahnn.flaskplugin.java.ExpressionData.ExpressionData;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

public class VariableTypeResolver {
    protected List<ExpressionData> collectedTypes;
    protected TypeEvalContext context;
    protected PsiElement element;
    protected PsiElement scope;
    PsiFile file;
    protected boolean isError = false;

    /**
     * Gets possible types for the element at the current caret position.
     * Determines the appropriate resolution strategy based on the element type.
     *
     * @param event The caret event containing position information
     * @return List of expression data containing possible types, or null if an error occurred
     */
    public @Nullable List<ExpressionData> getPossibleTypes(CaretEvent event) {
        initializeElements(event);
//      if caret on parameter in function declaration
        if(element instanceof PyParameter) {
            findDeclarationParameterType((PyParameter) element);
            return collectedTypes;
        }
//      if caret on parameter in function body
        if(isEvaluateParameter(element))
            return collectedTypes;
//      if caret on variable or call
        findVariableAssignments(element);
        return collectedTypes;
    }

    /**
     * Initializes all necessary elements to get PsiElement from PsiTree and later evaluate its context
     * Handles NullPointerException for file, element and scope
     * checks if element is variable
     * @param event The caret event
     */
    protected void initializeElements(CaretEvent event) {
        collectedTypes = new ArrayList<>();
        file = getPsiFile(event);
        if (file == null) {
            isError = true;
            return;
        }

        element = getPsiElementAtCaret(event, file);
        if (element == null) {
            isError = true;
            return;
        }
        element = element.getParent();
        if (!isVariable(element)) {
            isError = true;
            return;
        }

        scope = getScope(element);
        if (scope == null) {
            isError = true;
            return;
        }

        context = TypeEvalContext.codeAnalysis(scope.getProject(), scope.getContainingFile());

    }

    /**
     * checks all assignments in identifier scope
     * if assignment target is equal to identifier adds type to collected types
     * if identifier is function call it handles it like normal variable and returns types of function
     * @param identifier identifier under caret
     */
    protected void findVariableAssignments(PsiElement identifier) {

        scope.getContainingFile().accept(new PyRecursiveElementVisitor() {
            // if assignment check if target equals identifier
            @Override
            public void visitPyAssignmentStatement(@NotNull PyAssignmentStatement assignment) {
                super.visitPyAssignmentStatement(assignment);

                PyExpression[] targets = assignment.getTargets();
                PyExpression assignedValue = assignment.getAssignedValue();
                // additional logic for x,y,z = 1,2,3
                if(assignedValue instanceof PyTupleExpression) {
                    handleTuple(assignedValue, targets, identifier);
                    return;
                }
                if (assignedValue == null) return;
                //check type of every assignment expression
                for (PyExpression target : targets) {
                    if (Objects.equals(target.getName(), identifier.getText())) {
                        evaluateType(assignedValue);
                    }
                }
            }
            @Override
            public void visitPyCallExpression(@NotNull PyCallExpression node) {
                if(!identifier.isEquivalentTo(node.getCallee())) return;
                evaluateType(node);
            }
        });
    }

    /**
     * finds function which contains identifier as one of the parameters
     * checks if it already has declared type by annotation
     * if not finds what parameter is selected and runs helper function searchAllCalls()
     * @param identifier identifier under caret
     */
    protected void findDeclarationParameterType(@NotNull PyParameter identifier) {
        // if annotated return declared type
        if(identifier.getAsNamed() == null) return;
        PyNamedParameter namedParameter = identifier.getAsNamed();
        if(identifier.getAsNamed().getAnnotation() != null) {
            addParamAnnotationTypes(namedParameter);
            return;
        }
        // find containing function
        PyFunction function = PsiTreeUtil.getParentOfType(identifier, PyFunction.class);
        // if doesnt have function parent
        if (function == null) return;
        PyParameterList args = function.getParameterList();
        PyParameter[] arguments = args.getParameters();
        // find param place in arguments
        int idx=0;
        for(PyParameter param : arguments) {
            if (Objects.equals(param.getName(), identifier.getText())) {
                break;
            }
            idx++;
        }
        // search all function calls for every possible type
        searchAllCalls(function, idx);
    }

    /**
     * search all calls of function in file and adds type of argument with index x to collected types
     * @param expression function call we need to find
     * @param idx index of argument we need to check
     */

    private void searchAllCalls(@NotNull PyFunction expression ,int idx) {
        file.accept(new PyRecursiveElementVisitor() {
            @Override
            public void visitPyCallExpression(@NotNull PyCallExpression node) {
                if(node.getCallee() == null) return;
                String callName = Objects.requireNonNull(node.getCallee()).getName();
                if(callName==null || callName.isEmpty()) return;
                // compare calle with expression
                if(callName.equals(expression.getName())) {
                    PyArgumentList args = node.getArgumentList();
                    if (args == null) return;
                    PyExpression[] arguments = args.getArguments();
                    if (idx >= arguments.length) return;
                    // add type of argument with index idx to collected types
                    evaluateType(arguments[idx]);
                }
            }
        });
    }

    /**
     * Helper function to separate handle annotation types and add it to collected types
     * Extracts return type from function annotation and adds it to collected types
     *
     * @param expression expression to create ExpressionData
     * @param function annotated function
     */
    private void addFunctionAnnotationTypes(PyCallExpression expression, PyFunction function) {
        if(Objects.requireNonNull(function.getAnnotation()).getValue() == null) return;
        String type = function.getAnnotation().getValue().getText().replaceAll("->","");
        for(String t: separateUnionType(type)) {
            collectedTypes.add(makeExpressionData(expression, t));
        }
    }

    /**
     * Helper function to add annotation type to function parameter
     * Extracts type information from parameter annotation and adds it to collected types
     *
     * @param namedParameter annotated parameter
     */
    private void addParamAnnotationTypes(@NotNull PyNamedParameter namedParameter) {
        if(Objects.requireNonNull(namedParameter.getAnnotation()).getValue() == null) return;
        String type = namedParameter.getAnnotation().getValue().getText().replaceAll(":","");
        for(String t: separateUnionType(type)) {
            collectedTypes.add(makeExpressionData(namedParameter, t));
        }
    }

    /**
     * Checks if the element is a parameter reference in a function body
     * If it is, finds the declaration of the parameter and evaluates its type
     *
     * @param element The element to check
     * @return true if the element is a parameter reference, false otherwise
     */
    public boolean isEvaluateParameter(PsiElement element) {
        if (element instanceof PyReferenceExpression targetExpr) {
            // Get containing function
            PyFunction containingFunction = PsiTreeUtil.getParentOfType(targetExpr, PyFunction.class);
            if (containingFunction == null) {
                return false;
            }

            // Get parameter list
            PyParameterList paramList = containingFunction.getParameterList();
            PyParameter[] parameters = paramList.getParameters();

            // Check if the target's name matches any parameter name
            String targetName = targetExpr.getName();
            for (PyParameter param : parameters) {
                if (Objects.equals(param.getName(), targetName)) {
                    findDeclarationParameterType(param);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Handles tuple assignments like x, y, z = 1, 2, 3
     * Matches targets with assigned values by position and evaluates types
     *
     * @param assignedValue The tuple expression being assigned
     * @param targets The target expressions
     * @param identifier The identifier being analyzed
     */
    private void handleTuple(PyExpression assignedValue,
                             PyExpression @NotNull [] targets,
                             PsiElement identifier) {
        PyExpression []assignments = ((PyTupleExpression) assignedValue).getElements();
        if (assignments.length != targets.length) {
            return;
        }
        for (int i = 0; i < assignments.length; i++) {
            if (Objects.equals(targets[i].getName(), identifier.getText())) {
                evaluateType(assignments[i]);
            }
        }
    }

    /**
     * Checks if the expression is a variable and evaluates its type
     * If it's a variable, recursively finds all assignments to it
     *
     * @param expression The expression to check
     * @return true if the expression is a variable, false otherwise
     */
    protected boolean isEvaluateVariable(PyExpression expression) {
        if (isVariable(expression)) {
            findVariableAssignments(expression);
            return true;
        }
        return false;
    }

    /**
     * Checks if the expression is a function call and evaluates its return type
     * If it's a function call, finds all return statements in the function body
     *
     * @param expression The expression to check
     * @return true if the expression is a function call, false otherwise
     */
    protected boolean isEvaluateFunction(PyExpression expression) {
        if(expression instanceof PyCallExpression) {
            searchAllReturns((PyCallExpression) expression);
            return true;
        }
        return false;
    }

    /**
     * Gets the function definition for a call expression
     * Resolves the reference to find the actual function being called
     *
     * @param callExpression The call expression to resolve
     * @return The PyFunction object representing the function definition, or null if not found
     */
    protected PyFunction getFunctionBody(PyCallExpression callExpression) {
        if (callExpression == null) {
            return null;
        }

        // Step 1: Get the callee (the function reference being called)
        PyExpression callee = callExpression.getCallee();
        if (callee == null || callee.getReference() == null) {
            return null;
        }

        // Step 2: Resolve the reference to get the actual function definition
        PsiElement resolved = callee.getReference().resolve();
        if (!(resolved instanceof PyFunction)) {
            return null;
        }

        // Step 3: Get the statement list (the function body)
        return (PyFunction) resolved;
    }

    /**
     * Searches all return statements in a function and evaluates their types
     * Also handles function annotations and conditionally unreachable returns
     *
     * @param expression The call expression whose return types to find
     */
    protected void searchAllReturns(PyCallExpression expression) {
        PyFunction function = getFunctionBody(expression);
        if(function == null) return;
        if(function.getAnnotation() != null) {
            addFunctionAnnotationTypes(expression, function);
        }
        function.getStatementList().accept(new PyRecursiveElementVisitor() {
            boolean isNoneAdded = false;
            @Override
            public void visitPyReturnStatement(@NotNull PyReturnStatement returnStatement) {
                if(!isNoneAdded && isReturnUnreachable(returnStatement)) {
                    isNoneAdded = true;
                    addNoneType();
                }
                evaluateType(returnStatement.getExpression());
            }

            @Override
            public void visitPyFunction(@NotNull PyFunction function) {
                // Skip nested function definitions to prevent finding returns
                // that belong to nested functions rather than the target function
            }
            private void addNoneType() {
                ExpressionData expressionData = new ExpressionData(expression, "None");
                collectedTypes.add(expressionData);
            }
        });
    }

    /**
     * Checks if a return statement is unreachable
     * Return statements inside conditional blocks may not always be executed,
     * so they are considered potentially unreachable.
     *
     * @param returnStatement The return statement to check
     * @return true if the return statement is inside a control flow structure, false otherwise
     */
    private boolean isReturnUnreachable(PyReturnStatement returnStatement) {
        // Check if the return statement is inside a blocking control flow structure
        return PsiTreeUtil.getParentOfType(returnStatement,
                PyIfStatement.class,
                PyForStatement.class,
                PyWhileStatement.class,
                PyElsePart.class,
                PyTryExceptStatement.class,
                PyFinallyPart.class,
                PyMatchStatement.class) != null; // The return is inside a control flow-blocking structure
// No blocking parent found, return is reachable
    }

    /**
     * Creates an ExpressionData object for a given expression and type
     *
     * @param expr The expression
     * @param type The type as a string
     * @return A new ExpressionData object
     */
    private ExpressionData makeExpressionData(PyExpression expr, String type) {
        return new ExpressionData(expr, type);
    }

    /**
     * Evaluates the type of an expression and adds it to collected types
     * Handles variables, function calls, and direct value expressions differently
     *
     * @param expr The expression to evaluate
     */
    protected void evaluateType(PyExpression expr) {
        if(isEvaluateVariable(expr)) return;
        if(isEvaluateFunction(expr)) return;
        PyType type = context.getType(expr);
        if(type == null && expr instanceof PyCallExpression) {
            String typeString = Objects.requireNonNull(((PyCallExpression) expr).getCallee()).getText()+"()";
            collectedTypes.add(makeExpressionData(expr, typeString));
            return;
        }
        if (type == null) return;
        for(String t : separateTypes(type)) {
            collectedTypes.add(makeExpressionData(expr, t));
        }

    }

    /**
     * Separates a PyType object into a list of string type names
     * Handles union types by extracting all member types
     *
     * @param type The PyType to separate
     * @return List of type names as strings
     */
    protected @NotNull List<String> separateTypes(PyType type) {
        List<String> result = new ArrayList<>();

        if (type instanceof PyUnionType unionType) {
            for (PyType t : unionType.getMembers()) {
                result.addAll(getTypeName(t));
            }
        } else {
            result.addAll(getTypeName(type));
        }
        return result;
    }

    /**
     * Gets the name of a PyType and handles union types recursively
     *
     * @param type The PyType to get the name of
     * @return List of type names as strings
     */
    private @NotNull List<String> getTypeName(PyType type) {
        ArrayList<String> result = new ArrayList<>();
        if (type instanceof PyUnionType unionType) {
            unionType.getMembers().forEach(member -> {
                List<String> separatedTypes = separateUnionType(member.getName());
                result.addAll(separatedTypes);
            });
        } else {
            if (type == null) return Collections.emptyList();
            List<String> separatedTypes = separateUnionType(type.getName());
            result.addAll(separatedTypes);
        }

        return result;// Handle None explicitly
    }

    /**
     * Separates a union type string into a list of individual type names
     * For example, "str|int" becomes ["str", "int"]
     *
     * @param text The union type string to separate
     * @return List of individual type names, or null if the input is null or empty
     */
    @Contract("null -> null")
    public static @Unmodifiable List<String> separateUnionType(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        return List.of(text.split("\\|", 2));
    }

    /**
     * Checks if an element is a variable reference or target expression
     *
     * @param element The element to check
     * @return true if the element is a variable, false otherwise
     */
    protected static boolean isVariable(PsiElement element) {
        return element instanceof PyReferenceExpression ||
                element instanceof PyTargetExpression;

    }

    /**
     * Gets the scope owner for an element
     * Walks up the PSI tree until a scope owner is found
     *
     * @param element The element to find the scope for
     * @return The scope owner element, or null if not found
     */
    protected PsiElement getScope(PsiElement element) {
        while (element != null) {
            if (element instanceof ScopeOwner) {
                return element;
            } else {
                element = element.getParent();
            }
        }
        return null;
    }

    /**
     * Gets the PSI element at the caret position
     *
     * @param event The caret event
     * @param file The PSI file
     * @return The PSI element at the caret position
     */
    protected PsiElement getPsiElementAtCaret(@NotNull CaretEvent event, @NotNull PsiFile file) {
        int offset = Objects.requireNonNull(event.getCaret()).getOffset();
        return file.findElementAt(offset);
    }

    /**
     * Gets the PSI file from a caret event
     *
     * @param event The caret event
     * @return The PSI file, or null if not found
     */
    protected @Nullable PsiFile getPsiFile(@NotNull CaretEvent event) {
        Project project = event.getEditor().getProject();
        if (project == null) return null;
        return PsiDocumentManager.getInstance(project).getPsiFile(event.getEditor().getDocument());
    }
}