package com.github.api.sun.tools.javac.api;

import com.github.api.sun.source.util.TaskEvent;
import com.github.api.sun.source.util.TaskListener;
import com.github.api.sun.tools.javac.util.ClientCodeException;
import com.github.api.sun.tools.javac.util.Context;
import com.github.api.sun.tools.javac.util.JCDiagnostic;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.*;
import javax.tools.JavaFileObject.Kind;
import java.io.*;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.util.*;

public class ClientCodeWrapper {
    Map<Class<?>, Boolean> trustedClasses;

    protected ClientCodeWrapper(Context context) {
        trustedClasses = new HashMap<Class<?>, Boolean>();
    }

    public static ClientCodeWrapper instance(Context context) {
        ClientCodeWrapper instance = context.get(ClientCodeWrapper.class);
        if (instance == null)
            instance = new ClientCodeWrapper(context);
        return instance;
    }

    public JavaFileManager wrap(JavaFileManager fm) {
        if (isTrusted(fm))
            return fm;
        return new WrappedJavaFileManager(fm);
    }

    public FileObject wrap(FileObject fo) {
        if (isTrusted(fo))
            return fo;
        return new WrappedFileObject(fo);
    }

    FileObject unwrap(FileObject fo) {
        if (fo instanceof WrappedFileObject)
            return ((WrappedFileObject) fo).clientFileObject;
        else
            return fo;
    }

    public JavaFileObject wrap(JavaFileObject fo) {
        if (isTrusted(fo))
            return fo;
        return new WrappedJavaFileObject(fo);
    }

    public Iterable<JavaFileObject> wrapJavaFileObjects(Iterable<? extends JavaFileObject> list) {
        List<JavaFileObject> wrapped = new ArrayList<JavaFileObject>();
        for (JavaFileObject fo : list)
            wrapped.add(wrap(fo));
        return Collections.unmodifiableList(wrapped);
    }

    JavaFileObject unwrap(JavaFileObject fo) {
        if (fo instanceof WrappedJavaFileObject)
            return ((JavaFileObject) ((WrappedJavaFileObject) fo).clientFileObject);
        else
            return fo;
    }

    public <T> DiagnosticListener<T> wrap(DiagnosticListener<T> dl) {
        if (isTrusted(dl))
            return dl;
        return new WrappedDiagnosticListener<T>(dl);
    }

    TaskListener wrap(TaskListener tl) {
        if (isTrusted(tl))
            return tl;
        return new WrappedTaskListener(tl);
    }

    TaskListener unwrap(TaskListener l) {
        if (l instanceof WrappedTaskListener)
            return ((WrappedTaskListener) l).clientTaskListener;
        else
            return l;
    }

    Collection<TaskListener> unwrap(Collection<? extends TaskListener> listeners) {
        Collection<TaskListener> c = new ArrayList<TaskListener>(listeners.size());
        for (TaskListener l : listeners)
            c.add(unwrap(l));
        return c;
    }

    @SuppressWarnings("unchecked")
    private <T> Diagnostic<T> unwrap(final Diagnostic<T> diagnostic) {
        if (diagnostic instanceof JCDiagnostic) {
            JCDiagnostic d = (JCDiagnostic) diagnostic;
            return (Diagnostic<T>) new DiagnosticSourceUnwrapper(d);
        } else {
            return diagnostic;
        }
    }

    protected boolean isTrusted(Object o) {
        Class<?> c = o.getClass();
        Boolean trusted = trustedClasses.get(c);
        if (trusted == null) {
            trusted = c.getName().startsWith("com.github.api.sun.tools.javac.")
                    || c.isAnnotationPresent(Trusted.class);
            trustedClasses.put(c, trusted);
        }
        return trusted;
    }

