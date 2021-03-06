package com.github.api.sun.tools.doclint;

import com.github.api.sun.source.doctree.DocCommentTree;
import com.github.api.sun.source.tree.*;
import com.github.api.sun.source.util.*;
import com.github.api.sun.tools.javac.api.JavacTaskImpl;
import com.github.api.sun.tools.javac.api.JavacTool;
import com.github.api.sun.tools.javac.file.JavacFileManager;
import com.github.api.sun.tools.javac.main.JavaCompiler;
import com.github.api.sun.tools.javac.util.Context;

import javax.lang.model.element.Name;
import javax.tools.StandardLocation;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class DocLint implements Plugin {
    public static final String XMSGS_OPTION = "-Xmsgs";
    public static final String XMSGS_CUSTOM_PREFIX = "-Xmsgs:";
    public static final String XIMPLICIT_HEADERS = "-XimplicitHeaders:";
    public static final String XCUSTOM_TAGS_PREFIX = "-XcustomTags:";
    public static final String TAGS_SEPARATOR = ",";
    private static final String STATS = "-stats";
    List<File> javacBootClassPath;
    List<File> javacClassPath;
    List<File> javacSourcePath;
    List<String> javacOpts;
    List<File> javacFiles;
    boolean needHelp = false;
    Env env;
    Checker checker;

    public static void main(String... args) {
        DocLint dl = new DocLint();
        try {
            dl.run(args);
        } catch (BadArgs e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println(dl.localize("dc.main.ioerror", e.getLocalizedMessage()));
            System.exit(2);
        }
    }

    public static boolean isValidOption(String opt) {
        if (opt.equals(XMSGS_OPTION))
            return true;
        if (opt.startsWith(XMSGS_CUSTOM_PREFIX))
            return Messages.Options.isValidOptions(opt.substring(XMSGS_CUSTOM_PREFIX.length()));
        return false;
    }

    public void run(String... args) throws BadArgs, IOException {
        PrintWriter out = new PrintWriter(System.out);
        try {
            run(out, args);
        } finally {
            out.flush();
        }
    }

    public void run(PrintWriter out, String... args) throws BadArgs, IOException {
        env = new Env();
        processArgs(args);
        if (needHelp)
            showHelp(out);
        if (javacFiles.isEmpty()) {
            if (!needHelp)
                out.println(localize("dc.main.no.files.given"));
        }
        JavacTool tool = JavacTool.create();
        JavacFileManager fm = new JavacFileManager(new Context(), false, null);
        fm.setSymbolFileEnabled(false);
        fm.setLocation(StandardLocation.PLATFORM_CLASS_PATH, javacBootClassPath);
        fm.setLocation(StandardLocation.CLASS_PATH, javacClassPath);
        fm.setLocation(StandardLocation.SOURCE_PATH, javacSourcePath);
        JavacTask task = tool.getTask(out, fm, null, javacOpts, null,
                fm.getJavaFileObjectsFromFiles(javacFiles));
        Iterable<? extends CompilationUnitTree> units = task.parse();
        ((JavacTaskImpl) task).enter();
        env.init(task);
        checker = new Checker(env);
        DeclScanner ds = new DeclScanner() {
            @Override
            void visitDecl(Tree tree, Name name) {
                TreePath p = getCurrentPath();
                DocCommentTree dc = env.trees.getDocCommentTree(p);
                checker.scan(dc, p);
            }
        };
        ds.scan(units, null);
        reportStats(out);
        Context ctx = ((JavacTaskImpl) task).getContext();
        JavaCompiler c = JavaCompiler.instance(ctx);
        c.printCount("error", c.errorCount());
        c.printCount("warn", c.warningCount());
    }

    void processArgs(String... args) throws BadArgs {
        javacOpts = new ArrayList<>();
        javacFiles = new ArrayList<>();
        if (args.length == 0)
            needHelp = true;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.matches("-Xmax(errs|warns)") && i + 1 < args.length) {
                if (args[++i].matches("[0-9]+")) {
                    javacOpts.add(arg);
                    javacOpts.add(args[i]);
                } else {
                    throw new BadArgs("dc.bad.value.for.option", arg, args[i]);
                }
            } else if (arg.equals(STATS)) {
                env.messages.setStatsEnabled(true);
            } else if (arg.equals("-bootclasspath") && i + 1 < args.length) {
                javacBootClassPath = splitPath(args[++i]);
            } else if (arg.equals("-classpath") && i + 1 < args.length) {
                javacClassPath = splitPath(args[++i]);
            } else if (arg.equals("-cp") && i + 1 < args.length) {
                javacClassPath = splitPath(args[++i]);
            } else if (arg.equals("-sourcepath") && i + 1 < args.length) {
                javacSourcePath = splitPath(args[++i]);
            } else if (arg.equals(XMSGS_OPTION)) {
                env.messages.setOptions(null);
            } else if (arg.startsWith(XMSGS_CUSTOM_PREFIX)) {
                env.messages.setOptions(arg.substring(arg.indexOf(":") + 1));
            } else if (arg.startsWith(XCUSTOM_TAGS_PREFIX)) {
                env.setCustomTags(arg.substring(arg.indexOf(":") + 1));
            } else if (arg.equals("-h") || arg.equals("-help") || arg.equals("--help")
                    || arg.equals("-?") || arg.equals("-usage")) {
                needHelp = true;
            } else if (arg.startsWith("-")) {
                throw new BadArgs("dc.bad.option", arg);
            } else {
                while (i < args.length)
                    javacFiles.add(new File(args[i++]));
            }
        }
    }

    void showHelp(PrintWriter out) {
        String msg = localize("dc.main.usage");
        for (String line : msg.split("\n"))
            out.println(line);
    }

    List<File> splitPath(String path) {
        List<File> files = new ArrayList<>();
        for (String f : path.split(File.pathSeparator)) {
            if (f.length() > 0)
                files.add(new File(f));
        }
        return files;
    }

    @Override
    public String getName() {
        return "doclint";
    }

    @Override
    public void init(JavacTask task, String... args) {
        init(task, args, true);
    }

    public void init(JavacTask task, String[] args, boolean addTaskListener) {
        env = new Env();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals(XMSGS_OPTION)) {
                env.messages.setOptions(null);
            } else if (arg.startsWith(XMSGS_CUSTOM_PREFIX)) {
                env.messages.setOptions(arg.substring(arg.indexOf(":") + 1));
            } else if (arg.matches(XIMPLICIT_HEADERS + "[1-6]")) {
                char ch = arg.charAt(arg.length() - 1);
                env.setImplicitHeaders(Character.digit(ch, 10));
            } else if (arg.startsWith(XCUSTOM_TAGS_PREFIX)) {
                env.setCustomTags(arg.substring(arg.indexOf(":") + 1));
            } else
                throw new IllegalArgumentException(arg);
        }
        env.init(task);
        checker = new Checker(env);
        if (addTaskListener) {
            final DeclScanner ds = new DeclScanner() {
                @Override
                void visitDecl(Tree tree, Name name) {
                    TreePath p = getCurrentPath();
                    DocCommentTree dc = env.trees.getDocCommentTree(p);
                    checker.scan(dc, p);
                }
            };
            TaskListener tl = new TaskListener() {
                Queue<CompilationUnitTree> todo = new LinkedList<CompilationUnitTree>();

                @Override
                public void started(TaskEvent e) {
                    switch (e.getKind()) {
                        case ANALYZE:
                            CompilationUnitTree tree;
                            while ((tree = todo.poll()) != null)
                                ds.scan(tree, null);
                            break;
                    }
                }

                @Override
                public void finished(TaskEvent e) {
                    switch (e.getKind()) {
                        case PARSE:
                            todo.add(e.getCompilationUnit());
                            break;
                    }
                }
            };
            task.addTaskListener(tl);
        }
    }

    public void scan(TreePath p) {
        DocCommentTree dc = env.trees.getDocCommentTree(p);
        checker.scan(dc, p);
    }

    public void reportStats(PrintWriter out) {
        env.messages.reportStats(out);
    }

    private String localize(String code, Object... args) {
        Messages m = (env != null) ? env.messages : new Messages(null);
        return m.localize(code, args);
    }

    static abstract class DeclScanner extends TreePathScanner<Void, Void> {
        abstract void visitDecl(Tree tree, Name name);

        @Override
        public Void visitCompilationUnit(CompilationUnitTree tree, Void ignore) {
            if (tree.getPackageName() != null) {
                visitDecl(tree, null);
            }
            return super.visitCompilationUnit(tree, ignore);
        }

        @Override
        public Void visitClass(ClassTree tree, Void ignore) {
            visitDecl(tree, tree.getSimpleName());
            return super.visitClass(tree, ignore);
        }

        @Override
        public Void visitMethod(MethodTree tree, Void ignore) {
            visitDecl(tree, tree.getName());
            return null;
        }

        @Override
        public Void visitVariable(VariableTree tree, Void ignore) {
            visitDecl(tree, tree.getName());
            return super.visitVariable(tree, ignore);
        }
    }

    public class BadArgs extends Exception {
        private static final long serialVersionUID = 0;
        final String code;
        final Object[] args;

        BadArgs(String code, Object... args) {
            super(localize(code, args));
            this.code = code;
            this.args = args;
        }
    }
}
