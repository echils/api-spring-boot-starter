package com.sun.tools.classfile;

import java.io.IOException;

public class Signature_attribute extends Attribute {
    public final int signature_index;

    Signature_attribute(ClassReader cr, int name_index, int length) throws IOException {
        super(name_index, length);
        signature_index = cr.readUnsignedShort();
    }

    public Signature_attribute(ConstantPool constant_pool, int signature_index)
            throws ConstantPoolException {
        this(constant_pool.getUTF8Index(Attribute.Signature), signature_index);
    }

    public Signature_attribute(int name_index, int signature_index) {
        super(name_index, 2);
        this.signature_index = signature_index;
    }

    public String getSignature(ConstantPool constant_pool) throws ConstantPoolException {
        return constant_pool.getUTF8Value(signature_index);
    }

    public Signature getParsedSignature() {
        return new Signature(signature_index);
    }

    public <R, D> R accept(Visitor<R, D> visitor, D data) {
        return visitor.visitSignature(this, data);
    }
}