    private String wrappedToString(Class<?> wrapperClass, Object wrapped) {
        return wrapperClass.getSimpleName() + "[" + wrapped + "]";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Trusted {
    }

    protected class WrappedJavaFileManager implements JavaFileManager {
        protected JavaFileManager clientJavaFileManager;

        WrappedJavaFileManager(JavaFileManager clientJavaFileManager) {
            clientJavaFileManager.getClass();
            this.clientJavaFileManager = clientJavaFileManager;
        }

        @Override
        public ClassLoader getClassLoader(Location location) {
            try {
                return clientJavaFileManager.getClassLoader(location);
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public Iterable<JavaFileObject> list(Location location, String packageName, Set<Kind> kinds, boolean recurse) throws IOException {
            try {
                return wrapJavaFileObjects(clientJavaFileManager.list(location, packageName, kinds, recurse));
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public String inferBinaryName(Location location, JavaFileObject file) {
            try {
                return clientJavaFileManager.inferBinaryName(location, unwrap(file));
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public boolean isSameFile(FileObject a, FileObject b) {
            try {
                return clientJavaFileManager.isSameFile(unwrap(a), unwrap(b));
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public boolean handleOption(String current, Iterator<String> remaining) {
            try {
                return clientJavaFileManager.handleOption(current, remaining);
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public boolean hasLocation(Location location) {
            try {
                return clientJavaFileManager.hasLocation(location);
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public JavaFileObject getJavaFileForInput(Location location, String className, Kind kind) throws IOException {
            try {
                return wrap(clientJavaFileManager.getJavaFileForInput(location, className, kind));
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, Kind kind, FileObject sibling) throws IOException {
            try {
                return wrap(clientJavaFileManager.getJavaFileForOutput(location, className, kind, unwrap(sibling)));
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public FileObject getFileForInput(Location location, String packageName, String relativeName) throws IOException {
            try {
                return wrap(clientJavaFileManager.getFileForInput(location, packageName, relativeName));
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public FileObject getFileForOutput(Location location, String packageName, String relativeName, FileObject sibling) throws IOException {
            try {
                return wrap(clientJavaFileManager.getFileForOutput(location, packageName, relativeName, unwrap(sibling)));
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public void flush() throws IOException {
            try {
                clientJavaFileManager.flush();
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public void close() throws IOException {
            try {
                clientJavaFileManager.close();
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public int isSupportedOption(String option) {
            try {
                return clientJavaFileManager.isSupportedOption(option);
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public String toString() {
            return wrappedToString(getClass(), clientJavaFileManager);
        }
    }

    protected class WrappedFileObject implements FileObject {
        protected FileObject clientFileObject;

        WrappedFileObject(FileObject clientFileObject) {
            clientFileObject.getClass();
            this.clientFileObject = clientFileObject;
        }

        @Override
        public URI toUri() {
            try {
                return clientFileObject.toUri();
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public String getName() {
            try {
                return clientFileObject.getName();
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public InputStream openInputStream() throws IOException {
            try {
                return clientFileObject.openInputStream();
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            try {
                return clientFileObject.openOutputStream();
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
            try {
                return clientFileObject.openReader(ignoreEncodingErrors);
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            try {
                return clientFileObject.getCharContent(ignoreEncodingErrors);
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public Writer openWriter() throws IOException {
            try {
                return clientFileObject.openWriter();
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public long getLastModified() {
            try {
                return clientFileObject.getLastModified();
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public boolean delete() {
            try {
                return clientFileObject.delete();
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public String toString() {
            return wrappedToString(getClass(), clientFileObject);
        }
    }

    protected class WrappedJavaFileObject extends WrappedFileObject implements JavaFileObject {
        WrappedJavaFileObject(JavaFileObject clientJavaFileObject) {
            super(clientJavaFileObject);
        }

        @Override
        public Kind getKind() {
            try {
                return ((JavaFileObject) clientFileObject).getKind();
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public boolean isNameCompatible(String simpleName, Kind kind) {
            try {
                return ((JavaFileObject) clientFileObject).isNameCompatible(simpleName, kind);
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public NestingKind getNestingKind() {
            try {
                return ((JavaFileObject) clientFileObject).getNestingKind();
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public Modifier getAccessLevel() {
            try {
                return ((JavaFileObject) clientFileObject).getAccessLevel();
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public String toString() {
            return wrappedToString(getClass(), clientFileObject);
        }
    }

    protected class WrappedDiagnosticListener<T> implements DiagnosticListener<T> {
        protected DiagnosticListener<T> clientDiagnosticListener;

        WrappedDiagnosticListener(DiagnosticListener<T> clientDiagnosticListener) {
            clientDiagnosticListener.getClass();
            this.clientDiagnosticListener = clientDiagnosticListener;
        }

        @Override
        public void report(Diagnostic<? extends T> diagnostic) {
            try {
                clientDiagnosticListener.report(unwrap(diagnostic));
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public String toString() {
            return wrappedToString(getClass(), clientDiagnosticListener);
        }
    }

    public class DiagnosticSourceUnwrapper implements Diagnostic<JavaFileObject> {
        public final JCDiagnostic d;

        DiagnosticSourceUnwrapper(JCDiagnostic d) {
            this.d = d;
        }

        public Kind getKind() {
            return d.getKind();
        }

        public JavaFileObject getSource() {
            return unwrap(d.getSource());
        }

        public long getPosition() {
            return d.getPosition();
        }

        public long getStartPosition() {
            return d.getStartPosition();
        }

        public long getEndPosition() {
            return d.getEndPosition();
        }

        public long getLineNumber() {
            return d.getLineNumber();
        }

        public long getColumnNumber() {
            return d.getColumnNumber();
        }

        public String getCode() {
            return d.getCode();
        }

        public String getMessage(Locale locale) {
            return d.getMessage(locale);
        }

        @Override
        public String toString() {
            return d.toString();
        }
    }

    protected class WrappedTaskListener implements TaskListener {
        protected TaskListener clientTaskListener;

        WrappedTaskListener(TaskListener clientTaskListener) {
            clientTaskListener.getClass();
            this.clientTaskListener = clientTaskListener;
        }

        @Override
        public void started(TaskEvent ev) {
            try {
                clientTaskListener.started(ev);
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public void finished(TaskEvent ev) {
            try {
                clientTaskListener.finished(ev);
            } catch (ClientCodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ClientCodeException(e);
            } catch (Error e) {
                throw new ClientCodeException(e);
            }
        }

        @Override
        public String toString() {
            return wrappedToString(getClass(), clientTaskListener);
        }
    }
}
