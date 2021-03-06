package com.github.api.sun.tools.doclets.internal.toolkit.builders;

import com.github.api.sun.javadoc.ClassDoc;
import com.github.api.sun.javadoc.MemberDoc;
import com.github.api.sun.tools.doclets.internal.toolkit.AnnotationTypeOptionalMemberWriter;
import com.github.api.sun.tools.doclets.internal.toolkit.AnnotationTypeRequiredMemberWriter;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.util.VisibleMemberMap;

public class AnnotationTypeOptionalMemberBuilder extends
        AnnotationTypeRequiredMemberBuilder {

    private AnnotationTypeOptionalMemberBuilder(Context context,
                                                ClassDoc classDoc,
                                                AnnotationTypeOptionalMemberWriter writer) {
        super(context, classDoc, writer,
                VisibleMemberMap.ANNOTATION_TYPE_MEMBER_OPTIONAL);
    }

    public static AnnotationTypeOptionalMemberBuilder getInstance(
            Context context, ClassDoc classDoc,
            AnnotationTypeOptionalMemberWriter writer) {
        return new AnnotationTypeOptionalMemberBuilder(context,
                classDoc, writer);
    }

    @Override
    public String getName() {
        return "AnnotationTypeOptionalMemberDetails";
    }

    public void buildAnnotationTypeOptionalMember(XMLNode node, Content memberDetailsTree) {
        buildAnnotationTypeMember(node, memberDetailsTree);
    }

    public void buildDefaultValueInfo(XMLNode node, Content annotationDocTree) {
        ((AnnotationTypeOptionalMemberWriter) writer).addDefaultValueInfo(
                (MemberDoc) members.get(currentMemberIndex),
                annotationDocTree);
    }

    @Override
    public AnnotationTypeRequiredMemberWriter getWriter() {
        return writer;
    }
}
