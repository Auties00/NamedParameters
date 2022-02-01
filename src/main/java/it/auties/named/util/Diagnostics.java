package it.auties.named.util;

import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.JCDiagnostic.Factory;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Log.DiagnosticHandler;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.ExtensionMethod;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.Queue;

@Value
@Accessors(fluent = true)
@ExtensionMethod(Reflection.class)
public class Diagnostics {
    Factory factory;
    CachedDiagnosticHandler handler;
    Log javacLogger;
    Field javacDiagnosticHandlerField;
    DiagnosticHandler javacDiagnosticHandler;

    @SneakyThrows
    public Diagnostics(Context context) {
        var attr = Attr.instance(context);
        this.factory = Factory.instance(context);
        this.handler = new CachedDiagnosticHandler();
        this.javacLogger = (Log) attr.getClass()
                .getDeclaredField("log")
                .open()
                .get(attr);
        this.javacDiagnosticHandlerField = javacLogger.getClass()
                .getDeclaredField("diagnosticHandler")
                .open();
        this.javacDiagnosticHandler = (DiagnosticHandler) javacDiagnosticHandlerField.get(javacLogger);
    }

    @SneakyThrows
    public void useCachedHandler() {
        javacDiagnosticHandlerField.set(javacLogger, handler);
    }

    @SneakyThrows
    public void useJavacHandler() {
        javacDiagnosticHandlerField.set(javacLogger, javacDiagnosticHandler);
    }

    @AllArgsConstructor
    @Value
    @Accessors(fluent = true)
    @EqualsAndHashCode(callSuper = true)
    public static class CachedDiagnosticHandler extends DiagnosticHandler {
        Queue<JCDiagnostic> cachedErrors; // Could be useful

        public CachedDiagnosticHandler() {
            this(new LinkedList<>());
        }

        @Override
        public void report(JCDiagnostic diagnostic) {
            if (diagnostic == null) {
                return;
            }

            cachedErrors.add(diagnostic);
        }
    }
}