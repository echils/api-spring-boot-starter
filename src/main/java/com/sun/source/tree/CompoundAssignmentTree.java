package com.sun.source.tree;
@jdk.Exported
public interface CompoundAssignmentTree extends ExpressionTree {
    ExpressionTree getVariable();
    ExpressionTree getExpression();
}
