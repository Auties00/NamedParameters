package it.auties.named.plugin;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCNewClass;

// Removes the attributed types to prepare for attribution
// This is needed for correct error handling
public class RemoveTypesScanner extends TreeScanner<Void, Void> {
    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void ignored) {
        var invocation = (JCMethodInvocation) node;
        invocation.type = null;
        invocation.meth.type = null;
        return super.visitMethodInvocation(node, ignored);
    }

    @Override
    public Void visitNewClass(NewClassTree node, Void ignored) {
        var newClass = (JCNewClass) node;
        newClass.type = null;
        newClass.constructor = null;
        newClass.constructorType = null;
        return super.visitNewClass(node, ignored);
    }
}
