package it.auties.named.util;

import java.util.Arrays;

public record Debugger(boolean debug, boolean skip) {
    public static final String DEBUG_FLAG = "debug";
    public static final String SKIP_FLAG = "skip";

    public Debugger(String... args){
        this(hasFlag(DEBUG_FLAG, args), hasFlag(SKIP_FLAG, args));
    }

    private static boolean hasFlag(String flag, String[] args) {
        return Arrays.stream(args)
                .anyMatch(arg -> arg.equalsIgnoreCase(flag));
    }

    public void debug(Runnable runnable){
        if(!debug){
            return;
        }

        runnable.run();
    }
}