package com.sun.source.tree;

import java.util.List;

@jdk.Exported
public interface IntersectionTypeTree extends Tree {
    List<? extends Tree> getBounds();
}
