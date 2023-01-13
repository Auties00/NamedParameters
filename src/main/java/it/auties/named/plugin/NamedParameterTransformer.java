package it.auties.named.plugin;

import static com.sun.tools.javac.tree.TreeInfo.setSymbol;
import static com.sun.tools.javac.tree.TreeInfo.skipParens;
import static com.sun.tools.javac.tree.TreeInfo.symbolFor;
import static com.sun.tools.javac.util.List.nil;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCPolyExpression;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import it.auties.named.util.Annotations;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.IntStream;
import javax.lang.model.type.TypeKind;

// Handles the translation of all invocations known to Java
public class NamedParameterTransformer extends TreeTranslator {
    private static final Map<JCAnnotation, JCExpression> annotations = new HashMap<>();

    private final JavacTrees trees;
    private final Types types;
    public NamedParameterTransformer(Context context){
        this.trees = JavacTrees.instance(context);
        this.types = Types.instance(context);
    }

    // Handles methods' invocations
    @Override
    public void visitApply(JCMethodInvocation invocation) {
        var arguments = translateArguments(invocation, invocation.getArguments());
        invocation.args = List.from(arguments);
        super.visitApply(invocation);
    }

    // Handle classes' initializations
    @Override
    public void visitNewClass(JCTree.JCNewClass initialization) {
        var arguments = translateArguments(initialization, initialization.getArguments());
        initialization.args = List.from(arguments);
        super.visitNewClass(initialization);
    }

    // Translates the arguments provided into an ordered collection
    private Collection<JCExpression> translateArguments(JCPolyExpression expression, List<JCExpression> arguments) {
        // Get the method symbol of the method referenced by this invocation
        // Non-valid methods are de-attributed as Java's attribution handles them
        var invoked = getSymbol(expression, arguments);
        if(invoked.isEmpty()){
            removeAttributes(expression);
            return nil();
        }

        // The three of the invoked method
        var invokedTree = getTree(invoked.orElse(null));

        // A mutable list containing the arguments that don't need to be translated and the translated ones
        // A tree map is used to guarantee the order of the parameters
        var translatedArguments = new TreeMap<Integer, JCExpression>();

        // Iterate through the arguments to add them to translatedArguments in the right order
        var limit = Math.max(arguments.size(), invoked.get().getParameters().size());
        for (var index = 0; index < limit; index++) {
            // Create a default parameter
            // If any parameter was supplied, it will override the default one
            if(invokedTree.isPresent()) {
                var parameter = getOrLast(invokedTree.get().getParameters(), index);
                var defaultValue = getDefaultValue(parameter);
                if (defaultValue.isPresent()) {
                    translatedArguments.put(index, defaultValue.get());
                }
            }

            // Check if the argument is an assignment, required for named parameters
            var argument = skipParens(getOrLast(arguments, index));
            if (!(argument instanceof JCAssign assignment)) {
                translatedArguments.put(index, argument);
                continue;
            }

            // Get the named identifier for the argument, if empty it's not a named parameter
            var argumentIdentifier = getNamedIdentifier(assignment.getVariable());
            if (argumentIdentifier.isEmpty()) {
                translatedArguments.put(index, argument);
                continue;
            }

            // Match the argument to the corresponding parameter
            var parameterIndex = matchArgumentToParameter(argumentIdentifier.get(), invoked.get());
            if(parameterIndex == -1){
                continue;
            }

            // Position the named parameter in the arguments map
            translatedArguments.put(parameterIndex, assignment.getExpression());
        }

        // Return the values
        return translatedArguments.values();
    }

    private Optional<JCMethodDecl> getTree(MethodSymbol invoked) {
        return Optional.ofNullable(invoked)
                .map(trees::getTree);
    }

