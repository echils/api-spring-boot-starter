package com.github.api.sun.tools.doclets.formats.html;

import com.github.api.sun.javadoc.*;
import com.github.api.sun.tools.doclets.formats.html.markup.*;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.taglets.DeprecatedTaglet;
import com.github.api.sun.tools.doclets.internal.toolkit.util.MethodTypes;
import com.github.api.sun.tools.doclets.internal.toolkit.util.Util;
import com.github.api.sun.tools.doclets.internal.toolkit.util.VisibleMemberMap;

import java.lang.reflect.Modifier;
import java.util.*;

public abstract class AbstractMemberWriter {
    public final boolean nodepr;
    protected final ConfigurationImpl configuration;
    protected final SubWriterHolderWriter writer;
    protected final ClassDoc classdoc;
    protected Map<String, Integer> typeMap = new LinkedHashMap<String, Integer>();
    protected Set<MethodTypes> methodTypes = EnumSet.noneOf(MethodTypes.class);
    protected boolean printedSummaryHeader = false;
    private int methodTypesOr = 0;

    public AbstractMemberWriter(SubWriterHolderWriter writer, ClassDoc classdoc) {
        this.configuration = writer.configuration;
        this.writer = writer;
        this.nodepr = configuration.nodeprecated;
        this.classdoc = classdoc;
    }

    public AbstractMemberWriter(SubWriterHolderWriter writer) {
        this(writer, null);
    }


    public abstract void addSummaryLabel(Content memberTree);

    public abstract String getTableSummary();

    public abstract Content getCaption();

    public abstract String[] getSummaryTableHeader(ProgramElementDoc member);

    public abstract void addInheritedSummaryLabel(ClassDoc cd, Content inheritedTree);

    public abstract void addSummaryAnchor(ClassDoc cd, Content memberTree);

    public abstract void addInheritedSummaryAnchor(ClassDoc cd, Content inheritedTree);

    protected abstract void addSummaryType(ProgramElementDoc member,
                                           Content tdSummaryType);

    protected void addSummaryLink(ClassDoc cd, ProgramElementDoc member,
                                  Content tdSummary) {
        addSummaryLink(LinkInfoImpl.Kind.MEMBER, cd, member, tdSummary);
    }

    protected abstract void addSummaryLink(LinkInfoImpl.Kind context,
                                           ClassDoc cd, ProgramElementDoc member, Content tdSummary);

    protected abstract void addInheritedSummaryLink(ClassDoc cd,
                                                    ProgramElementDoc member, Content linksTree);

    protected abstract Content getDeprecatedLink(ProgramElementDoc member);

    protected abstract Content getNavSummaryLink(ClassDoc cd, boolean link);

    protected abstract void addNavDetailLink(boolean link, Content liNav);

    protected void addName(String name, Content htmltree) {
        htmltree.addContent(name);
    }

    protected String modifierString(MemberDoc member) {
        int ms = member.modifierSpecifier();
        int no = Modifier.NATIVE | Modifier.SYNCHRONIZED;
        return Modifier.toString(ms & ~no);
    }

    protected String typeString(MemberDoc member) {
        String type = "";
        if (member instanceof MethodDoc) {
            type = ((MethodDoc) member).returnType().toString();
        } else if (member instanceof FieldDoc) {
            type = ((FieldDoc) member).type().toString();
        }
        return type;
    }

    protected void addModifiers(MemberDoc member, Content htmltree) {
        String mod = modifierString(member);


        if ((member.isField() || member.isMethod()) &&
                writer instanceof ClassWriterImpl &&
                ((ClassWriterImpl) writer).getClassDoc().isInterface()) {


            mod = (member.isMethod() && ((MethodDoc) member).isDefault()) ?
                    Util.replaceText(mod, "public", "default").trim() :
                    Util.replaceText(mod, "public", "").trim();
        }
        if (mod.length() > 0) {
            htmltree.addContent(mod);
            htmltree.addContent(writer.getSpace());
        }
    }

