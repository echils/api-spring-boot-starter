package com.sun.tools.javac.processing;

import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.api.MultiTaskListener;
import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.CompletionFailure;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.file.FSInfo;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.jvm.ClassReader.BadClassFile;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.parser.Tokens;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.ServiceLoader;
import com.sun.tools.javac.util.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.ElementScanner8;
import javax.lang.model.util.Elements;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import java.io.Closeable;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

import static com.sun.tools.javac.code.Lint.LintCategory.PROCESSING;
import static com.sun.tools.javac.comp.CompileStates.CompileState;
import static com.sun.tools.javac.main.Option.*;
import static com.sun.tools.javac.util.JCDiagnostic.DiagnosticFlag.RECOVERABLE;
import static javax.tools.StandardLocation.ANNOTATION_PROCESSOR_PATH;
import static javax.tools.StandardLocation.CLASS_PATH;

public class JavacProcessingEnvironment implements ProcessingEnvironment, Closeable {
    public static final Pattern noMatches = Pattern.compile("(\\P{all})+");
    private static final TreeScanner treeCleaner = new TreeScanner() {
        public void scan(JCTree node) {
            super.scan(node);
            if (node != null)
                node.type = null;
        }

        public void visitTopLevel(JCCompilationUnit node) {
            node.packge = null;
            super.visitTopLevel(node);
        }

        public void visitClassDef(JCClassDecl node) {
            node.sym = null;
            super.visitClassDef(node);
        }

        public void visitMethodDef(JCMethodDecl node) {
            node.sym = null;
            super.visitMethodDef(node);
        }

        public void visitVarDef(JCVariableDecl node) {
            node.sym = null;
            super.visitVarDef(node);
        }

        public void visitNewClass(JCNewClass node) {
            node.constructor = null;
            super.visitNewClass(node);
        }

        public void visitAssignop(JCAssignOp node) {
            node.operator = null;
            super.visitAssignop(node);
        }

        public void visitUnary(JCUnary node) {
            node.operator = null;
            super.visitUnary(node);
        }

        public void visitBinary(JCBinary node) {
            node.operator = null;
            super.visitBinary(node);
        }

        public void visitSelect(JCFieldAccess node) {
            node.sym = null;
            super.visitSelect(node);
        }

        public void visitIdent(JCIdent node) {
            node.sym = null;
            super.visitIdent(node);
        }

        public void visitAnnotation(JCAnnotation node) {
            node.attribute = null;
            super.visitAnnotation(node);
        }
    };
    private static final Pattern allMatches = Pattern.compile(".*");
    private final Options options;
    private final boolean printProcessorInfo;
    private final boolean printRounds;
    private final boolean verbose;
    private final boolean lint;
    private final boolean fatalErrors;
    private final boolean werror;
    private final boolean showResolveErrors;
    private final JavacFiler filer;
    private final JavacMessager messager;
    private final JavacElements elementUtils;
    private final JavacTypes typeUtils;
    private final Map<String, String> processorOptions;
    private final Set<String> unmatchedProcessorOptions;
    private final Set<String> platformAnnotations;
    Log log;
    JCDiagnostic.Factory diags;
    Source source;
    private DiscoveredProcessors discoveredProcs;
    private Set<PackageSymbol> specifiedPackages = Collections.emptySet();
    private ClassLoader processorClassLoader;
    private SecurityException processorClassLoaderException;
    private JavacMessages messages;
    private MultiTaskListener taskListener;
    private Context context;

    protected JavacProcessingEnvironment(Context context) {
        this.context = context;
        context.put(JavacProcessingEnvironment.class, this);
        log = Log.instance(context);
        source = Source.instance(context);
        diags = JCDiagnostic.Factory.instance(context);
        options = Options.instance(context);
        printProcessorInfo = options.isSet(XPRINTPROCESSORINFO);
        printRounds = options.isSet(XPRINTROUNDS);
        verbose = options.isSet(VERBOSE);
        lint = Lint.instance(context).isEnabled(PROCESSING);
        if (options.isSet(PROC, "only") || options.isSet(XPRINT)) {
            JavaCompiler compiler = JavaCompiler.instance(context);
            compiler.shouldStopPolicyIfNoError = CompileState.PROCESS;
        }
        fatalErrors = options.isSet("fatalEnterError");
        showResolveErrors = options.isSet("showResolveErrors");
        werror = options.isSet(WERROR);
        platformAnnotations = initPlatformAnnotations();


        filer = new JavacFiler(context);
        messager = new JavacMessager(context, this);
        elementUtils = JavacElements.instance(context);
        typeUtils = JavacTypes.instance(context);
        processorOptions = initProcessorOptions(context);
        unmatchedProcessorOptions = initUnmatchedProcessorOptions();
        messages = JavacMessages.instance(context);
        taskListener = MultiTaskListener.instance(context);
        initProcessorClassLoader();
    }

