package com.github.api.sun.source.util;

import com.github.api.sun.source.tree.Tree;

@jdk.Exported
public class TreePathScanner<R, P> extends TreeScanner<R, P> {
    private TreePath path;

    public R scan(TreePath path, P p) {
        this.path = path;
        try {
            return path.getLeaf().accept(this, p);
        } finally {
            this.path = null;
        }
    }

    @Override
    public R scan(Tree tree, P p) {
        if (tree == null)
            return null;
        TreePath prev = path;
        path = new TreePath(path, tree);
        try {
            return tree.accept(this, p);
        } finally {
            path = prev;
        }
    }

    public TreePath getCurrentPath() {
        return path;
    }
}
