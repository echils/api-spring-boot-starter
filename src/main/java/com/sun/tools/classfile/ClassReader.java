package com.sun.tools.classfile;

import java.io.*;

public class ClassReader {
    private DataInputStream in;
    private ClassFile classFile;
    private Attribute.Factory attributeFactory;

    ClassReader(ClassFile classFile, InputStream in, Attribute.Factory attributeFactory) throws IOException {

        classFile.getClass();
        attributeFactory.getClass();
        this.classFile = classFile;
        this.in = new DataInputStream(new BufferedInputStream(in));
        this.attributeFactory = attributeFactory;
    }

    ClassFile getClassFile() {
        return classFile;
    }

    ConstantPool getConstantPool() {
        return classFile.constant_pool;
    }

    public Attribute readAttribute() throws IOException {
        int name_index = readUnsignedShort();
        int length = readInt();
        byte[] data = new byte[length];
        readFully(data);
        DataInputStream prev = in;
        in = new DataInputStream(new ByteArrayInputStream(data));
        try {
            return attributeFactory.createAttribute(this, name_index, data);
        } finally {
            in = prev;
        }
    }

    public void readFully(byte[] b) throws IOException {
        in.readFully(b);
    }

    public int readUnsignedByte() throws IOException {
        return in.readUnsignedByte();
    }

    public int readUnsignedShort() throws IOException {
        return in.readUnsignedShort();
    }

    public int readInt() throws IOException {
        return in.readInt();
    }

    public long readLong() throws IOException {
        return in.readLong();
    }

    public float readFloat() throws IOException {
        return in.readFloat();
    }

    public double readDouble() throws IOException {
        return in.readDouble();
    }

    public String readUTF() throws IOException {
        return in.readUTF();
    }
}
