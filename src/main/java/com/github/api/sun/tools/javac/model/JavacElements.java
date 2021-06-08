package com.github.api.sun.tools.javac.model;

import com.github.api.sun.tools.javac.code.*;
import com.github.api.sun.tools.javac.code.Symbol.*;
import com.github.api.sun.tools.javac.comp.AttrContext;
import com.github.api.sun.tools.javac.comp.Enter;
import com.github.api.sun.tools.javac.comp.Env;
import com.github.api.sun.tools.javac.main.JavaCompiler;
import com.github.api.sun.tools.javac.processing.PrintingProcessor;
import com.github.api.sun.tools.javac.tree.JCTree;
import com.github.api.sun.tools.javac.tree.JCTree.*;
import com.github.api.sun.tools.javac.tree.TreeInfo;
import com.github.api.sun.tools.javac.tree.TreeScanner;
import com.github.api.sun.tools.javac.util.Name;
import com.github.api.sun.tools.javac.util.*;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;
import java.util.Map;

import static com.github.api.sun.tools.javac.code.TypeTag.CLASS;
import static com.github.api.sun.tools.javac.tree.JCTree.Tag.IDENT;
import static com.github.api.sun.tools.javac.tree.JCTree.Tag.NEWARRAY;
import static javax.lang.model.util.ElementFilter.methodsIn;

public class JavacElements implements Elements {
    private JavaCompiler javaCompiler;
    private Symtab syms;
    private Names names;
    private Types types;
    private Enter enter;

    protected JavacElements(Context context) {
        setContext(context);
    }

    public static JavacElements instance(Context context) {
        JavacElements instance = context.get(JavacElements.class);
        if (instance == null)
            instance = new JavacElements(context);
        return instance;
    }

    private static boolean containsAnnoOfType(List<Attribute.Compound> annos,
                                              Type type) {
        for (Attribute.Compound anno : annos) {
            if (anno.type.tsym == type.tsym)
                return true;
        }
        return false;
    }

    private static <T> T cast(Class<T> clazz, Object o) {
        if (!clazz.isInstance(o))
            throw new IllegalArgumentException(o.toString());
        return clazz.cast(o);
    }

    public void setContext(Context context) {
        context.put(JavacElements.class, this);
        javaCompiler = JavaCompiler.instance(context);
        syms = Symtab.instance(context);
        names = Names.instance(context);
        types = Types.instance(context);
        enter = Enter.instance(context);
    }

    public PackageSymbol getPackageElement(CharSequence name) {
        String strName = name.toString();
        if (strName.equals(""))
            return syms.unnamedPackage;
        return SourceVersion.isName(strName)
                ? nameToSymbol(strName, PackageSymbol.class)
                : null;
    }

    public ClassSymbol getTypeElement(CharSequence name) {
        String strName = name.toString();
        return SourceVersion.isName(strName)
                ? nameToSymbol(strName, ClassSymbol.class)
                : null;
    }

    private <S extends Symbol> S nameToSymbol(String nameStr, Class<S> clazz) {
        Name name = names.fromString(nameStr);

        Symbol sym = (clazz == ClassSymbol.class)
                ? syms.classes.get(name)
                : syms.packages.get(name);
        try {
            if (sym == null)
                sym = javaCompiler.resolveIdent(nameStr);
            sym.complete();
            return (sym.kind != Kinds.ERR &&
                    sym.exists() &&
                    clazz.isInstance(sym) &&
                    name.equals(sym.getQualifiedName()))
                    ? clazz.cast(sym)
                    : null;
        } catch (CompletionFailure e) {
            return null;
        }
    }

    public JavacSourcePosition getSourcePosition(Element e) {
        Pair<JCTree, JCCompilationUnit> treeTop = getTreeAndTopLevel(e);
        if (treeTop == null)
            return null;
        JCTree tree = treeTop.fst;
        JCCompilationUnit toplevel = treeTop.snd;
        JavaFileObject sourcefile = toplevel.sourcefile;
        if (sourcefile == null)
            return null;
        return new JavacSourcePosition(sourcefile, tree.pos, toplevel.lineMap);
    }

