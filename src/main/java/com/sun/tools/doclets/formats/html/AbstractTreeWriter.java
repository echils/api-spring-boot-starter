package com.sun.tools.doclets.formats.html;

import com.sun.javadoc.ClassDoc;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.ClassTree;
import com.sun.tools.doclets.internal.toolkit.util.DocPath;
import com.sun.tools.doclets.internal.toolkit.util.Util;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public abstract class AbstractTreeWriter extends HtmlDocletWriter {

    private static final String LI_CIRCLE = "circle";
    protected final ClassTree classtree;

    protected AbstractTreeWriter(ConfigurationImpl configuration,
                                 DocPath filename, ClassTree classtree)
            throws IOException {
        super(configuration, filename);
        this.classtree = classtree;
    }

    protected void addLevelInfo(ClassDoc parent, List<ClassDoc> list,
                                boolean isEnum, Content contentTree) {
        int size = list.size();
        if (size > 0) {
            Content ul = new HtmlTree(HtmlTag.UL);
            for (int i = 0; i < size; i++) {
                ClassDoc local = list.get(i);
                HtmlTree li = new HtmlTree(HtmlTag.LI);
                li.addAttr(HtmlAttr.TYPE, LI_CIRCLE);
                addPartialInfo(local, li);
                addExtendsImplements(parent, local, li);
                addLevelInfo(local, classtree.subs(local, isEnum),
                        isEnum, li);
                ul.addContent(li);
            }
            contentTree.addContent(ul);
        }
    }

    protected void addTree(List<ClassDoc> list, String heading, Content div) {
        if (list.size() > 0) {
            ClassDoc firstClassDoc = list.get(0);
            Content headingContent = getResource(heading);
            div.addContent(HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING, true,
                    headingContent));
            addLevelInfo(!firstClassDoc.isInterface() ? firstClassDoc : null,
                    list, list == classtree.baseEnums(), div);
        }
    }

    protected void addExtendsImplements(ClassDoc parent, ClassDoc cd,
                                        Content contentTree) {
        ClassDoc[] interfaces = cd.interfaces();
        if (interfaces.length > (cd.isInterface() ? 1 : 0)) {
            Arrays.sort(interfaces);
            int counter = 0;
            for (int i = 0; i < interfaces.length; i++) {
                if (parent != interfaces[i]) {
                    if (!(interfaces[i].isPublic() ||
                            Util.isLinkable(interfaces[i], configuration))) {
                        continue;
                    }
                    if (counter == 0) {
                        if (cd.isInterface()) {
                            contentTree.addContent(" (");
                            contentTree.addContent(getResource("doclet.also"));
                            contentTree.addContent(" extends ");
                        } else {
                            contentTree.addContent(" (implements ");
                        }
                    } else {
                        contentTree.addContent(", ");
                    }
                    addPreQualifiedClassLink(LinkInfoImpl.Kind.TREE,
                            interfaces[i], contentTree);
                    counter++;
                }
            }
            if (counter > 0) {
                contentTree.addContent(")");
            }
        }
    }

    protected void addPartialInfo(ClassDoc cd, Content contentTree) {
        addPreQualifiedStrongClassLink(LinkInfoImpl.Kind.TREE, cd, contentTree);
    }

    protected Content getNavLinkTree() {
        Content li = HtmlTree.LI(HtmlStyle.navBarCell1Rev, treeLabel);
        return li;
    }
}
