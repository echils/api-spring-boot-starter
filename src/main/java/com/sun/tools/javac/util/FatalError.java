package com.sun.tools.javac.util;

public class FatalError extends Error {
    private static final long serialVersionUID = 0;

    public FatalError(JCDiagnostic d) {
        super(d.toString());
    }

    public FatalError(JCDiagnostic d, Throwable t) {
        super(d.toString(), t);
    }

    public FatalError(String s) {
        super(s);
    }
}