    public JavacSourcePosition getSourcePosition(Element e, AnnotationMirror a) {
        Pair<JCTree, JCCompilationUnit> treeTop = getTreeAndTopLevel(e);
        if (treeTop == null)
            return null;
        JCTree tree = treeTop.fst;
        JCCompilationUnit toplevel = treeTop.snd;
        JavaFileObject sourcefile = toplevel.sourcefile;
        if (sourcefile == null)
            return null;
        JCTree annoTree = matchAnnoToTree(a, e, tree);
        if (annoTree == null)
            return null;
        return new JavacSourcePosition(sourcefile, annoTree.pos,
                toplevel.lineMap);
    }

    public JavacSourcePosition getSourcePosition(Element e, AnnotationMirror a,
                                                 AnnotationValue v) {

        return getSourcePosition(e, a);
    }

    private JCTree matchAnnoToTree(AnnotationMirror findme,
                                   Element e, JCTree tree) {
        Symbol sym = cast(Symbol.class, e);
        class Vis extends JCTree.Visitor {
            List<JCAnnotation> result = null;

            public void visitTopLevel(JCCompilationUnit tree) {
                result = tree.packageAnnotations;
            }

            public void visitClassDef(JCClassDecl tree) {
                result = tree.mods.annotations;
            }

            public void visitMethodDef(JCMethodDecl tree) {
                result = tree.mods.annotations;
            }

            public void visitVarDef(JCVariableDecl tree) {
                result = tree.mods.annotations;
            }
        }
        Vis vis = new Vis();
        tree.accept(vis);
        if (vis.result == null)
            return null;
        List<Attribute.Compound> annos = sym.getRawAttributes();
        return matchAnnoToTree(cast(Attribute.Compound.class, findme),
                annos,
                vis.result);
    }

    private JCTree matchAnnoToTree(Attribute.Compound findme,
                                   List<Attribute.Compound> annos,
                                   List<JCAnnotation> trees) {
        for (Attribute.Compound anno : annos) {
            for (JCAnnotation tree : trees) {
                JCTree match = matchAnnoToTree(findme, anno, tree);
                if (match != null)
                    return match;
            }
        }
        return null;
    }

    private JCTree matchAnnoToTree(final Attribute.Compound findme,
                                   final Attribute attr,
                                   final JCTree tree) {
        if (attr == findme)
            return (tree.type.tsym == findme.type.tsym) ? tree : null;
        class Vis implements Attribute.Visitor {
            JCTree result = null;

            public void visitConstant(Attribute.Constant value) {
            }

            public void visitClass(Attribute.Class clazz) {
            }

            public void visitCompound(Attribute.Compound anno) {
                for (Pair<MethodSymbol, Attribute> pair : anno.values) {
                    JCExpression expr = scanForAssign(pair.fst, tree);
                    if (expr != null) {
                        JCTree match = matchAnnoToTree(findme, pair.snd, expr);
                        if (match != null) {
                            result = match;
                            return;
                        }
                    }
                }
            }

            public void visitArray(Attribute.Array array) {
                if (tree.hasTag(NEWARRAY) &&
                        types.elemtype(array.type).tsym == findme.type.tsym) {
                    List<JCExpression> elems = ((JCNewArray) tree).elems;
                    for (Attribute value : array.values) {
                        if (value == findme) {
                            result = elems.head;
                            return;
                        }
                        elems = elems.tail;
                    }
                }
            }

            public void visitEnum(Attribute.Enum e) {
            }

            public void visitError(Attribute.Error e) {
            }
        }
        Vis vis = new Vis();
        attr.accept(vis);
        return vis.result;
    }

