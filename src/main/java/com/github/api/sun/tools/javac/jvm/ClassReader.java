package com.github.api.sun.tools.javac.jvm;

import com.github.api.sun.tools.javac.code.*;
import com.github.api.sun.tools.javac.code.Lint.LintCategory;
import com.github.api.sun.tools.javac.code.Symbol.*;
import com.github.api.sun.tools.javac.code.Type.*;
import com.github.api.sun.tools.javac.comp.Annotate;
import com.github.api.sun.tools.javac.file.BaseFileObject;
import com.github.api.sun.tools.javac.util.*;
import com.github.api.sun.tools.javac.util.List;
import com.github.api.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

import javax.lang.model.SourceVersion;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.CharBuffer;
import java.util.*;

import static com.github.api.sun.tools.javac.code.Flags.*;
import static com.github.api.sun.tools.javac.code.Kinds.*;
import static com.github.api.sun.tools.javac.code.TypeTag.CLASS;
import static com.github.api.sun.tools.javac.code.TypeTag.TYPEVAR;
import static com.github.api.sun.tools.javac.jvm.ClassFile.*;
import static com.github.api.sun.tools.javac.jvm.ClassFile.Version.*;
import static com.github.api.sun.tools.javac.main.Option.VERBOSE;
import static javax.tools.StandardLocation.*;

import com.github.api.sun.tools.javac.code.Attribute.Visitor;

public class ClassReader {

    public static final int INITIAL_BUFFER_SIZE = 0x0fff0;
    protected static final Context.Key<ClassReader> classReaderKey =
            new Context.Key<ClassReader>();
    public final Profile profile;
    final Log log;
    final Names names;
    final Name completionFailureName;
    private final JavaFileManager fileManager;
    public boolean readAllOfClassFile = false;
    public boolean saveParameterNames;
    public boolean preferSource;
    public SourceCompleter sourceCompleter = null;
    protected Scope typevars;
    protected JavaFileObject currentClassFile = null;
    protected Symbol currentOwner = null;
    protected int bp;
    protected Set<AttributeKind> CLASS_ATTRIBUTE =
            EnumSet.of(AttributeKind.CLASS);
    protected Set<AttributeKind> MEMBER_ATTRIBUTE =
            EnumSet.of(AttributeKind.MEMBER);
    protected Set<AttributeKind> CLASS_OR_MEMBER_ATTRIBUTE =
            EnumSet.of(AttributeKind.CLASS, AttributeKind.MEMBER);
    protected Map<Name, AttributeReader> attributeReaders = new HashMap<Name, AttributeReader>();
    protected Location currentLoc;
    Annotate annotate;
    boolean verbose;
    boolean checkClassFile;
    boolean allowGenerics;
    boolean allowVarargs;
    boolean allowAnnotations;
    boolean allowSimplifiedVarargs;
    boolean lintClassfile;
    boolean allowDefaultMethods;
    Symtab syms;
    Types types;
    JCDiagnostic.Factory diagFactory;
    byte[] buf = new byte[INITIAL_BUFFER_SIZE];
    Object[] poolObj;
    int[] poolIdx;
    int majorVersion;
    int minorVersion;
    int[] parameterNameIndices;
    boolean haveParameterNameIndices;
    boolean sawMethodParameters;
    Set<Name> warnedAttrs = new HashSet<Name>();
    byte[] signature;
    int sigp;
    int siglimit;
    boolean sigEnterPhase = false;
    byte[] signatureBuffer = new byte[0];
    int sbp = 0;
    private boolean cacheCompletionFailure;
    private Map<Name, ClassSymbol> classes;
    private Map<Name, PackageSymbol> packages;
    private boolean readingClassAttr = false;
    private List<Type> missingTypeVariables = List.nil();
    private List<Type> foundTypeVariables = List.nil();
    private boolean filling = false;
    private CompletionFailure cachedCompletionFailure =
            new CompletionFailure(null, (JCDiagnostic) null);
    private boolean verbosePath = true;
    private final Completer thisCompleter = new Completer() {
        @Override
        public void complete(Symbol sym) throws CompletionFailure {
            ClassReader.this.complete(sym);
        }
    };

    {
        cachedCompletionFailure.setStackTrace(new StackTraceElement[0]);
    }

    protected ClassReader(Context context, boolean definitive) {
        if (definitive) context.put(classReaderKey, this);
        names = Names.instance(context);
        syms = Symtab.instance(context);
        types = Types.instance(context);
        fileManager = context.get(JavaFileManager.class);
        if (fileManager == null)
            throw new AssertionError("FileManager initialization error");
        diagFactory = JCDiagnostic.Factory.instance(context);
        init(syms, definitive);
        log = Log.instance(context);
        Options options = Options.instance(context);
        annotate = Annotate.instance(context);
        verbose = options.isSet(VERBOSE);
        checkClassFile = options.isSet("-checkclassfile");
        Source source = Source.instance(context);
        allowGenerics = source.allowGenerics();
        allowVarargs = source.allowVarargs();
        allowAnnotations = source.allowAnnotations();
        allowSimplifiedVarargs = source.allowSimplifiedVarargs();
        allowDefaultMethods = source.allowDefaultMethods();
        saveParameterNames = options.isSet("save-parameter-names");
        cacheCompletionFailure = options.isUnset("dev");
        preferSource = "source".equals(options.get("-Xprefer"));
        profile = Profile.instance(context);
        completionFailureName =
                options.isSet("failcomplete")
                        ? names.fromString(options.get("failcomplete"))
                        : null;
        typevars = new Scope(syms.noSymbol);
        lintClassfile = Lint.instance(context).isEnabled(LintCategory.CLASSFILE);
        initAttributeReaders();
    }

    public static ClassReader instance(Context context) {
        ClassReader instance = context.get(classReaderKey);
        if (instance == null)
            instance = new ClassReader(context, true);
        return instance;
    }

    private static boolean isAsciiDigit(char c) {
        return '0' <= c && c <= '9';
    }

    private static byte[] readInputStream(byte[] buf, InputStream s) throws IOException {
        try {
            buf = ensureCapacity(buf, s.available());
            int r = s.read(buf);
            int bp = 0;
            while (r != -1) {
                bp += r;
                buf = ensureCapacity(buf, bp);
                r = s.read(buf, bp, buf.length - bp);
            }
            return buf;
        } finally {
            try {
                s.close();
            } catch (IOException e) {

            }
        }
    }

    private static byte[] ensureCapacity(byte[] buf, int needed) {
        if (buf.length <= needed) {
            byte[] old = buf;
            buf = new byte[Integer.highestOneBit(needed) << 1];
            System.arraycopy(old, 0, buf, 0, old.length);
        }
        return buf;
    }

    public void init(Symtab syms) {
        init(syms, true);
    }

    private void init(Symtab syms, boolean definitive) {
        if (classes != null) return;
        if (definitive) {
            Assert.check(packages == null || packages == syms.packages);
            packages = syms.packages;
            Assert.check(classes == null || classes == syms.classes);
            classes = syms.classes;
        } else {
            packages = new HashMap<Name, PackageSymbol>();
            classes = new HashMap<Name, ClassSymbol>();
        }
        packages.put(names.empty, syms.rootPackage);
        syms.rootPackage.completer = thisCompleter;
        syms.unnamedPackage.completer = thisCompleter;
    }

    private void enterMember(ClassSymbol c, Symbol sym) {


        if ((sym.flags_field & (SYNTHETIC | BRIDGE)) != SYNTHETIC || sym.name.startsWith(names.lambda))
            c.members_field.enter(sym);
    }

    private JCDiagnostic createBadClassFileDiagnostic(JavaFileObject file, JCDiagnostic diag) {
        String key = (file.getKind() == JavaFileObject.Kind.SOURCE
                ? "bad.source.file.header" : "bad.class.file.header");
        return diagFactory.fragment(key, file, diag);
    }

    public BadClassFile badClassFile(String key, Object... args) {
        return new BadClassFile(
                currentOwner.enclClass(),
                currentClassFile,
                diagFactory.fragment(key, args));
    }

    char nextChar() {
        return (char) (((buf[bp++] & 0xFF) << 8) + (buf[bp++] & 0xFF));
    }

    int nextByte() {
        return buf[bp++] & 0xFF;
    }

    int nextInt() {
        return
                ((buf[bp++] & 0xFF) << 24) +
                        ((buf[bp++] & 0xFF) << 16) +
                        ((buf[bp++] & 0xFF) << 8) +
                        (buf[bp++] & 0xFF);
    }

    char getChar(int bp) {
        return
                (char) (((buf[bp] & 0xFF) << 8) + (buf[bp + 1] & 0xFF));
    }

    int getInt(int bp) {
        return
                ((buf[bp] & 0xFF) << 24) +
                        ((buf[bp + 1] & 0xFF) << 16) +
                        ((buf[bp + 2] & 0xFF) << 8) +
                        (buf[bp + 3] & 0xFF);
    }

