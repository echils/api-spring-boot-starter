package com.github.api.sun.source.tree;

import java.util.List;

@jdk.Exported
public interface UnionTypeTree extends Tree {
    List<? extends Tree> getTypeAlternatives();
}