    private JCExpression scanForAssign(final MethodSymbol sym,
                                       final JCTree tree) {
        class TS extends TreeScanner {
            JCExpression result = null;

            public void scan(JCTree t) {
                if (t != null && result == null)
                    t.accept(this);
            }

            public void visitAnnotation(JCAnnotation t) {
                if (t == tree)
                    scan(t.args);
            }

            public void visitAssign(JCAssign t) {
                if (t.lhs.hasTag(IDENT)) {
                    JCIdent ident = (JCIdent) t.lhs;
                    if (ident.sym == sym)
                        result = t.rhs;
                }
            }
        }
        TS scanner = new TS();
        tree.accept(scanner);
        return scanner.result;
    }

    public JCTree getTree(Element e) {
        Pair<JCTree, ?> treeTop = getTreeAndTopLevel(e);
        return (treeTop != null) ? treeTop.fst : null;
    }

    public String getDocComment(Element e) {


        Pair<JCTree, JCCompilationUnit> treeTop = getTreeAndTopLevel(e);
        if (treeTop == null)
            return null;
        JCTree tree = treeTop.fst;
        JCCompilationUnit toplevel = treeTop.snd;
        if (toplevel.docComments == null)
            return null;
        return toplevel.docComments.getCommentText(tree);
    }

    public PackageElement getPackageOf(Element e) {
        return cast(Symbol.class, e).packge();
    }

    public boolean isDeprecated(Element e) {
        Symbol sym = cast(Symbol.class, e);
        return (sym.flags() & Flags.DEPRECATED) != 0;
    }

    public Name getBinaryName(TypeElement type) {
        return cast(TypeSymbol.class, type).flatName();
    }

    public Map<MethodSymbol, Attribute> getElementValuesWithDefaults(
            AnnotationMirror a) {
        Attribute.Compound anno = cast(Attribute.Compound.class, a);
        DeclaredType annotype = a.getAnnotationType();
        Map<MethodSymbol, Attribute> valmap = anno.getElementValues();
        for (ExecutableElement ex :
                methodsIn(annotype.asElement().getEnclosedElements())) {
            MethodSymbol meth = (MethodSymbol) ex;
            Attribute defaultValue = meth.getDefaultValue();
            if (defaultValue != null && !valmap.containsKey(meth)) {
                valmap.put(meth, defaultValue);
            }
        }
        return valmap;
    }

    public FilteredMemberList getAllMembers(TypeElement element) {
        Symbol sym = cast(Symbol.class, element);
        Scope scope = sym.members().dupUnshared();
        List<Type> closure = types.closure(sym.asType());
        for (Type t : closure)
            addMembers(scope, t);
        return new FilteredMemberList(scope);
    }

    private void addMembers(Scope scope, Type type) {
        members:
        for (Scope.Entry e = type.asElement().members().elems; e != null; e = e.sibling) {
            Scope.Entry overrider = scope.lookup(e.sym.getSimpleName());
            while (overrider.scope != null) {
                if (overrider.sym.kind == e.sym.kind
                        && (overrider.sym.flags() & Flags.SYNTHETIC) == 0) {
                    if (overrider.sym.getKind() == ElementKind.METHOD
                            && overrides((ExecutableElement) overrider.sym, (ExecutableElement) e.sym, (TypeElement) type.asElement())) {
                        continue members;
                    }
                }
                overrider = overrider.next();
            }
            boolean derived = e.sym.getEnclosingElement() != scope.owner;
            ElementKind kind = e.sym.getKind();
            boolean initializer = kind == ElementKind.CONSTRUCTOR
                    || kind == ElementKind.INSTANCE_INIT
                    || kind == ElementKind.STATIC_INIT;
            if (!derived || (!initializer && e.sym.isInheritedIn(scope.owner, types)))
                scope.enter(e.sym);
        }
    }

    @Override
    public List<Attribute.Compound> getAllAnnotationMirrors(Element e) {
        Symbol sym = cast(Symbol.class, e);
        List<Attribute.Compound> annos = sym.getAnnotationMirrors();
        while (sym.getKind() == ElementKind.CLASS) {
            Type sup = ((ClassSymbol) sym).getSuperclass();
            if (!sup.hasTag(CLASS) || sup.isErroneous() ||
                    sup.tsym == syms.objectType.tsym) {
                break;
            }
            sym = sup.tsym;
            List<Attribute.Compound> oldAnnos = annos;
            List<Attribute.Compound> newAnnos = sym.getAnnotationMirrors();
            for (Attribute.Compound anno : newAnnos) {
                if (isInherited(anno.type) &&
                        !containsAnnoOfType(oldAnnos, anno.type)) {
                    annos = annos.prepend(anno);
                }
            }
        }
        return annos;
    }

