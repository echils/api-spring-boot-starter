package com.sun.tools.javac.processing;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementScanner8;
import javax.lang.model.util.Types;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class JavacRoundEnvironment implements RoundEnvironment {

    private static final String NOT_AN_ANNOTATION_TYPE =
            "The argument does not represent an annotation type: ";
    private final boolean processingOver;
    private final boolean errorRaised;
    private final ProcessingEnvironment processingEnv;
    private final Set<? extends Element> rootElements;

    JavacRoundEnvironment(boolean processingOver,
                          boolean errorRaised,
                          Set<? extends Element> rootElements,
                          ProcessingEnvironment processingEnv) {
        this.processingOver = processingOver;
        this.errorRaised = errorRaised;
        this.rootElements = rootElements;
        this.processingEnv = processingEnv;
    }

    public String toString() {
        return String.format("[errorRaised=%b, rootElements=%s, processingOver=%b]",
                errorRaised,
                rootElements,
                processingOver);
    }

    public boolean processingOver() {
        return processingOver;
    }

    public boolean errorRaised() {
        return errorRaised;
    }

    public Set<? extends Element> getRootElements() {
        return rootElements;
    }

    public Set<? extends Element> getElementsAnnotatedWith(TypeElement a) {
        Set<Element> result = Collections.emptySet();
        Types typeUtil = processingEnv.getTypeUtils();
        if (a.getKind() != ElementKind.ANNOTATION_TYPE)
            throw new IllegalArgumentException(NOT_AN_ANNOTATION_TYPE + a);
        DeclaredType annotationTypeElement;
        TypeMirror tm = a.asType();
        if (tm instanceof DeclaredType)
            annotationTypeElement = (DeclaredType) a.asType();
        else
            throw new AssertionError("Bad implementation type for " + tm);
        ElementScanner8<Set<Element>, DeclaredType> scanner =
                new AnnotationSetScanner(result, typeUtil);
        for (Element element : rootElements)
            result = scanner.scan(element, annotationTypeElement);
        return result;
    }

    public Set<? extends Element> getElementsAnnotatedWith(Class<? extends Annotation> a) {
        if (!a.isAnnotation())
            throw new IllegalArgumentException(NOT_AN_ANNOTATION_TYPE + a);
        String name = a.getCanonicalName();
        if (name == null)
            return Collections.emptySet();
        else {
            TypeElement annotationType = processingEnv.getElementUtils().getTypeElement(name);
            if (annotationType == null)
                return Collections.emptySet();
            else
                return getElementsAnnotatedWith(annotationType);
        }
    }

    private class AnnotationSetScanner extends
            ElementScanner8<Set<Element>, DeclaredType> {

        Set<Element> annotatedElements = new LinkedHashSet<Element>();
        Types typeUtil;

        AnnotationSetScanner(Set<Element> defaultSet, Types typeUtil) {
            super(defaultSet);
            this.typeUtil = typeUtil;
        }

        @Override
        public Set<Element> visitType(TypeElement e, DeclaredType p) {

            scan(e.getTypeParameters(), p);
            return scan(e.getEnclosedElements(), p);
        }

        @Override
        public Set<Element> visitExecutable(ExecutableElement e, DeclaredType p) {

            scan(e.getTypeParameters(), p);
            return scan(e.getEnclosedElements(), p);
        }

        @Override
        public Set<Element> scan(Element e, DeclaredType p) {
            List<? extends AnnotationMirror> annotationMirrors =
                    processingEnv.getElementUtils().getAllAnnotationMirrors(e);
            for (AnnotationMirror annotationMirror : annotationMirrors) {
                if (typeUtil.isSameType(annotationMirror.getAnnotationType(), p))
                    annotatedElements.add(e);
            }
            e.accept(this, p);
            return annotatedElements;
        }
    }
}
