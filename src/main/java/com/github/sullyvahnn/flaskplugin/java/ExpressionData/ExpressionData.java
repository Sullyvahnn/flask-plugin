package com.github.sullyvahnn.flaskplugin.java.ExpressionData;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyExpression;

public class ExpressionData {
    public final int lineNumber;
    public final String type;
    public PyExpression expression;


    public ExpressionData(PyExpression expr, String type) {
        int offset = expr.getTextOffset();
        // To convert to line and column
        PsiFile containingFile = expr.getContainingFile();
        Project project = containingFile.getProject();
        Document document = PsiDocumentManager.getInstance(project).getDocument(containingFile);
        if (document != null) {
            this.lineNumber = document.getLineNumber(offset);
        } else {
            this.lineNumber = -1;
        }
        this.type = type;
        this.expression = expr;
    }

}