    private boolean isInherited(Type annotype) {
        return annotype.tsym.attribute(syms.inheritedType.tsym) != null;
    }

    public boolean hides(Element hiderEl, Element hideeEl) {
        Symbol hider = cast(Symbol.class, hiderEl);
        Symbol hidee = cast(Symbol.class, hideeEl);


        if (hider == hidee ||
                hider.kind != hidee.kind ||
                hider.name != hidee.name) {
            return false;
        }


        if (hider.kind == Kinds.MTH) {
            if (!hider.isStatic() ||
                    !types.isSubSignature(hider.type, hidee.type)) {
                return false;
            }
        }


        ClassSymbol hiderClass = hider.owner.enclClass();
        ClassSymbol hideeClass = hidee.owner.enclClass();
        if (hiderClass == null || hideeClass == null ||
                !hiderClass.isSubClass(hideeClass, types)) {
            return false;
        }


        return hidee.isInheritedIn(hiderClass, types);
    }

    public boolean overrides(ExecutableElement riderEl,
                             ExecutableElement rideeEl, TypeElement typeEl) {
        MethodSymbol rider = cast(MethodSymbol.class, riderEl);
        MethodSymbol ridee = cast(MethodSymbol.class, rideeEl);
        ClassSymbol origin = cast(ClassSymbol.class, typeEl);
        return rider.name == ridee.name &&

                rider != ridee &&


                !rider.isStatic() &&

                ridee.isMemberOf(origin, types) &&

                rider.overrides(ridee, origin, types, false);
    }

    public String getConstantExpression(Object value) {
        return Constants.format(value);
    }

    public void printElements(java.io.Writer w, Element... elements) {
        for (Element element : elements)
            (new PrintingProcessor.PrintingElementVisitor(w, this)).visit(element).flush();
    }

    public Name getName(CharSequence cs) {
        return names.fromString(cs.toString());
    }

    @Override
    public boolean isFunctionalInterface(TypeElement element) {
        if (element.getKind() != ElementKind.INTERFACE)
            return false;
        else {
            TypeSymbol tsym = cast(TypeSymbol.class, element);
            return types.isFunctionalInterface(tsym);
        }
    }

    private Pair<JCTree, JCCompilationUnit> getTreeAndTopLevel(Element e) {
        Symbol sym = cast(Symbol.class, e);
        Env<AttrContext> enterEnv = getEnterEnv(sym);
        if (enterEnv == null)
            return null;
        JCTree tree = TreeInfo.declarationFor(sym, enterEnv.tree);
        if (tree == null || enterEnv.toplevel == null)
            return null;
        return new Pair<JCTree, JCCompilationUnit>(tree, enterEnv.toplevel);
    }

    public Pair<JCTree, JCCompilationUnit> getTreeAndTopLevel(
            Element e, AnnotationMirror a, AnnotationValue v) {
        if (e == null)
            return null;
        Pair<JCTree, JCCompilationUnit> elemTreeTop = getTreeAndTopLevel(e);
        if (elemTreeTop == null)
            return null;
        if (a == null)
            return elemTreeTop;
        JCTree annoTree = matchAnnoToTree(a, e, elemTreeTop.fst);
        if (annoTree == null)
            return elemTreeTop;


        return new Pair<JCTree, JCCompilationUnit>(annoTree, elemTreeTop.snd);
    }

    private Env<AttrContext> getEnterEnv(Symbol sym) {


        TypeSymbol ts = (sym.kind != Kinds.PCK)
                ? sym.enclClass()
                : (PackageSymbol) sym;
        return (ts != null)
                ? enter.getEnv(ts)
                : null;
    }
}