    public static JavacProcessingEnvironment instance(Context context) {
        JavacProcessingEnvironment instance = context.get(JavacProcessingEnvironment.class);
        if (instance == null)
            instance = new JavacProcessingEnvironment(context);
        return instance;
    }

    private static <T> List<T> join(List<T> list1, List<T> list2) {
        return list1.appendList(list2);
    }

    private static <T extends JCTree> List<T> cleanTrees(List<T> nodes) {
        for (T node : nodes)
            treeCleaner.scan(node);
        return nodes;
    }

    private static Pattern importStringToPattern(String s, Processor p, Log log) {
        if (isValidImportString(s)) {
            return validImportStringToPattern(s);
        } else {
            log.warning("proc.malformed.supported.string", s, p.getClass().getName());
            return noMatches;
        }
    }

    public static boolean isValidImportString(String s) {
        if (s.equals("*"))
            return true;
        boolean valid = true;
        String t = s;
        int index = t.indexOf('*');
        if (index != -1) {

            if (index == t.length() - 1) {

                if (index - 1 >= 0) {
                    valid = t.charAt(index - 1) == '.';

                    t = t.substring(0, t.length() - 2);
                }
            } else
                return false;
        }

        if (valid) {
            String[] javaIds = t.split("\\.", t.length() + 2);
            for (String javaId : javaIds)
                valid &= SourceVersion.isIdentifier(javaId);
        }
        return valid;
    }

    public static Pattern validImportStringToPattern(String s) {
        if (s.equals("*")) {
            return allMatches;
        } else {
            String s_prime = s.replace(".", "\\.");
            if (s_prime.endsWith("*")) {
                s_prime = s_prime.substring(0, s_prime.length() - 1) + ".+";
            }
            return Pattern.compile(s_prime);
        }
    }

    public static boolean isValidOptionName(String optionName) {
        for (String s : optionName.split("\\.", -1)) {
            if (!SourceVersion.isIdentifier(s))
                return false;
        }
        return true;
    }

    public void setProcessors(Iterable<? extends Processor> processors) {
        Assert.checkNull(discoveredProcs);
        initProcessorIterator(context, processors);
    }

    private Set<String> initPlatformAnnotations() {
        Set<String> platformAnnotations = new HashSet<String>();
        platformAnnotations.add("java.lang.Deprecated");
        platformAnnotations.add("java.lang.Override");
        platformAnnotations.add("java.lang.SuppressWarnings");
        platformAnnotations.add("java.lang.annotation.Documented");
        platformAnnotations.add("java.lang.annotation.Inherited");
        platformAnnotations.add("java.lang.annotation.Retention");
        platformAnnotations.add("java.lang.annotation.Target");
        return Collections.unmodifiableSet(platformAnnotations);
    }

    private void initProcessorClassLoader() {
        JavaFileManager fileManager = context.get(JavaFileManager.class);
        try {

            processorClassLoader = fileManager.hasLocation(ANNOTATION_PROCESSOR_PATH)
                    ? fileManager.getClassLoader(ANNOTATION_PROCESSOR_PATH)
                    : fileManager.getClassLoader(CLASS_PATH);
            if (processorClassLoader != null && processorClassLoader instanceof Closeable) {
                JavaCompiler compiler = JavaCompiler.instance(context);
                compiler.closeables = compiler.closeables.prepend((Closeable) processorClassLoader);
            }
        } catch (SecurityException e) {
            processorClassLoaderException = e;
        }
    }

    private void initProcessorIterator(Context context, Iterable<? extends Processor> processors) {
        Log log = Log.instance(context);
        Iterator<? extends Processor> processorIterator;
        if (options.isSet(XPRINT)) {
            try {
                Processor processor = PrintingProcessor.class.newInstance();
                processorIterator = List.of(processor).iterator();
            } catch (Throwable t) {
                AssertionError assertError =
                        new AssertionError("Problem instantiating PrintingProcessor.", t);
                throw assertError;
            }
        } else if (processors != null) {
            processorIterator = processors.iterator();
        } else {
            String processorNames = options.get(PROCESSOR);
            if (processorClassLoaderException == null) {

                if (processorNames != null) {
                    processorIterator = new NameProcessIterator(processorNames, processorClassLoader, log);
                } else {
                    processorIterator = new ServiceIterator(processorClassLoader, log);
                }
            } else {

                processorIterator = handleServiceLoaderUnavailability("proc.cant.create.loader",
                        processorClassLoaderException);
            }
        }
        discoveredProcs = new DiscoveredProcessors(processorIterator);
    }

    private Iterator<Processor> handleServiceLoaderUnavailability(String key, Exception e) {
        JavaFileManager fileManager = context.get(JavaFileManager.class);
        if (fileManager instanceof JavacFileManager) {
            StandardJavaFileManager standardFileManager = (JavacFileManager) fileManager;
            Iterable<? extends File> workingPath = fileManager.hasLocation(ANNOTATION_PROCESSOR_PATH)
                    ? standardFileManager.getLocation(ANNOTATION_PROCESSOR_PATH)
                    : standardFileManager.getLocation(CLASS_PATH);
            if (needClassLoader(options.get(PROCESSOR), workingPath))
                handleException(key, e);
        } else {
            handleException(key, e);
        }
        java.util.List<Processor> pl = Collections.emptyList();
        return pl.iterator();
    }

