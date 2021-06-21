package com.github.api.sun.tools.javac.code;

import com.github.api.sun.tools.javac.code.Symbol.*;
import com.github.api.sun.tools.javac.code.Type.*;
import com.github.api.sun.tools.javac.jvm.ByteCodes;
import com.github.api.sun.tools.javac.jvm.ClassReader;
import com.github.api.sun.tools.javac.jvm.Target;
import com.github.api.sun.tools.javac.util.*;

import javax.lang.model.element.ElementVisitor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.github.api.sun.tools.javac.code.Flags.*;
import static com.github.api.sun.tools.javac.code.TypeTag.*;
import static com.github.api.sun.tools.javac.jvm.ByteCodes.*;

public class Symtab {

    protected static final Context.Key<Symtab> symtabKey =
            new Context.Key<Symtab>();
    public final JCPrimitiveType byteType = new JCPrimitiveType(BYTE, null);
    public final JCPrimitiveType charType = new JCPrimitiveType(CHAR, null);
    public final JCPrimitiveType shortType = new JCPrimitiveType(SHORT, null);
    public final JCPrimitiveType intType = new JCPrimitiveType(INT, null);
    public final JCPrimitiveType longType = new JCPrimitiveType(LONG, null);
    public final JCPrimitiveType floatType = new JCPrimitiveType(FLOAT, null);
    public final JCPrimitiveType doubleType = new JCPrimitiveType(DOUBLE, null);
    public final JCPrimitiveType booleanType = new JCPrimitiveType(BOOLEAN, null);
    public final Type botType = new BottomType();
    public final JCVoidType voidType = new JCVoidType();
    public final PackageSymbol rootPackage;
    public final PackageSymbol unnamedPackage;
    public final TypeSymbol noSymbol;
    public final ClassSymbol errSymbol;
    public final ClassSymbol unknownSymbol;
    public final Type errType;
    public final Type unknownType;
    public final ClassSymbol arrayClass;
    public final MethodSymbol arrayCloneMethod;
    public final ClassSymbol boundClass;
    public final ClassSymbol methodClass;
    public final Type objectType;
    public final Type classType;
    public final Type classLoaderType;
    public final Type stringType;
    public final Type stringBufferType;
    public final Type stringBuilderType;
    public final Type cloneableType;
    public final Type serializableType;
    public final Type serializedLambdaType;
    public final Type methodHandleType;
    public final Type methodHandleLookupType;
    public final Type methodTypeType;
    public final Type nativeHeaderType;
    public final Type throwableType;
    public final Type errorType;
    public final Type interruptedExceptionType;
    public final Type illegalArgumentExceptionType;
    public final Type exceptionType;
    public final Type runtimeExceptionType;
    public final Type classNotFoundExceptionType;
    public final Type noClassDefFoundErrorType;
    public final Type noSuchFieldErrorType;
    public final Type assertionErrorType;
    public final Type cloneNotSupportedExceptionType;
    public final Type annotationType;
    public final TypeSymbol enumSym;
    public final Type listType;
    public final Type collectionsType;
    public final Type comparableType;
    public final Type comparatorType;
    public final Type arraysType;
    public final Type iterableType;
    public final Type iteratorType;
    public final Type annotationTargetType;
    public final Type overrideType;
    public final Type retentionType;
    public final Type deprecatedType;
    public final Type suppressWarningsType;
    public final Type inheritedType;
    public final Type profileType;
    public final Type proprietaryType;
    public final Type systemType;
    public final Type autoCloseableType;
    public final Type trustMeType;
    public final Type lambdaMetafactory;
    public final Type repeatableType;
    public final Type documentedType;
    public final Type elementTypeType;
    public final Type functionalInterfaceType;
    public final VarSymbol lengthVar;
    public final OperatorSymbol nullcheck;
    public final MethodSymbol enumFinalFinalize;
    public final MethodSymbol autoCloseableClose;
    public final Type[] typeOfTag = new Type[TypeTag.getTypeTagCount()];
    public final Name[] boxedName = new Name[TypeTag.getTypeTagCount()];
    public final Set<Name> operatorNames = new HashSet<Name>();
    public final Map<Name, ClassSymbol> classes = new HashMap<Name, ClassSymbol>();
    public final Map<Name, PackageSymbol> packages = new HashMap<Name, PackageSymbol>();
    public final ClassSymbol predefClass;
    private final Names names;
    private final ClassReader reader;
    private final Target target;

