package com.github.api.sun.tools.classfile;

import java.io.IOException;

public abstract class RuntimeParameterAnnotations_attribute extends Attribute {
    public final Annotation[][] parameter_annotations;

    RuntimeParameterAnnotations_attribute(ClassReader cr, int name_index, int length)
            throws IOException, Annotation.InvalidAnnotation {
        super(name_index, length);
        int num_parameters = cr.readUnsignedByte();
        parameter_annotations = new Annotation[num_parameters][];
        for (int p = 0; p < parameter_annotations.length; p++) {
            int num_annotations = cr.readUnsignedShort();
            Annotation[] annotations = new Annotation[num_annotations];
            for (int i = 0; i < num_annotations; i++)
                annotations[i] = new Annotation(cr);
            parameter_annotations[p] = annotations;
        }
    }

    protected RuntimeParameterAnnotations_attribute(int name_index, Annotation[][] parameter_annotations) {
        super(name_index, length(parameter_annotations));
        this.parameter_annotations = parameter_annotations;
    }

    private static int length(Annotation[][] anno_arrays) {
        int n = 1;
        for (Annotation[] anno_array : anno_arrays) {
            n += 2;
            for (Annotation anno : anno_array)
                n += anno.length();
        }
        return n;
    }
}
