package com.sun.tools.doclets.internal.toolkit;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.Type;

import java.io.IOException;

public interface MethodWriter {

    Content getMethodDetailsTreeHeader(ClassDoc classDoc,
                                       Content memberDetailsTree);

    Content getMethodDocTreeHeader(MethodDoc method,
                                   Content methodDetailsTree);

    Content getSignature(MethodDoc method);

    void addDeprecated(MethodDoc method, Content methodDocTree);

    void addComments(Type holder, MethodDoc method, Content methodDocTree);

    void addTags(MethodDoc method, Content methodDocTree);

    Content getMethodDetails(Content methodDetailsTree);

    Content getMethodDoc(Content methodDocTree, boolean isLastContent);

    void close() throws IOException;
}