    protected Symtab(Context context) throws CompletionFailure {
        context.put(symtabKey, this);
        names = Names.instance(context);
        target = Target.instance(context);

        unknownType = new UnknownType();

        rootPackage = new PackageSymbol(names.empty, null);
        final JavacMessages messages = JavacMessages.instance(context);
        unnamedPackage = new PackageSymbol(names.empty, rootPackage) {
            public String toString() {
                return messages.getLocalizedString("compiler.misc.unnamed.package");
            }
        };
        noSymbol = new TypeSymbol(Kinds.NIL, 0, names.empty, Type.noType, rootPackage) {
            public <R, P> R accept(ElementVisitor<R, P> v, P p) {
                return v.visitUnknown(this, p);
            }
        };

        errSymbol = new ClassSymbol(PUBLIC | STATIC | ACYCLIC, names.any, null, rootPackage);
        errType = new ErrorType(errSymbol, Type.noType);
        unknownSymbol = new ClassSymbol(PUBLIC | STATIC | ACYCLIC, names.fromString("<any?>"), null, rootPackage);
        unknownSymbol.members_field = new Scope.ErrorScope(unknownSymbol);
        unknownSymbol.type = unknownType;

        initType(byteType, "byte", "Byte");
        initType(shortType, "short", "Short");
        initType(charType, "char", "Character");
        initType(intType, "int", "Integer");
        initType(longType, "long", "Long");
        initType(floatType, "float", "Float");
        initType(doubleType, "double", "Double");
        initType(booleanType, "boolean", "Boolean");
        initType(voidType, "void", "Void");
        initType(botType, "<nulltype>");
        initType(errType, errSymbol);
        initType(unknownType, unknownSymbol);

        arrayClass = new ClassSymbol(PUBLIC | ACYCLIC, names.Array, noSymbol);

        boundClass = new ClassSymbol(PUBLIC | ACYCLIC, names.Bound, noSymbol);
        boundClass.members_field = new Scope.ErrorScope(boundClass);

        methodClass = new ClassSymbol(PUBLIC | ACYCLIC, names.Method, noSymbol);
        methodClass.members_field = new Scope.ErrorScope(boundClass);

        predefClass = new ClassSymbol(PUBLIC | ACYCLIC, names.empty, rootPackage);
        Scope scope = new Scope(predefClass);
        predefClass.members_field = scope;

        scope.enter(byteType.tsym);
        scope.enter(shortType.tsym);
        scope.enter(charType.tsym);
        scope.enter(intType.tsym);
        scope.enter(longType.tsym);
        scope.enter(floatType.tsym);
        scope.enter(doubleType.tsym);
        scope.enter(booleanType.tsym);
        scope.enter(errType.tsym);

        scope.enter(errSymbol);
        classes.put(predefClass.fullname, predefClass);
        reader = ClassReader.instance(context);
        reader.init(this);

        objectType = enterClass("java.lang.Object");
        classType = enterClass("java.lang.Class");
        stringType = enterClass("java.lang.String");
        stringBufferType = enterClass("java.lang.StringBuffer");
        stringBuilderType = enterClass("java.lang.StringBuilder");
        cloneableType = enterClass("java.lang.Cloneable");
        throwableType = enterClass("java.lang.Throwable");
        serializableType = enterClass("java.io.Serializable");
        serializedLambdaType = enterClass("java.lang.invoke.SerializedLambda");
        methodHandleType = enterClass("java.lang.invoke.MethodHandle");
        methodHandleLookupType = enterClass("java.lang.invoke.MethodHandles$Lookup");
        methodTypeType = enterClass("java.lang.invoke.MethodType");
        errorType = enterClass("java.lang.Error");
        illegalArgumentExceptionType = enterClass("java.lang.IllegalArgumentException");
        interruptedExceptionType = enterClass("java.lang.InterruptedException");
        exceptionType = enterClass("java.lang.Exception");
        runtimeExceptionType = enterClass("java.lang.RuntimeException");
        classNotFoundExceptionType = enterClass("java.lang.ClassNotFoundException");
        noClassDefFoundErrorType = enterClass("java.lang.NoClassDefFoundError");
        noSuchFieldErrorType = enterClass("java.lang.NoSuchFieldError");
        assertionErrorType = enterClass("java.lang.AssertionError");
        cloneNotSupportedExceptionType = enterClass("java.lang.CloneNotSupportedException");
        annotationType = enterClass("java.lang.annotation.Annotation");
        classLoaderType = enterClass("java.lang.ClassLoader");
        enumSym = reader.enterClass(names.java_lang_Enum);
        enumFinalFinalize =
                new MethodSymbol(PROTECTED | FINAL | HYPOTHETICAL,
                        names.finalize,
                        new MethodType(List.nil(), voidType,
                                List.nil(), methodClass),
                        enumSym);
        listType = enterClass("java.util.List");
        collectionsType = enterClass("java.util.Collections");
        comparableType = enterClass("java.lang.Comparable");
        comparatorType = enterClass("java.util.Comparator");
        arraysType = enterClass("java.util.Arrays");
        iterableType = target.hasIterable()
                ? enterClass("java.lang.Iterable")
                : enterClass("java.util.Collection");
        iteratorType = enterClass("java.util.Iterator");
        annotationTargetType = enterClass("java.lang.annotation.Target");
        overrideType = enterClass("java.lang.Override");
        retentionType = enterClass("java.lang.annotation.Retention");
        deprecatedType = enterClass("java.lang.Deprecated");
        suppressWarningsType = enterClass("java.lang.SuppressWarnings");
        inheritedType = enterClass("java.lang.annotation.Inherited");
        repeatableType = enterClass("java.lang.annotation.Repeatable");
        documentedType = enterClass("java.lang.annotation.Documented");
        elementTypeType = enterClass("java.lang.annotation.ElementType");
        systemType = enterClass("java.lang.System");
        autoCloseableType = enterClass("java.lang.AutoCloseable");
        autoCloseableClose = new MethodSymbol(PUBLIC,
                names.close,
                new MethodType(List.nil(), voidType,
                        List.of(exceptionType), methodClass),
                autoCloseableType.tsym);
        trustMeType = enterClass("java.lang.SafeVarargs");
        nativeHeaderType = enterClass("java.lang.annotation.Native");
        lambdaMetafactory = enterClass("java.lang.invoke.LambdaMetafactory");
        functionalInterfaceType = enterClass("java.lang.FunctionalInterface");
        synthesizeEmptyInterfaceIfMissing(autoCloseableType);
        synthesizeEmptyInterfaceIfMissing(cloneableType);
        synthesizeEmptyInterfaceIfMissing(serializableType);
        synthesizeEmptyInterfaceIfMissing(lambdaMetafactory);
        synthesizeEmptyInterfaceIfMissing(serializedLambdaType);
        synthesizeBoxTypeIfMissing(doubleType);
        synthesizeBoxTypeIfMissing(floatType);
        synthesizeBoxTypeIfMissing(voidType);


        proprietaryType = enterSyntheticAnnotation("sun.Proprietary+Annotation");


        profileType = enterSyntheticAnnotation("map.Profile+Annotation");
        MethodSymbol m = new MethodSymbol(PUBLIC | ABSTRACT, names.value, intType, profileType.tsym);
        profileType.tsym.members().enter(m);


        ClassType arrayClassType = (ClassType) arrayClass.type;
        arrayClassType.supertype_field = objectType;
        arrayClassType.interfaces_field = List.of(cloneableType, serializableType);
        arrayClass.members_field = new Scope(arrayClass);
        lengthVar = new VarSymbol(
                PUBLIC | FINAL,
                names.length,
                intType,
                arrayClass);
        arrayClass.members().enter(lengthVar);
        arrayCloneMethod = new MethodSymbol(
                PUBLIC,
                names.clone,
                new MethodType(List.nil(), objectType,
                        List.nil(), methodClass),
                arrayClass);
        arrayClass.members().enter(arrayCloneMethod);


        enterUnop("+++", doubleType, doubleType, nop);
        enterUnop("+++", floatType, floatType, nop);
        enterUnop("+++", longType, longType, nop);
        enterUnop("+++", intType, intType, nop);
        enterUnop("---", doubleType, doubleType, dneg);
        enterUnop("---", floatType, floatType, fneg);
        enterUnop("---", longType, longType, lneg);
        enterUnop("---", intType, intType, ineg);
        enterUnop("~", longType, longType, lxor);
        enterUnop("~", intType, intType, ixor);
        enterUnop("++", doubleType, doubleType, dadd);
        enterUnop("++", floatType, floatType, fadd);
        enterUnop("++", longType, longType, ladd);
        enterUnop("++", intType, intType, iadd);
        enterUnop("++", charType, charType, iadd);
        enterUnop("++", shortType, shortType, iadd);
        enterUnop("++", byteType, byteType, iadd);
        enterUnop("--", doubleType, doubleType, dsub);
        enterUnop("--", floatType, floatType, fsub);
        enterUnop("--", longType, longType, lsub);
        enterUnop("--", intType, intType, isub);
        enterUnop("--", charType, charType, isub);
        enterUnop("--", shortType, shortType, isub);
        enterUnop("--", byteType, byteType, isub);
        enterUnop("!", booleanType, booleanType, bool_not);
        nullcheck = enterUnop("<*nullchk*>", objectType, objectType, nullchk);

        enterBinop("+", stringType, objectType, stringType, string_add);
        enterBinop("+", objectType, stringType, stringType, string_add);
        enterBinop("+", stringType, stringType, stringType, string_add);
        enterBinop("+", stringType, intType, stringType, string_add);
        enterBinop("+", stringType, longType, stringType, string_add);
        enterBinop("+", stringType, floatType, stringType, string_add);
        enterBinop("+", stringType, doubleType, stringType, string_add);
        enterBinop("+", stringType, booleanType, stringType, string_add);
        enterBinop("+", stringType, botType, stringType, string_add);
        enterBinop("+", intType, stringType, stringType, string_add);
        enterBinop("+", longType, stringType, stringType, string_add);
        enterBinop("+", floatType, stringType, stringType, string_add);
        enterBinop("+", doubleType, stringType, stringType, string_add);
        enterBinop("+", booleanType, stringType, stringType, string_add);
        enterBinop("+", botType, stringType, stringType, string_add);

        enterBinop("+", botType, botType, botType, error);
        enterBinop("+", botType, intType, botType, error);
        enterBinop("+", botType, longType, botType, error);
        enterBinop("+", botType, floatType, botType, error);
        enterBinop("+", botType, doubleType, botType, error);
        enterBinop("+", botType, booleanType, botType, error);
        enterBinop("+", botType, objectType, botType, error);
        enterBinop("+", intType, botType, botType, error);
        enterBinop("+", longType, botType, botType, error);
        enterBinop("+", floatType, botType, botType, error);
        enterBinop("+", doubleType, botType, botType, error);
        enterBinop("+", booleanType, botType, botType, error);
        enterBinop("+", objectType, botType, botType, error);
        enterBinop("+", doubleType, doubleType, doubleType, dadd);
        enterBinop("+", floatType, floatType, floatType, fadd);
        enterBinop("+", longType, longType, longType, ladd);
        enterBinop("+", intType, intType, intType, iadd);
        enterBinop("-", doubleType, doubleType, doubleType, dsub);
        enterBinop("-", floatType, floatType, floatType, fsub);
        enterBinop("-", longType, longType, longType, lsub);
        enterBinop("-", intType, intType, intType, isub);
        enterBinop("*", doubleType, doubleType, doubleType, dmul);
        enterBinop("*", floatType, floatType, floatType, fmul);
        enterBinop("*", longType, longType, longType, lmul);
        enterBinop("*", intType, intType, intType, imul);
        enterBinop("/", doubleType, doubleType, doubleType, ddiv);
        enterBinop("/", floatType, floatType, floatType, fdiv);
        enterBinop("/", longType, longType, longType, ldiv);
        enterBinop("/", intType, intType, intType, idiv);
        enterBinop("%", doubleType, doubleType, doubleType, dmod);
        enterBinop("%", floatType, floatType, floatType, fmod);
        enterBinop("%", longType, longType, longType, lmod);
        enterBinop("%", intType, intType, intType, imod);
        enterBinop("&", booleanType, booleanType, booleanType, iand);
        enterBinop("&", longType, longType, longType, land);
        enterBinop("&", intType, intType, intType, iand);
        enterBinop("|", booleanType, booleanType, booleanType, ior);
        enterBinop("|", longType, longType, longType, lor);
        enterBinop("|", intType, intType, intType, ior);
        enterBinop("^", booleanType, booleanType, booleanType, ixor);
        enterBinop("^", longType, longType, longType, lxor);
        enterBinop("^", intType, intType, intType, ixor);
        enterBinop("<<", longType, longType, longType, lshll);
        enterBinop("<<", intType, longType, intType, ishll);
        enterBinop("<<", longType, intType, longType, lshl);
        enterBinop("<<", intType, intType, intType, ishl);
        enterBinop(">>", longType, longType, longType, lshrl);
        enterBinop(">>", intType, longType, intType, ishrl);
        enterBinop(">>", longType, intType, longType, lshr);
        enterBinop(">>", intType, intType, intType, ishr);
        enterBinop(">>>", longType, longType, longType, lushrl);
        enterBinop(">>>", intType, longType, intType, iushrl);
        enterBinop(">>>", longType, intType, longType, lushr);
        enterBinop(">>>", intType, intType, intType, iushr);
        enterBinop("<", doubleType, doubleType, booleanType, dcmpg, iflt);
        enterBinop("<", floatType, floatType, booleanType, fcmpg, iflt);
        enterBinop("<", longType, longType, booleanType, lcmp, iflt);
        enterBinop("<", intType, intType, booleanType, if_icmplt);
        enterBinop(">", doubleType, doubleType, booleanType, dcmpl, ifgt);
        enterBinop(">", floatType, floatType, booleanType, fcmpl, ifgt);
        enterBinop(">", longType, longType, booleanType, lcmp, ifgt);
        enterBinop(">", intType, intType, booleanType, if_icmpgt);
        enterBinop("<=", doubleType, doubleType, booleanType, dcmpg, ifle);
        enterBinop("<=", floatType, floatType, booleanType, fcmpg, ifle);
        enterBinop("<=", longType, longType, booleanType, lcmp, ifle);
        enterBinop("<=", intType, intType, booleanType, if_icmple);
        enterBinop(">=", doubleType, doubleType, booleanType, dcmpl, ifge);
        enterBinop(">=", floatType, floatType, booleanType, fcmpl, ifge);
        enterBinop(">=", longType, longType, booleanType, lcmp, ifge);
        enterBinop(">=", intType, intType, booleanType, if_icmpge);
        enterBinop("==", objectType, objectType, booleanType, if_acmpeq);
        enterBinop("==", booleanType, booleanType, booleanType, if_icmpeq);
        enterBinop("==", doubleType, doubleType, booleanType, dcmpl, ifeq);
        enterBinop("==", floatType, floatType, booleanType, fcmpl, ifeq);
        enterBinop("==", longType, longType, booleanType, lcmp, ifeq);
        enterBinop("==", intType, intType, booleanType, if_icmpeq);
        enterBinop("!=", objectType, objectType, booleanType, if_acmpne);
        enterBinop("!=", booleanType, booleanType, booleanType, if_icmpne);
        enterBinop("!=", doubleType, doubleType, booleanType, dcmpl, ifne);
        enterBinop("!=", floatType, floatType, booleanType, fcmpl, ifne);
        enterBinop("!=", longType, longType, booleanType, lcmp, ifne);
        enterBinop("!=", intType, intType, booleanType, if_icmpne);
        enterBinop("&&", booleanType, booleanType, booleanType, bool_and);
        enterBinop("||", booleanType, booleanType, booleanType, bool_or);
    }

