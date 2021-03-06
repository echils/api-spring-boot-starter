package com.github.api.sun.tools.javac.api;

import com.github.api.sun.source.util.JavacTask;
import com.github.api.sun.tools.javac.file.JavacFileManager;
import com.github.api.sun.tools.javac.main.Main;
import com.github.api.sun.tools.javac.main.Option;
import com.github.api.sun.tools.javac.main.OptionHelper;
import com.github.api.sun.tools.javac.main.OptionHelper.GrumpyHelper;
import com.github.api.sun.tools.javac.util.ClientCodeException;
import com.github.api.sun.tools.javac.util.Context;
import com.github.api.sun.tools.javac.util.Log;
import com.github.api.sun.tools.javac.util.Log.PrefixKind;
import com.github.api.sun.tools.javac.util.Options;

import javax.lang.model.SourceVersion;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

public final class JavacTool implements JavaCompiler {
    @Deprecated
    public JavacTool() {
    }

    public static JavacTool create() {
        return new JavacTool();
    }

    public static void processOptions(Context context,
                                      JavaFileManager fileManager,
                                      Iterable<String> options) {
        if (options == null)
            return;
        final Options optionTable = Options.instance(context);
        Log log = Log.instance(context);
        Option[] recognizedOptions =
                Option.getJavacToolOptions().toArray(new Option[0]);
        OptionHelper optionHelper = new GrumpyHelper(log) {
            @Override
            public String get(Option option) {
                return optionTable.get(option.getText());
            }

            @Override
            public void put(String name, String value) {
                optionTable.put(name, value);
            }

            @Override
            public void remove(String name) {
                optionTable.remove(name);
            }
        };
        Iterator<String> flags = options.iterator();
        while (flags.hasNext()) {
            String flag = flags.next();
            int j;
            for (j = 0; j < recognizedOptions.length; j++)
                if (recognizedOptions[j].matches(flag))
                    break;
            if (j == recognizedOptions.length) {
                if (fileManager.handleOption(flag, flags)) {
                    continue;
                } else {
                    String msg = log.localize(PrefixKind.JAVAC, "err.invalid.flag", flag);
                    throw new IllegalArgumentException(msg);
                }
            }
            Option option = recognizedOptions[j];
            if (option.hasArg()) {
                if (!flags.hasNext()) {
                    String msg = log.localize(PrefixKind.JAVAC, "err.req.arg", flag);
                    throw new IllegalArgumentException(msg);
                }
                String operand = flags.next();
                if (option.process(optionHelper, flag, operand))
                    throw new IllegalArgumentException(flag + " " + operand);
            } else {
                if (option.process(optionHelper, flag))
                    throw new IllegalArgumentException(flag);
            }
        }
        optionTable.notifyListeners();
    }

    public JavacFileManager getStandardFileManager(
            DiagnosticListener<? super JavaFileObject> diagnosticListener,
            Locale locale,
            Charset charset) {
        Context context = new Context();
        context.put(Locale.class, locale);
        if (diagnosticListener != null)
            context.put(DiagnosticListener.class, diagnosticListener);
        PrintWriter pw = (charset == null)
                ? new PrintWriter(System.err, true)
                : new PrintWriter(new OutputStreamWriter(System.err, charset), true);
        context.put(Log.outKey, pw);
        return new JavacFileManager(context, true, charset);
    }

    @Override
    public JavacTask getTask(Writer out,
                             JavaFileManager fileManager,
                             DiagnosticListener<? super JavaFileObject> diagnosticListener,
                             Iterable<String> options,
                             Iterable<String> classes,
                             Iterable<? extends JavaFileObject> compilationUnits) {
        Context context = new Context();
        return getTask(out, fileManager, diagnosticListener,
                options, classes, compilationUnits,
                context);
    }

    public JavacTask getTask(Writer out,
                             JavaFileManager fileManager,
                             DiagnosticListener<? super JavaFileObject> diagnosticListener,
                             Iterable<String> options,
                             Iterable<String> classes,
                             Iterable<? extends JavaFileObject> compilationUnits,
                             Context context) {
        try {
            ClientCodeWrapper ccw = ClientCodeWrapper.instance(context);
            if (options != null)
                for (String option : options)
                    option.getClass();
            if (classes != null) {
                for (String cls : classes)
                    if (!SourceVersion.isName(cls))
                        throw new IllegalArgumentException("Not a valid class name: " + cls);
            }
            if (compilationUnits != null) {
                compilationUnits = ccw.wrapJavaFileObjects(compilationUnits);
                for (JavaFileObject cu : compilationUnits) {
                    if (cu.getKind() != JavaFileObject.Kind.SOURCE) {
                        String kindMsg = "Compilation unit is not of SOURCE kind: "
                                + "\"" + cu.getName() + "\"";
                        throw new IllegalArgumentException(kindMsg);
                    }
                }
            }
            if (diagnosticListener != null)
                context.put(DiagnosticListener.class, ccw.wrap(diagnosticListener));
            if (out == null)
                context.put(Log.outKey, new PrintWriter(System.err, true));
            else
                context.put(Log.outKey, new PrintWriter(out, true));
            if (fileManager == null)
                fileManager = getStandardFileManager(diagnosticListener, null, null);
            fileManager = ccw.wrap(fileManager);
            context.put(JavaFileManager.class, fileManager);
            processOptions(context, fileManager, options);
            Main compiler = new Main("javacTask", context.get(Log.outKey));
            return new JavacTaskImpl(compiler, options, context, classes, compilationUnits);
        } catch (ClientCodeException ex) {
            throw new RuntimeException(ex.getCause());
        }
    }

    public int run(InputStream in, OutputStream out, OutputStream err, String... arguments) {
        if (err == null)
            err = System.err;
        for (String argument : arguments)
            argument.getClass();
        return com.github.api.sun.tools.javac.Main.compile(arguments, new PrintWriter(err, true));
    }

    public Set<SourceVersion> getSourceVersions() {
        return Collections.unmodifiableSet(EnumSet.range(SourceVersion.RELEASE_3,
                SourceVersion.latest()));
    }

    public int isSupportedOption(String option) {
        Set<Option> recognizedOptions = Option.getJavacToolOptions();
        for (Option o : recognizedOptions) {
            if (o.matches(option))
                return o.hasArg() ? 1 : 0;
        }
        return -1;
    }
}
