package it.auties.named.plugin;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import it.auties.named.util.Debugger;
import it.auties.named.util.Diagnostics;
import it.auties.named.util.Reflection;

import static com.sun.tools.javac.code.Flags.UNATTRIBUTED;

// Main entry point
public class NamedParameterPlugin implements Plugin, TaskListener {
    private Diagnostics diagnostics;
    private NamedParameterTransformer transformer;
    private JavacPatchScanner patcher;
    private Attr attr;
    private Enter enter;
    private MemberEnter memberEnter;
    private Debugger debugger;

    // Opens Javac's packages by bypassing the add opens mechanism
    static{
        Reflection.openJavac();
    }

    // Initializes the local variables of this object
    @Override
    public void init(JavacTask task, String... args) {
        var context = ((BasicJavacTask) task).getContext();
        this.diagnostics = new Diagnostics(context);
        this.transformer = new NamedParameterTransformer(context);
        this.patcher = new JavacPatchScanner(context);
        this.attr = Attr.instance(context);
        this.enter = Enter.instance(context);
        this.memberEnter = MemberEnter.instance(context);
        this.debugger = new Debugger(args);
        task.addTaskListener(this);
    }

    @Override
    public void started(TaskEvent event) {
        if(event.getKind() != TaskEvent.Kind.ANALYZE){
            return;
        }

        // Get the compilation unit currently being scanned
        var compilationUnit = (JCCompilationUnit) event.getCompilationUnit();

        // Patch javac bug
        patcher.scan(compilationUnit);

        // Disable javac's attribution error handling
        diagnostics.useCachedHandler();
    }

    @Override
    public void finished(TaskEvent event) {
        if(event.getKind() != TaskEvent.Kind.ANALYZE){
            return;
        }

        // Get the compilation unit currently being scanned
        var compilationUnit = (JCCompilationUnit) event.getCompilationUnit();

        // Translate all named invocations inside the unit
        transformer.translate(compilationUnit);
        debugger.debug(() -> System.err.printf("Translated: %n%s%n", compilationUnit));

        // Switch back to javac's error handling
        diagnostics.useJavacHandler();

        // Attribute the unit again
        attribute(compilationUnit);
    }

    // Forces attribution of a compilation unit
    private void attribute(JCCompilationUnit unit){
        unit.getTypeDecls()
                .stream()
                .filter(declaration -> declaration instanceof JCClassDecl)
                .map(declaration -> (JCClassDecl) declaration)
                .forEach(this::attribute);
    }

    // Forces attribution of class
    private void attribute(JCClassDecl classDeclaration) {
        var env = enterClass(classDeclaration);
        classDeclaration.defs.stream()
                .filter(definition -> definition instanceof JCMethodDecl)
                .map(definition -> (JCMethodDecl) definition)
                .map(methodDeclaration -> memberEnter.getMethodEnv(methodDeclaration, env))
                .forEach(methodEnv -> attr.attrib(methodEnv));
    }

    // Forces attribution of class
    private Env<AttrContext> enterClass(JCClassDecl classDeclaration) {
        var env = enter.getClassEnv(classDeclaration.sym);
        classDeclaration.sym.flags_field = classDeclaration.sym.flags_field | UNATTRIBUTED;
        attr.attrib(env);
        return env;
    }

    @Override
    public String getName() {
        return "named";
    }

    @Override
    public boolean autoStart() {
        return true;
    }
}
