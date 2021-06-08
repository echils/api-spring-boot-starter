package com.github.api.sun.tools.classfile;

import java.io.IOException;

public class LocalVariableTypeTable_attribute extends Attribute {
    public final int local_variable_table_length;
    public final Entry[] local_variable_table;

    LocalVariableTypeTable_attribute(ClassReader cr, int name_index, int length) throws IOException {
        super(name_index, length);
        local_variable_table_length = cr.readUnsignedShort();
        local_variable_table = new Entry[local_variable_table_length];
        for (int i = 0; i < local_variable_table_length; i++)
            local_variable_table[i] = new Entry(cr);
    }

    public LocalVariableTypeTable_attribute(ConstantPool constant_pool, Entry[] local_variable_table)
            throws ConstantPoolException {
        this(constant_pool.getUTF8Index(Attribute.LocalVariableTypeTable), local_variable_table);
    }

    public LocalVariableTypeTable_attribute(int name_index, Entry[] local_variable_table) {
        super(name_index, 2 + local_variable_table.length * Entry.length());
        this.local_variable_table_length = local_variable_table.length;
        this.local_variable_table = local_variable_table;
    }

    public <R, D> R accept(Visitor<R, D> visitor, D data) {
        return visitor.visitLocalVariableTypeTable(this, data);
    }

    public static class Entry {
        public final int start_pc;
        public final int length;
        public final int name_index;
        public final int signature_index;
        public final int index;
        Entry(ClassReader cr) throws IOException {
            start_pc = cr.readUnsignedShort();
            length = cr.readUnsignedShort();
            name_index = cr.readUnsignedShort();
            signature_index = cr.readUnsignedShort();
            index = cr.readUnsignedShort();
        }

        public static int length() {
            return 10;
        }
    }
}