    protected String makeSpace(int len) {
        if (len <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }

    protected void addModifierAndType(ProgramElementDoc member, Type type,
                                      Content tdSummaryType) {
        HtmlTree code = new HtmlTree(HtmlTag.CODE);
        addModifier(member, code);
        if (type == null) {
            if (member.isClass()) {
                code.addContent("class");
            } else {
                code.addContent("interface");
            }
            code.addContent(writer.getSpace());
        } else {
            if (member instanceof ExecutableMemberDoc &&
                    ((ExecutableMemberDoc) member).typeParameters().length > 0) {
                Content typeParameters = ((AbstractExecutableMemberWriter) this).getTypeParameters(
                        (ExecutableMemberDoc) member);
                code.addContent(typeParameters);

                if (typeParameters.charCount() > 10) {
                    code.addContent(new HtmlTree(HtmlTag.BR));
                } else {
                    code.addContent(writer.getSpace());
                }
                code.addContent(
                        writer.getLink(new LinkInfoImpl(configuration,
                                LinkInfoImpl.Kind.SUMMARY_RETURN_TYPE, type)));
            } else {
                code.addContent(
                        writer.getLink(new LinkInfoImpl(configuration,
                                LinkInfoImpl.Kind.SUMMARY_RETURN_TYPE, type)));
            }
        }
        tdSummaryType.addContent(code);
    }

    private void addModifier(ProgramElementDoc member, Content code) {
        if (member.isProtected()) {
            code.addContent("protected ");
        } else if (member.isPrivate()) {
            code.addContent("private ");
        } else if (!member.isPublic()) {
            code.addContent(configuration.getText("doclet.Package_private"));
            code.addContent(" ");
        }
        if (member.isMethod()) {
            if (!(member.containingClass().isInterface()) &&
                    ((MethodDoc) member).isAbstract()) {
                code.addContent("abstract ");
            }


            if (((MethodDoc) member).isDefault()) {
                code.addContent("default ");
            }
        }
        if (member.isStatic()) {
            code.addContent("static ");
        }
    }

    protected void addDeprecatedInfo(ProgramElementDoc member, Content contentTree) {
        Content output = (new DeprecatedTaglet()).getTagletOutput(member,
                writer.getTagletWriterInstance(false));
        if (!output.isEmpty()) {
            Content deprecatedContent = output;
            Content div = HtmlTree.DIV(HtmlStyle.block, deprecatedContent);
            contentTree.addContent(div);
        }
    }

    protected void addComment(ProgramElementDoc member, Content htmltree) {
        if (member.inlineTags().length > 0) {
            writer.addInlineComment(member, htmltree);
        }
    }

    protected String name(ProgramElementDoc member) {
        return member.name();
    }

    protected Content getHead(MemberDoc member) {
        Content memberContent = new StringContent(member.name());
        Content heading = HtmlTree.HEADING(HtmlConstants.MEMBER_HEADING, memberContent);
        return heading;
    }

    protected boolean isInherited(ProgramElementDoc ped) {
        return !ped.isPrivate() && (!ped.isPackagePrivate() ||
                ped.containingPackage().equals(classdoc.containingPackage()));
    }

    protected void addDeprecatedAPI(List<Doc> deprmembers, String headingKey,
                                    String tableSummary, String[] tableHeader, Content contentTree) {
        if (deprmembers.size() > 0) {
            Content table = HtmlTree.TABLE(HtmlStyle.deprecatedSummary, 0, 3, 0, tableSummary,
                    writer.getTableCaption(configuration.getResource(headingKey)));
            table.addContent(writer.getSummaryTableHeader(tableHeader, "col"));
            Content tbody = new HtmlTree(HtmlTag.TBODY);
            for (int i = 0; i < deprmembers.size(); i++) {
                ProgramElementDoc member = (ProgramElementDoc) deprmembers.get(i);
                HtmlTree td = HtmlTree.TD(HtmlStyle.colOne, getDeprecatedLink(member));
                if (member.tags("deprecated").length > 0)
                    writer.addInlineDeprecatedComment(member,
                            member.tags("deprecated")[0], td);
                HtmlTree tr = HtmlTree.TR(td);
                if (i % 2 == 0)
                    tr.addStyle(HtmlStyle.altColor);
                else
                    tr.addStyle(HtmlStyle.rowColor);
                tbody.addContent(tr);
            }
            table.addContent(tbody);
            Content li = HtmlTree.LI(HtmlStyle.blockList, table);
            Content ul = HtmlTree.UL(HtmlStyle.blockList, li);
            contentTree.addContent(ul);
        }
    }

    protected void addUseInfo(List<? extends ProgramElementDoc> mems,
                              Content heading, String tableSummary, Content contentTree) {
        if (mems == null) {
            return;
        }
        List<? extends ProgramElementDoc> members = mems;
        boolean printedUseTableHeader = false;
        if (members.size() > 0) {
            Content table = HtmlTree.TABLE(HtmlStyle.useSummary, 0, 3, 0, tableSummary,
                    writer.getTableCaption(heading));
            Content tbody = new HtmlTree(HtmlTag.TBODY);
            Iterator<? extends ProgramElementDoc> it = members.iterator();
            for (int i = 0; it.hasNext(); i++) {
                ProgramElementDoc pgmdoc = it.next();
                ClassDoc cd = pgmdoc.containingClass();
                if (!printedUseTableHeader) {
                    table.addContent(writer.getSummaryTableHeader(
                            this.getSummaryTableHeader(pgmdoc), "col"));
                    printedUseTableHeader = true;
                }
                HtmlTree tr = new HtmlTree(HtmlTag.TR);
                if (i % 2 == 0) {
                    tr.addStyle(HtmlStyle.altColor);
                } else {
                    tr.addStyle(HtmlStyle.rowColor);
                }
                HtmlTree tdFirst = new HtmlTree(HtmlTag.TD);
                tdFirst.addStyle(HtmlStyle.colFirst);
                writer.addSummaryType(this, pgmdoc, tdFirst);
                tr.addContent(tdFirst);
                HtmlTree tdLast = new HtmlTree(HtmlTag.TD);
                tdLast.addStyle(HtmlStyle.colLast);
                if (cd != null && !(pgmdoc instanceof ConstructorDoc)
                        && !(pgmdoc instanceof ClassDoc)) {
                    HtmlTree name = new HtmlTree(HtmlTag.SPAN);
                    name.addStyle(HtmlStyle.typeNameLabel);
                    name.addContent(cd.name() + ".");
                    tdLast.addContent(name);
                }
                addSummaryLink(pgmdoc instanceof ClassDoc ?
                                LinkInfoImpl.Kind.CLASS_USE : LinkInfoImpl.Kind.MEMBER,
                        cd, pgmdoc, tdLast);
                writer.addSummaryLinkComment(this, pgmdoc, tdLast);
                tr.addContent(tdLast);
                tbody.addContent(tr);
            }
            table.addContent(tbody);
            contentTree.addContent(table);
        }
    }

    protected void addNavDetailLink(List<?> members, Content liNav) {
        addNavDetailLink(members.size() > 0, liNav);
    }

    protected void addNavSummaryLink(List<?> members,
                                     VisibleMemberMap visibleMemberMap, Content liNav) {
        if (members.size() > 0) {
            liNav.addContent(getNavSummaryLink(null, true));
            return;
        }
        ClassDoc icd = classdoc.superclass();
        while (icd != null) {
            List<?> inhmembers = visibleMemberMap.getMembersFor(icd);
            if (inhmembers.size() > 0) {
                liNav.addContent(getNavSummaryLink(icd, true));
                return;
            }
            icd = icd.superclass();
        }
        liNav.addContent(getNavSummaryLink(null, false));
    }

    protected void serialWarning(SourcePosition pos, String key, String a1, String a2) {
        if (configuration.serialwarn) {
            configuration.getDocletSpecificMsg().warning(pos, key, a1, a2);
        }
    }

    public ProgramElementDoc[] eligibleMembers(ProgramElementDoc[] members) {
        return nodepr ? Util.excludeDeprecatedMembers(members) : members;
    }

    public void addMemberSummary(ClassDoc classDoc, ProgramElementDoc member,
                                 Tag[] firstSentenceTags, List<Content> tableContents, int counter) {
        HtmlTree tdSummaryType = new HtmlTree(HtmlTag.TD);
        tdSummaryType.addStyle(HtmlStyle.colFirst);
        writer.addSummaryType(this, member, tdSummaryType);
        HtmlTree tdSummary = new HtmlTree(HtmlTag.TD);
        setSummaryColumnStyle(tdSummary);
        addSummaryLink(classDoc, member, tdSummary);
        writer.addSummaryLinkComment(this, member, firstSentenceTags, tdSummary);
        HtmlTree tr = HtmlTree.TR(tdSummaryType);
        tr.addContent(tdSummary);
        if (member instanceof MethodDoc && !member.isAnnotationTypeElement()) {
            int methodType = (member.isStatic()) ? MethodTypes.STATIC.value() :
                    MethodTypes.INSTANCE.value();
            if (member.containingClass().isInterface()) {
                methodType = (((MethodDoc) member).isAbstract())
                        ? methodType | MethodTypes.ABSTRACT.value()
                        : methodType | MethodTypes.DEFAULT.value();
            } else {
                methodType = (((MethodDoc) member).isAbstract())
                        ? methodType | MethodTypes.ABSTRACT.value()
                        : methodType | MethodTypes.CONCRETE.value();
            }
            if (Util.isDeprecated(member) || Util.isDeprecated(classdoc)) {
                methodType = methodType | MethodTypes.DEPRECATED.value();
            }
            methodTypesOr = methodTypesOr | methodType;
            String tableId = "i" + counter;
            typeMap.put(tableId, methodType);
            tr.addAttr(HtmlAttr.ID, tableId);
        }
        if (counter % 2 == 0)
            tr.addStyle(HtmlStyle.altColor);
        else
            tr.addStyle(HtmlStyle.rowColor);
        tableContents.add(tr);
    }

    public boolean showTabs() {
        int value;
        for (MethodTypes type : EnumSet.allOf(MethodTypes.class)) {
            value = type.value();
            if ((value & methodTypesOr) == value) {
                methodTypes.add(type);
            }
        }
        boolean showTabs = methodTypes.size() > 1;
        if (showTabs) {
            methodTypes.add(MethodTypes.ALL);
        }
        return showTabs;
    }

    public void setSummaryColumnStyle(HtmlTree tdTree) {
        tdTree.addStyle(HtmlStyle.colLast);
    }

    public void addInheritedMemberSummary(ClassDoc classDoc,
                                          ProgramElementDoc nestedClass, boolean isFirst, boolean isLast,
                                          Content linksTree) {
        writer.addInheritedMemberSummary(this, classDoc, nestedClass, isFirst,
                linksTree);
    }

    public Content getInheritedSummaryHeader(ClassDoc classDoc) {
        Content inheritedTree = writer.getMemberTreeHeader();
        writer.addInheritedSummaryHeader(this, classDoc, inheritedTree);
        return inheritedTree;
    }

    public Content getInheritedSummaryLinksTree() {
        return new HtmlTree(HtmlTag.CODE);
    }

    public Content getSummaryTableTree(ClassDoc classDoc, List<Content> tableContents) {
        return writer.getSummaryTableTree(this, classDoc, tableContents, showTabs());
    }

    public Content getMemberTree(Content memberTree) {
        return writer.getMemberTree(memberTree);
    }

    public Content getMemberTree(Content memberTree, boolean isLastContent) {
        if (isLastContent)
            return HtmlTree.UL(HtmlStyle.blockListLast, memberTree);
        else
            return HtmlTree.UL(HtmlStyle.blockList, memberTree);
    }
}
