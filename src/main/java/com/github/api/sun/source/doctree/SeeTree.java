package com.github.api.sun.source.doctree;

import java.util.List;

@jdk.Exported
public interface SeeTree extends BlockTagTree {
    List<? extends DocTree> getReference();
}