    private Optional<JCExpression> getDefaultValue(JCVariableDecl parameter) {
        return parameter.getModifiers()
                .getAnnotations()
                .stream()
                .map(Annotations::getCachedAnnotation)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    // Gets the element at the specified index or the last element in the collection
    private <T> T getOrLast(List<? extends T> collection, int index){
        return index < collection.size() ? collection.get(index) :
                collection.get(collection.size() - 1);
    }

    // Attributes the provided invocation using the provided symbol
    private void attribute(JCPolyExpression expression, MethodSymbol methodSymbol) {
        if(methodSymbol == null){
            return;
        }

        if(expression instanceof JCMethodInvocation invocation) {
            setSymbol(invocation.meth, methodSymbol);
            invocation.type = methodSymbol.asType();
            return;
        }

        if(expression instanceof JCNewClass initialization){
            initialization.constructor = methodSymbol;
            initialization.constructorType = methodSymbol.asType();
            initialization.type = methodSymbol.asType();
            return;
        }

        throw new IllegalStateException("Unsupported type: " + expression.getClass().getName());
    }

    // Removes all the attributes from an invocation to make javac do its job later
    private void removeAttributes(JCPolyExpression expression) {
        if(expression instanceof JCMethodInvocation invocation) {
            setSymbol(invocation.meth, null);
            invocation.meth.type = null;
            invocation.type = null;
            invocation.args = fixArguments(invocation.getArguments());
            return;
        }

        if(expression instanceof JCNewClass initialization){
            initialization.constructor = null;
            initialization.constructorType = null;
            initialization.type = null;
            initialization.args = fixArguments(initialization.getArguments());
            return;
        }

        throw new IllegalStateException("Unsupported type: " + expression.getClass().getName());
    }

    // Removes named parameters from a method
    private List<JCExpression> fixArguments(List<JCExpression> arguments) {
        return arguments.stream()
                .map(this::fixArgument)
                .collect(List.collector());
    }

    // Returns the content of a named parameter or the argument
    private JCExpression fixArgument(JCExpression argument) {
        return argument instanceof JCAssign assign && (assign.type == null || assign.type.isErroneous())
                ? assign.getExpression() : argument;
    }

    // Searches for a parameter that matches the name of the argument supplied
    // If no result can be found, -1 is returned
    private int matchArgumentToParameter(JCIdent argument, MethodSymbol invoked) {
        return invoked.getParameters()
                .stream()
                .filter(parameter -> parameter.getSimpleName().contentEquals(argument.getName()))
                .findFirst()
                .map(invoked.getParameters()::indexOf)
                .orElse(-1);
    }

    // Resolves the symbol associated with an invocation(the invoked method)
    public Optional<MethodSymbol> getSymbol(JCPolyExpression expression, List<JCExpression> arguments){
        // Get the underlying symbol
        var symbol = symbolFor(expression);

        // If it's a method symbol, return it
        if (symbol instanceof MethodSymbol methodSymbol) {
            return Optional.of(methodSymbol);
        }

        // If it's a class symbol Javac found an error
        // Obviously a method invocation cannot link to a class
        if(symbol instanceof ClassSymbol classSymbol) {
            // Search for the correct method symbol using non-optional parameters
            var methodSymbol = (MethodSymbol) classSymbol.enclClass()
                    .members()
                    .findFirst(classSymbol.getSimpleName(), candidate -> isAssignable(candidate, arguments));
            // Attribute and return the possible result
            attribute(expression, methodSymbol);
            return Optional.ofNullable(methodSymbol);
        }

        // Otherwise, return empty(undefined behaviour)
        return Optional.empty();
    }

    // Checks if a symbol is assignable to a list of arguments
    private boolean isAssignable(Symbol candidate, List<JCExpression> arguments) {
        if (!(candidate instanceof MethodSymbol methodCandidate)) {
            return false;
        }

        return IntStream.range(0, arguments.size())
                .allMatch(index -> isAssignable(arguments, methodCandidate.getParameters(), index));
    }

    // Check if an argument is assignable to a parameter fetching both from a specified collection using the provided index
    private boolean isAssignable(List<JCExpression> arguments, List<VarSymbol> parameters, int index) {
        var argument = getOrLast(arguments, index);
        var parameter = getOrLast(parameters, index);
        return types.isAssignable(argument.type, parameter.type)
                || Annotations.hasOptionalModifier(parameter);
    }

    // Assignments are supported in a method invocation
    // For example someMethod(name=1000) is valid if name is a valid identifier
    // This method checks if said identifier exists but is erroneous, meaning it refers to a missing variable
    // Which is exactly what is needed to create named parameters and keep support for assignments
    private Optional<JCIdent> getNamedIdentifier(JCExpression expression) {
        return expression instanceof JCIdent identifier
                && identifier.sym != null
                && identifier.sym.asType().getKind() == TypeKind.ERROR ? Optional.of(identifier) : Optional.empty();
    }
}
