package com.github.api.sun.source.tree;

import javax.lang.model.element.Name;

@jdk.Exported
public interface IdentifierTree extends ExpressionTree {
    Name getName();
}