    private void handleException(String key, Exception e) {
        if (e != null) {
            log.error(key, e.getLocalizedMessage());
            throw new Abort(e);
        } else {
            log.error(key);
            throw new Abort();
        }
    }

    public boolean atLeastOneProcessor() {
        return discoveredProcs.iterator().hasNext();
    }

    private Map<String, String> initProcessorOptions(Context context) {
        Options options = Options.instance(context);
        Set<String> keySet = options.keySet();
        Map<String, String> tempOptions = new LinkedHashMap<String, String>();
        for (String key : keySet) {
            if (key.startsWith("-A") && key.length() > 2) {
                int sepIndex = key.indexOf('=');
                String candidateKey = null;
                String candidateValue = null;
                if (sepIndex == -1)
                    candidateKey = key.substring(2);
                else if (sepIndex >= 3) {
                    candidateKey = key.substring(2, sepIndex);
                    candidateValue = (sepIndex < key.length() - 1) ?
                            key.substring(sepIndex + 1) : null;
                }
                tempOptions.put(candidateKey, candidateValue);
            }
        }
        return Collections.unmodifiableMap(tempOptions);
    }

    private Set<String> initUnmatchedProcessorOptions() {
        Set<String> unmatchedProcessorOptions = new HashSet<String>();
        unmatchedProcessorOptions.addAll(processorOptions.keySet());
        return unmatchedProcessorOptions;
    }

    private void discoverAndRunProcs(Context context,
                                     Set<TypeElement> annotationsPresent,
                                     List<ClassSymbol> topLevelClasses,
                                     List<PackageSymbol> packageInfoFiles) {
        Map<String, TypeElement> unmatchedAnnotations =
                new HashMap<String, TypeElement>(annotationsPresent.size());
        for (TypeElement a : annotationsPresent) {
            unmatchedAnnotations.put(a.getQualifiedName().toString(),
                    a);
        }

        if (unmatchedAnnotations.size() == 0)
            unmatchedAnnotations.put("", null);
        DiscoveredProcessors.ProcessorStateIterator psi = discoveredProcs.iterator();


        Set<Element> rootElements = new LinkedHashSet<Element>();
        rootElements.addAll(topLevelClasses);
        rootElements.addAll(packageInfoFiles);
        rootElements = Collections.unmodifiableSet(rootElements);
        RoundEnvironment renv = new JavacRoundEnvironment(false,
                false,
                rootElements,
                JavacProcessingEnvironment.this);
        while (unmatchedAnnotations.size() > 0 && psi.hasNext()) {
            ProcessorState ps = psi.next();
            Set<String> matchedNames = new HashSet<String>();
            Set<TypeElement> typeElements = new LinkedHashSet<TypeElement>();
            for (Map.Entry<String, TypeElement> entry : unmatchedAnnotations.entrySet()) {
                String unmatchedAnnotationName = entry.getKey();
                if (ps.annotationSupported(unmatchedAnnotationName)) {
                    matchedNames.add(unmatchedAnnotationName);
                    TypeElement te = entry.getValue();
                    if (te != null)
                        typeElements.add(te);
                }
            }
            if (matchedNames.size() > 0 || ps.contributed) {
                boolean processingResult = callProcessor(ps.processor, typeElements, renv);
                ps.contributed = true;
                ps.removeSupportedOptions(unmatchedProcessorOptions);
                if (printProcessorInfo || verbose) {
                    log.printLines("x.print.processor.info",
                            ps.processor.getClass().getName(),
                            matchedNames.toString(),
                            processingResult);
                }
                if (processingResult) {
                    unmatchedAnnotations.keySet().removeAll(matchedNames);
                }
            }
        }
        unmatchedAnnotations.remove("");
        if (lint && unmatchedAnnotations.size() > 0) {

            unmatchedAnnotations.keySet().removeAll(platformAnnotations);
            if (unmatchedAnnotations.size() > 0) {
                log = Log.instance(context);
                log.warning("proc.annotations.without.processors",
                        unmatchedAnnotations.keySet());
            }
        }

        psi.runContributingProcs(renv);

        if (options.isSet("displayFilerState"))
            filer.displayState();
    }

    private boolean callProcessor(Processor proc,
                                  Set<? extends TypeElement> tes,
                                  RoundEnvironment renv) {
        try {
            return proc.process(tes, renv);
        } catch (BadClassFile ex) {
            log.error("proc.cant.access.1", ex.sym, ex.getDetailValue());
            return false;
        } catch (CompletionFailure ex) {
            StringWriter out = new StringWriter();
            ex.printStackTrace(new PrintWriter(out));
            log.error("proc.cant.access", ex.sym, ex.getDetailValue(), out.toString());
            return false;
        } catch (ClientCodeException e) {
            throw e;
        } catch (Throwable t) {
            throw new AnnotationProcessingError(t);
        }
    }

