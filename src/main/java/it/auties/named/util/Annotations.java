package it.auties.named.util;

import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.TreeInfo;
import it.auties.named.annotation.Optional;

import java.util.HashMap;
import java.util.Map;

import static com.sun.tools.javac.util.List.nil;
import static java.util.Optional.ofNullable;

public class Annotations {
    private final static Map<JCAnnotation, JCExpression> CACHED_ANNOTATIONS = new HashMap<>();

    public static JCExpression addAnnotation(JCAnnotation annotation){
        if(!hasOptionalModifier(annotation)
                || annotation.getArguments() == null
                || annotation.getArguments().isEmpty()){
            return null;
        }

        var argument = annotation.getArguments().head;
        var value = argument instanceof JCTree.JCAssign assignment ? assignment.getExpression() : argument;
        CACHED_ANNOTATIONS.put(annotation, value);
        annotation.args = nil();
        return value;
    }

    public static java.util.Optional<JCExpression> getCachedAnnotation(JCAnnotation annotation){
        return ofNullable(CACHED_ANNOTATIONS.containsKey(annotation) ? CACHED_ANNOTATIONS.get(annotation)
                : addAnnotation(annotation));
    }

    public static boolean hasOptionalModifier(JCAnnotation annotation) {
        return TreeInfo.name(annotation.getAnnotationType())
                .contentEquals(Optional.class.getSimpleName());
    }

    public static boolean hasOptionalModifier(VarSymbol parameter) {
        return parameter.getAnnotation(Optional.class) != null;
    }
}
