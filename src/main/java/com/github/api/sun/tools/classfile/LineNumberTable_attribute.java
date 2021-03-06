package com.github.api.sun.tools.classfile;

import java.io.IOException;

public class LineNumberTable_attribute extends Attribute {
    public final int line_number_table_length;
    public final Entry[] line_number_table;

    LineNumberTable_attribute(ClassReader cr, int name_index, int length) throws IOException {
        super(name_index, length);
        line_number_table_length = cr.readUnsignedShort();
        line_number_table = new Entry[line_number_table_length];
        for (int i = 0; i < line_number_table_length; i++)
            line_number_table[i] = new Entry(cr);
    }

    public LineNumberTable_attribute(ConstantPool constant_pool, Entry[] line_number_table)
            throws ConstantPoolException {
        this(constant_pool.getUTF8Index(Attribute.LineNumberTable), line_number_table);
    }

    public LineNumberTable_attribute(int name_index, Entry[] line_number_table) {
        super(name_index, 2 + line_number_table.length * Entry.length());
        this.line_number_table_length = line_number_table.length;
        this.line_number_table = line_number_table;
    }

    public <R, D> R accept(Visitor<R, D> visitor, D data) {
        return visitor.visitLineNumberTable(this, data);
    }

    public static class Entry {
        public final int start_pc;
        public final int line_number;

        Entry(ClassReader cr) throws IOException {
            start_pc = cr.readUnsignedShort();
            line_number = cr.readUnsignedShort();
        }

        public static int length() {
            return 4;
        }
    }
}
