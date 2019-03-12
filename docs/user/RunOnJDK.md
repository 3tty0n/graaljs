# Run Graal JavaScript on a stock JDK

Graal JavaScript is optimized for execution as part of GraalVM or in an embedding scenario built on the GraalVM.
This guarantees best possible performance by using [Graal](https://github.com/oracle/graal) as the optimizing compiler and potentially [SubstrateVM](https://github.com/oracle/graal/tree/master/substratevm) to ahead-of-time compile the engine into a native binary.

As Graal JavaScript is a Java application, it is possible to execute it on a stock Java VM like OpenJDK.
When executed without Graal as optimizing compiler, performance of Graal JavaScript will be significantly worse.
While the JIT compilers available on stock JVMs can execute and JIT-compile the Graal JavaScript codebase, they cannot optimize it to its full performance potential.
This document describes how to run Graal JavaScript on stock Java VMs, while using Graal as JIT compiler to guarantee best possible performance.

## Graal JavaScript on Maven
Graal and Graal JavaScript are open source and are regularly pushed to Maven by the community.
You can find it as package [org.graalvm.js](https://mvnrepository.com/artifact/org.graalvm.js/js).

## Graal JavaScript Maven example
We have prepared and published an exemplar Maven project for Graal JavaScript on JDK11 (or later) using Graal as optimizing compiler at [graal-js-jdk11-maven-demo](https://github.com/graalvm/graal-js-jdk11-maven-demo).
The example contains a Maven project for a JavaScript benchmark (a prime number generator).
It allows to compare the performance of Graal JavaScript running with or without Graal as optimizing compiler.
Running with Graal will siginificantly improve the execution performance of any significantly large JavaScript codebase.

In essence, the example pom file activates JVMCI to install additional JIT compilers, and configures the JIT compiler to be Graal by providing it on `--module-path` and `--upgrade-module-path`.

## Graal JavaScript without Maven - JAR files from GraalVM
To work without Maven, the JAR files from a GraalVM release can be used as well.
GraalVM is available on the [Oracle Technology Network](http://www.oracle.com/technetwork/oracle-labs/program-languages/downloads/index.html) (the Enterprise Edition) and on [GitHub](https://github.com/oracle/graal/releases) (the Community Edition).
Both editions' files can be used.

The relevant files are:
* $GRAALVM/jre/languages/js/graaljs.jar - core component of Graal JavaScript (always required)
* $GRAALVM/jre/tools/regex/tregex.jar - Graal's regular expression engine (always required)
* $GRAALVM/jre/lib/boot/graal-sdk.jar - Graal's SDK to implement languages (always required)
* $GRAALVM/jre/lib/truffle/truffle-api.jar - Graal's Truffle API, to implement language interpreters (always required)
* $GRAALVM/jre/lib/graalvm/graaljs-launcher.jar - Graal JavaScript's command line interpreter (optional)
* $GRAALVM/jre/lib/graalvm/launcher-common.jar - Common launcher code shared by all languages (required by graaljs-launcher.jar)
* $GRAALVM/jre/lib/boot/graaljs-scriptengine.jar - Graal JavaScript's ScriptEngine/JSR 223 support (optional)

## Graal JavaScript on JDK 8
This command line executes Graal JavaScript on a JDK 8, starting a JavaScript console:

```
GRAALVM=/path/to/GraalVM
/path/to/jdk8/bin/java -cp $GRAALVM/jre/lib/graalvm/launcher-common.jar:$GRAALVM/jre/lib/graalvm/graaljs-launcher.jar:$GRAALVM/jre/languages/js/graaljs.jar:$GRAALVM/jre/lib/truffle/truffle-api.jar:$GRAALVM/jre/lib/boot/graal-sdk.jar:$GRAALVM/jre/lib/boot/graaljs-scriptengine.jar:$GRAALVM/jre/tools/regex/tregex.jar com.oracle.truffle.js.shell.JSLauncher
```

To start a Java application instead and launch Graal JavaScript via Graal SDK's `Context` (encouraged) or a `ScriptEngine` (supported, but discouraged), the launcher-common.jar and the graaljs-launcher.jar can be omitted (see example below).

### ScriptEngine JSR 223
Graal JavaScript can be started via a `ScriptEngine` when graaljs-scriptengine.jar is included on the classpath.
The engine registers under several different names, e.g. `Graal.js`.
Note that the Nashorn engine might be available under its names as well.

To start Graal JavaScript from a ScriptEngine, the following code can be used:

```java
new ScriptEngineManager().getEngineByName("graal.js");
```

To list all available engines:

```java
List<ScriptEngineFactory> engines = (new ScriptEngineManager()).getEngineFactories();
for (ScriptEngineFactory f: engines) {
    System.out.println(f.getLanguageName()+" "+f.getEngineName()+" "+f.getNames().toString());
}
```

Assuming this code is called from `MyJavaApp.java` and this is properly compiled to a class file, it can be executed with:

```
GRAALVM=/path/to/GraalVM
/path/to/jdk8/bin/java -cp $GRAALVM/jre/languages/js/graaljs.jar:$GRAALVM/jre/lib/truffle/truffle-api.jar:$GRAALVM/jre/lib/boot/graal-sdk.jar:$GRAALVM/jre/lib/boot/graaljs-scriptengine.jar:$GRAALVM/jre/tools/regex/tregex.jar:. MyJavaApp
```

## Graal JavaScript on JDK 11
The Maven example as given above is the preferred way to start on JDK 11.
Working without Maven, you have to provide the JAR files manually and provide them to the Java command.
Using --upgrade-module-path executes Graal JavaScript with Graal as optimizing compiler, guaranteeing best performance.
For that, a [Graal compiler](https://github.com/oracle/graal) built with JDK 11 is required.

```
GRAALVM=/path/to/GraalVM
GRAAL_JDK11=/path/to/Graal
/path/to/jdk-11/bin/java -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -XX:+UseJVMCICompiler --module-path=$GRAAL_JDK11/graal/sdk/mxbuild/modules/org.graalvm.graal_sdk.jar:$GRAAL_JDK11/graal/truffle/mxbuild/modules/com.oracle.truffle.truffle_api.jar --upgrade-module-path=$GRAAL_HOME/graal/compiler/mxbuild/modules/jdk.internal.vm.compiler.jar:$GRAAL_HOME/graal/compiler/mxbuild/modules/jdk.internal.vm.compiler.management.jar -cp $GRAALVM/jre/lib/graalvm/launcher-common.jar:$GRAALVM/jre/lib/graalvm/graaljs-launcher.jar:$GRAALVM/jre/languages/js/graaljs.jar:$GRAALVM/jre/lib/truffle/truffle-api.jar:$GRAALVM/jre/lib/boot/graal-sdk.jar:$GRAALVM/jre/lib/boot/graaljs-scriptengine.jar:$GRAALVM/jre/tools/regex/tregex.jar -Dgraal.TraceTruffleCompilation=true com.oracle.truffle.js.shell.JSLauncher
```

## Inspecting the setup - Is Graal used as JIT compiler?
The `-Dgraal.TraceTruffleCompilation=true` flag enables a debug output whenever a JavaScript method is compiled by Graal.
JavaScript source code with long-enough run time will trigger the compilation and print such log output:

```
> function add(a,b) { return a+b; }; for (var i=0;i<1000*1000;i++) { add(i,i); }
[truffle] opt done         add <opt> <split-c0875dd>                                   |ASTSize       7/    7 |Time    99(  90+9   )ms |DirectCallNodes I    0/D    0 |GraalNodes    22/   71 |CodeSize          274 |CodeAddress 0x7f76e4c1fe10 |Source    <shell>:1:1
```


