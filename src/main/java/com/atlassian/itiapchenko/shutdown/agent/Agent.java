package com.atlassian.itiapchenko.shutdown.agent;

import java.lang.instrument.Instrumentation;

/**
 * Java agent that can write name and stack trace of the thread that called Shutdown.halt
 */
public class Agent {
    /**
     * Entry point for agent
     */
    public static void premain(final String args, final Instrumentation instrumentation) throws Exception {
        instrumentLockBytecode(instrumentation);
    }

    private static void instrumentLockBytecode(Instrumentation instrumentation) throws ClassNotFoundException {
        new ShutdownDecorator().instrument(instrumentation);
        AgentLogger.print("All bytecode has been instrumented");
    }
}
