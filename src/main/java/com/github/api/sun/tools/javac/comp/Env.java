package com.github.api.sun.tools.javac.comp;

import com.github.api.sun.tools.javac.tree.JCTree;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class Env<A> implements Iterable<Env<A>> {

    public Env<A> next;

    public Env<A> outer;

    public JCTree tree;

    public JCTree.JCCompilationUnit toplevel;

    public JCTree.JCClassDecl enclClass;

    public JCTree.JCMethodDecl enclMethod;

    public A info;

    public boolean baseClause = false;

    public Env(JCTree tree, A info) {
        this.next = null;
        this.outer = null;
        this.tree = tree;
        this.toplevel = null;
        this.enclClass = null;
        this.enclMethod = null;
        this.info = info;
    }

    public Env<A> dup(JCTree tree, A info) {
        return dupto(new Env<A>(tree, info));
    }

    public Env<A> dupto(Env<A> that) {
        that.next = this;
        that.outer = this.outer;
        that.toplevel = this.toplevel;
        that.enclClass = this.enclClass;
        that.enclMethod = this.enclMethod;
        return that;
    }

    public Env<A> dup(JCTree tree) {
        return dup(tree, this.info);
    }

    public Env<A> enclosing(JCTree.Tag tag) {
        Env<A> env1 = this;
        while (env1 != null && !env1.tree.hasTag(tag)) env1 = env1.next;
        return env1;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Env[").append(info);
        if (outer != null)
            sb.append(",outer=").append(outer);
        sb.append("]");
        return sb.toString();
    }

    public Iterator<Env<A>> iterator() {
        return new Iterator<Env<A>>() {
            Env<A> next = Env.this;

            public boolean hasNext() {
                return next.outer != null;
            }

            public Env<A> next() {
                if (hasNext()) {
                    Env<A> current = next;
                    next = current.outer;
                    return current;
                }
                throw new NoSuchElementException();
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
