package com.sun.tools.classfile;

import java.io.IOException;

public class MethodParameters_attribute extends Attribute {
    public final int method_parameter_table_length;
    public final Entry[] method_parameter_table;

    MethodParameters_attribute(ClassReader cr,
                               int name_index,
                               int length)
            throws IOException {
        super(name_index, length);
        method_parameter_table_length = cr.readUnsignedByte();
        method_parameter_table = new Entry[method_parameter_table_length];
        for (int i = 0; i < method_parameter_table_length; i++)
            method_parameter_table[i] = new Entry(cr);
    }

    public MethodParameters_attribute(ConstantPool constant_pool,
                                      Entry[] method_parameter_table)
            throws ConstantPoolException {
        this(constant_pool.getUTF8Index(Attribute.MethodParameters),
                method_parameter_table);
    }

    public MethodParameters_attribute(int name_index,
                                      Entry[] method_parameter_table) {
        super(name_index, 1 + method_parameter_table.length * Entry.length());
        this.method_parameter_table_length = method_parameter_table.length;
        this.method_parameter_table = method_parameter_table;
    }

    public <R, D> R accept(Visitor<R, D> visitor, D data) {
        return visitor.visitMethodParameters(this, data);
    }

    public static class Entry {
        public final int name_index;
        public final int flags;

        Entry(ClassReader cr) throws IOException {
            name_index = cr.readUnsignedShort();
            flags = cr.readUnsignedShort();
        }

        public static int length() {
            return 6;
        }
    }
}
