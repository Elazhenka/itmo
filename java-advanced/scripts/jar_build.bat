@echo off
set lib=..\..\java-advanced-2024\artifacts\info.kgeorgiy.java.advanced.implementor.jar

javac -cp "%lib%" ..\java-solutions\info\kgeorgiy\ja\elagina\implementor\Implementor.java

jar -cfm Implementor.jar META-INF\MANIFEST.MF ..\java-solutions\info\kgeorgiy\ja\elagina\implementor\Implementor.class

del ..\java-solutions\info\kgeorgiy\ja\elagina\implementor\Implementor.class

