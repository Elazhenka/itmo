@echo off
set docs="https://docs.oracle.com/en/java/javase/19/docs/api/"
set module=..\..\java-advanced-2024\modules\info.kgeorgiy.java.advanced.implementor\info\kgeorgiy\java\advanced\implementor\
set lib=..\..\java-advanced-2024\artifacts\info.kgeorgiy.java.advanced.implementor.jar
set file=..\java-solutions\info\kgeorgiy\ja\elagina\implementor\Implementor.java

javadoc -d javadoc -link "%docs%" -cp "%lib%" -private %file% "%module%JarImpler.java" "%module%ImplerException.java"