    public JavaCompiler doProcessing(Context context,
                                     List<JCCompilationUnit> roots,
                                     List<ClassSymbol> classSymbols,
                                     Iterable<? extends PackageSymbol> pckSymbols,
                                     Log.DeferredDiagnosticHandler deferredDiagnosticHandler) {
        log = Log.instance(context);
        Set<PackageSymbol> specifiedPackages = new LinkedHashSet<PackageSymbol>();
        for (PackageSymbol psym : pckSymbols)
            specifiedPackages.add(psym);
        this.specifiedPackages = Collections.unmodifiableSet(specifiedPackages);
        Round round = new Round(context, roots, classSymbols, deferredDiagnosticHandler);
        boolean errorStatus;
        boolean moreToDo;
        do {

            round.run(false, false);


            errorStatus = round.unrecoverableError();
            moreToDo = moreToDo();
            round.showDiagnostics(errorStatus || showResolveErrors);


            round = round.next(
                    new LinkedHashSet<JavaFileObject>(filer.getGeneratedSourceFileObjects()),
                    new LinkedHashMap<String, JavaFileObject>(filer.getGeneratedClasses()));

            if (round.unrecoverableError())
                errorStatus = true;
        } while (moreToDo && !errorStatus);

        round.run(true, errorStatus);
        round.showDiagnostics(true);
        filer.warnIfUnclosedFiles();
        warnIfUnmatchedOptions();

        if (messager.errorRaised()
                || werror && round.warningCount() > 0 && round.errorCount() > 0)
            errorStatus = true;
        Set<JavaFileObject> newSourceFiles =
                new LinkedHashSet<JavaFileObject>(filer.getGeneratedSourceFileObjects());
        roots = cleanTrees(round.roots);
        JavaCompiler compiler = round.finalCompiler();
        if (newSourceFiles.size() > 0)
            roots = roots.appendList(compiler.parseFiles(newSourceFiles));
        errorStatus = errorStatus || (compiler.errorCount() > 0);

        this.close();
        if (!taskListener.isEmpty())
            taskListener.finished(new TaskEvent(TaskEvent.Kind.ANNOTATION_PROCESSING));
        if (errorStatus) {
            if (compiler.errorCount() == 0)
                compiler.log.nerrors++;
            return compiler;
        }
        compiler.enterTreesIfNeeded(roots);
        return compiler;
    }

    private void warnIfUnmatchedOptions() {
        if (!unmatchedProcessorOptions.isEmpty()) {
            log.warning("proc.unmatched.processor.options", unmatchedProcessorOptions.toString());
        }
    }

    public void close() {
        filer.close();
        if (discoveredProcs != null)
            discoveredProcs.close();
        discoveredProcs = null;
    }

    private List<ClassSymbol> getTopLevelClasses(List<? extends JCCompilationUnit> units) {
        List<ClassSymbol> classes = List.nil();
        for (JCCompilationUnit unit : units) {
            for (JCTree node : unit.defs) {
                if (node.hasTag(Tag.CLASSDEF)) {
                    ClassSymbol sym = ((JCClassDecl) node).sym;
                    Assert.checkNonNull(sym);
                    classes = classes.prepend(sym);
                }
            }
        }
        return classes.reverse();
    }

    private List<ClassSymbol> getTopLevelClassesFromClasses(List<? extends ClassSymbol> syms) {
        List<ClassSymbol> classes = List.nil();
        for (ClassSymbol sym : syms) {
            if (!isPkgInfo(sym)) {
                classes = classes.prepend(sym);
            }
        }
        return classes.reverse();
    }

    private List<PackageSymbol> getPackageInfoFiles(List<? extends JCCompilationUnit> units) {
        List<PackageSymbol> packages = List.nil();
        for (JCCompilationUnit unit : units) {
            if (isPkgInfo(unit.sourcefile, JavaFileObject.Kind.SOURCE)) {
                packages = packages.prepend(unit.packge);
            }
        }
        return packages.reverse();
    }

    private List<PackageSymbol> getPackageInfoFilesFromClasses(List<? extends ClassSymbol> syms) {
        List<PackageSymbol> packages = List.nil();
        for (ClassSymbol sym : syms) {
            if (isPkgInfo(sym)) {
                packages = packages.prepend((PackageSymbol) sym.owner);
            }
        }
        return packages.reverse();
    }

    private boolean isPkgInfo(JavaFileObject fo, JavaFileObject.Kind kind) {
        return fo.isNameCompatible("package-info", kind);
    }

    private boolean isPkgInfo(ClassSymbol sym) {
        return isPkgInfo(sym.classfile, JavaFileObject.Kind.CLASS) && (sym.packge().package_info == sym);
    }

