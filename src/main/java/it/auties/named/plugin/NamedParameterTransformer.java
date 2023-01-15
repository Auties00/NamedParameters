package it.auties.named.plugin;

import static com.sun.tools.javac.tree.TreeInfo.setSymbol;
import static com.sun.tools.javac.tree.TreeInfo.skipParens;
import static com.sun.tools.javac.tree.TreeInfo.symbolFor;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type.ArrayType;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCPolyExpression;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import it.auties.named.util.Annotations;
import it.auties.named.util.Diagnostics;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.lang.model.type.TypeKind;

// Handles the translation of all invocations known to Java
public class NamedParameterTransformer extends TreeTranslator {
    // Some useful utility types from Javac
    private final JavacTrees trees;
    private final Types types;
    private final Diagnostics diagnostics;
    private TreeMaker maker;
    private Symtab symtab;

    public NamedParameterTransformer(Context context, Diagnostics diagnostics){
        this.trees = JavacTrees.instance(context);
        this.types = Types.instance(context);
        this.diagnostics = diagnostics;
        this.maker = TreeMaker.instance(context);
        this.symtab = Symtab.instance(context);
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
            return arguments;
        }

        // Gets the tree of the method that was invoked
        // Needed for @Option annotation
        var invokedTree = trees.getTree(invoked.get());
        if(invokedTree == null){
            return arguments;
        }

        // This map is used as fast access to the expressions associated with a parameter name
        // A collection is used because of varargs
        var namedArgsMap = new HashMap<String, Collection<JCAssign>>();

        // This bit set is used to determine if the named argument should be parsed before the positional argument or vice versa
        // This is important because if the method call looks something like:
        // void whatever(int... params);
        // whatever(params=10, 11);
        // Then we need to remember the order or the values will get mixed up
        var orderBitSet = new BitSet(arguments.size());

        // A collection of ordered positional arguments
        var positionalArguments = new ArrayList<JCExpression>();

        // Populate the collections defined before
        for (var index = 0; index < arguments.size(); index++) {
            var argument = arguments.get(index);
            if (!(skipParens(argument) instanceof JCAssign assignment)) {
                positionalArguments.add(argument); // Not an assignment, must be a positional argument
                continue;
            }

            var identifier = getNamedIdentifier(assignment.getVariable());
            if (identifier.isEmpty()) {
                positionalArguments.add(argument); // Not an erroneous assigment, must be a positional argument
                continue;
            }

            var name = identifier.get().getName().toString();
            var known = namedArgsMap.get(name);
            if (known != null) {
                known.add(assignment); // Add the named argument to the ones known(var args)
                continue;
            }

            // Add the newly found named argument to the map
            var collection = new ArrayList<JCAssign>();
            collection.add(assignment);
            namedArgsMap.put(name, collection);
            orderBitSet.set(index);
        }

        // Just an iterator to parse positional arguments
        var positionalArgsIterator = positionalArguments.iterator();

        // Now map the parameters to the right argument
        var results = new ArrayList<JCExpression>();
        for(var index = 0; index < invokedTree.getParameters().size(); index++){
            // Get the parameter to parse
            var parameter = invokedTree.getParameters().get(index);

            // Get the available named argument
            var namedArgumentName = parameter.getName().toString();

            // If the parameter has var args, use the bit set to determine what should be done first
            // If the parameter has var args, the default value isn't needed as it's provided by Javac automatically
            if(hasVarArgs(parameter)) {
                var oldSize = results.size();
                if(orderBitSet.get(index)){
                    results.addAll(getNamedArguments(namedArgumentName, namedArgsMap));
                    positionalArgsIterator.forEachRemaining(results::add);
                    if(oldSize == results.size()){
                        var defaultValue = getDefaultValue(parameter);
                        defaultValue.ifPresent(results::add);
                    }
                }else {
                    positionalArgsIterator.forEachRemaining(results::add);
                    results.addAll(getNamedArguments(namedArgumentName, namedArgsMap));
                    if(oldSize == results.size()){
                        var defaultValue = getDefaultValue(parameter);
                        defaultValue.ifPresent(results::add);
                    }
                }
            } else {
                var namedArguments = getNamedArguments(namedArgumentName, namedArgsMap);
                if(!namedArguments.isEmpty()) {
                    results.addAll(namedArguments);
                }else if (positionalArgsIterator.hasNext()) {
                    results.add(positionalArgsIterator.next());
                }else {
                    var defaultValue = getDefaultValue(parameter);
                    defaultValue.ifPresent(results::add);
                }
            }
        }

