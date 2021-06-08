package com.sun.tools.javac.tree;

import com.sun.source.tree.MemberReferenceTree.ReferenceMode;
import com.sun.tools.javac.code.BoundKind;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.Convert;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import static com.sun.tools.javac.code.Flags.ANNOTATION;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.tree.JCTree.Tag.*;

public class Pretty extends Visitor {
    private final static String trimSequence = "[...]";
    private final static int PREFERRED_LENGTH = 20;
    private final boolean sourceOutput;
    public int width = 4;
    Writer out;
    int lmargin = 0;
    Name enclClassName;
    DocCommentTable docComments = null;
    String lineSep = System.getProperty("line.separator");
    int prec;

    public Pretty(Writer out, boolean sourceOutput) {
        this.out = out;
        this.sourceOutput = sourceOutput;
    }

    public static String toSimpleString(JCTree tree) {
        return toSimpleString(tree, PREFERRED_LENGTH);
    }

    public static String toSimpleString(JCTree tree, int maxLength) {
        StringWriter s = new StringWriter();
        try {
            new Pretty(s, false).printExpr(tree);
        } catch (IOException e) {


            throw new AssertionError(e);
        }


        String res = s.toString().trim().replaceAll("\\s+", " ").replaceAll("/\\*missing\\*/", "");
        if (res.length() < maxLength) {
            return res;
        } else {
            int head = (maxLength - trimSequence.length()) * 2 / 3;
            int tail = maxLength - trimSequence.length() - head;
            return res.substring(0, head) + trimSequence + res.substring(res.length() - tail);
        }
    }

    static int lineEndPos(String s, int start) {
        int pos = s.indexOf('\n', start);
        if (pos < 0) pos = s.length();
        return pos;
    }

    void align() throws IOException {
        for (int i = 0; i < lmargin; i++) out.write(" ");
    }

    void indent() {
        lmargin = lmargin + width;
    }

    void undent() {
        lmargin = lmargin - width;
    }

    void open(int contextPrec, int ownPrec) throws IOException {
        if (ownPrec < contextPrec) out.write("(");
    }

    void close(int contextPrec, int ownPrec) throws IOException {
        if (ownPrec < contextPrec) out.write(")");
    }

    public void print(Object s) throws IOException {
        out.write(Convert.escapeUnicode(s.toString()));
    }

    public void println() throws IOException {
        out.write(lineSep);
    }

    public void printExpr(JCTree tree, int prec) throws IOException {
        int prevPrec = this.prec;
        try {
            this.prec = prec;
            if (tree == null) print("/*missing*/");
            else {
                tree.accept(this);
            }
        } catch (UncheckedIOException ex) {
            IOException e = new IOException(ex.getMessage(), ex);
            throw e;
        } finally {
            this.prec = prevPrec;
        }
    }

    public void printExpr(JCTree tree) throws IOException {
        printExpr(tree, TreeInfo.noPrec);
    }

    public void printStat(JCTree tree) throws IOException {
        printExpr(tree, TreeInfo.notExpression);
    }

    public <T extends JCTree> void printExprs(List<T> trees, String sep) throws IOException {
        if (trees.nonEmpty()) {
            printExpr(trees.head);
            for (List<T> l = trees.tail; l.nonEmpty(); l = l.tail) {
                print(sep);
                printExpr(l.head);
            }
        }
    }

    public <T extends JCTree> void printExprs(List<T> trees) throws IOException {
        printExprs(trees, ", ");
    }

    public void printStats(List<? extends JCTree> trees) throws IOException {
        for (List<? extends JCTree> l = trees; l.nonEmpty(); l = l.tail) {
            align();
            printStat(l.head);
            println();
        }
    }

    public void printFlags(long flags) throws IOException {
        if ((flags & SYNTHETIC) != 0) print("/*synthetic*/ ");
        print(TreeInfo.flagNames(flags));
        if ((flags & ExtendedStandardFlags) != 0) print(" ");
        if ((flags & ANNOTATION) != 0) print("@");
    }