    public static Symtab instance(Context context) {
        Symtab instance = context.get(symtabKey);
        if (instance == null)
            instance = new Symtab(context);
        return instance;
    }

    public void initType(Type type, ClassSymbol c) {
        type.tsym = c;
        typeOfTag[type.getTag().ordinal()] = type;
    }

    public void initType(Type type, String name) {
        initType(
                type,
                new ClassSymbol(
                        PUBLIC, names.fromString(name), type, rootPackage));
    }

    public void initType(Type type, String name, String bname) {
        initType(type, name);
        boxedName[type.getTag().ordinal()] = names.fromString("java.lang." + bname);
    }

    private VarSymbol enterConstant(String name, Type type) {
        VarSymbol c = new VarSymbol(
                PUBLIC | STATIC | FINAL,
                names.fromString(name),
                type,
                predefClass);
        c.setData(type.constValue());
        predefClass.members().enter(c);
        return c;
    }

    private void enterBinop(String name,
                            Type left, Type right, Type res,
                            int opcode) {
        predefClass.members().enter(
                new OperatorSymbol(
                        makeOperatorName(name),
                        new MethodType(List.of(left, right), res,
                                List.nil(), methodClass),
                        opcode,
                        predefClass));
    }

    private void enterBinop(String name,
                            Type left, Type right, Type res,
                            int opcode1, int opcode2) {
        enterBinop(
                name, left, right, res, (opcode1 << ByteCodes.preShift) | opcode2);
    }

