package com.sun.source.doctree;

import java.util.List;

@jdk.Exported
public interface AuthorTree extends BlockTagTree {
    List<? extends DocTree> getName();
}
