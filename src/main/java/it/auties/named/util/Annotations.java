package it.auties.named.util;

import static com.sun.tools.javac.util.List.nil;

import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import com.sun.tools.javac.tree.TreeInfo;
import it.auties.named.annotation.Option;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Annotations {
  private final static Map<JCAnnotation, JCExpression> annotations = new HashMap<>();

  public static Optional<JCExpression> getDefaultValue(JCAnnotation annotation){
    var cached = annotations.get(annotation);
    if(annotations.get(annotation) != null){
      return Optional.of(cached);
    }

    if(!isOption(annotation)
        || annotation.getArguments() == null
        || annotation.getArguments().isEmpty()){
      return Optional.empty();
    }

    var argument = annotation.getArguments().head;
    var value = argument instanceof JCTree.JCAssign assignment
        ? assignment.getExpression() : argument;
    if(TreeInfo.skipParens(value) instanceof JCLiteral literal
        && literal.getValue() == Option.DEFAULT_VALUE){
      return Optional.empty();
    }

    annotations.put(annotation, value);
    annotation.args = nil();
    return Optional.of(value);
  }

  public static boolean isOption(JCAnnotation annotation) {
    return TreeInfo.name(annotation.getAnnotationType())
        .contentEquals(Option.class.getSimpleName());
  }

  public static boolean hasOptionalModifier(VarSymbol parameter) {
    return parameter.getAnnotation(Option.class) != null;
  }
}