package com.atlassian.itiapchenko.shutdown.agent;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.Descriptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShutdownDecorator implements ClassFileTransformer {

    private final Map<ClassName, List<String>> methodsByClass = new HashMap<>();

    private final List<Class<?>> classes = new ArrayList<>();

    ShutdownDecorator() throws ClassNotFoundException {
        methodsByClass.put(ClassName.SHUTDOWN, Arrays.asList("halt"));
        methodsByClass.put(ClassName.PROCESS_BUILDER, Arrays.asList("start"));

        for (ClassName className : methodsByClass.keySet()) {
            classes.add(Class.forName(className.toString().replace('/', '.')));
        }
    }

    void instrument(Instrumentation instrumentation) {
        try {
            instrumentation.addTransformer(this, true);
            // This is a required step because these classes are loaded before the agent
            instrumentation.retransformClasses(classes.toArray(new Class[0]));
        } catch (UnmodifiableClassException e) {
            throw new RuntimeException("Failed to initialize instrumentation");
        }
    }

    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] bytecode) {
        for (ClassName targetClass : methodsByClass.keySet()) {
            if (targetClass.toString().equals(className)) {
                return instrumentMethods(bytecode, targetClass, methodsByClass.get(targetClass));
            }
        }

        return bytecode;
    }

    private byte[] instrumentMethods(byte[] bytecode, ClassName className, Collection<String> methods) {
        try {
            return doInstrumentMethods(bytecode, className, methods);
        } catch (NotFoundException | CannotCompileException | IOException e) {
            throw new RuntimeException("Instrumentation failed", e);
        }
    }

    private byte[] doInstrumentMethods(byte[] bytecode, ClassName className, Collection<String> methods) throws NotFoundException, CannotCompileException, IOException {
        if (methods.isEmpty()) {
            return bytecode;
        }

        final ClassPool pool = ClassPool.getDefault();
        final CtClass classDefinition = pool.makeClass(new ByteArrayInputStream(bytecode));

        AgentLogger.print("Instrumenting {0} methods for class {1}", methods.size(), className);

        for (String methodName : methods) {
            AgentLogger.print("Inserting new code to the method {0}", methodName);
            switch (className){
                case SHUTDOWN:
                    instrumentShutdownHaltMethod(classDefinition, methodName);
                    break;

                case PROCESS_BUILDER:
                    instrumentProcessBuilderStartMethod(pool, classDefinition, methodName);
                    break;
                default:
                    throw new IllegalStateException("Unknown class name "+ className);
            }
        }

        byte[] newBytecode = classDefinition.toBytecode();
        classDefinition.detach();
        AgentLogger.print("Done! The length of the new bytecode is {0}", newBytecode.length);
        return newBytecode;
    }

    private void instrumentProcessBuilderStartMethod(ClassPool pool, CtClass classDefinition, String methodName) throws NotFoundException, CannotCompileException {
        CtClass processClass = pool.get("java.lang.Process");
        final CtMethod method = classDefinition.getMethod(methodName,
                Descriptor.ofMethod(processClass, new CtClass[0]));

        method.insertBefore("System.err.println(\"Agent detected process creation by ProcessBuilder.start() method. Commands passed to the builder: \" + String.join(\", \", command)); " +
                "System.err.println(\"Current thread name:\" + Thread.currentThread().getName()); " +
                "Thread.dumpStack();");
    }

    private void instrumentShutdownHaltMethod(CtClass classDefinition, String methodName) throws NotFoundException, CannotCompileException {
        final CtMethod method = classDefinition.getMethod(methodName,
                Descriptor.ofMethod(CtClass.voidType, new CtClass[]{CtClass.intType}));
        method.insertBefore("System.err.println(\"Shutdown agent detected Shutdown.halt() call. Current thread name:\" + Thread.currentThread().getName()); " +
                "Thread.dumpStack();");
    }

    private enum ClassName {
        SHUTDOWN("java/lang/Shutdown"),
        PROCESS_BUILDER("java/lang/ProcessBuilder");

        private final String className;

        ClassName(String className) {
            this.className = className;
        }

        @Override
        public String toString(){
            return className;
        }
    }
}
