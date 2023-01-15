package it.auties.named.util;

import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Log.DeferredDiagnosticHandler;
import com.sun.tools.javac.util.Log.DiagnosticHandler;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

public class Diagnostics {
    private final Log javacLogger;
    private final JavaCompiler javaCompiler;

    private final Field diagnosticHandlerField;
    private final DiagnosticHandler diagnosticHandler;

    private final Field defferedDiagnosticHandlerField;
    private final DiagnosticHandler deferredDiagnosticHandler;

    private final CustomDeferredDiagnosticHandler customDeferredDiagnosticHandler;

    public Diagnostics(Context context) {
        try {
            var attr = Attr.instance(context);
            var javacLoggerField = attr.getClass()
                .getDeclaredField("log");
            Reflection.open(javacLoggerField);
            this.javacLogger = (Log) javacLoggerField.get(attr);

            this.javaCompiler = JavaCompiler.instance(context);

            this.diagnosticHandlerField =  javacLogger.getClass()
                .getDeclaredField("diagnosticHandler");
            Reflection.open(diagnosticHandlerField);
            this.diagnosticHandler = (DiagnosticHandler) diagnosticHandlerField.get(javacLogger);

            this.defferedDiagnosticHandlerField = javaCompiler.getClass()
                .getDeclaredField("deferredDiagnosticHandler");
            Reflection.open(defferedDiagnosticHandlerField);
            this.deferredDiagnosticHandler = (DiagnosticHandler) defferedDiagnosticHandlerField.get(javaCompiler);

            this.customDeferredDiagnosticHandler = new CustomDeferredDiagnosticHandler();
        }catch (ReflectiveOperationException exception){
            throw new RuntimeException("Cannot run diagnostics", exception);
        }
    }

    public void useCachedHandler() {
        try {
            diagnosticHandlerField.set(javacLogger, customDeferredDiagnosticHandler);
            defferedDiagnosticHandlerField.set(javaCompiler, customDeferredDiagnosticHandler);
        } catch (IllegalAccessException exception) {
            throw new RuntimeException("Cannot switch to cached handler", exception);
        }
    }

    public void useJavacHandler() {
        try {
            diagnosticHandlerField.set(javacLogger, diagnosticHandler);
            defferedDiagnosticHandlerField.set(javaCompiler, deferredDiagnosticHandler);
            customDeferredDiagnosticHandler.reportAll();
        } catch (IllegalAccessException exception) {
            throw new RuntimeException("Cannot switch to javac handler", exception);
        }
    }

    public void markResolved(JCTree tree) {
        customDeferredDiagnosticHandler.cachedErrors()
            .stream()
            .filter(entry -> Objects.equals(tree, entry.getDiagnosticPosition().getTree()))
            .findFirst()
            .ifPresent(customDeferredDiagnosticHandler::markResolved);
    }

    private class CustomDeferredDiagnosticHandler extends DeferredDiagnosticHandler {
        private final Map<JCDiagnostic, Boolean> cachedErrors;

        private CustomDeferredDiagnosticHandler() {
            super(javacLogger);
            this.cachedErrors = new HashMap<>();
        }

        @Override
        public void report(JCDiagnostic diagnostic) {
            if (diagnostic == null) {
                return;
            }

            cachedErrors.put(diagnostic, true);
        }

        private Collection<JCDiagnostic> cachedErrors() {
            return Collections.unmodifiableCollection(cachedErrors.keySet());
        }

        private void markResolved(JCDiagnostic diagnostic) {
            cachedErrors.put(diagnostic, false);
        }

        private void reportAll(){
            cachedErrors.entrySet()
                .stream()
                .filter(Entry::getValue)
                .map(Entry::getKey)
                .forEach(diagnosticHandler::report);
            cachedErrors.clear();
        }
    }
}