package com.github.api.sun.tools.javac.parser;

import com.github.api.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.github.api.sun.tools.javac.tree.JCTree.JCExpression;
import com.github.api.sun.tools.javac.tree.JCTree.JCStatement;

public interface Parser {

    JCCompilationUnit parseCompilationUnit();

    JCExpression parseExpression();

    JCStatement parseStatement();

    JCExpression parseType();
}
