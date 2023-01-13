package it.auties.named.util;

import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Log.DiagnosticHandler;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;

public class Diagnostics {
    private final Log javacLogger;
    private final JavaCompiler javaCompiler;

    private final Field diagnosticHandlerField;
    private final DiagnosticHandler diagnosticHandler;

    private final Field defferedDiagnosticHandlerField;
    private final DiagnosticHandler deffereDiagnosticHandler;

    private final CachedDiagnosticHandler cachedDiagnosticHandler;

    public Diagnostics(Context context) {
        try {
            Attr attr = Attr.instance(context);
            this.cachedDiagnosticHandler = new CachedDiagnosticHandler();
            this.javaCompiler = JavaCompiler.instance(context);
            var javacLoggerField = attr.getClass()
                .getDeclaredField("log");
            Reflection.open(javacLoggerField);
            this.javacLogger = (Log) javacLoggerField.get(attr);
            this.diagnosticHandlerField =  javacLogger.getClass()
                .getDeclaredField("diagnosticHandler");
            Reflection.open(diagnosticHandlerField);
            this.diagnosticHandler = (DiagnosticHandler) diagnosticHandlerField.get(javacLogger);
            this.defferedDiagnosticHandlerField = javaCompiler.getClass()
                .getDeclaredField("deferredDiagnosticHandler");
            Reflection.open(defferedDiagnosticHandlerField);
            this.deffereDiagnosticHandler = (DiagnosticHandler) defferedDiagnosticHandlerField.get(javaCompiler);
        }catch (ReflectiveOperationException exception){
            throw new RuntimeException("Cannot run diagnostics", exception);
        }
    }

    public void useCachedHandler() {
        try {
            diagnosticHandlerField.set(javacLogger, cachedDiagnosticHandler);
            defferedDiagnosticHandlerField.set(javaCompiler, cachedDiagnosticHandler);
        } catch (IllegalAccessException exception) {
            throw new RuntimeException("Cannot switch to cached handler", exception);
        }
    }

    public void useJavacHandler() {
        try {
            diagnosticHandlerField.set(javacLogger, diagnosticHandler);
            defferedDiagnosticHandlerField.set(javaCompiler, deffereDiagnosticHandler);
        } catch (IllegalAccessException exception) {
            throw new RuntimeException("Cannot switch to javac handler", exception);
        }
    }


    public static class CachedDiagnosticHandler extends DiagnosticHandler {
        private final Queue<JCDiagnostic> cachedErrors; // Could be useful
        public CachedDiagnosticHandler() {
            this.cachedErrors = new LinkedList<>();
        }

        @Override
        public void report(JCDiagnostic diagnostic) {
            if (diagnostic == null) {
                return;
            }

            cachedErrors.add(diagnostic);
        }

        @SuppressWarnings("unused")
        public Collection<JCDiagnostic> cachedErrors() {
            return Collections.unmodifiableCollection(cachedErrors);
        }
    }
}