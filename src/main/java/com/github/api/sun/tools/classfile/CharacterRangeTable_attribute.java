package com.github.api.sun.tools.classfile;

import java.io.IOException;

public class CharacterRangeTable_attribute extends Attribute {
    public static final int CRT_STATEMENT = 0x0001;
    public static final int CRT_BLOCK = 0x0002;
    public static final int CRT_ASSIGNMENT = 0x0004;
    public static final int CRT_FLOW_CONTROLLER = 0x0008;
    public static final int CRT_FLOW_TARGET = 0x0010;
    public static final int CRT_INVOKE = 0x0020;
    public static final int CRT_CREATE = 0x0040;
    public static final int CRT_BRANCH_TRUE = 0x0080;
    public static final int CRT_BRANCH_FALSE = 0x0100;
    public final Entry[] character_range_table;

    CharacterRangeTable_attribute(ClassReader cr, int name_index, int length) throws IOException {
        super(name_index, length);
        int character_range_table_length = cr.readUnsignedShort();
        character_range_table = new Entry[character_range_table_length];
        for (int i = 0; i < character_range_table_length; i++)
            character_range_table[i] = new Entry(cr);
    }

    public CharacterRangeTable_attribute(ConstantPool constant_pool, Entry[] character_range_table)
            throws ConstantPoolException {
        this(constant_pool.getUTF8Index(Attribute.CharacterRangeTable), character_range_table);
    }

    public CharacterRangeTable_attribute(int name_index, Entry[] character_range_table) {
        super(name_index, 2 + character_range_table.length * Entry.length());
        this.character_range_table = character_range_table;
    }

    public <R, D> R accept(Visitor<R, D> visitor, D data) {
        return visitor.visitCharacterRangeTable(this, data);
    }

    public static class Entry {
        public final int start_pc;
        public final int end_pc;
        public final int character_range_start;
        public final int character_range_end;
        public final int flags;
        Entry(ClassReader cr) throws IOException {
            start_pc = cr.readUnsignedShort();
            end_pc = cr.readUnsignedShort();
            character_range_start = cr.readInt();
            character_range_end = cr.readInt();
            flags = cr.readUnsignedShort();
        }

        public static int length() {
            return 14;
        }
    }

}
