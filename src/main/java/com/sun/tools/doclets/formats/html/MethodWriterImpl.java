package com.sun.tools.doclets.formats.html;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.javadoc.Type;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.MemberSummaryWriter;
import com.sun.tools.doclets.internal.toolkit.MethodWriter;
import com.sun.tools.doclets.internal.toolkit.util.ImplementedMethods;
import com.sun.tools.doclets.internal.toolkit.util.Util;
import com.sun.tools.doclets.internal.toolkit.util.VisibleMemberMap;

import java.io.IOException;

public class MethodWriterImpl extends AbstractExecutableMemberWriter
        implements MethodWriter, MemberSummaryWriter {

    public MethodWriterImpl(SubWriterHolderWriter writer, ClassDoc classDoc) {
        super(writer, classDoc);
    }

    public MethodWriterImpl(SubWriterHolderWriter writer) {
        super(writer);
    }

    protected static void addOverridden(HtmlDocletWriter writer,
                                        Type overriddenType, MethodDoc method, Content dl) {
        if (writer.configuration.nocomment) {
            return;
        }
        ClassDoc holderClassDoc = overriddenType.asClassDoc();
        if (!(holderClassDoc.isPublic() ||
                Util.isLinkable(holderClassDoc, writer.configuration))) {

            return;
        }
        if (overriddenType.asClassDoc().isIncluded() && !method.isIncluded()) {


            return;
        }
        Content label = writer.overridesLabel;
        LinkInfoImpl.Kind context = LinkInfoImpl.Kind.METHOD_OVERRIDES;
        if (method != null) {
            if (overriddenType.asClassDoc().isAbstract() && method.isAbstract()) {


                label = writer.specifiedByLabel;
                context = LinkInfoImpl.Kind.METHOD_SPECIFIED_BY;
            }
            Content dt = HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.overrideSpecifyLabel, label));
            dl.addContent(dt);
            Content overriddenTypeLink =
                    writer.getLink(new LinkInfoImpl(writer.configuration, context, overriddenType));
            Content codeOverridenTypeLink = HtmlTree.CODE(overriddenTypeLink);
            String name = method.name();
            Content methlink = writer.getLink(
                    new LinkInfoImpl(writer.configuration, LinkInfoImpl.Kind.MEMBER,
                            overriddenType.asClassDoc())
                            .where(writer.getName(writer.getAnchor(method))).label(name));
            Content codeMethLink = HtmlTree.CODE(methlink);
            Content dd = HtmlTree.DD(codeMethLink);
            dd.addContent(writer.getSpace());
            dd.addContent(writer.getResource("doclet.in_class"));
            dd.addContent(writer.getSpace());
            dd.addContent(codeOverridenTypeLink);
            dl.addContent(dd);
        }
    }

    protected static void addImplementsInfo(HtmlDocletWriter writer,
                                            MethodDoc method, Content dl) {
        if (writer.configuration.nocomment) {
            return;
        }
        ImplementedMethods implementedMethodsFinder =
                new ImplementedMethods(method, writer.configuration);
        MethodDoc[] implementedMethods = implementedMethodsFinder.build();
        for (int i = 0; i < implementedMethods.length; i++) {
            MethodDoc implementedMeth = implementedMethods[i];
            Type intfac = implementedMethodsFinder.getMethodHolder(implementedMeth);
            Content intfaclink = writer.getLink(new LinkInfoImpl(
                    writer.configuration, LinkInfoImpl.Kind.METHOD_SPECIFIED_BY, intfac));
            Content codeIntfacLink = HtmlTree.CODE(intfaclink);
            Content dt = HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.overrideSpecifyLabel, writer.specifiedByLabel));
            dl.addContent(dt);
            Content methlink = writer.getDocLink(
                    LinkInfoImpl.Kind.MEMBER, implementedMeth,
                    implementedMeth.name(), false);
            Content codeMethLink = HtmlTree.CODE(methlink);
            Content dd = HtmlTree.DD(codeMethLink);
            dd.addContent(writer.getSpace());
            dd.addContent(writer.getResource("doclet.in_interface"));
            dd.addContent(writer.getSpace());
            dd.addContent(codeIntfacLink);
            dl.addContent(dd);
        }
    }

    public Content getMemberSummaryHeader(ClassDoc classDoc,
                                          Content memberSummaryTree) {
        memberSummaryTree.addContent(HtmlConstants.START_OF_METHOD_SUMMARY);
        Content memberTree = writer.getMemberTreeHeader();
        writer.addSummaryHeader(this, classDoc, memberTree);
        return memberTree;
    }

    public Content getMethodDetailsTreeHeader(ClassDoc classDoc,
                                              Content memberDetailsTree) {
        memberDetailsTree.addContent(HtmlConstants.START_OF_METHOD_DETAILS);
        Content methodDetailsTree = writer.getMemberTreeHeader();
        methodDetailsTree.addContent(writer.getMarkerAnchor(
                SectionName.METHOD_DETAIL));
        Content heading = HtmlTree.HEADING(HtmlConstants.DETAILS_HEADING,
                writer.methodDetailsLabel);
        methodDetailsTree.addContent(heading);
        return methodDetailsTree;
    }

    public Content getMethodDocTreeHeader(MethodDoc method,
                                          Content methodDetailsTree) {
        String erasureAnchor;
        if ((erasureAnchor = getErasureAnchor(method)) != null) {
            methodDetailsTree.addContent(writer.getMarkerAnchor((erasureAnchor)));
        }
        methodDetailsTree.addContent(
                writer.getMarkerAnchor(writer.getAnchor(method)));
        Content methodDocTree = writer.getMemberTreeHeader();
        Content heading = new HtmlTree(HtmlConstants.MEMBER_HEADING);
        heading.addContent(method.name());
        methodDocTree.addContent(heading);
        return methodDocTree;
    }

    public Content getSignature(MethodDoc method) {
        Content pre = new HtmlTree(HtmlTag.PRE);
        writer.addAnnotationInfo(method, pre);
        addModifiers(method, pre);
        addTypeParameters(method, pre);
        addReturnType(method, pre);
        if (configuration.linksource) {
            Content methodName = new StringContent(method.name());
            writer.addSrcLink(method, methodName, pre);
        } else {
            addName(method.name(), pre);
        }
        int indent = pre.charCount();
        addParameters(method, pre, indent);
        addExceptions(method, pre, indent);
        return pre;
    }

    public void addDeprecated(MethodDoc method, Content methodDocTree) {
        addDeprecatedInfo(method, methodDocTree);
    }

    public void addComments(Type holder, MethodDoc method, Content methodDocTree) {
        ClassDoc holderClassDoc = holder.asClassDoc();
        if (method.inlineTags().length > 0) {
            if (holder.asClassDoc().equals(classdoc) ||
                    (!(holderClassDoc.isPublic() ||
                            Util.isLinkable(holderClassDoc, configuration)))) {
                writer.addInlineComment(method, methodDocTree);
            } else {
                Content link =
                        writer.getDocLink(LinkInfoImpl.Kind.METHOD_DOC_COPY,
                                holder.asClassDoc(), method,
                                holder.asClassDoc().isIncluded() ?
                                        holder.typeName() : holder.qualifiedTypeName(),
                                false);
                Content codelLink = HtmlTree.CODE(link);
                Content descfrmLabel = HtmlTree.SPAN(HtmlStyle.descfrmTypeLabel, holder.asClassDoc().isClass() ?
                        writer.descfrmClassLabel : writer.descfrmInterfaceLabel);
                descfrmLabel.addContent(writer.getSpace());
                descfrmLabel.addContent(codelLink);
                methodDocTree.addContent(HtmlTree.DIV(HtmlStyle.block, descfrmLabel));
                writer.addInlineComment(method, methodDocTree);
            }
        }
    }

    public void addTags(MethodDoc method, Content methodDocTree) {
        writer.addTagsInfo(method, methodDocTree);
    }

    public Content getMethodDetails(Content methodDetailsTree) {
        return getMemberTree(methodDetailsTree);
    }

    public Content getMethodDoc(Content methodDocTree,
                                boolean isLastContent) {
        return getMemberTree(methodDocTree, isLastContent);
    }

    public void close() throws IOException {
        writer.close();
    }

    public int getMemberKind() {
        return VisibleMemberMap.METHODS;
    }

    public void addSummaryLabel(Content memberTree) {
        Content label = HtmlTree.HEADING(HtmlConstants.SUMMARY_HEADING,
                writer.getResource("doclet.Method_Summary"));
        memberTree.addContent(label);
    }

    public String getTableSummary() {
        return configuration.getText("doclet.Member_Table_Summary",
                configuration.getText("doclet.Method_Summary"),
                configuration.getText("doclet.methods"));
    }

    public Content getCaption() {
        return configuration.getResource("doclet.Methods");
    }

    public String[] getSummaryTableHeader(ProgramElementDoc member) {
        String[] header = new String[]{
                writer.getModifierTypeHeader(),
                configuration.getText("doclet.0_and_1",
                        configuration.getText("doclet.Method"),
                        configuration.getText("doclet.Description"))
        };
        return header;
    }

    public void addSummaryAnchor(ClassDoc cd, Content memberTree) {
        memberTree.addContent(writer.getMarkerAnchor(
                SectionName.METHOD_SUMMARY));
    }

    public void addInheritedSummaryAnchor(ClassDoc cd, Content inheritedTree) {
        inheritedTree.addContent(writer.getMarkerAnchor(
                SectionName.METHODS_INHERITANCE, configuration.getClassName(cd)));
    }

    public void addInheritedSummaryLabel(ClassDoc cd, Content inheritedTree) {
        Content classLink = writer.getPreQualifiedClassLink(
                LinkInfoImpl.Kind.MEMBER, cd, false);
        Content label = new StringContent(cd.isClass() ?
                configuration.getText("doclet.Methods_Inherited_From_Class") :
                configuration.getText("doclet.Methods_Inherited_From_Interface"));
        Content labelHeading = HtmlTree.HEADING(HtmlConstants.INHERITED_SUMMARY_HEADING,
                label);
        labelHeading.addContent(writer.getSpace());
        labelHeading.addContent(classLink);
        inheritedTree.addContent(labelHeading);
    }

    protected void addSummaryType(ProgramElementDoc member, Content tdSummaryType) {
        MethodDoc meth = (MethodDoc) member;
        addModifierAndType(meth, meth.returnType(), tdSummaryType);
    }

    protected String parseCodeTag(String tag) {
        if (tag == null) {
            return "";
        }
        String lc = tag.toLowerCase();
        int begin = lc.indexOf("<code>");
        int end = lc.indexOf("</code>");
        if (begin == -1 || end == -1 || end <= begin) {
            return tag;
        } else {
            return tag.substring(begin + 6, end);
        }
    }

    protected void addReturnType(MethodDoc method, Content htmltree) {
        Type type = method.returnType();
        if (type != null) {
            Content linkContent = writer.getLink(
                    new LinkInfoImpl(configuration, LinkInfoImpl.Kind.RETURN_TYPE, type));
            htmltree.addContent(linkContent);
            htmltree.addContent(writer.getSpace());
        }
    }

    protected Content getNavSummaryLink(ClassDoc cd, boolean link) {
        if (link) {
            if (cd == null) {
                return writer.getHyperLink(
                        SectionName.METHOD_SUMMARY,
                        writer.getResource("doclet.navMethod"));
            } else {
                return writer.getHyperLink(
                        SectionName.METHODS_INHERITANCE,
                        configuration.getClassName(cd), writer.getResource("doclet.navMethod"));
            }
        } else {
            return writer.getResource("doclet.navMethod");
        }
    }

    protected void addNavDetailLink(boolean link, Content liNav) {
        if (link) {
            liNav.addContent(writer.getHyperLink(
                    SectionName.METHOD_DETAIL, writer.getResource("doclet.navMethod")));
        } else {
            liNav.addContent(writer.getResource("doclet.navMethod"));
        }
    }
}