    private OperatorSymbol enterUnop(String name,
                                     Type arg,
                                     Type res,
                                     int opcode) {
        OperatorSymbol sym =
                new OperatorSymbol(makeOperatorName(name),
                        new MethodType(List.of(arg),
                                res,
                                List.nil(),
                                methodClass),
                        opcode,
                        predefClass);
        predefClass.members().enter(sym);
        return sym;
    }

    private Name makeOperatorName(String name) {
        Name opName = names.fromString(name);
        operatorNames.add(opName);
        return opName;
    }

    private Type enterClass(String s) {
        return reader.enterClass(names.fromString(s)).type;
    }

    public void synthesizeEmptyInterfaceIfMissing(final Type type) {
        final Completer completer = type.tsym.completer;
        if (completer != null) {
            type.tsym.completer = new Completer() {
                public void complete(Symbol sym) throws CompletionFailure {
                    try {
                        completer.complete(sym);
                    } catch (CompletionFailure e) {
                        sym.flags_field |= (PUBLIC | INTERFACE);
                        ((ClassType) sym.type).supertype_field = objectType;
                    }
                }
            };
        }
    }

    public void synthesizeBoxTypeIfMissing(final Type type) {
        ClassSymbol sym = reader.enterClass(boxedName[type.getTag().ordinal()]);
        final Completer completer = sym.completer;
        if (completer != null) {
            sym.completer = new Completer() {
                public void complete(Symbol sym) throws CompletionFailure {
                    try {
                        completer.complete(sym);
                    } catch (CompletionFailure e) {
                        sym.flags_field |= PUBLIC;
                        ((ClassType) sym.type).supertype_field = objectType;
                        Name n = target.boxWithConstructors() ? names.init : names.valueOf;
                        MethodSymbol boxMethod =
                                new MethodSymbol(PUBLIC | STATIC,
                                        n,
                                        new MethodType(List.of(type), sym.type,
                                                List.nil(), methodClass),
                                        sym);
                        sym.members().enter(boxMethod);
                        MethodSymbol unboxMethod =
                                new MethodSymbol(PUBLIC,
                                        type.tsym.name.append(names.Value),
                                        new MethodType(List.nil(), type,
                                                List.nil(), methodClass),
                                        sym);
                        sym.members().enter(unboxMethod);
                    }
                }
            };
        }
    }

    private Type enterSyntheticAnnotation(String name) {
        ClassType type = (ClassType) enterClass(name);
        ClassSymbol sym = (ClassSymbol) type.tsym;
        sym.completer = null;
        sym.flags_field = PUBLIC | ACYCLIC | ANNOTATION | INTERFACE;
        sym.erasure_field = type;
        sym.members_field = new Scope(sym);
        type.typarams_field = List.nil();
        type.allparams_field = List.nil();
        type.supertype_field = annotationType;
        type.interfaces_field = List.nil();
        return type;
    }
}