    public void printAnnotations(List<JCAnnotation> trees) throws IOException {
        for (List<JCAnnotation> l = trees; l.nonEmpty(); l = l.tail) {
            printStat(l.head);
            println();
            align();
        }
    }

    public void printTypeAnnotations(List<JCAnnotation> trees) throws IOException {
        for (List<JCAnnotation> l = trees; l.nonEmpty(); l = l.tail) {
            printExpr(l.head);
            print(" ");
        }
    }

    public void printDocComment(JCTree tree) throws IOException {
        if (docComments != null) {
            String dc = docComments.getCommentText(tree);
            if (dc != null) {
                print("/**");
                println();
                int pos = 0;
                int endpos = lineEndPos(dc, pos);
                while (pos < dc.length()) {
                    align();
                    print(" *");
                    if (pos < dc.length() && dc.charAt(pos) > ' ') print(" ");
                    print(dc.substring(pos, endpos));
                    println();
                    pos = endpos + 1;
                    endpos = lineEndPos(dc, pos);
                }
                align();
                print(" */");
                println();
                align();
            }
        }
    }

    public void printTypeParameters(List<JCTypeParameter> trees) throws IOException {
        if (trees.nonEmpty()) {
            print("<");
            printExprs(trees);
            print(">");
        }
    }

    public void printBlock(List<? extends JCTree> stats) throws IOException {
        print("{");
        println();
        indent();
        printStats(stats);
        undent();
        align();
        print("}");
    }

    public void printEnumBody(List<JCTree> stats) throws IOException {
        print("{");
        println();
        indent();
        boolean first = true;
        for (List<JCTree> l = stats; l.nonEmpty(); l = l.tail) {
            if (isEnumerator(l.head)) {
                if (!first) {
                    print(",");
                    println();
                }
                align();
                printStat(l.head);
                first = false;
            }
        }
        print(";");
        println();
        for (List<JCTree> l = stats; l.nonEmpty(); l = l.tail) {
            if (!isEnumerator(l.head)) {
                align();
                printStat(l.head);
                println();
            }
        }
        undent();
        align();
        print("}");
    }

    boolean isEnumerator(JCTree t) {
        return t.hasTag(VARDEF) && (((JCVariableDecl) t).mods.flags & ENUM) != 0;
    }

    public void printUnit(JCCompilationUnit tree, JCClassDecl cdef) throws IOException {
        docComments = tree.docComments;
        printDocComment(tree);
        if (tree.pid != null) {
            print("package ");
            printExpr(tree.pid);
            print(";");
            println();
        }
        boolean firstImport = true;
        for (List<JCTree> l = tree.defs;
             l.nonEmpty() && (cdef == null || l.head.hasTag(IMPORT));
             l = l.tail) {
            if (l.head.hasTag(IMPORT)) {
                JCImport imp = (JCImport) l.head;
                Name name = TreeInfo.name(imp.qualid);
                if (name == name.table.names.asterisk ||
                        cdef == null ||
                        isUsed(TreeInfo.symbol(imp.qualid), cdef)) {
                    if (firstImport) {
                        firstImport = false;
                        println();
                    }
                    printStat(imp);
                }
            } else {
                printStat(l.head);
            }
        }
        if (cdef != null) {
            printStat(cdef);
            println();
        }
    }

    boolean isUsed(final Symbol t, JCTree cdef) {
        class UsedVisitor extends TreeScanner {
            boolean result = false;

            public void scan(JCTree tree) {
                if (tree != null && !result) tree.accept(this);
            }

            public void visitIdent(JCIdent tree) {
                if (tree.sym == t) result = true;
            }
        }
        UsedVisitor v = new UsedVisitor();
        v.scan(cdef);
        return v.result;
    }

