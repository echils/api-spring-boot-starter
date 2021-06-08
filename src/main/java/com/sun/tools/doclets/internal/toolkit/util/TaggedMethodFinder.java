package com.sun.tools.doclets.internal.toolkit.util;

import com.sun.javadoc.MethodDoc;

public class TaggedMethodFinder extends MethodFinder {
    public boolean isCorrectMethod(MethodDoc method) {
        return method.paramTags().length + method.tags("return").length +
                method.throwsTags().length + method.seeTags().length > 0;
    }
}
