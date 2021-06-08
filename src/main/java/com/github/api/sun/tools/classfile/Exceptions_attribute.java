package com.github.api.sun.tools.classfile;

import java.io.IOException;

public class Exceptions_attribute extends Attribute {
    public final int number_of_exceptions;
    public final int[] exception_index_table;

    Exceptions_attribute(ClassReader cr, int name_index, int length) throws IOException {
        super(name_index, length);
        number_of_exceptions = cr.readUnsignedShort();
        exception_index_table = new int[number_of_exceptions];
        for (int i = 0; i < number_of_exceptions; i++)
            exception_index_table[i] = cr.readUnsignedShort();
    }

    public Exceptions_attribute(ConstantPool constant_pool, int[] exception_index_table)
            throws ConstantPoolException {
        this(constant_pool.getUTF8Index(Attribute.Exceptions), exception_index_table);
    }

    public Exceptions_attribute(int name_index, int[] exception_index_table) {
        super(name_index, 2 + 2 * exception_index_table.length);
        this.number_of_exceptions = exception_index_table.length;
        this.exception_index_table = exception_index_table;
    }

    public String getException(int index, ConstantPool constant_pool) throws ConstantPoolException {
        int exception_index = exception_index_table[index];
        return constant_pool.getClassInfo(exception_index).getName();
    }

    public <R, D> R accept(Visitor<R, D> visitor, D data) {
        return visitor.visitExceptions(this, data);
    }
}
