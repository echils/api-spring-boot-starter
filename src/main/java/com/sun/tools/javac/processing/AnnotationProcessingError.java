package com.sun.tools.javac.processing;

public class AnnotationProcessingError extends Error {
    static final long serialVersionUID = 305337707019230790L;

    AnnotationProcessingError(Throwable cause) {
        super(cause);
    }
}
