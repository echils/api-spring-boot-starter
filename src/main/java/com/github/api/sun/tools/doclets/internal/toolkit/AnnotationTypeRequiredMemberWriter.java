package com.github.api.sun.tools.doclets.internal.toolkit;

import com.github.api.sun.javadoc.ClassDoc;
import com.github.api.sun.javadoc.MemberDoc;

import java.io.IOException;

public interface AnnotationTypeRequiredMemberWriter {

    Content getMemberTreeHeader();

    void addAnnotationDetailsMarker(Content memberDetails);

    void addAnnotationDetailsTreeHeader(ClassDoc classDoc,
                                        Content memberDetailsTree);

    Content getAnnotationDocTreeHeader(MemberDoc member,
                                       Content annotationDetailsTree);

    Content getAnnotationDetails(Content annotationDetailsTree);

    Content getAnnotationDoc(Content annotationDocTree, boolean isLastContent);

    Content getSignature(MemberDoc member);

    void addDeprecated(MemberDoc member, Content annotationDocTree);

    void addComments(MemberDoc member, Content annotationDocTree);

    void addTags(MemberDoc member, Content annotationDocTree);

    void close() throws IOException;
}
