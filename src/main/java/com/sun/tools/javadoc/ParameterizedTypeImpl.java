package com.sun.tools.javadoc;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ParameterizedType;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;

import static com.sun.tools.javac.code.TypeTag.CLASS;

public class ParameterizedTypeImpl extends AbstractTypeImpl implements ParameterizedType {

    ParameterizedTypeImpl(DocEnv env, Type type) {
        super(env, type);
    }

    static String parameterizedTypeToString(DocEnv env, ClassType cl,
                                            boolean full) {
        if (env.legacyDoclet) {
            return TypeMaker.getTypeName(cl, full);
        }
        StringBuilder s = new StringBuilder();
        if (!(cl.getEnclosingType().hasTag(CLASS))) {
            s.append(TypeMaker.getTypeName(cl, full));
        } else {
            ClassType encl = (ClassType) cl.getEnclosingType();
            s.append(parameterizedTypeToString(env, encl, full))
                    .append('.')
                    .append(cl.tsym.name.toString());
        }
        s.append(TypeMaker.typeArgumentsString(env, cl, full));
        return s.toString();
    }

    @Override
    public ClassDoc asClassDoc() {
        return env.getClassDoc((ClassSymbol) type.tsym);
    }

    public com.sun.javadoc.Type[] typeArguments() {
        return TypeMaker.getTypes(env, type.getTypeArguments());
    }

    public com.sun.javadoc.Type superclassType() {
        if (asClassDoc().isInterface()) {
            return null;
        }
        Type sup = env.types.supertype(type);
        return TypeMaker.getType(env,
                (sup != type) ? sup : env.syms.objectType);
    }

    public com.sun.javadoc.Type[] interfaceTypes() {
        return TypeMaker.getTypes(env, env.types.interfaces(type));
    }

    public com.sun.javadoc.Type containingType() {
        if (type.getEnclosingType().hasTag(CLASS)) {
            return TypeMaker.getType(env, type.getEnclosingType());
        }
        ClassSymbol enclosing = type.tsym.owner.enclClass();
        if (enclosing != null) {
            return env.getClassDoc(enclosing);
        }
        return null;
    }

    @Override
    public String typeName() {
        return TypeMaker.getTypeName(type, false);
    }

    @Override
    public ParameterizedType asParameterizedType() {
        return this;
    }

    @Override
    public String toString() {
        return parameterizedTypeToString(env, (ClassType) type, true);
    }
}