    public void visitTopLevel(JCCompilationUnit tree) {
        try {
            printUnit(tree, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitImport(JCImport tree) {
        try {
            print("import ");
            if (tree.staticImport) print("static ");
            printExpr(tree.qualid);
            print(";");
            println();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitClassDef(JCClassDecl tree) {
        try {
            println();
            align();
            printDocComment(tree);
            printAnnotations(tree.mods.annotations);
            printFlags(tree.mods.flags & ~INTERFACE);
            Name enclClassNamePrev = enclClassName;
            enclClassName = tree.name;
            if ((tree.mods.flags & INTERFACE) != 0) {
                print("interface " + tree.name);
                printTypeParameters(tree.typarams);
                if (tree.implementing.nonEmpty()) {
                    print(" extends ");
                    printExprs(tree.implementing);
                }
            } else {
                if ((tree.mods.flags & ENUM) != 0)
                    print("enum " + tree.name);
                else
                    print("class " + tree.name);
                printTypeParameters(tree.typarams);
                if (tree.extending != null) {
                    print(" extends ");
                    printExpr(tree.extending);
                }
                if (tree.implementing.nonEmpty()) {
                    print(" implements ");
                    printExprs(tree.implementing);
                }
            }
            print(" ");
            if ((tree.mods.flags & ENUM) != 0) {
                printEnumBody(tree.defs);
            } else {
                printBlock(tree.defs);
            }
            enclClassName = enclClassNamePrev;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitMethodDef(JCMethodDecl tree) {
        try {

            if (tree.name == tree.name.table.names.init &&
                    enclClassName == null &&
                    sourceOutput) return;
            println();
            align();
            printDocComment(tree);
            printExpr(tree.mods);
            printTypeParameters(tree.typarams);
            if (tree.name == tree.name.table.names.init) {
                print(enclClassName != null ? enclClassName : tree.name);
            } else {
                printExpr(tree.restype);
                print(" " + tree.name);
            }
            print("(");
            if (tree.recvparam != null) {
                printExpr(tree.recvparam);
                if (tree.params.size() > 0) {
                    print(", ");
                }
            }
            printExprs(tree.params);
            print(")");
            if (tree.thrown.nonEmpty()) {
                print(" throws ");
                printExprs(tree.thrown);
            }
            if (tree.defaultValue != null) {
                print(" default ");
                printExpr(tree.defaultValue);
            }
            if (tree.body != null) {
                print(" ");
                printStat(tree.body);
            } else {
                print(";");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitVarDef(JCVariableDecl tree) {
        try {
            if (docComments != null && docComments.hasComment(tree)) {
                println();
                align();
            }
            printDocComment(tree);
            if ((tree.mods.flags & ENUM) != 0) {
                print("/*public static final*/ ");
                print(tree.name);
                if (tree.init != null) {
                    if (sourceOutput && tree.init.hasTag(NEWCLASS)) {
                        print(" /*enum*/ ");
                        JCNewClass init = (JCNewClass) tree.init;
                        if (init.args != null && init.args.nonEmpty()) {
                            print("(");
                            print(init.args);
                            print(")");
                        }
                        if (init.def != null && init.def.defs != null) {
                            print(" ");
                            printBlock(init.def.defs);
                        }
                        return;
                    }
                    print(" /* = ");
                    printExpr(tree.init);
                    print(" */");
                }
            } else {
                printExpr(tree.mods);
                if ((tree.mods.flags & VARARGS) != 0) {
                    JCTree vartype = tree.vartype;
                    List<JCAnnotation> tas = null;
                    if (vartype instanceof JCAnnotatedType) {
                        tas = ((JCAnnotatedType) vartype).annotations;
                        vartype = ((JCAnnotatedType) vartype).underlyingType;
                    }
                    printExpr(((JCArrayTypeTree) vartype).elemtype);
                    if (tas != null) {
                        print(' ');
                        printTypeAnnotations(tas);
                    }
                    print("... " + tree.name);
                } else {
                    printExpr(tree.vartype);
                    print(" " + tree.name);
                }
                if (tree.init != null) {
                    print(" = ");
                    printExpr(tree.init);
                }
                if (prec == TreeInfo.notExpression) print(";");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitSkip(JCSkip tree) {
        try {
            print(";");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitBlock(JCBlock tree) {
        try {
            printFlags(tree.flags);
            printBlock(tree.stats);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitDoLoop(JCDoWhileLoop tree) {
        try {
            print("do ");
            printStat(tree.body);
            align();
            print(" while ");
            if (tree.cond.hasTag(PARENS)) {
                printExpr(tree.cond);
            } else {
                print("(");
                printExpr(tree.cond);
                print(")");
            }
            print(";");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitWhileLoop(JCWhileLoop tree) {
        try {
            print("while ");
            if (tree.cond.hasTag(PARENS)) {
                printExpr(tree.cond);
            } else {
                print("(");
                printExpr(tree.cond);
                print(")");
            }
            print(" ");
            printStat(tree.body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitForLoop(JCForLoop tree) {
        try {
            print("for (");
            if (tree.init.nonEmpty()) {
                if (tree.init.head.hasTag(VARDEF)) {
                    printExpr(tree.init.head);
                    for (List<JCStatement> l = tree.init.tail; l.nonEmpty(); l = l.tail) {
                        JCVariableDecl vdef = (JCVariableDecl) l.head;
                        print(", " + vdef.name + " = ");
                        printExpr(vdef.init);
                    }
                } else {
                    printExprs(tree.init);
                }
            }
            print("; ");
            if (tree.cond != null) printExpr(tree.cond);
            print("; ");
            printExprs(tree.step);
            print(") ");
            printStat(tree.body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitForeachLoop(JCEnhancedForLoop tree) {
        try {
            print("for (");
            printExpr(tree.var);
            print(" : ");
            printExpr(tree.expr);
            print(") ");
            printStat(tree.body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitLabelled(JCLabeledStatement tree) {
        try {
            print(tree.label + ": ");
            printStat(tree.body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitSwitch(JCSwitch tree) {
        try {
            print("switch ");
            if (tree.selector.hasTag(PARENS)) {
                printExpr(tree.selector);
            } else {
                print("(");
                printExpr(tree.selector);
                print(")");
            }
            print(" {");
            println();
            printStats(tree.cases);
            align();
            print("}");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitCase(JCCase tree) {
        try {
            if (tree.pat == null) {
                print("default");
            } else {
                print("case ");
                printExpr(tree.pat);
            }
            print(": ");
            println();
            indent();
            printStats(tree.stats);
            undent();
            align();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitSynchronized(JCSynchronized tree) {
        try {
            print("synchronized ");
            if (tree.lock.hasTag(PARENS)) {
                printExpr(tree.lock);
            } else {
                print("(");
                printExpr(tree.lock);
                print(")");
            }
            print(" ");
            printStat(tree.body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitTry(JCTry tree) {
        try {
            print("try ");
            if (tree.resources.nonEmpty()) {
                print("(");
                boolean first = true;
                for (JCTree var : tree.resources) {
                    if (!first) {
                        println();
                        indent();
                    }
                    printStat(var);
                    first = false;
                }
                print(") ");
            }
            printStat(tree.body);
            for (List<JCCatch> l = tree.catchers; l.nonEmpty(); l = l.tail) {
                printStat(l.head);
            }
            if (tree.finalizer != null) {
                print(" finally ");
                printStat(tree.finalizer);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitCatch(JCCatch tree) {
        try {
            print(" catch (");
            printExpr(tree.param);
            print(") ");
            printStat(tree.body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitConditional(JCConditional tree) {
        try {
            open(prec, TreeInfo.condPrec);
            printExpr(tree.cond, TreeInfo.condPrec + 1);
            print(" ? ");
            printExpr(tree.truepart);
            print(" : ");
            printExpr(tree.falsepart, TreeInfo.condPrec);
            close(prec, TreeInfo.condPrec);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitIf(JCIf tree) {
        try {
            print("if ");
            if (tree.cond.hasTag(PARENS)) {
                printExpr(tree.cond);
            } else {
                print("(");
                printExpr(tree.cond);
                print(")");
            }
            print(" ");
            printStat(tree.thenpart);
            if (tree.elsepart != null) {
                print(" else ");
                printStat(tree.elsepart);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitExec(JCExpressionStatement tree) {
        try {
            printExpr(tree.expr);
            if (prec == TreeInfo.notExpression) print(";");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitBreak(JCBreak tree) {
        try {
            print("break");
            if (tree.label != null) print(" " + tree.label);
            print(";");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitContinue(JCContinue tree) {
        try {
            print("continue");
            if (tree.label != null) print(" " + tree.label);
            print(";");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitReturn(JCReturn tree) {
        try {
            print("return");
            if (tree.expr != null) {
                print(" ");
                printExpr(tree.expr);
            }
            print(";");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitThrow(JCThrow tree) {
        try {
            print("throw ");
            printExpr(tree.expr);
            print(";");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitAssert(JCAssert tree) {
        try {
            print("assert ");
            printExpr(tree.cond);
            if (tree.detail != null) {
                print(" : ");
                printExpr(tree.detail);
            }
            print(";");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitApply(JCMethodInvocation tree) {
        try {
            if (!tree.typeargs.isEmpty()) {
                if (tree.meth.hasTag(SELECT)) {
                    JCFieldAccess left = (JCFieldAccess) tree.meth;
                    printExpr(left.selected);
                    print(".<");
                    printExprs(tree.typeargs);
                    print(">" + left.name);
                } else {
                    print("<");
                    printExprs(tree.typeargs);
                    print(">");
                    printExpr(tree.meth);
                }
            } else {
                printExpr(tree.meth);
            }
            print("(");
            printExprs(tree.args);
            print(")");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitNewClass(JCNewClass tree) {
        try {
            if (tree.encl != null) {
                printExpr(tree.encl);
                print(".");
            }
            print("new ");
            if (!tree.typeargs.isEmpty()) {
                print("<");
                printExprs(tree.typeargs);
                print(">");
            }
            if (tree.def != null && tree.def.mods.annotations.nonEmpty()) {
                printTypeAnnotations(tree.def.mods.annotations);
            }
            printExpr(tree.clazz);
            print("(");
            printExprs(tree.args);
            print(")");
            if (tree.def != null) {
                Name enclClassNamePrev = enclClassName;
                enclClassName =
                        tree.def.name != null ? tree.def.name :
                                tree.type != null && tree.type.tsym.name != tree.type.tsym.name.table.names.empty
                                        ? tree.type.tsym.name : null;
                if ((tree.def.mods.flags & Flags.ENUM) != 0) print("/*enum*/");
                printBlock(tree.def.defs);
                enclClassName = enclClassNamePrev;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitNewArray(JCNewArray tree) {
        try {
            if (tree.elemtype != null) {
                print("new ");
                JCTree elem = tree.elemtype;
                printBaseElementType(elem);
                if (!tree.annotations.isEmpty()) {
                    print(' ');
                    printTypeAnnotations(tree.annotations);
                }
                if (tree.elems != null) {
                    print("[]");
                }
                int i = 0;
                List<List<JCAnnotation>> da = tree.dimAnnotations;
                for (List<JCExpression> l = tree.dims; l.nonEmpty(); l = l.tail) {
                    if (da.size() > i && !da.get(i).isEmpty()) {
                        print(' ');
                        printTypeAnnotations(da.get(i));
                    }
                    print("[");
                    i++;
                    printExpr(l.head);
                    print("]");
                }
                printBrackets(elem);
            }
            if (tree.elems != null) {
                print("{");
                printExprs(tree.elems);
                print("}");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitLambda(JCLambda tree) {
        try {
            print("(");
            if (tree.paramKind == JCLambda.ParameterKind.EXPLICIT) {
                printExprs(tree.params);
            } else {
                String sep = "";
                for (JCVariableDecl param : tree.params) {
                    print(sep);
                    print(param.name);
                    sep = ",";
                }
            }
            print(")->");
            printExpr(tree.body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitParens(JCParens tree) {
        try {
            print("(");
            printExpr(tree.expr);
            print(")");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitAssign(JCAssign tree) {
        try {
            open(prec, TreeInfo.assignPrec);
            printExpr(tree.lhs, TreeInfo.assignPrec + 1);
            print(" = ");
            printExpr(tree.rhs, TreeInfo.assignPrec);
            close(prec, TreeInfo.assignPrec);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String operatorName(Tag tag) {
        switch (tag) {
            case POS:
                return "+";
            case NEG:
                return "-";
            case NOT:
                return "!";
            case COMPL:
                return "~";
            case PREINC:
                return "++";
            case PREDEC:
                return "--";
            case POSTINC:
                return "++";
            case POSTDEC:
                return "--";
            case NULLCHK:
                return "<*nullchk*>";
            case OR:
                return "||";
            case AND:
                return "&&";
            case EQ:
                return "==";
            case NE:
                return "!=";
            case LT:
                return "<";
            case GT:
                return ">";
            case LE:
                return "<=";
            case GE:
                return ">=";
            case BITOR:
                return "|";
            case BITXOR:
                return "^";
            case BITAND:
                return "&";
            case SL:
                return "<<";
            case SR:
                return ">>";
            case USR:
                return ">>>";
            case PLUS:
                return "+";
            case MINUS:
                return "-";
            case MUL:
                return "*";
            case DIV:
                return "/";
            case MOD:
                return "%";
            default:
                throw new Error();
        }
    }

    public void visitAssignop(JCAssignOp tree) {
        try {
            open(prec, TreeInfo.assignopPrec);
            printExpr(tree.lhs, TreeInfo.assignopPrec + 1);
            print(" " + operatorName(tree.getTag().noAssignOp()) + "= ");
            printExpr(tree.rhs, TreeInfo.assignopPrec);
            close(prec, TreeInfo.assignopPrec);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitUnary(JCUnary tree) {
        try {
            int ownprec = TreeInfo.opPrec(tree.getTag());
            String opname = operatorName(tree.getTag());
            open(prec, ownprec);
            if (!tree.getTag().isPostUnaryOp()) {
                print(opname);
                printExpr(tree.arg, ownprec);
            } else {
                printExpr(tree.arg, ownprec);
                print(opname);
            }
            close(prec, ownprec);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitBinary(JCBinary tree) {
        try {
            int ownprec = TreeInfo.opPrec(tree.getTag());
            String opname = operatorName(tree.getTag());
            open(prec, ownprec);
            printExpr(tree.lhs, ownprec);
            print(" " + opname + " ");
            printExpr(tree.rhs, ownprec + 1);
            close(prec, ownprec);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitTypeCast(JCTypeCast tree) {
        try {
            open(prec, TreeInfo.prefixPrec);
            print("(");
            printExpr(tree.clazz);
            print(")");
            printExpr(tree.expr, TreeInfo.prefixPrec);
            close(prec, TreeInfo.prefixPrec);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitTypeTest(JCInstanceOf tree) {
        try {
            open(prec, TreeInfo.ordPrec);
            printExpr(tree.expr, TreeInfo.ordPrec);
            print(" instanceof ");
            printExpr(tree.clazz, TreeInfo.ordPrec + 1);
            close(prec, TreeInfo.ordPrec);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitIndexed(JCArrayAccess tree) {
        try {
            printExpr(tree.indexed, TreeInfo.postfixPrec);
            print("[");
            printExpr(tree.index);
            print("]");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitSelect(JCFieldAccess tree) {
        try {
            printExpr(tree.selected, TreeInfo.postfixPrec);
            print("." + tree.name);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitReference(JCMemberReference tree) {
        try {
            printExpr(tree.expr);
            print("::");
            if (tree.typeargs != null) {
                print("<");
                printExprs(tree.typeargs);
                print(">");
            }
            print(tree.getMode() == ReferenceMode.INVOKE ? tree.name : "new");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitIdent(JCIdent tree) {
        try {
            print(tree.name);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitLiteral(JCLiteral tree) {
        try {
            switch (tree.typetag) {
                case INT:
                    print(tree.value.toString());
                    break;
                case LONG:
                    print(tree.value + "L");
                    break;
                case FLOAT:
                    print(tree.value + "F");
                    break;
                case DOUBLE:
                    print(tree.value.toString());
                    break;
                case CHAR:
                    print("'" +
                            Convert.quote(
                                    String.valueOf((char) ((Number) tree.value).intValue())) +
                            "'");
                    break;
                case BOOLEAN:
                    print(((Number) tree.value).intValue() == 1 ? "true" : "false");
                    break;
                case BOT:
                    print("null");
                    break;
                default:
                    print("\"" + Convert.quote(tree.value.toString()) + "\"");
                    break;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitTypeIdent(JCPrimitiveTypeTree tree) {
        try {
            switch (tree.typetag) {
                case BYTE:
                    print("byte");
                    break;
                case CHAR:
                    print("char");
                    break;
                case SHORT:
                    print("short");
                    break;
                case INT:
                    print("int");
                    break;
                case LONG:
                    print("long");
                    break;
                case FLOAT:
                    print("float");
                    break;
                case DOUBLE:
                    print("double");
                    break;
                case BOOLEAN:
                    print("boolean");
                    break;
                case VOID:
                    print("void");
                    break;
                default:
                    print("error");
                    break;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitTypeArray(JCArrayTypeTree tree) {
        try {
            printBaseElementType(tree);
            printBrackets(tree);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void printBaseElementType(JCTree tree) throws IOException {
        printExpr(TreeInfo.innermostType(tree));
    }

    private void printBrackets(JCTree tree) throws IOException {
        JCTree elem = tree;
        while (true) {
            if (elem.hasTag(ANNOTATED_TYPE)) {
                JCAnnotatedType atype = (JCAnnotatedType) elem;
                elem = atype.underlyingType;
                if (elem.hasTag(TYPEARRAY)) {
                    print(' ');
                    printTypeAnnotations(atype.annotations);
                }
            }
            if (elem.hasTag(TYPEARRAY)) {
                print("[]");
                elem = ((JCArrayTypeTree) elem).elemtype;
            } else {
                break;
            }
        }
    }

    public void visitTypeApply(JCTypeApply tree) {
        try {
            printExpr(tree.clazz);
            print("<");
            printExprs(tree.arguments);
            print(">");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitTypeUnion(JCTypeUnion tree) {
        try {
            printExprs(tree.alternatives, " | ");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitTypeIntersection(JCTypeIntersection tree) {
        try {
            printExprs(tree.bounds, " & ");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitTypeParameter(JCTypeParameter tree) {
        try {
            if (tree.annotations.nonEmpty()) {
                this.printTypeAnnotations(tree.annotations);
            }
            print(tree.name);
            if (tree.bounds.nonEmpty()) {
                print(" extends ");
                printExprs(tree.bounds, " & ");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void visitWildcard(JCWildcard tree) {
        try {
            print(tree.kind);
            if (tree.kind.kind != BoundKind.UNBOUND)
                printExpr(tree.inner);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void visitTypeBoundKind(TypeBoundKind tree) {
        try {
            print(String.valueOf(tree.kind));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitErroneous(JCErroneous tree) {
        try {
            print("(ERROR)");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitLetExpr(LetExpr tree) {
        try {
            print("(let " + tree.defs + " in " + tree.expr + ")");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitModifiers(JCModifiers mods) {
        try {
            printAnnotations(mods.annotations);
            printFlags(mods.flags);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitAnnotation(JCAnnotation tree) {
        try {
            print("@");
            printExpr(tree.annotationType);
            print("(");
            printExprs(tree.args);
            print(")");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitAnnotatedType(JCAnnotatedType tree) {
        try {
            if (tree.underlyingType.hasTag(SELECT)) {
                JCFieldAccess access = (JCFieldAccess) tree.underlyingType;
                printExpr(access.selected, TreeInfo.postfixPrec);
                print(".");
                printTypeAnnotations(tree.annotations);
                print(access.name);
            } else if (tree.underlyingType.hasTag(TYPEARRAY)) {
                printBaseElementType(tree);
                printBrackets(tree);
            } else {
                printTypeAnnotations(tree.annotations);
                printExpr(tree.underlyingType);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void visitTree(JCTree tree) {
        try {
            print("(UNKNOWN: " + tree + ")");
            println();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static class UncheckedIOException extends Error {
        static final long serialVersionUID = -4032692679158424751L;

        UncheckedIOException(IOException e) {
            super(e.getMessage(), e);
        }
    }
}