    long getLong(int bp) {
        DataInputStream bufin =
                new DataInputStream(new ByteArrayInputStream(buf, bp, 8));
        try {
            return bufin.readLong();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    float getFloat(int bp) {
        DataInputStream bufin =
                new DataInputStream(new ByteArrayInputStream(buf, bp, 4));
        try {
            return bufin.readFloat();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    double getDouble(int bp) {
        DataInputStream bufin =
                new DataInputStream(new ByteArrayInputStream(buf, bp, 8));
        try {
            return bufin.readDouble();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    void indexPool() {
        poolIdx = new int[nextChar()];
        poolObj = new Object[poolIdx.length];
        int i = 1;
        while (i < poolIdx.length) {
            poolIdx[i++] = bp;
            byte tag = buf[bp++];
            switch (tag) {
                case CONSTANT_Utf8:
                case CONSTANT_Unicode: {
                    int len = nextChar();
                    bp = bp + len;
                    break;
                }
                case CONSTANT_Class:
                case CONSTANT_String:
                case CONSTANT_MethodType:
                    bp = bp + 2;
                    break;
                case CONSTANT_MethodHandle:
                    bp = bp + 3;
                    break;
                case CONSTANT_Fieldref:
                case CONSTANT_Methodref:
                case CONSTANT_InterfaceMethodref:
                case CONSTANT_NameandType:
                case CONSTANT_Integer:
                case CONSTANT_Float:
                case CONSTANT_InvokeDynamic:
                    bp = bp + 4;
                    break;
                case CONSTANT_Long:
                case CONSTANT_Double:
                    bp = bp + 8;
                    i++;
                    break;
                default:
                    throw badClassFile("bad.const.pool.tag.at",
                            Byte.toString(tag),
                            Integer.toString(bp - 1));
            }
        }
    }

    Object readPool(int i) {
        Object result = poolObj[i];
        if (result != null) return result;
        int index = poolIdx[i];
        if (index == 0) return null;
        byte tag = buf[index];
        switch (tag) {
            case CONSTANT_Utf8:
                poolObj[i] = names.fromUtf(buf, index + 3, getChar(index + 1));
                break;
            case CONSTANT_Unicode:
                throw badClassFile("unicode.str.not.supported");
            case CONSTANT_Class:
                poolObj[i] = readClassOrType(getChar(index + 1));
                break;
            case CONSTANT_String:

                poolObj[i] = readName(getChar(index + 1)).toString();
                break;
            case CONSTANT_Fieldref: {
                ClassSymbol owner = readClassSymbol(getChar(index + 1));
                NameAndType nt = (NameAndType) readPool(getChar(index + 3));
                poolObj[i] = new VarSymbol(0, nt.name, nt.uniqueType.type, owner);
                break;
            }
            case CONSTANT_Methodref:
            case CONSTANT_InterfaceMethodref: {
                ClassSymbol owner = readClassSymbol(getChar(index + 1));
                NameAndType nt = (NameAndType) readPool(getChar(index + 3));
                poolObj[i] = new MethodSymbol(0, nt.name, nt.uniqueType.type, owner);
                break;
            }
            case CONSTANT_NameandType:
                poolObj[i] = new NameAndType(
                        readName(getChar(index + 1)),
                        readType(getChar(index + 3)), types);
                break;
            case CONSTANT_Integer:
                poolObj[i] = getInt(index + 1);
                break;
            case CONSTANT_Float:
                poolObj[i] = new Float(getFloat(index + 1));
                break;
            case CONSTANT_Long:
                poolObj[i] = new Long(getLong(index + 1));
                break;
            case CONSTANT_Double:
                poolObj[i] = new Double(getDouble(index + 1));
                break;
            case CONSTANT_MethodHandle:
                skipBytes(4);
                break;
            case CONSTANT_MethodType:
                skipBytes(3);
                break;
            case CONSTANT_InvokeDynamic:
                skipBytes(5);
                break;
            default:
                throw badClassFile("bad.const.pool.tag", Byte.toString(tag));
        }
        return poolObj[i];
    }

    Type readType(int i) {
        int index = poolIdx[i];
        return sigToType(buf, index + 3, getChar(index + 1));
    }

    Object readClassOrType(int i) {
        int index = poolIdx[i];
        int len = getChar(index + 1);
        int start = index + 3;
        Assert.check(buf[start] == '[' || buf[start + len - 1] != ';');


        return (buf[start] == '[' || buf[start + len - 1] == ';')
                ? sigToType(buf, start, len)
                : enterClass(names.fromUtf(internalize(buf, start,
                len)));
    }

    List<Type> readTypeParams(int i) {
        int index = poolIdx[i];
        return sigToTypeParams(buf, index + 3, getChar(index + 1));
    }

    ClassSymbol readClassSymbol(int i) {
        return (ClassSymbol) (readPool(i));
    }

    Name readName(int i) {
        return (Name) (readPool(i));
    }

    Type sigToType(byte[] sig, int offset, int len) {
        signature = sig;
        sigp = offset;
        siglimit = offset + len;
        return sigToType();
    }

    Type sigToType() {
        switch ((char) signature[sigp]) {
            case 'T':
                sigp++;
                int start = sigp;
                while (signature[sigp] != ';') sigp++;
                sigp++;
                return sigEnterPhase
                        ? Type.noType
                        : findTypeVar(names.fromUtf(signature, start, sigp - 1 - start));
            case '+': {
                sigp++;
                Type t = sigToType();
                return new WildcardType(t, BoundKind.EXTENDS,
                        syms.boundClass);
            }
            case '*':
                sigp++;
                return new WildcardType(syms.objectType, BoundKind.UNBOUND,
                        syms.boundClass);
            case '-': {
                sigp++;
                Type t = sigToType();
                return new WildcardType(t, BoundKind.SUPER,
                        syms.boundClass);
            }
            case 'B':
                sigp++;
                return syms.byteType;
            case 'C':
                sigp++;
                return syms.charType;
            case 'D':
                sigp++;
                return syms.doubleType;
            case 'F':
                sigp++;
                return syms.floatType;
            case 'I':
                sigp++;
                return syms.intType;
            case 'J':
                sigp++;
                return syms.longType;
            case 'L': {

                Type t = classSigToType();
                if (sigp < siglimit && signature[sigp] == '.')
                    throw badClassFile("deprecated inner class signature syntax " +
                            "(please recompile from source)");

                return t;
            }
            case 'S':
                sigp++;
                return syms.shortType;
            case 'V':
                sigp++;
                return syms.voidType;
            case 'Z':
                sigp++;
                return syms.booleanType;
            case '[':
                sigp++;
                return new ArrayType(sigToType(), syms.arrayClass);
            case '(':
                sigp++;
                List<Type> argtypes = sigToTypes(')');
                Type restype = sigToType();
                List<Type> thrown = List.nil();
                while (signature[sigp] == '^') {
                    sigp++;
                    thrown = thrown.prepend(sigToType());
                }

                for (List<Type> l = thrown; l.nonEmpty(); l = l.tail) {
                    if (l.head.hasTag(TYPEVAR)) {
                        l.head.tsym.flags_field |= THROWS;
                    }
                }
                return new MethodType(argtypes,
                        restype,
                        thrown.reverse(),
                        syms.methodClass);
            case '<':
                typevars = typevars.dup(currentOwner);
                Type poly = new ForAll(sigToTypeParams(), sigToType());
                typevars = typevars.leave();
                return poly;
            default:
                throw badClassFile("bad.signature",
                        Convert.utf2string(signature, sigp, 10));
        }
    }

    Type classSigToType() {
        if (signature[sigp] != 'L')
            throw badClassFile("bad.class.signature",
                    Convert.utf2string(signature, sigp, 10));
        sigp++;
        Type outer = Type.noType;
        int startSbp = sbp;
        while (true) {
            final byte c = signature[sigp++];
            switch (c) {
                case ';': {
                    ClassSymbol t = enterClass(names.fromUtf(signatureBuffer,
                            startSbp,
                            sbp - startSbp));
                    try {
                        return (outer == Type.noType) ?
                                t.erasure(types) :
                                new ClassType(outer, List.nil(), t);
                    } finally {
                        sbp = startSbp;
                    }
                }
                case '<':
                    ClassSymbol t = enterClass(names.fromUtf(signatureBuffer,
                            startSbp,
                            sbp - startSbp));
                    outer = new ClassType(outer, sigToTypes('>'), t) {
                        boolean completed = false;

                        @Override
                        public Type getEnclosingType() {
                            if (!completed) {
                                completed = true;
                                tsym.complete();
                                Type enclosingType = tsym.type.getEnclosingType();
                                if (enclosingType != Type.noType) {
                                    List<Type> typeArgs =
                                            super.getEnclosingType().allparams();
                                    List<Type> typeParams =
                                            enclosingType.allparams();
                                    if (typeParams.length() != typeArgs.length()) {

                                        super.setEnclosingType(types.erasure(enclosingType));
                                    } else {
                                        super.setEnclosingType(types.subst(enclosingType,
                                                typeParams,
                                                typeArgs));
                                    }
                                } else {
                                    super.setEnclosingType(Type.noType);
                                }
                            }
                            return super.getEnclosingType();
                        }

                        @Override
                        public void setEnclosingType(Type outer) {
                            throw new UnsupportedOperationException();
                        }
                    };
                    switch (signature[sigp++]) {
                        case ';':
                            if (sigp < signature.length && signature[sigp] == '.') {


                                sigp += (sbp - startSbp) +
                                        3;
                                signatureBuffer[sbp++] = (byte) '$';
                                break;
                            } else {
                                sbp = startSbp;
                                return outer;
                            }
                        case '.':
                            signatureBuffer[sbp++] = (byte) '$';
                            break;
                        default:
                            throw new AssertionError(signature[sigp - 1]);
                    }
                    continue;
                case '.':

                    if (outer != Type.noType) {
                        t = enterClass(names.fromUtf(signatureBuffer,
                                startSbp,
                                sbp - startSbp));
                        outer = new ClassType(outer, List.nil(), t);
                    }
                    signatureBuffer[sbp++] = (byte) '$';
                    continue;
                case '/':
                    signatureBuffer[sbp++] = (byte) '.';
                    continue;
                default:
                    signatureBuffer[sbp++] = c;
                    continue;
            }
        }
    }

    List<Type> sigToTypes(char terminator) {
        List<Type> head = List.of(null);
        List<Type> tail = head;
        while (signature[sigp] != terminator)
            tail = tail.setTail(List.of(sigToType()));
        sigp++;
        return head.tail;
    }

    List<Type> sigToTypeParams(byte[] sig, int offset, int len) {
        signature = sig;
        sigp = offset;
        siglimit = offset + len;
        return sigToTypeParams();
    }

    List<Type> sigToTypeParams() {
        List<Type> tvars = List.nil();
        if (signature[sigp] == '<') {
            sigp++;
            int start = sigp;
            sigEnterPhase = true;
            while (signature[sigp] != '>')
                tvars = tvars.prepend(sigToTypeParam());
            sigEnterPhase = false;
            sigp = start;
            while (signature[sigp] != '>')
                sigToTypeParam();
            sigp++;
        }
        return tvars.reverse();
    }

    Type sigToTypeParam() {
        int start = sigp;
        while (signature[sigp] != ':') sigp++;
        Name name = names.fromUtf(signature, start, sigp - start);
        TypeVar tvar;
        if (sigEnterPhase) {
            tvar = new TypeVar(name, currentOwner, syms.botType);
            typevars.enter(tvar.tsym);
        } else {
            tvar = (TypeVar) findTypeVar(name);
        }
        List<Type> bounds = List.nil();
        boolean allInterfaces = false;
        if (signature[sigp] == ':' && signature[sigp + 1] == ':') {
            sigp++;
            allInterfaces = true;
        }
        while (signature[sigp] == ':') {
            sigp++;
            bounds = bounds.prepend(sigToType());
        }
        if (!sigEnterPhase) {
            types.setBounds(tvar, bounds.reverse(), allInterfaces);
        }
        return tvar;
    }

    Type findTypeVar(Name name) {
        Scope.Entry e = typevars.lookup(name);
        if (e.scope != null) {
            return e.sym.type;
        } else {
            if (readingClassAttr) {


                TypeVar t = new TypeVar(name, currentOwner, syms.botType);
                missingTypeVariables = missingTypeVariables.prepend(t);

                return t;
            }
            throw badClassFile("undecl.type.var", name);
        }
    }

    private void initAttributeReaders() {
        AttributeReader[] readers = {

                new AttributeReader(names.Code, V45_3, MEMBER_ATTRIBUTE) {
                    protected void read(Symbol sym, int attrLen) {
                        if (readAllOfClassFile || saveParameterNames)
                            ((MethodSymbol) sym).code = readCode(sym);
                        else
                            bp = bp + attrLen;
                    }
                },
                new AttributeReader(names.ConstantValue, V45_3, MEMBER_ATTRIBUTE) {
                    protected void read(Symbol sym, int attrLen) {
                        Object v = readPool(nextChar());

                        if ((sym.flags() & FINAL) != 0)
                            ((VarSymbol) sym).setData(v);
                    }
                },
                new AttributeReader(names.Deprecated, V45_3, CLASS_OR_MEMBER_ATTRIBUTE) {
                    protected void read(Symbol sym, int attrLen) {
                        sym.flags_field |= DEPRECATED;
                    }
                },
                new AttributeReader(names.Exceptions, V45_3, CLASS_OR_MEMBER_ATTRIBUTE) {
                    protected void read(Symbol sym, int attrLen) {
                        int nexceptions = nextChar();
                        List<Type> thrown = List.nil();
                        for (int j = 0; j < nexceptions; j++)
                            thrown = thrown.prepend(readClassSymbol(nextChar()).type);
                        if (sym.type.getThrownTypes().isEmpty())
                            sym.type.asMethodType().thrown = thrown.reverse();
                    }
                },
                new AttributeReader(names.InnerClasses, V45_3, CLASS_ATTRIBUTE) {
                    protected void read(Symbol sym, int attrLen) {
                        ClassSymbol c = (ClassSymbol) sym;
                        readInnerClasses(c);
                    }
                },
                new AttributeReader(names.LocalVariableTable, V45_3, CLASS_OR_MEMBER_ATTRIBUTE) {
                    protected void read(Symbol sym, int attrLen) {
                        int newbp = bp + attrLen;
                        if (saveParameterNames && !sawMethodParameters) {


                            int numEntries = nextChar();
                            for (int i = 0; i < numEntries; i++) {
                                int start_pc = nextChar();
                                int length = nextChar();
                                int nameIndex = nextChar();
                                int sigIndex = nextChar();
                                int register = nextChar();
                                if (start_pc == 0) {

                                    if (register >= parameterNameIndices.length) {
                                        int newSize = Math.max(register, parameterNameIndices.length + 8);
                                        parameterNameIndices =
                                                Arrays.copyOf(parameterNameIndices, newSize);
                                    }
                                    parameterNameIndices[register] = nameIndex;
                                    haveParameterNameIndices = true;
                                }
                            }
                        }
                        bp = newbp;
                    }
                },
                new AttributeReader(names.MethodParameters, V52, MEMBER_ATTRIBUTE) {
                    protected void read(Symbol sym, int attrlen) {
                        int newbp = bp + attrlen;
                        if (saveParameterNames) {
                            sawMethodParameters = true;
                            int numEntries = nextByte();
                            parameterNameIndices = new int[numEntries];
                            haveParameterNameIndices = true;
                            for (int i = 0; i < numEntries; i++) {
                                int nameIndex = nextChar();
                                int flags = nextChar();
                                parameterNameIndices[i] = nameIndex;
                            }
                        }
                        bp = newbp;
                    }
                },
                new AttributeReader(names.SourceFile, V45_3, CLASS_ATTRIBUTE) {
                    protected void read(Symbol sym, int attrLen) {
                        ClassSymbol c = (ClassSymbol) sym;
                        Name n = readName(nextChar());
                        c.sourcefile = new SourceFileObject(n, c.flatname);


                        String sn = n.toString();
                        if (c.owner.kind == Kinds.PCK &&
                                sn.endsWith(".java") &&
                                !sn.equals(c.name.toString() + ".java")) {
                            c.flags_field |= AUXILIARY;
                        }
                    }
                },
                new AttributeReader(names.Synthetic, V45_3, CLASS_OR_MEMBER_ATTRIBUTE) {
                    protected void read(Symbol sym, int attrLen) {

                        if (allowGenerics || (sym.flags_field & BRIDGE) == 0)
                            sym.flags_field |= SYNTHETIC;
                    }
                },

                new AttributeReader(names.EnclosingMethod, V49, CLASS_ATTRIBUTE) {
                    protected void read(Symbol sym, int attrLen) {
                        int newbp = bp + attrLen;
                        readEnclosingMethodAttr(sym);
                        bp = newbp;
                    }
                },
                new AttributeReader(names.Signature, V49, CLASS_OR_MEMBER_ATTRIBUTE) {
                    @Override
                    protected boolean accepts(AttributeKind kind) {
                        return super.accepts(kind) && allowGenerics;
                    }

                    protected void read(Symbol sym, int attrLen) {
                        if (sym.kind == TYP) {
                            ClassSymbol c = (ClassSymbol) sym;
                            readingClassAttr = true;
                            try {
                                ClassType ct1 = (ClassType) c.type;
                                Assert.check(c == currentOwner);
                                ct1.typarams_field = readTypeParams(nextChar());
                                ct1.supertype_field = sigToType();
                                ListBuffer<Type> is = new ListBuffer<Type>();
                                while (sigp != siglimit) is.append(sigToType());
                                ct1.interfaces_field = is.toList();
                            } finally {
                                readingClassAttr = false;
                            }
                        } else {
                            List<Type> thrown = sym.type.getThrownTypes();
                            sym.type = readType(nextChar());

                            if (sym.kind == MTH && sym.type.getThrownTypes().isEmpty())
                                sym.type.asMethodType().thrown = thrown;
                        }
                    }
                },

                new AttributeReader(names.AnnotationDefault, V49, CLASS_OR_MEMBER_ATTRIBUTE) {
                    protected void read(Symbol sym, int attrLen) {
                        attachAnnotationDefault(sym);
                    }
                },
                new AttributeReader(names.RuntimeInvisibleAnnotations, V49, CLASS_OR_MEMBER_ATTRIBUTE) {
                    protected void read(Symbol sym, int attrLen) {
                        attachAnnotations(sym);
                    }
                },
                new AttributeReader(names.RuntimeInvisibleParameterAnnotations, V49, CLASS_OR_MEMBER_ATTRIBUTE) {
                    protected void read(Symbol sym, int attrLen) {
                        attachParameterAnnotations(sym);
                    }
                },
                new AttributeReader(names.RuntimeVisibleAnnotations, V49, CLASS_OR_MEMBER_ATTRIBUTE) {
                    protected void read(Symbol sym, int attrLen) {
                        attachAnnotations(sym);
                    }
                },
                new AttributeReader(names.RuntimeVisibleParameterAnnotations, V49, CLASS_OR_MEMBER_ATTRIBUTE) {
                    protected void read(Symbol sym, int attrLen) {
                        attachParameterAnnotations(sym);
                    }
                },

                new AttributeReader(names.Annotation, V49, CLASS_OR_MEMBER_ATTRIBUTE) {
                    protected void read(Symbol sym, int attrLen) {
                        if (allowAnnotations)
                            sym.flags_field |= ANNOTATION;
                    }
                },
                new AttributeReader(names.Bridge, V49, MEMBER_ATTRIBUTE) {
                    protected void read(Symbol sym, int attrLen) {
                        sym.flags_field |= BRIDGE;
                        if (!allowGenerics)
                            sym.flags_field &= ~SYNTHETIC;
                    }
                },
                new AttributeReader(names.Enum, V49, CLASS_OR_MEMBER_ATTRIBUTE) {
                    protected void read(Symbol sym, int attrLen) {
                        sym.flags_field |= ENUM;
                    }
                },
                new AttributeReader(names.Varargs, V49, CLASS_OR_MEMBER_ATTRIBUTE) {
                    protected void read(Symbol sym, int attrLen) {
                        if (allowVarargs)
                            sym.flags_field |= VARARGS;
                    }
                },
                new AttributeReader(names.RuntimeVisibleTypeAnnotations, V52, CLASS_OR_MEMBER_ATTRIBUTE) {
                    protected void read(Symbol sym, int attrLen) {
                        attachTypeAnnotations(sym);
                    }
                },
                new AttributeReader(names.RuntimeInvisibleTypeAnnotations, V52, CLASS_OR_MEMBER_ATTRIBUTE) {
                    protected void read(Symbol sym, int attrLen) {
                        attachTypeAnnotations(sym);
                    }
                },


        };
        for (AttributeReader r : readers)
            attributeReaders.put(r.name, r);
    }

    void unrecognized(Name attrName) {
        if (checkClassFile)
            printCCF("ccf.unrecognized.attribute", attrName);
    }

    protected void readEnclosingMethodAttr(Symbol sym) {


        sym.owner.members().remove(sym);
        ClassSymbol self = (ClassSymbol) sym;
        ClassSymbol c = readClassSymbol(nextChar());
        NameAndType nt = (NameAndType) readPool(nextChar());
        if (c.members_field == null)
            throw badClassFile("bad.enclosing.class", self, c);
        MethodSymbol m = findMethod(nt, c.members_field, self.flags());
        if (nt != null && m == null)
            throw badClassFile("bad.enclosing.method", self);
        self.name = simpleBinaryName(self.flatname, c.flatname);
        self.owner = m != null ? m : c;
        if (self.name.isEmpty())
            self.fullname = names.empty;
        else
            self.fullname = ClassSymbol.formFullName(self.name, self.owner);
        if (m != null) {
            ((ClassType) sym.type).setEnclosingType(m.type);
        } else if ((self.flags_field & STATIC) == 0) {
            ((ClassType) sym.type).setEnclosingType(c.type);
        } else {
            ((ClassType) sym.type).setEnclosingType(Type.noType);
        }
        enterTypevars(self);
        if (!missingTypeVariables.isEmpty()) {
            ListBuffer<Type> typeVars = new ListBuffer<Type>();
            for (Type typevar : missingTypeVariables) {
                typeVars.append(findTypeVar(typevar.tsym.name));
            }
            foundTypeVariables = typeVars.toList();
        } else {
            foundTypeVariables = List.nil();
        }
    }

    private Name simpleBinaryName(Name self, Name enclosing) {
        String simpleBinaryName = self.toString().substring(enclosing.toString().length());
        if (simpleBinaryName.length() < 1 || simpleBinaryName.charAt(0) != '$')
            throw badClassFile("bad.enclosing.method", self);
        int index = 1;
        while (index < simpleBinaryName.length() &&
                isAsciiDigit(simpleBinaryName.charAt(index)))
            index++;
        return names.fromString(simpleBinaryName.substring(index));
    }

    private MethodSymbol findMethod(NameAndType nt, Scope scope, long flags) {
        if (nt == null)
            return null;
        MethodType type = nt.uniqueType.type.asMethodType();
        for (Scope.Entry e = scope.lookup(nt.name); e.scope != null; e = e.next())
            if (e.sym.kind == MTH && isSameBinaryType(e.sym.type.asMethodType(), type))
                return (MethodSymbol) e.sym;
        if (nt.name != names.init)

            return null;
        if ((flags & INTERFACE) != 0)

            return null;
        if (nt.uniqueType.type.getParameterTypes().isEmpty())

            return null;


        nt.setType(new MethodType(nt.uniqueType.type.getParameterTypes().tail,
                nt.uniqueType.type.getReturnType(),
                nt.uniqueType.type.getThrownTypes(),
                syms.methodClass));

        return findMethod(nt, scope, flags);
    }

    private boolean isSameBinaryType(MethodType mt1, MethodType mt2) {
        List<Type> types1 = types.erasure(mt1.getParameterTypes())
                .prepend(types.erasure(mt1.getReturnType()));
        List<Type> types2 = mt2.getParameterTypes().prepend(mt2.getReturnType());
        while (!types1.isEmpty() && !types2.isEmpty()) {
            if (types1.head.tsym != types2.head.tsym)
                return false;
            types1 = types1.tail;
            types2 = types2.tail;
        }
        return types1.isEmpty() && types2.isEmpty();
    }

    void readMemberAttrs(Symbol sym) {
        readAttrs(sym, AttributeKind.MEMBER);
    }

    void readAttrs(Symbol sym, AttributeKind kind) {
        char ac = nextChar();
        for (int i = 0; i < ac; i++) {
            Name attrName = readName(nextChar());
            int attrLen = nextInt();
            AttributeReader r = attributeReaders.get(attrName);
            if (r != null && r.accepts(kind))
                r.read(sym, attrLen);
            else {
                unrecognized(attrName);
                bp = bp + attrLen;
            }
        }
    }

    void readClassAttrs(ClassSymbol c) {
        readAttrs(c, AttributeKind.CLASS);
    }

    Code readCode(Symbol owner) {
        nextChar();
        nextChar();
        final int code_length = nextInt();
        bp += code_length;
        final char exception_table_length = nextChar();
        bp += exception_table_length * 8;
        readMemberAttrs(owner);
        return null;
    }

    void attachAnnotations(final Symbol sym) {
        int numAttributes = nextChar();
        if (numAttributes != 0) {
            ListBuffer<CompoundAnnotationProxy> proxies =
                    new ListBuffer<CompoundAnnotationProxy>();
            for (int i = 0; i < numAttributes; i++) {
                CompoundAnnotationProxy proxy = readCompoundAnnotation();
                if (proxy.type.tsym == syms.proprietaryType.tsym)
                    sym.flags_field |= PROPRIETARY;
                else if (proxy.type.tsym == syms.profileType.tsym) {
                    if (profile != Profile.DEFAULT) {
                        for (Pair<Name, Attribute> v : proxy.values) {
                            if (v.fst == names.value && v.snd instanceof Attribute.Constant) {
                                Attribute.Constant c = (Attribute.Constant) v.snd;
                                if (c.type == syms.intType && ((Integer) c.value) > profile.value) {
                                    sym.flags_field |= NOT_IN_PROFILE;
                                }
                            }
                        }
                    }
                } else
                    proxies.append(proxy);
            }
            annotate.normal(new AnnotationCompleter(sym, proxies.toList()));
        }
    }

    void attachParameterAnnotations(final Symbol method) {
        final MethodSymbol meth = (MethodSymbol) method;
        int numParameters = buf[bp++] & 0xFF;
        List<VarSymbol> parameters = meth.params();
        int pnum = 0;
        while (parameters.tail != null) {
            attachAnnotations(parameters.head);
            parameters = parameters.tail;
            pnum++;
        }
        if (pnum != numParameters) {
            throw badClassFile("bad.runtime.invisible.param.annotations", meth);
        }
    }

    void attachTypeAnnotations(final Symbol sym) {
        int numAttributes = nextChar();
        if (numAttributes != 0) {
            ListBuffer<TypeAnnotationProxy> proxies = new ListBuffer<>();
            for (int i = 0; i < numAttributes; i++)
                proxies.append(readTypeAnnotation());
            annotate.normal(new TypeAnnotationCompleter(sym, proxies.toList()));
        }
    }

    void attachAnnotationDefault(final Symbol sym) {
        final MethodSymbol meth = (MethodSymbol) sym;
        final Attribute value = readAttributeValue();


        meth.defaultValue = value;
        annotate.normal(new AnnotationDefaultCompleter(meth, value));
    }

    Type readTypeOrClassSymbol(int i) {

        if (buf[poolIdx[i]] == CONSTANT_Class)
            return readClassSymbol(i).type;
        return readType(i);
    }

    Type readEnumType(int i) {

        int index = poolIdx[i];
        int length = getChar(index + 1);
        if (buf[index + length + 2] != ';')
            return enterClass(readName(i)).type;
        return readType(i);
    }

    CompoundAnnotationProxy readCompoundAnnotation() {
        Type t = readTypeOrClassSymbol(nextChar());
        int numFields = nextChar();
        ListBuffer<Pair<Name, Attribute>> pairs =
                new ListBuffer<Pair<Name, Attribute>>();
        for (int i = 0; i < numFields; i++) {
            Name name = readName(nextChar());
            Attribute value = readAttributeValue();
            pairs.append(new Pair<Name, Attribute>(name, value));
        }
        return new CompoundAnnotationProxy(t, pairs.toList());
    }

    TypeAnnotationProxy readTypeAnnotation() {
        TypeAnnotationPosition position = readPosition();
        CompoundAnnotationProxy proxy = readCompoundAnnotation();
        return new TypeAnnotationProxy(proxy, position);
    }

    TypeAnnotationPosition readPosition() {
        int tag = nextByte();
        if (!TargetType.isValidTargetTypeValue(tag))
            throw this.badClassFile("bad.type.annotation.value", String.format("0x%02X", tag));
        TypeAnnotationPosition position = new TypeAnnotationPosition();
        TargetType type = TargetType.fromTargetTypeValue(tag);
        position.type = type;
        switch (type) {

            case INSTANCEOF:

            case NEW:

            case CONSTRUCTOR_REFERENCE:
            case METHOD_REFERENCE:
                position.offset = nextChar();
                break;

            case LOCAL_VARIABLE:

            case RESOURCE_VARIABLE:
                int table_length = nextChar();
                position.lvarOffset = new int[table_length];
                position.lvarLength = new int[table_length];
                position.lvarIndex = new int[table_length];
                for (int i = 0; i < table_length; ++i) {
                    position.lvarOffset[i] = nextChar();
                    position.lvarLength[i] = nextChar();
                    position.lvarIndex[i] = nextChar();
                }
                break;

            case EXCEPTION_PARAMETER:
                position.exception_index = nextChar();
                break;

            case METHOD_RECEIVER:

                break;

            case CLASS_TYPE_PARAMETER:
            case METHOD_TYPE_PARAMETER:
                position.parameter_index = nextByte();
                break;

            case CLASS_TYPE_PARAMETER_BOUND:
            case METHOD_TYPE_PARAMETER_BOUND:
                position.parameter_index = nextByte();
                position.bound_index = nextByte();
                break;

            case CLASS_EXTENDS:
                position.type_index = nextChar();
                break;

            case THROWS:
                position.type_index = nextChar();
                break;

            case METHOD_FORMAL_PARAMETER:
                position.parameter_index = nextByte();
                break;

            case CAST:

            case CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT:
            case METHOD_INVOCATION_TYPE_ARGUMENT:
            case CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT:
            case METHOD_REFERENCE_TYPE_ARGUMENT:
                position.offset = nextChar();
                position.type_index = nextByte();
                break;

            case METHOD_RETURN:
            case FIELD:
                break;
            case UNKNOWN:
                throw new AssertionError("jvm.ClassReader: UNKNOWN target type should never occur!");
            default:
                throw new AssertionError("jvm.ClassReader: Unknown target type for position: " + position);
        }
        {
            int len = nextByte();
            ListBuffer<Integer> loc = new ListBuffer<>();
            for (int i = 0; i < len * TypeAnnotationPosition.TypePathEntry.bytesPerEntry; ++i)
                loc = loc.append(nextByte());
            position.location = TypeAnnotationPosition.getTypePathFromBinary(loc.toList());
        }
        return position;
    }

    Attribute readAttributeValue() {
        char c = (char) buf[bp++];
        switch (c) {
            case 'B':
                return new Attribute.Constant(syms.byteType, readPool(nextChar()));
            case 'C':
                return new Attribute.Constant(syms.charType, readPool(nextChar()));
            case 'D':
                return new Attribute.Constant(syms.doubleType, readPool(nextChar()));
            case 'F':
                return new Attribute.Constant(syms.floatType, readPool(nextChar()));
            case 'I':
                return new Attribute.Constant(syms.intType, readPool(nextChar()));
            case 'J':
                return new Attribute.Constant(syms.longType, readPool(nextChar()));
            case 'S':
                return new Attribute.Constant(syms.shortType, readPool(nextChar()));
            case 'Z':
                return new Attribute.Constant(syms.booleanType, readPool(nextChar()));
            case 's':
                return new Attribute.Constant(syms.stringType, readPool(nextChar()).toString());
            case 'e':
                return new EnumAttributeProxy(readEnumType(nextChar()), readName(nextChar()));
            case 'c':
                return new Attribute.Class(types, readTypeOrClassSymbol(nextChar()));
            case '[': {
                int n = nextChar();
                ListBuffer<Attribute> l = new ListBuffer<Attribute>();
                for (int i = 0; i < n; i++)
                    l.append(readAttributeValue());
                return new ArrayAttributeProxy(l.toList());
            }
            case '@':
                return readCompoundAnnotation();
            default:
                throw new AssertionError("unknown annotation tag '" + c + "'");
        }
    }

    VarSymbol readField() {
        long flags = adjustFieldFlags(nextChar());
        Name name = readName(nextChar());
        Type type = readType(nextChar());
        VarSymbol v = new VarSymbol(flags, name, type, currentOwner);
        readMemberAttrs(v);
        return v;
    }

    MethodSymbol readMethod() {
        long flags = adjustMethodFlags(nextChar());
        Name name = readName(nextChar());
        Type type = readType(nextChar());
        if (currentOwner.isInterface() &&
                (flags & ABSTRACT) == 0 && !name.equals(names.clinit)) {
            if (majorVersion > Target.JDK1_8.majorVersion ||
                    (majorVersion == Target.JDK1_8.majorVersion && minorVersion >= Target.JDK1_8.minorVersion)) {
                if ((flags & STATIC) == 0) {
                    currentOwner.flags_field |= DEFAULT;
                    flags |= DEFAULT | ABSTRACT;
                }
            } else {

                throw badClassFile((flags & STATIC) == 0 ? "invalid.default.interface" : "invalid.static.interface",
                        Integer.toString(majorVersion),
                        Integer.toString(minorVersion));
            }
        }
        if (name == names.init && currentOwner.hasOuterInstance()) {


            if (!currentOwner.name.isEmpty())
                type = new MethodType(adjustMethodParams(flags, type.getParameterTypes()),
                        type.getReturnType(),
                        type.getThrownTypes(),
                        syms.methodClass);
        }
        MethodSymbol m = new MethodSymbol(flags, name, type, currentOwner);
        if (types.isSignaturePolymorphic(m)) {
            m.flags_field |= SIGNATURE_POLYMORPHIC;
        }
        if (saveParameterNames)
            initParameterNames(m);
        Symbol prevOwner = currentOwner;
        currentOwner = m;
        try {
            readMemberAttrs(m);
        } finally {
            currentOwner = prevOwner;
        }
        if (saveParameterNames)
            setParameterNames(m, type);
        return m;
    }

    private List<Type> adjustMethodParams(long flags, List<Type> args) {
        boolean isVarargs = (flags & VARARGS) != 0;
        if (isVarargs) {
            Type varargsElem = args.last();
            ListBuffer<Type> adjustedArgs = new ListBuffer<>();
            for (Type t : args) {
                adjustedArgs.append(t != varargsElem ?
                        t :
                        ((ArrayType) t).makeVarargs());
            }
            args = adjustedArgs.toList();
        }
        return args.tail;
    }

    void initParameterNames(MethodSymbol sym) {

        final int excessSlots = 4;
        int expectedParameterSlots =
                Code.width(sym.type.getParameterTypes()) + excessSlots;
        if (parameterNameIndices == null
                || parameterNameIndices.length < expectedParameterSlots) {
            parameterNameIndices = new int[expectedParameterSlots];
        } else
            Arrays.fill(parameterNameIndices, 0);
        haveParameterNameIndices = false;
        sawMethodParameters = false;
    }

    void setParameterNames(MethodSymbol sym, Type jvmType) {

        if (!haveParameterNameIndices)
            return;


        int firstParam = 0;
        if (!sawMethodParameters) {
            firstParam = ((sym.flags() & STATIC) == 0) ? 1 : 0;


            if (sym.name == names.init && currentOwner.hasOuterInstance()) {


                if (!currentOwner.name.isEmpty())
                    firstParam += 1;
            }
            if (sym.type != jvmType) {


                int skip = Code.width(jvmType.getParameterTypes())
                        - Code.width(sym.type.getParameterTypes());
                firstParam += skip;
            }
        }
        List<Name> paramNames = List.nil();
        int index = firstParam;
        for (Type t : sym.type.getParameterTypes()) {
            int nameIdx = (index < parameterNameIndices.length
                    ? parameterNameIndices[index] : 0);
            Name name = nameIdx == 0 ? names.empty : readName(nameIdx);
            paramNames = paramNames.prepend(name);
            index += Code.width(t);
        }
        sym.savedParameterNames = paramNames.reverse();
    }

    void skipBytes(int n) {
        bp = bp + n;
    }

    void skipMember() {
        bp = bp + 6;
        char ac = nextChar();
        for (int i = 0; i < ac; i++) {
            bp = bp + 2;
            int attrLen = nextInt();
            bp = bp + attrLen;
        }
    }

    protected void enterTypevars(Type t) {
        if (t.getEnclosingType() != null && t.getEnclosingType().hasTag(CLASS))
            enterTypevars(t.getEnclosingType());
        for (List<Type> xs = t.getTypeArguments(); xs.nonEmpty(); xs = xs.tail)
            typevars.enter(xs.head.tsym);
    }

    protected void enterTypevars(Symbol sym) {
        if (sym.owner.kind == MTH) {
            enterTypevars(sym.owner);
            enterTypevars(sym.owner.owner);
        }
        enterTypevars(sym.type);
    }

    void readClass(ClassSymbol c) {
        ClassType ct = (ClassType) c.type;

        c.members_field = new Scope(c);

        typevars = typevars.dup(currentOwner);
        if (ct.getEnclosingType().hasTag(CLASS))
            enterTypevars(ct.getEnclosingType());

        long flags = adjustClassFlags(nextChar());
        if (c.owner.kind == PCK) c.flags_field = flags;

        ClassSymbol self = readClassSymbol(nextChar());
        if (c != self)
            throw badClassFile("class.file.wrong.class",
                    self.flatname);


        int startbp = bp;
        nextChar();
        char interfaceCount = nextChar();
        bp += interfaceCount * 2;
        char fieldCount = nextChar();
        for (int i = 0; i < fieldCount; i++) skipMember();
        char methodCount = nextChar();
        for (int i = 0; i < methodCount; i++) skipMember();
        readClassAttrs(c);
        if (readAllOfClassFile) {
            for (int i = 1; i < poolObj.length; i++) readPool(i);
            c.pool = new Pool(poolObj.length, poolObj, types);
        }

        bp = startbp;
        int n = nextChar();
        if (ct.supertype_field == null)
            ct.supertype_field = (n == 0)
                    ? Type.noType
                    : readClassSymbol(n).erasure(types);
        n = nextChar();
        List<Type> is = List.nil();
        for (int i = 0; i < n; i++) {
            Type _inter = readClassSymbol(nextChar()).erasure(types);
            is = is.prepend(_inter);
        }
        if (ct.interfaces_field == null)
            ct.interfaces_field = is.reverse();
        Assert.check(fieldCount == nextChar());
        for (int i = 0; i < fieldCount; i++) enterMember(c, readField());
        Assert.check(methodCount == nextChar());
        for (int i = 0; i < methodCount; i++) enterMember(c, readMethod());
        typevars = typevars.leave();
    }

    void readInnerClasses(ClassSymbol c) {
        int n = nextChar();
        for (int i = 0; i < n; i++) {
            nextChar();
            ClassSymbol outer = readClassSymbol(nextChar());
            Name name = readName(nextChar());
            if (name == null) name = names.empty;
            long flags = adjustClassFlags(nextChar());
            if (outer != null) {
                if (name == names.empty)
                    name = names.one;
                ClassSymbol member = enterClass(name, outer);
                if ((flags & STATIC) == 0) {
                    ((ClassType) member.type).setEnclosingType(outer.type);
                    if (member.erasure_field != null)
                        ((ClassType) member.erasure_field).setEnclosingType(types.erasure(outer.type));
                }
                if (c == outer) {
                    member.flags_field = flags;
                    enterMember(c, member);
                }
            }
        }
    }

    private void readClassFile(ClassSymbol c) throws IOException {
        int magic = nextInt();
        if (magic != JAVA_MAGIC)
            throw badClassFile("illegal.start.of.class.file");
        minorVersion = nextChar();
        majorVersion = nextChar();
        int maxMajor = Target.MAX().majorVersion;
        int maxMinor = Target.MAX().minorVersion;
        if (majorVersion > maxMajor ||
                majorVersion * 1000 + minorVersion <
                        Target.MIN().majorVersion * 1000 + Target.MIN().minorVersion) {
            if (majorVersion == (maxMajor + 1))
                log.warning("big.major.version",
                        currentClassFile,
                        majorVersion,
                        maxMajor);
            else
                throw badClassFile("wrong.version",
                        Integer.toString(majorVersion),
                        Integer.toString(minorVersion),
                        Integer.toString(maxMajor),
                        Integer.toString(maxMinor));
        } else if (checkClassFile &&
                majorVersion == maxMajor &&
                minorVersion > maxMinor) {
            printCCF("found.later.version",
                    Integer.toString(minorVersion));
        }
        indexPool();
        if (signatureBuffer.length < bp) {
            int ns = Integer.highestOneBit(bp) << 1;
            signatureBuffer = new byte[ns];
        }
        readClass(c);
    }

    long adjustFieldFlags(long flags) {
        return flags;
    }

    long adjustMethodFlags(long flags) {
        if ((flags & ACC_BRIDGE) != 0) {
            flags &= ~ACC_BRIDGE;
            flags |= BRIDGE;
            if (!allowGenerics)
                flags &= ~SYNTHETIC;
        }
        if ((flags & ACC_VARARGS) != 0) {
            flags &= ~ACC_VARARGS;
            flags |= VARARGS;
        }
        return flags;
    }

    long adjustClassFlags(long flags) {
        return flags & ~ACC_SUPER;
    }

    public ClassSymbol defineClass(Name name, Symbol owner) {
        ClassSymbol c = new ClassSymbol(0, name, owner);
        if (owner.kind == PCK)
            Assert.checkNull(classes.get(c.flatname), c);
        c.completer = thisCompleter;
        return c;
    }

    public ClassSymbol enterClass(Name name, TypeSymbol owner) {
        Name flatname = TypeSymbol.formFlatName(name, owner);
        ClassSymbol c = classes.get(flatname);
        if (c == null) {
            c = defineClass(name, owner);
            classes.put(flatname, c);
        } else if ((c.name != name || c.owner != owner) && owner.kind == TYP && c.owner.kind == PCK) {


            c.owner.members().remove(c);
            c.name = name;
            c.owner = owner;
            c.fullname = ClassSymbol.formFullName(name, owner);
        }
        return c;
    }

    public ClassSymbol enterClass(Name flatName, JavaFileObject classFile) {
        ClassSymbol cs = classes.get(flatName);
        if (cs != null) {
            String msg = Log.format("%s: completer = %s; class file = %s; source file = %s",
                    cs.fullname,
                    cs.completer,
                    cs.classfile,
                    cs.sourcefile);
            throw new AssertionError(msg);
        }
        Name packageName = Convert.packagePart(flatName);
        PackageSymbol owner = packageName.isEmpty()
                ? syms.unnamedPackage
                : enterPackage(packageName);
        cs = defineClass(Convert.shortName(flatName), owner);
        cs.classfile = classFile;
        classes.put(flatName, cs);
        return cs;
    }

    public ClassSymbol enterClass(Name flatname) {
        ClassSymbol c = classes.get(flatname);
        if (c == null)
            return enterClass(flatname, (JavaFileObject) null);
        else
            return c;
    }

    private void complete(Symbol sym) throws CompletionFailure {
        if (sym.kind == TYP) {
            ClassSymbol c = (ClassSymbol) sym;
            c.members_field = new Scope.ErrorScope(c);
            annotate.enterStart();
            try {
                completeOwners(c.owner);
                completeEnclosing(c);
            } finally {


                annotate.enterDoneWithoutFlush();
            }
            fillIn(c);
        } else if (sym.kind == PCK) {
            PackageSymbol p = (PackageSymbol) sym;
            try {
                fillIn(p);
            } catch (IOException ex) {
                throw new CompletionFailure(sym, ex.getLocalizedMessage()).initCause(ex);
            }
        }
        if (!filling)
            annotate.flush();
    }

    private void completeOwners(Symbol o) {
        if (o.kind != PCK) completeOwners(o.owner);
        o.complete();
    }

    private void completeEnclosing(ClassSymbol c) {
        if (c.owner.kind == PCK) {
            Symbol owner = c.owner;
            for (Name name : Convert.enclosingCandidates(Convert.shortName(c.name))) {
                Symbol encl = owner.members().lookup(name).sym;
                if (encl == null)
                    encl = classes.get(TypeSymbol.formFlatName(name, owner));
                if (encl != null)
                    encl.complete();
            }
        }
    }

    private void fillIn(ClassSymbol c) {
        if (completionFailureName == c.fullname) {
            throw new CompletionFailure(c, "user-selected completion failure by class name");
        }
        currentOwner = c;
        warnedAttrs.clear();
        JavaFileObject classfile = c.classfile;
        if (classfile != null) {
            JavaFileObject previousClassFile = currentClassFile;
            try {
                if (filling) {
                    Assert.error("Filling " + classfile.toUri() + " during " + previousClassFile);
                }
                currentClassFile = classfile;
                if (verbose) {
                    log.printVerbose("loading", currentClassFile.toString());
                }
                if (classfile.getKind() == JavaFileObject.Kind.CLASS) {
                    filling = true;
                    try {
                        bp = 0;
                        buf = readInputStream(buf, classfile.openInputStream());
                        readClassFile(c);
                        if (!missingTypeVariables.isEmpty() && !foundTypeVariables.isEmpty()) {
                            List<Type> missing = missingTypeVariables;
                            List<Type> found = foundTypeVariables;
                            missingTypeVariables = List.nil();
                            foundTypeVariables = List.nil();
                            filling = false;
                            ClassType ct = (ClassType) currentOwner.type;
                            ct.supertype_field =
                                    types.subst(ct.supertype_field, missing, found);
                            ct.interfaces_field =
                                    types.subst(ct.interfaces_field, missing, found);
                        } else if (missingTypeVariables.isEmpty() !=
                                foundTypeVariables.isEmpty()) {
                            Name name = missingTypeVariables.head.tsym.name;
                            throw badClassFile("undecl.type.var", name);
                        }
                    } finally {
                        missingTypeVariables = List.nil();
                        foundTypeVariables = List.nil();
                        filling = false;
                    }
                } else {
                    if (sourceCompleter != null) {
                        sourceCompleter.complete(c);
                    } else {
                        throw new IllegalStateException("Source completer required to read "
                                + classfile.toUri());
                    }
                }
                return;
            } catch (IOException ex) {
                throw badClassFile("unable.to.access.file", ex.getMessage());
            } finally {
                currentClassFile = previousClassFile;
            }
        } else {
            JCDiagnostic diag =
                    diagFactory.fragment("class.file.not.found", c.flatname);
            throw
                    newCompletionFailure(c, diag);
        }
    }

    private CompletionFailure newCompletionFailure(TypeSymbol c,
                                                   JCDiagnostic diag) {
        if (!cacheCompletionFailure) {


            return new CompletionFailure(c, diag);
        } else {
            CompletionFailure result = cachedCompletionFailure;
            result.sym = c;
            result.diag = diag;
            return result;
        }
    }

    public ClassSymbol loadClass(Name flatname) throws CompletionFailure {
        boolean absent = classes.get(flatname) == null;
        ClassSymbol c = enterClass(flatname);
        if (c.members_field == null && c.completer != null) {
            try {
                c.complete();
            } catch (CompletionFailure ex) {
                if (absent) classes.remove(flatname);
                throw ex;
            }
        }
        return c;
    }

    public boolean packageExists(Name fullname) {
        return enterPackage(fullname).exists();
    }

    public PackageSymbol enterPackage(Name fullname) {
        PackageSymbol p = packages.get(fullname);
        if (p == null) {
            Assert.check(!fullname.isEmpty(), "rootPackage missing!");
            p = new PackageSymbol(
                    Convert.shortName(fullname),
                    enterPackage(Convert.packagePart(fullname)));
            p.completer = thisCompleter;
            packages.put(fullname, p);
        }
        return p;
    }

    public PackageSymbol enterPackage(Name name, PackageSymbol owner) {
        return enterPackage(TypeSymbol.formFullName(name, owner));
    }

    protected void includeClassFile(PackageSymbol p, JavaFileObject file) {
        if ((p.flags_field & EXISTS) == 0)
            for (Symbol q = p; q != null && q.kind == PCK; q = q.owner)
                q.flags_field |= EXISTS;
        JavaFileObject.Kind kind = file.getKind();
        int seen;
        if (kind == JavaFileObject.Kind.CLASS)
            seen = CLASS_SEEN;
        else
            seen = SOURCE_SEEN;
        String binaryName = fileManager.inferBinaryName(currentLoc, file);
        int lastDot = binaryName.lastIndexOf(".");
        Name classname = names.fromString(binaryName.substring(lastDot + 1));
        boolean isPkgInfo = classname == names.package_info;
        ClassSymbol c = isPkgInfo
                ? p.package_info
                : (ClassSymbol) p.members_field.lookup(classname).sym;
        if (c == null) {
            c = enterClass(classname, p);
            if (c.classfile == null)
                c.classfile = file;
            if (isPkgInfo) {
                p.package_info = c;
            } else {
                if (c.owner == p)
                    p.members_field.enter(c);
            }
        } else if (c.classfile != null && (c.flags_field & seen) == 0) {


            if ((c.flags_field & (CLASS_SEEN | SOURCE_SEEN)) != 0)
                c.classfile = preferredFileObject(file, c.classfile);
        }
        c.flags_field |= seen;
    }

    protected JavaFileObject preferredFileObject(JavaFileObject a,
                                                 JavaFileObject b) {
        if (preferSource)
            return (a.getKind() == JavaFileObject.Kind.SOURCE) ? a : b;
        else {
            long adate = a.getLastModified();
            long bdate = b.getLastModified();


            return (adate > bdate) ? a : b;
        }
    }

    protected EnumSet<JavaFileObject.Kind> getPackageFileKinds() {
        return EnumSet.of(JavaFileObject.Kind.CLASS, JavaFileObject.Kind.SOURCE);
    }

    protected void extraFileActions(PackageSymbol pack, JavaFileObject fe) {
    }

    private void fillIn(PackageSymbol p) throws IOException {
        if (p.members_field == null) p.members_field = new Scope(p);
        String packageName = p.fullname.toString();
        Set<JavaFileObject.Kind> kinds = getPackageFileKinds();
        fillIn(p, PLATFORM_CLASS_PATH,
                fileManager.list(PLATFORM_CLASS_PATH,
                        packageName,
                        EnumSet.of(JavaFileObject.Kind.CLASS),
                        false));
        Set<JavaFileObject.Kind> classKinds = EnumSet.copyOf(kinds);
        classKinds.remove(JavaFileObject.Kind.SOURCE);
        boolean wantClassFiles = !classKinds.isEmpty();
        Set<JavaFileObject.Kind> sourceKinds = EnumSet.copyOf(kinds);
        sourceKinds.remove(JavaFileObject.Kind.CLASS);
        boolean wantSourceFiles = !sourceKinds.isEmpty();
        boolean haveSourcePath = fileManager.hasLocation(SOURCE_PATH);
        if (verbose && verbosePath) {
            if (fileManager instanceof StandardJavaFileManager) {
                StandardJavaFileManager fm = (StandardJavaFileManager) fileManager;
                if (haveSourcePath && wantSourceFiles) {
                    List<File> path = List.nil();
                    for (File file : fm.getLocation(SOURCE_PATH)) {
                        path = path.prepend(file);
                    }
                    log.printVerbose("sourcepath", path.reverse().toString());
                } else if (wantSourceFiles) {
                    List<File> path = List.nil();
                    for (File file : fm.getLocation(CLASS_PATH)) {
                        path = path.prepend(file);
                    }
                    log.printVerbose("sourcepath", path.reverse().toString());
                }
                if (wantClassFiles) {
                    List<File> path = List.nil();
                    for (File file : fm.getLocation(PLATFORM_CLASS_PATH)) {
                        path = path.prepend(file);
                    }
                    for (File file : fm.getLocation(CLASS_PATH)) {
                        path = path.prepend(file);
                    }
                    log.printVerbose("classpath", path.reverse().toString());
                }
            }
        }
        if (wantSourceFiles && !haveSourcePath) {
            fillIn(p, CLASS_PATH,
                    fileManager.list(CLASS_PATH,
                            packageName,
                            kinds,
                            false));
        } else {
            if (wantClassFiles)
                fillIn(p, CLASS_PATH,
                        fileManager.list(CLASS_PATH,
                                packageName,
                                classKinds,
                                false));
            if (wantSourceFiles)
                fillIn(p, SOURCE_PATH,
                        fileManager.list(SOURCE_PATH,
                                packageName,
                                sourceKinds,
                                false));
        }
        verbosePath = false;
    }

    private void fillIn(PackageSymbol p,
                        Location location,
                        Iterable<JavaFileObject> files) {
        currentLoc = location;
        for (JavaFileObject fo : files) {
            switch (fo.getKind()) {
                case CLASS:
                case SOURCE: {

                    String binaryName = fileManager.inferBinaryName(currentLoc, fo);
                    String simpleName = binaryName.substring(binaryName.lastIndexOf(".") + 1);
                    if (SourceVersion.isIdentifier(simpleName) ||
                            simpleName.equals("package-info"))
                        includeClassFile(p, fo);
                    break;
                }
                default:
                    extraFileActions(p, fo);
            }
        }
    }

    private void printCCF(String key, Object arg) {
        log.printLines(key, arg);
    }

    protected enum AttributeKind {
        CLASS, MEMBER
    }

    interface ProxyVisitor extends Attribute.Visitor {
        void visitEnumAttributeProxy(EnumAttributeProxy proxy);

        void visitArrayAttributeProxy(ArrayAttributeProxy proxy);

        void visitCompoundAnnotationProxy(CompoundAnnotationProxy proxy);
    }

    public interface SourceCompleter {
        void complete(ClassSymbol sym)
                throws CompletionFailure;
    }

    static class EnumAttributeProxy extends Attribute {
        Type enumType;
        Name enumerator;

        public EnumAttributeProxy(Type enumType, Name enumerator) {
            super(null);
            this.enumType = enumType;
            this.enumerator = enumerator;
        }

        public void accept(Visitor v) {
            ((ProxyVisitor) v).visitEnumAttributeProxy(this);
        }

        @Override
        public String toString() {
            return "/*proxy enum*/" + enumType + "." + enumerator;
        }
    }

    static class ArrayAttributeProxy extends Attribute {
        List<Attribute> values;

        ArrayAttributeProxy(List<Attribute> values) {
            super(null);
            this.values = values;
        }

        public void accept(Visitor v) {
            ((ProxyVisitor) v).visitArrayAttributeProxy(this);
        }

        @Override
        public String toString() {
            return "{" + values + "}";
        }
    }

    static class CompoundAnnotationProxy extends Attribute {
        final List<Pair<Name, Attribute>> values;

        public CompoundAnnotationProxy(Type type,
                                       List<Pair<Name, Attribute>> values) {
            super(type);
            this.values = values;
        }

        public void accept(Visitor v) {
            ((ProxyVisitor) v).visitCompoundAnnotationProxy(this);
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("@");
            buf.append(type.tsym.getQualifiedName());
            buf.append("/*proxy*/{");
            boolean first = true;
            for (List<Pair<Name, Attribute>> v = values;
                 v.nonEmpty(); v = v.tail) {
                Pair<Name, Attribute> value = v.head;
                if (!first) buf.append(",");
                first = false;
                buf.append(value.fst);
                buf.append("=");
                buf.append(value.snd);
            }
            buf.append("}");
            return buf.toString();
        }
    }

    static class TypeAnnotationProxy {
        final CompoundAnnotationProxy compound;
        final TypeAnnotationPosition position;

        public TypeAnnotationProxy(CompoundAnnotationProxy compound,
                                   TypeAnnotationPosition position) {
            this.compound = compound;
            this.position = position;
        }
    }

    private static class SourceFileObject extends BaseFileObject {

        private Name name;
        private Name flatname;

        public SourceFileObject(Name name, Name flatname) {
            super(null);
            this.name = name;
            this.flatname = flatname;
        }

        @Override
        public URI toUri() {
            try {
                return new URI(null, name.toString(), null);
            } catch (URISyntaxException e) {
                throw new CannotCreateUriError(name.toString(), e);
            }
        }

        @Override
        public String getName() {
            return name.toString();
        }

        @Override
        public String getShortName() {
            return getName();
        }

        @Override
        public Kind getKind() {
            return getKind(getName());
        }

        @Override
        public InputStream openInputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public OutputStream openOutputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CharBuffer getCharContent(boolean ignoreEncodingErrors) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Reader openReader(boolean ignoreEncodingErrors) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Writer openWriter() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLastModified() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean delete() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected String inferBinaryName(Iterable<? extends File> path) {
            return flatname.toString();
        }

        @Override
        public boolean isNameCompatible(String simpleName, Kind kind) {
            return true;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other)
                return true;
            if (!(other instanceof SourceFileObject))
                return false;
            SourceFileObject o = (SourceFileObject) other;
            return name.equals(o.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    public class BadClassFile extends CompletionFailure {
        private static final long serialVersionUID = 0;

        public BadClassFile(TypeSymbol sym, JavaFileObject file, JCDiagnostic diag) {
            super(sym, createBadClassFileDiagnostic(file, diag));
        }
    }

    protected abstract class AttributeReader {
        protected final Name name;
        protected final Version version;
        protected final Set<AttributeKind> kinds;

        protected AttributeReader(Name name, Version version, Set<AttributeKind> kinds) {
            this.name = name;
            this.version = version;
            this.kinds = kinds;
        }

        protected boolean accepts(AttributeKind kind) {
            if (kinds.contains(kind)) {
                if (majorVersion > version.major || (majorVersion == version.major && minorVersion >= version.minor))
                    return true;
                if (lintClassfile && !warnedAttrs.contains(name)) {
                    JavaFileObject prev = log.useSource(currentClassFile);
                    try {
                        log.warning(LintCategory.CLASSFILE, (DiagnosticPosition) null, "future.attr",
                                name, version.major, version.minor, majorVersion, minorVersion);
                    } finally {
                        log.useSource(prev);
                    }
                    warnedAttrs.add(name);
                }
            }
            return false;
        }

        protected abstract void read(Symbol sym, int attrLen);
    }

    class AnnotationDeproxy implements ProxyVisitor {
        Attribute result;
        Type type;
        private ClassSymbol requestingOwner = currentOwner.kind == MTH
                ? currentOwner.enclClass() : (ClassSymbol) currentOwner;

        List<Attribute.Compound> deproxyCompoundList(List<CompoundAnnotationProxy> pl) {

            ListBuffer<Attribute.Compound> buf =
                    new ListBuffer<Attribute.Compound>();
            for (List<CompoundAnnotationProxy> l = pl; l.nonEmpty(); l = l.tail) {
                buf.append(deproxyCompound(l.head));
            }
            return buf.toList();
        }

        Attribute.Compound deproxyCompound(CompoundAnnotationProxy a) {
            ListBuffer<Pair<MethodSymbol, Attribute>> buf =
                    new ListBuffer<Pair<MethodSymbol, Attribute>>();
            for (List<Pair<Name, Attribute>> l = a.values;
                 l.nonEmpty();
                 l = l.tail) {
                MethodSymbol meth = findAccessMethod(a.type, l.head.fst);
                buf.append(new Pair<MethodSymbol, Attribute>
                        (meth, deproxy(meth.type.getReturnType(), l.head.snd)));
            }
            return new Attribute.Compound(a.type, buf.toList());
        }

        MethodSymbol findAccessMethod(Type container, Name name) {
            CompletionFailure failure = null;
            try {
                for (Scope.Entry e = container.tsym.members().lookup(name);
                     e.scope != null;
                     e = e.next()) {
                    Symbol sym = e.sym;
                    if (sym.kind == MTH && sym.type.getParameterTypes().length() == 0)
                        return (MethodSymbol) sym;
                }
            } catch (CompletionFailure ex) {
                failure = ex;
            }

            JavaFileObject prevSource = log.useSource(requestingOwner.classfile);
            try {
                if (failure == null) {
                    log.warning("annotation.method.not.found",
                            container,
                            name);
                } else {
                    log.warning("annotation.method.not.found.reason",
                            container,
                            name,
                            failure.getDetailValue());
                }
            } finally {
                log.useSource(prevSource);
            }


            MethodType mt = new MethodType(List.nil(),
                    syms.botType,
                    List.nil(),
                    syms.methodClass);
            return new MethodSymbol(PUBLIC | ABSTRACT, name, mt, container.tsym);
        }

        Attribute deproxy(Type t, Attribute a) {
            Type oldType = type;
            try {
                type = t;
                a.accept(this);
                return result;
            } finally {
                type = oldType;
            }
        }

        public void visitConstant(Attribute.Constant value) {

            result = value;
        }

        public void visitClass(Attribute.Class clazz) {
            result = clazz;
        }

        public void visitEnum(Attribute.Enum e) {
            throw new AssertionError();
        }

        public void visitCompound(Attribute.Compound compound) {
            throw new AssertionError();
        }

        public void visitArray(Attribute.Array array) {
            throw new AssertionError();
        }

        public void visitError(Attribute.Error e) {
            throw new AssertionError();
        }

        public void visitEnumAttributeProxy(EnumAttributeProxy proxy) {

            TypeSymbol enumTypeSym = proxy.enumType.tsym;
            VarSymbol enumerator = null;
            CompletionFailure failure = null;
            try {
                for (Scope.Entry e = enumTypeSym.members().lookup(proxy.enumerator);
                     e.scope != null;
                     e = e.next()) {
                    if (e.sym.kind == VAR) {
                        enumerator = (VarSymbol) e.sym;
                        break;
                    }
                }
            } catch (CompletionFailure ex) {
                failure = ex;
            }
            if (enumerator == null) {
                if (failure != null) {
                    log.warning("unknown.enum.constant.reason",
                            currentClassFile, enumTypeSym, proxy.enumerator,
                            failure.getDiagnostic());
                } else {
                    log.warning("unknown.enum.constant",
                            currentClassFile, enumTypeSym, proxy.enumerator);
                }
                result = new Attribute.Enum(enumTypeSym.type,
                        new VarSymbol(0, proxy.enumerator, syms.botType, enumTypeSym));
            } else {
                result = new Attribute.Enum(enumTypeSym.type, enumerator);
            }
        }

        public void visitArrayAttributeProxy(ArrayAttributeProxy proxy) {
            int length = proxy.values.length();
            Attribute[] ats = new Attribute[length];
            Type elemtype = types.elemtype(type);
            int i = 0;
            for (List<Attribute> p = proxy.values; p.nonEmpty(); p = p.tail) {
                ats[i++] = deproxy(elemtype, p.head);
            }
            result = new Attribute.Array(type, ats);
        }

        public void visitCompoundAnnotationProxy(CompoundAnnotationProxy proxy) {
            result = deproxyCompound(proxy);
        }
    }

    class AnnotationDefaultCompleter extends AnnotationDeproxy implements Annotate.Worker {
        final MethodSymbol sym;
        final Attribute value;
        final JavaFileObject classFile = currentClassFile;

        AnnotationDefaultCompleter(MethodSymbol sym, Attribute value) {
            this.sym = sym;
            this.value = value;
        }

        @Override
        public String toString() {
            return " ClassReader store default for " + sym.owner + "." + sym + " is " + value;
        }

        public void run() {
            JavaFileObject previousClassFile = currentClassFile;
            try {


                sym.defaultValue = null;
                currentClassFile = classFile;
                sym.defaultValue = deproxy(sym.type.getReturnType(), value);
            } finally {
                currentClassFile = previousClassFile;
            }
        }
    }

    class AnnotationCompleter extends AnnotationDeproxy implements Annotate.Worker {
        final Symbol sym;
        final List<CompoundAnnotationProxy> l;
        final JavaFileObject classFile;

        AnnotationCompleter(Symbol sym, List<CompoundAnnotationProxy> l) {
            this.sym = sym;
            this.l = l;
            this.classFile = currentClassFile;
        }

        @Override
        public String toString() {
            return " ClassReader annotate " + sym.owner + "." + sym + " with " + l;
        }

        public void run() {
            JavaFileObject previousClassFile = currentClassFile;
            try {
                currentClassFile = classFile;
                List<Attribute.Compound> newList = deproxyCompoundList(l);
                if (sym.annotationsPendingCompletion()) {
                    sym.setDeclarationAttributes(newList);
                } else {
                    sym.appendAttributes(newList);
                }
            } finally {
                currentClassFile = previousClassFile;
            }
        }
    }

    class TypeAnnotationCompleter extends AnnotationCompleter {
        List<TypeAnnotationProxy> proxies;

        TypeAnnotationCompleter(Symbol sym,
                                List<TypeAnnotationProxy> proxies) {
            super(sym, List.nil());
            this.proxies = proxies;
        }

        List<Attribute.TypeCompound> deproxyTypeCompoundList(List<TypeAnnotationProxy> proxies) {
            ListBuffer<Attribute.TypeCompound> buf = new ListBuffer<>();
            for (TypeAnnotationProxy proxy : proxies) {
                Attribute.Compound compound = deproxyCompound(proxy.compound);
                Attribute.TypeCompound typeCompound = new Attribute.TypeCompound(compound, proxy.position);
                buf.add(typeCompound);
            }
            return buf.toList();
        }

        @Override
        public void run() {
            JavaFileObject previousClassFile = currentClassFile;
            try {
                currentClassFile = classFile;
                List<Attribute.TypeCompound> newList = deproxyTypeCompoundList(proxies);
                sym.setTypeAttributes(newList.prependList(sym.getRawTypeAttributes()));
            } finally {
                currentClassFile = previousClassFile;
            }
        }
    }
}
