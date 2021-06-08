package com.sun.tools.javadoc;

import com.sun.tools.javac.code.Symbol.CompletionFailure;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.*;

import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class JavadocTool extends com.sun.tools.javac.main.JavaCompiler {
    final static boolean surrogatesSupported = surrogatesSupported();
    final Messager messager;
    final JavadocClassReader javadocReader;
    final JavadocEnter javadocEnter;
    DocEnv docenv;

    protected JavadocTool(Context context) {
        super(context);
        messager = Messager.instance0(context);
        javadocReader = JavadocClassReader.instance0(context);
        javadocEnter = JavadocEnter.instance0(context);
    }

    public static JavadocTool make0(Context context) {
        Messager messager = null;
        try {
            JavadocClassReader.preRegister(context);
            JavadocEnter.preRegister(context);
            JavadocMemberEnter.preRegister(context);
            JavadocTodo.preRegister(context);
            messager = Messager.instance0(context);
            return new JavadocTool(context);
        } catch (CompletionFailure ex) {
            messager.error(Position.NOPOS, ex.getMessage());
            return null;
        }
    }

    private static boolean isValidJavaClassFile(String file) {
        if (!file.endsWith(".class")) return false;
        String clazzName = file.substring(0, file.length() - ".class".length());
        return isValidClassName(clazzName);
    }

    private static boolean isValidJavaSourceFile(String file) {
        if (!file.endsWith(".java")) return false;
        String clazzName = file.substring(0, file.length() - ".java".length());
        return isValidClassName(clazzName);
    }

    private static boolean surrogatesSupported() {
        try {
            boolean b = Character.isHighSurrogate('a');
            return true;
        } catch (NoSuchMethodError ex) {
            return false;
        }
    }

    public static boolean isValidClassName(String s) {
        if (s.length() < 1) return false;
        if (s.equals("package-info")) return true;
        if (surrogatesSupported) {
            int cp = s.codePointAt(0);
            if (!Character.isJavaIdentifierStart(cp))
                return false;
            for (int j = Character.charCount(cp); j < s.length(); j += Character.charCount(cp)) {
                cp = s.codePointAt(j);
                if (!Character.isJavaIdentifierPart(cp))
                    return false;
            }
        } else {
            if (!Character.isJavaIdentifierStart(s.charAt(0)))
                return false;
            for (int j = 1; j < s.length(); j++)
                if (!Character.isJavaIdentifierPart(s.charAt(j)))
                    return false;
        }
        return true;
    }

    protected boolean keepComments() {
        return true;
    }

    public RootDocImpl getRootDocImpl(String doclocale,
                                      String encoding,
                                      ModifierFilter filter,
                                      List<String> javaNames,
                                      List<String[]> options,
                                      Iterable<? extends JavaFileObject> fileObjects,
                                      boolean breakiterator,
                                      List<String> subPackages,
                                      List<String> excludedPackages,
                                      boolean docClasses,
                                      boolean legacyDoclet,
                                      boolean quiet) throws IOException {
        docenv = DocEnv.instance(context);
        docenv.showAccess = filter;
        docenv.quiet = quiet;
        docenv.breakiterator = breakiterator;
        docenv.setLocale(doclocale);
        docenv.setEncoding(encoding);
        docenv.docClasses = docClasses;
        docenv.legacyDoclet = legacyDoclet;
        javadocReader.sourceCompleter = docClasses ? null : thisCompleter;
        ListBuffer<String> names = new ListBuffer<String>();
        ListBuffer<JCCompilationUnit> classTrees = new ListBuffer<JCCompilationUnit>();
        ListBuffer<JCCompilationUnit> packTrees = new ListBuffer<JCCompilationUnit>();
        try {
            StandardJavaFileManager fm = docenv.fileManager instanceof StandardJavaFileManager
                    ? (StandardJavaFileManager) docenv.fileManager : null;
            for (List<String> it = javaNames; it.nonEmpty(); it = it.tail) {
                String name = it.head;
                if (!docClasses && fm != null && name.endsWith(".java") && new File(name).exists()) {
                    JavaFileObject fo = fm.getJavaFileObjects(name).iterator().next();
                    docenv.notice("main.Loading_source_file", name);
                    JCCompilationUnit tree = parse(fo);
                    classTrees.append(tree);
                } else if (isValidPackageName(name)) {
                    names = names.append(name);
                } else if (name.endsWith(".java")) {
                    if (fm == null)
                        throw new IllegalArgumentException();
                    else
                        docenv.error(null, "main.file_not_found", name);
                } else {
                    docenv.error(null, "main.illegal_package_name", name);
                }
            }
            for (JavaFileObject fo : fileObjects) {
                docenv.notice("main.Loading_source_file", fo.getName());
                JCCompilationUnit tree = parse(fo);
                classTrees.append(tree);
            }
            if (!docClasses) {
                Map<String, List<JavaFileObject>> packageFiles =
                        searchSubPackages(subPackages, names, excludedPackages);
                for (List<String> packs = names.toList(); packs.nonEmpty(); packs = packs.tail) {
                    String packageName = packs.head;
                    parsePackageClasses(packageName, packageFiles.get(packageName), packTrees, excludedPackages);
                }
                if (messager.nerrors() != 0) return null;
                docenv.notice("main.Building_tree");
                javadocEnter.main(classTrees.toList().appendList(packTrees.toList()));
            }
        } catch (Abort ex) {
        }
        if (messager.nerrors() != 0)
            return null;
        if (docClasses)
            return new RootDocImpl(docenv, javaNames, options);
        else
            return new RootDocImpl(docenv, listClasses(classTrees.toList()), names.toList(), options);
    }

    boolean isValidPackageName(String s) {
        int index;
        while ((index = s.indexOf('.')) != -1) {
            if (!isValidClassName(s.substring(0, index))) return false;
            s = s.substring(index + 1);
        }
        return isValidClassName(s);
    }

    private void parsePackageClasses(String name,
                                     Iterable<JavaFileObject> files,
                                     ListBuffer<JCCompilationUnit> trees,
                                     List<String> excludedPackages)
            throws IOException {
        if (excludedPackages.contains(name)) {
            return;
        }
        boolean hasFiles = false;
        docenv.notice("main.Loading_source_files_for_package", name);
        if (files == null) {
            Location location = docenv.fileManager.hasLocation(StandardLocation.SOURCE_PATH)
                    ? StandardLocation.SOURCE_PATH : StandardLocation.CLASS_PATH;
            ListBuffer<JavaFileObject> lb = new ListBuffer<JavaFileObject>();
            for (JavaFileObject fo : docenv.fileManager.list(
                    location, name, EnumSet.of(JavaFileObject.Kind.SOURCE), false)) {
                String binaryName = docenv.fileManager.inferBinaryName(location, fo);
                String simpleName = getSimpleName(binaryName);
                if (isValidClassName(simpleName)) {
                    lb.append(fo);
                }
            }
            files = lb.toList();
        }
        for (JavaFileObject fo : files) {
            trees.append(parse(fo));
            hasFiles = true;
        }
        if (!hasFiles) {
            messager.warning(Messager.NOPOS, "main.no_source_files_for_package",
                    name.replace(File.separatorChar, '.'));
        }
    }

    private Map<String, List<JavaFileObject>> searchSubPackages(
            List<String> subPackages,
            ListBuffer<String> packages,
            List<String> excludedPackages)
            throws IOException {
        Map<String, List<JavaFileObject>> packageFiles =
                new HashMap<String, List<JavaFileObject>>();
        Map<String, Boolean> includedPackages = new HashMap<String, Boolean>();
        includedPackages.put("", true);
        for (String p : excludedPackages)
            includedPackages.put(p, false);
        StandardLocation path = docenv.fileManager.hasLocation(StandardLocation.SOURCE_PATH)
                ? StandardLocation.SOURCE_PATH : StandardLocation.CLASS_PATH;
        searchSubPackages(subPackages,
                includedPackages,
                packages, packageFiles,
                path,
                EnumSet.of(JavaFileObject.Kind.SOURCE));
        return packageFiles;
    }

    private void searchSubPackages(List<String> subPackages,
                                   Map<String, Boolean> includedPackages,
                                   ListBuffer<String> packages,
                                   Map<String, List<JavaFileObject>> packageFiles,
                                   StandardLocation location, Set<JavaFileObject.Kind> kinds)
            throws IOException {
        for (String subPackage : subPackages) {
            if (!isIncluded(subPackage, includedPackages))
                continue;
            for (JavaFileObject fo : docenv.fileManager.list(location, subPackage, kinds, true)) {
                String binaryName = docenv.fileManager.inferBinaryName(location, fo);
                String packageName = getPackageName(binaryName);
                String simpleName = getSimpleName(binaryName);
                if (isIncluded(packageName, includedPackages) && isValidClassName(simpleName)) {
                    List<JavaFileObject> list = packageFiles.get(packageName);
                    list = (list == null ? List.of(fo) : list.prepend(fo));
                    packageFiles.put(packageName, list);
                    if (!packages.contains(packageName))
                        packages.add(packageName);
                }
            }
        }
    }

    private String getPackageName(String name) {
        int lastDot = name.lastIndexOf(".");
        return (lastDot == -1 ? "" : name.substring(0, lastDot));
    }

    private String getSimpleName(String name) {
        int lastDot = name.lastIndexOf(".");
        return (lastDot == -1 ? name : name.substring(lastDot + 1));
    }

    private boolean isIncluded(String packageName, Map<String, Boolean> includedPackages) {
        Boolean b = includedPackages.get(packageName);
        if (b == null) {
            b = isIncluded(getPackageName(packageName), includedPackages);
            includedPackages.put(packageName, b);
        }
        return b;
    }

    private void searchSubPackage(String packageName,
                                  ListBuffer<String> packages,
                                  List<String> excludedPackages,
                                  Collection<File> pathnames) {
        if (excludedPackages.contains(packageName))
            return;
        String packageFilename = packageName.replace('.', File.separatorChar);
        boolean addedPackage = false;
        for (File pathname : pathnames) {
            File f = new File(pathname, packageFilename);
            String[] filenames = f.list();
            if (filenames != null) {
                for (String filename : filenames) {
                    if (!addedPackage
                            && (isValidJavaSourceFile(filename) ||
                            isValidJavaClassFile(filename))
                            && !packages.contains(packageName)) {
                        packages.append(packageName);
                        addedPackage = true;
                    } else if (isValidClassName(filename) &&
                            (new File(f, filename)).isDirectory()) {
                        searchSubPackage(packageName + "." + filename,
                                packages, excludedPackages, pathnames);
                    }
                }
            }
        }
    }

    List<JCClassDecl> listClasses(List<JCCompilationUnit> trees) {
        ListBuffer<JCClassDecl> result = new ListBuffer<JCClassDecl>();
        for (JCCompilationUnit t : trees) {
            for (JCTree def : t.defs) {
                if (def.hasTag(JCTree.Tag.CLASSDEF))
                    result.append((JCClassDecl) def);
            }
        }
        return result.toList();
    }
}
