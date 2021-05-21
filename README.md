## Shutdown agent

Simple java agent which logs stack trace inside Shutdown.halt and ProcessBuilder.start methods.

---
### How it works
Java agent loads before `main()` method is called and instruments bytecode of the methods in standard Java classes:

* `java.lang.Shutdown#halt`
* `java.lang.ProcessBuilder#start`

Instrumented code writes name and stack trace of current thread to `System.err`. We write this information before any code inside those methods is executed

### How to build


```
git clone git@bitbucket.org:itiapchenko/shutdown-agent.git
cd shutdown-agent
mvn clean package
```

The agent jar can be found in target/lock-snitch-agent.jar

### How to use

To use in your JVM, simply pass `-javaagent:/path/to/shutdown-agent.jar`. Please note: it is important NOT to rename agent JAR file because its filename is hardcoded for classloading hacks.