        // Check if all named arguments have been resolved
        if(!namedArgsMap.isEmpty()){
            removeAttributes(expression);
            return arguments;
        }

        // Check if all positional arguments have been resolved
        if(positionalArgsIterator.hasNext()){
            removeAttributes(expression);
            return arguments;
        }

        // Return the results
        return results;
    }

    private Collection<JCExpression> getNamedArguments(String name, Map<String, Collection<JCAssign>> map) {
        var namedArguments = map.get(name);
        if (namedArguments == null) {
            return List.nil();
        }

        namedArguments.stream()
            .map(JCAssign::getVariable)
            .forEach(diagnostics::markResolved);
        map.remove(name);
        return namedArguments.stream()
            .map(JCAssign::getExpression)
            .toList();
    }

    // Checks if a parameter is varargs
    private boolean hasVarArgs(JCVariableDecl parameter) {
        return (parameter.mods.flags & Flags.VARARGS) != 0;
    }

    // Gets the default value from the @Option annotation or creates it
    private Optional<JCExpression> getDefaultValue(JCVariableDecl parameter) {
        var result = parameter.getModifiers()
            .getAnnotations()
            .stream()
            .filter(Annotations::isOption)
            .findFirst()
            .flatMap(Annotations::getDefaultValue)
            .or(() -> createDefaultValue(parameter));
        result.ifPresent(diagnostics::markResolved);
        return result;
    }

    // Creates the correct default value if none was specified
    private Optional<JCExpression> createDefaultValue(JCVariableDecl parameter) {
        var type = parameter.sym.asType();
        if(hasVarArgs(parameter)){
            return Optional.empty();
        }

        if(type instanceof ArrayType arrayType){
            var arrayComponent = maker.Type(arrayType.getComponentType());
            var dimensions = maker.Literal(0);
            var newArray = maker.NewArray(arrayComponent, List.of(dimensions), null);
            return Optional.of(newArray);
        }

        if(!type.isPrimitive() || type.isErroneous()){
            return Optional.of(maker.Literal(TypeTag.BOT, null)
                .setType(symtab.botType));
        }

        return Optional.of(switch (type.getKind()) {
            case BYTE -> maker.Literal((byte) 0);
            case CHAR -> maker.Literal((char) 0);
            case SHORT -> maker.Literal((short) 0);
            case INT -> maker.Literal(0);
            case LONG -> maker.Literal(0L);
            case FLOAT -> maker.Literal(0.0F);
            case DOUBLE -> maker.Literal(0.0D);
            case BOOLEAN -> maker.Literal(false);
            default -> throw new IllegalArgumentException(
                "Unknown primitive type: %s".formatted(type.getKind().name()));
        });
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
            var methodSymbol = getProbableSymbol(arguments, classSymbol);
            if(methodSymbol.isEmpty()){
                return Optional.empty();
            }

            // Attribute and return the possible result
            attribute(expression, methodSymbol.get());

            // Return it
            return methodSymbol;
        }

        // Otherwise, return empty
        return Optional.empty();
    }

    // Tries to infer the method symbol from an erroneous one
    // If a perfect match is found, it will be returned instantly
    // Otherwise the closest will be chosen
    private Optional<MethodSymbol> getProbableSymbol(List<JCExpression> arguments, ClassSymbol classSymbol) {
        var methods = classSymbol.enclClass()
            .members()
            .getSymbolsByName(classSymbol.getSimpleName(), symbol -> symbol instanceof MethodSymbol)
            .iterator();
        MethodSymbol bestMatch = null;
        long bestCounter = -1;
        while (methods.hasNext()){ // This code would be nicer with streams, but less efficient
            var method = (MethodSymbol) methods.next();
            var counter = IntStream.range(0, arguments.size())
                .filter(index -> isAssignable(arguments, method.getParameters(), index))
                .count();
            if(counter > bestCounter){
                bestMatch = method;
                bestCounter = counter;
            }

            if(bestCounter == arguments.size() - 1){
                break;
            }
        }

        return Optional.ofNullable(bestMatch);
    }

    // Check if an argument is assignable to a parameter fetching both from a specified collection using the provided index
    private boolean isAssignable(List<JCExpression> arguments, List<VarSymbol> parameters, int index) {
        var argument = getOrLast(arguments, index);
        var parameter = getOrLast(parameters, index);
        return types.isAssignable(argument.type, parameter.type)
                || Annotations.hasOptionalModifier(parameter);
    }

    // Gets the element at the specified index or the last element in the collection
    private <T> T getOrLast(List<? extends T> collection, int index){
        return index < collection.size() ? collection.get(index) :
            collection.get(collection.size() - 1);
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
