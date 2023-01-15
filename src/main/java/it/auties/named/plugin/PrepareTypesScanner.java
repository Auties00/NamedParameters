package it.auties.named.plugin;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import it.auties.named.util.Annotations;
import java.util.Map;

// Javac expects the compilation process to terminate after an unrecoverable syntax error has been found
// Using Diagnostics, though, this behaviour is altered to make the process continue
// This means that the attribution process will not be completed fully
// This scanner is used to add some bogus types just to make Javac not crash because of some asserts and/or NPEs
public class PrepareTypesScanner extends TreeScanner<Map<JCAnnotation, JCExpression>, Void> {
    private final Symtab symtab;
    public PrepareTypesScanner(Context context) {
        this.symtab = Symtab.instance(context);
    }

    // Bogus types for methods
    @Override
    public Map<JCAnnotation, JCExpression> visitMethodInvocation(MethodInvocationTree node, Void ignored) {
        var invocation = (JCMethodInvocation) node;
        if(invocation.meth.type == null || invocation.meth.type.isErroneous()){
            invocation.meth.type = new MethodType(
                invocation.args.map(e -> symtab.errorType),
                symtab.errorType,
                List.nil(),
                null
            );
        }

        return super.visitMethodInvocation(node, ignored);
    }

    // Bogus types for classes
    @Override
    public Map<JCAnnotation, JCExpression> visitNewClass(NewClassTree node, Void ignored) {
        var newClass = (JCNewClass) node;
        if(newClass.constructorType == null || newClass.constructorType.isErroneous()){
            newClass.constructorType = new MethodType(
                newClass.args.map(e -> symtab.errorType),
                symtab.errorType,
                List.nil(),
                null
            );
        }

        return super.visitNewClass(node, ignored);
    }

    // Cache the default value of the annotation and remove its value from the AST to make the compilation process work
    @Override
    public Map<JCAnnotation, JCExpression> visitAnnotation(AnnotationTree node, Void unused) {
        var annotation = (JCAnnotation) node;
        if(!Annotations.isOption(annotation)){
            return super.visitAnnotation(node, unused);
        }

        var argument = Annotations.getDefaultValue(annotation);
        if(argument.isEmpty()){
            return super.visitAnnotation(node, unused);
        }

        annotation.args = List.nil();
        annotation.attribute = null;
        return super.visitAnnotation(node, unused);
    }

    public void scan(Tree tree) {
        super.scan(tree, null);
    }
}
