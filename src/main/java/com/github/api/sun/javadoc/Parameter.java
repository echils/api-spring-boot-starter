package com.github.api.sun.javadoc;

public interface Parameter {
    Type type();

    String name();

    String typeName();

    String toString();

    AnnotationDesc[] annotations();
}
