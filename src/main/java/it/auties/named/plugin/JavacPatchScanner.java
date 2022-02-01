package it.auties.named.plugin;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.util.Context;

// Fixes a bug in javac to not make it crash
// Kind of my fault that it happens, so it's not worth even reporting it
public class JavacPatchScanner extends TreeScanner<Void, Void> {
    private final Symtab symtab;
    public JavacPatchScanner(Context context) {
        this.symtab = Symtab.instance(context);
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
        var invocation = (JCMethodInvocation) node;
        if(invocation.meth.type == null || invocation.meth.type.isErroneous()){
            invocation.meth.type = symtab.unknownType;
        }

        return super.visitMethodInvocation(node, unused);
    }

    public void scan(JCTree tree) {
        scan(tree, null);
    }
}