    private boolean needClassLoader(String procNames, Iterable<? extends File> workingpath) {
        if (procNames != null)
            return true;
        URL[] urls = new URL[1];
        for (File pathElement : workingpath) {
            try {
                urls[0] = pathElement.toURI().toURL();
                if (ServiceProxy.hasService(Processor.class, urls))
                    return true;
            } catch (MalformedURLException ex) {
                throw new AssertionError(ex);
            } catch (ServiceProxy.ServiceConfigurationError e) {
                log.error("proc.bad.config.file", e.getLocalizedMessage());
                return true;
            }
        }
        return false;
    }

    private boolean moreToDo() {
        return filer.newFiles();
    }

    public Map<String, String> getOptions() {
        return processorOptions;
    }

    public Messager getMessager() {
        return messager;
    }

    public Filer getFiler() {
        return filer;
    }

    public JavacElements getElementUtils() {
        return elementUtils;
    }

    public JavacTypes getTypeUtils() {
        return typeUtils;
    }

    public SourceVersion getSourceVersion() {
        return Source.toSourceVersion(source);
    }

    public Locale getLocale() {
        return messages.getCurrentLocale();
    }

    public Set<PackageSymbol> getSpecifiedPackages() {
        return specifiedPackages;
    }

    public Context getContext() {
        return context;
    }

    public ClassLoader getProcessorClassLoader() {
        return processorClassLoader;
    }

    public String toString() {
        return "javac ProcessingEnvironment";
    }

    private static class NameProcessIterator implements Iterator<Processor> {
        Processor nextProc = null;
        Iterator<String> names;
        ClassLoader processorCL;
        Log log;

        NameProcessIterator(String names, ClassLoader processorCL, Log log) {
            this.names = Arrays.asList(names.split(",")).iterator();
            this.processorCL = processorCL;
            this.log = log;
        }

        public boolean hasNext() {
            if (nextProc != null)
                return true;
            else {
                if (!names.hasNext())
                    return false;
                else {
                    String processorName = names.next();
                    Processor processor;
                    try {
                        try {
                            processor =
                                    (Processor) (processorCL.loadClass(processorName).newInstance());
                        } catch (ClassNotFoundException cnfe) {
                            log.error("proc.processor.not.found", processorName);
                            return false;
                        } catch (ClassCastException cce) {
                            log.error("proc.processor.wrong.type", processorName);
                            return false;
                        } catch (Exception e) {
                            log.error("proc.processor.cant.instantiate", processorName);
                            return false;
                        }
                    } catch (ClientCodeException e) {
                        throw e;
                    } catch (Throwable t) {
                        throw new AnnotationProcessingError(t);
                    }
                    nextProc = processor;
                    return true;
                }
            }
        }

        public Processor next() {
            if (hasNext()) {
                Processor p = nextProc;
                nextProc = null;
                return p;
            } else
                throw new NoSuchElementException();
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    static class ProcessorState {
        public Processor processor;
        public boolean contributed;
        private ArrayList<Pattern> supportedAnnotationPatterns;
        private ArrayList<String> supportedOptionNames;

        ProcessorState(Processor p, Log log, Source source, ProcessingEnvironment env) {
            processor = p;
            contributed = false;
            try {
                processor.init(env);
                checkSourceVersionCompatibility(source, log);
                supportedAnnotationPatterns = new ArrayList<Pattern>();
                for (String importString : processor.getSupportedAnnotationTypes()) {
                    supportedAnnotationPatterns.add(importStringToPattern(importString,
                            processor,
                            log));
                }
                supportedOptionNames = new ArrayList<String>();
                for (String optionName : processor.getSupportedOptions()) {
                    if (checkOptionName(optionName, log))
                        supportedOptionNames.add(optionName);
                }
            } catch (ClientCodeException e) {
                throw e;
            } catch (Throwable t) {
                throw new AnnotationProcessingError(t);
            }
        }

        private void checkSourceVersionCompatibility(Source source, Log log) {
            SourceVersion procSourceVersion = processor.getSupportedSourceVersion();
            if (procSourceVersion.compareTo(Source.toSourceVersion(source)) < 0) {
                log.warning("proc.processor.incompatible.source.version",
                        procSourceVersion,
                        processor.getClass().getName(),
                        source.name);
            }
        }

        private boolean checkOptionName(String optionName, Log log) {
            boolean valid = isValidOptionName(optionName);
            if (!valid)
                log.error("proc.processor.bad.option.name",
                        optionName,
                        processor.getClass().getName());
            return valid;
        }

        public boolean annotationSupported(String annotationName) {
            for (Pattern p : supportedAnnotationPatterns) {
                if (p.matcher(annotationName).matches())
                    return true;
            }
            return false;
        }

        public void removeSupportedOptions(Set<String> unmatchedProcessorOptions) {
            unmatchedProcessorOptions.removeAll(supportedOptionNames);
        }
    }

    public static class ComputeAnnotationSet extends
            ElementScanner8<Set<TypeElement>, Set<TypeElement>> {
        final Elements elements;

        public ComputeAnnotationSet(Elements elements) {
            super();
            this.elements = elements;
        }

        @Override
        public Set<TypeElement> visitPackage(PackageElement e, Set<TypeElement> p) {

            return p;
        }

        @Override
        public Set<TypeElement> visitType(TypeElement e, Set<TypeElement> p) {

            scan(e.getTypeParameters(), p);
            return scan(e.getEnclosedElements(), p);
        }

        @Override
        public Set<TypeElement> visitExecutable(ExecutableElement e, Set<TypeElement> p) {

            scan(e.getTypeParameters(), p);
            return scan(e.getEnclosedElements(), p);
        }

        void addAnnotations(Element e, Set<TypeElement> p) {
            for (AnnotationMirror annotationMirror :
                    elements.getAllAnnotationMirrors(e)) {
                Element e2 = annotationMirror.getAnnotationType().asElement();
                p.add((TypeElement) e2);
            }
        }

        @Override
        public Set<TypeElement> scan(Element e, Set<TypeElement> p) {
            addAnnotations(e, p);
            return super.scan(e, p);
        }
    }

    private class ServiceIterator implements Iterator<Processor> {
        private Iterator<Processor> iterator;
        private Log log;
        private ServiceLoader<Processor> loader;

        ServiceIterator(ClassLoader classLoader, Log log) {
            this.log = log;
            try {
                try {
                    loader = ServiceLoader.load(Processor.class, classLoader);
                    this.iterator = loader.iterator();
                } catch (Exception e) {

                    this.iterator = handleServiceLoaderUnavailability("proc.no.service", null);
                }
            } catch (Throwable t) {
                log.error("proc.service.problem");
                throw new Abort(t);
            }
        }

        public boolean hasNext() {
            try {
                return iterator.hasNext();
            } catch (ServiceConfigurationError sce) {
                log.error("proc.bad.config.file", sce.getLocalizedMessage());
                throw new Abort(sce);
            } catch (Throwable t) {
                throw new Abort(t);
            }
        }

        public Processor next() {
            try {
                return iterator.next();
            } catch (ServiceConfigurationError sce) {
                log.error("proc.bad.config.file", sce.getLocalizedMessage());
                throw new Abort(sce);
            } catch (Throwable t) {
                throw new Abort(t);
            }
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

        public void close() {
            if (loader != null) {
                try {
                    loader.reload();
                } catch (Exception e) {
                }
            }
        }
    }

    class DiscoveredProcessors implements Iterable<ProcessorState> {
        Iterator<? extends Processor> processorIterator;
        ArrayList<ProcessorState> procStateList;
        DiscoveredProcessors(Iterator<? extends Processor> processorIterator) {
            this.processorIterator = processorIterator;
            this.procStateList = new ArrayList<ProcessorState>();
        }

        public ProcessorStateIterator iterator() {
            return new ProcessorStateIterator(this);
        }

        public void close() {
            if (processorIterator != null &&
                    processorIterator instanceof ServiceIterator) {
                ((ServiceIterator) processorIterator).close();
            }
        }

        class ProcessorStateIterator implements Iterator<ProcessorState> {
            DiscoveredProcessors psi;
            Iterator<ProcessorState> innerIter;
            boolean onProcInterator;

            ProcessorStateIterator(DiscoveredProcessors psi) {
                this.psi = psi;
                this.innerIter = psi.procStateList.iterator();
                this.onProcInterator = false;
            }

            public ProcessorState next() {
                if (!onProcInterator) {
                    if (innerIter.hasNext())
                        return innerIter.next();
                    else
                        onProcInterator = true;
                }
                if (psi.processorIterator.hasNext()) {
                    ProcessorState ps = new ProcessorState(psi.processorIterator.next(),
                            log, source, JavacProcessingEnvironment.this);
                    psi.procStateList.add(ps);
                    return ps;
                } else
                    throw new NoSuchElementException();
            }

            public boolean hasNext() {
                if (onProcInterator)
                    return psi.processorIterator.hasNext();
                else
                    return innerIter.hasNext() || psi.processorIterator.hasNext();
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }

            public void runContributingProcs(RoundEnvironment re) {
                if (!onProcInterator) {
                    Set<TypeElement> emptyTypeElements = Collections.emptySet();
                    while (innerIter.hasNext()) {
                        ProcessorState ps = innerIter.next();
                        if (ps.contributed)
                            callProcessor(ps.processor, emptyTypeElements, re);
                    }
                }
            }
        }
    }

    class Round {

        final int number;

        final Context context;

        final JavaCompiler compiler;

        final Log log;

        final Log.DeferredDiagnosticHandler deferredDiagnosticHandler;

        List<JCCompilationUnit> roots;

        Map<String, JavaFileObject> genClassFiles;

        Set<TypeElement> annotationsPresent;

        List<ClassSymbol> topLevelClasses;

        List<PackageSymbol> packageInfoFiles;

        private Round(Context context, int number, int priorErrors, int priorWarnings,
                      Log.DeferredDiagnosticHandler deferredDiagnosticHandler) {
            this.context = context;
            this.number = number;
            compiler = JavaCompiler.instance(context);
            log = Log.instance(context);
            log.nerrors = priorErrors;
            log.nwarnings = priorWarnings;
            if (number == 1) {
                Assert.checkNonNull(deferredDiagnosticHandler);
                this.deferredDiagnosticHandler = deferredDiagnosticHandler;
            } else {
                this.deferredDiagnosticHandler = new Log.DeferredDiagnosticHandler(log);
            }

            JavacProcessingEnvironment.this.context = context;

            topLevelClasses = List.nil();
            packageInfoFiles = List.nil();
        }

        Round(Context context, List<JCCompilationUnit> roots, List<ClassSymbol> classSymbols,
              Log.DeferredDiagnosticHandler deferredDiagnosticHandler) {
            this(context, 1, 0, 0, deferredDiagnosticHandler);
            this.roots = roots;
            genClassFiles = new HashMap<String, JavaFileObject>();
            compiler.todo.clear();


            topLevelClasses =
                    getTopLevelClasses(roots).prependList(classSymbols.reverse());
            packageInfoFiles = getPackageInfoFiles(roots);
            findAnnotationsPresent();
        }

        private Round(Round prev,
                      Set<JavaFileObject> newSourceFiles, Map<String, JavaFileObject> newClassFiles) {
            this(prev.nextContext(),
                    prev.number + 1,
                    prev.compiler.log.nerrors,
                    prev.compiler.log.nwarnings,
                    null);
            this.genClassFiles = prev.genClassFiles;
            List<JCCompilationUnit> parsedFiles = compiler.parseFiles(newSourceFiles);
            roots = cleanTrees(prev.roots).appendList(parsedFiles);

            if (unrecoverableError())
                return;
            enterClassFiles(genClassFiles);
            List<ClassSymbol> newClasses = enterClassFiles(newClassFiles);
            genClassFiles.putAll(newClassFiles);
            enterTrees(roots);
            if (unrecoverableError())
                return;
            topLevelClasses = join(
                    getTopLevelClasses(parsedFiles),
                    getTopLevelClassesFromClasses(newClasses));
            packageInfoFiles = join(
                    getPackageInfoFiles(parsedFiles),
                    getPackageInfoFilesFromClasses(newClasses));
            findAnnotationsPresent();
        }

        Round next(Set<JavaFileObject> newSourceFiles, Map<String, JavaFileObject> newClassFiles) {
            try {
                return new Round(this, newSourceFiles, newClassFiles);
            } finally {
                compiler.close(false);
            }
        }

        JavaCompiler finalCompiler() {
            try {
                Context nextCtx = nextContext();
                JavacProcessingEnvironment.this.context = nextCtx;
                JavaCompiler c = JavaCompiler.instance(nextCtx);
                c.log.initRound(compiler.log);
                return c;
            } finally {
                compiler.close(false);
            }
        }

        int errorCount() {
            return compiler.errorCount();
        }

        int warningCount() {
            return compiler.warningCount();
        }

        boolean unrecoverableError() {
            if (messager.errorRaised())
                return true;
            for (JCDiagnostic d : deferredDiagnosticHandler.getDiagnostics()) {
                switch (d.getKind()) {
                    case WARNING:
                        if (werror)
                            return true;
                        break;
                    case ERROR:
                        if (fatalErrors || !d.isFlagSet(RECOVERABLE))
                            return true;
                        break;
                }
            }
            return false;
        }

        void findAnnotationsPresent() {
            ComputeAnnotationSet annotationComputer = new ComputeAnnotationSet(elementUtils);

            annotationsPresent = new LinkedHashSet<TypeElement>();
            for (ClassSymbol classSym : topLevelClasses)
                annotationComputer.scan(classSym, annotationsPresent);
            for (PackageSymbol pkgSym : packageInfoFiles)
                annotationComputer.scan(pkgSym, annotationsPresent);
        }

        private List<ClassSymbol> enterClassFiles(Map<String, JavaFileObject> classFiles) {
            ClassReader reader = ClassReader.instance(context);
            Names names = Names.instance(context);
            List<ClassSymbol> list = List.nil();
            for (Map.Entry<String, JavaFileObject> entry : classFiles.entrySet()) {
                Name name = names.fromString(entry.getKey());
                JavaFileObject file = entry.getValue();
                if (file.getKind() != JavaFileObject.Kind.CLASS)
                    throw new AssertionError(file);
                ClassSymbol cs;
                if (isPkgInfo(file, JavaFileObject.Kind.CLASS)) {
                    Name packageName = Convert.packagePart(name);
                    PackageSymbol p = reader.enterPackage(packageName);
                    if (p.package_info == null)
                        p.package_info = reader.enterClass(Convert.shortName(name), p);
                    cs = p.package_info;
                    if (cs.classfile == null)
                        cs.classfile = file;
                } else
                    cs = reader.enterClass(name, file);
                list = list.prepend(cs);
            }
            return list.reverse();
        }

        private void enterTrees(List<JCCompilationUnit> roots) {
            compiler.enterTrees(roots);
        }

        void run(boolean lastRound, boolean errorStatus) {
            printRoundInfo(lastRound);
            if (!taskListener.isEmpty())
                taskListener.started(new TaskEvent(TaskEvent.Kind.ANNOTATION_PROCESSING_ROUND));
            try {
                if (lastRound) {
                    filer.setLastRound(true);
                    Set<Element> emptyRootElements = Collections.emptySet();
                    RoundEnvironment renv = new JavacRoundEnvironment(true,
                            errorStatus,
                            emptyRootElements,
                            JavacProcessingEnvironment.this);
                    discoveredProcs.iterator().runContributingProcs(renv);
                } else {
                    discoverAndRunProcs(context, annotationsPresent, topLevelClasses, packageInfoFiles);
                }
            } catch (Throwable t) {


                deferredDiagnosticHandler.reportDeferredDiagnostics();
                log.popDiagnosticHandler(deferredDiagnosticHandler);
                throw t;
            } finally {
                if (!taskListener.isEmpty())
                    taskListener.finished(new TaskEvent(TaskEvent.Kind.ANNOTATION_PROCESSING_ROUND));
            }
        }

        void showDiagnostics(boolean showAll) {
            Set<JCDiagnostic.Kind> kinds = EnumSet.allOf(JCDiagnostic.Kind.class);
            if (!showAll) {

                kinds.remove(JCDiagnostic.Kind.ERROR);
            }
            deferredDiagnosticHandler.reportDeferredDiagnostics(kinds);
            log.popDiagnosticHandler(deferredDiagnosticHandler);
        }

        private void printRoundInfo(boolean lastRound) {
            if (printRounds || verbose) {
                List<ClassSymbol> tlc = lastRound ? List.nil() : topLevelClasses;
                Set<TypeElement> ap = lastRound ? Collections.emptySet() : annotationsPresent;
                log.printLines("x.print.rounds",
                        number,
                        "{" + tlc.toString(", ") + "}",
                        ap,
                        lastRound);
            }
        }

        private Context nextContext() {
            Context next = new Context(context);
            Options options = Options.instance(context);
            Assert.checkNonNull(options);
            next.put(Options.optionsKey, options);
            Locale locale = context.get(Locale.class);
            if (locale != null)
                next.put(Locale.class, locale);
            Assert.checkNonNull(messages);
            next.put(JavacMessages.messagesKey, messages);
            final boolean shareNames = true;
            if (shareNames) {
                Names names = Names.instance(context);
                Assert.checkNonNull(names);
                next.put(Names.namesKey, names);
            }
            DiagnosticListener<?> dl = context.get(DiagnosticListener.class);
            if (dl != null)
                next.put(DiagnosticListener.class, dl);
            MultiTaskListener mtl = context.get(MultiTaskListener.taskListenerKey);
            if (mtl != null)
                next.put(MultiTaskListener.taskListenerKey, mtl);
            FSInfo fsInfo = context.get(FSInfo.class);
            if (fsInfo != null)
                next.put(FSInfo.class, fsInfo);
            JavaFileManager jfm = context.get(JavaFileManager.class);
            Assert.checkNonNull(jfm);
            next.put(JavaFileManager.class, jfm);
            if (jfm instanceof JavacFileManager) {
                ((JavacFileManager) jfm).setContext(next);
            }
            Names names = Names.instance(context);
            Assert.checkNonNull(names);
            next.put(Names.namesKey, names);
            Tokens tokens = Tokens.instance(context);
            Assert.checkNonNull(tokens);
            next.put(Tokens.tokensKey, tokens);
            Log nextLog = Log.instance(next);
            nextLog.initRound(log);
            JavaCompiler oldCompiler = JavaCompiler.instance(context);
            JavaCompiler nextCompiler = JavaCompiler.instance(next);
            nextCompiler.initRound(oldCompiler);
            filer.newRound(next);
            messager.newRound(next);
            elementUtils.setContext(next);
            typeUtils.setContext(next);
            JavacTask task = context.get(JavacTask.class);
            if (task != null) {
                next.put(JavacTask.class, task);
                if (task instanceof BasicJavacTask)
                    ((BasicJavacTask) task).updateContext(next);
            }
            JavacTrees trees = context.get(JavacTrees.class);
            if (trees != null) {
                next.put(JavacTrees.class, trees);
                trees.updateContext(next);
            }
            context.clear();
            return next;
        }
    }
}
