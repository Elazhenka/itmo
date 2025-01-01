package info.kgeorgiy.ja.elagina.implementor;

import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;


import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

/**
 *  Console application that implements and optionally compile given class or interface.
 *  If meant to be compiled, pack the class or interface into {@code .jar} file.
 *  @author Elagina Alena
 */

public class Implementor implements JarImpler {

    /**
     * tabulation size
     */
    private final int TAB_SIZE = 4;
    /**
     * tabulation string
     */
    private final String TAB = " ".repeat(TAB_SIZE);

    /**
     * Default constructor
     */
    public Implementor() {}

    /**
     * Creates a directory in which to put the implemented class
     * @param directory Path to directory
     * @throws ImplerException If the directory cannot be created
     */
    private void createDirectories(Path directory) throws ImplerException {
        try {
            if (Files.notExists(directory)) {
                Files.createDirectories(directory);
            }
        } catch (IOException e) {
            throw new ImplerException("Cannot create file");
        }
    }

    /**
     * Write implemented interface into file
     * @param writer Write implemented interface into file
     * @param token Interface to be implemented
     * @throws ImplerException if interface cannot be implemented
     */
    private void writeInterface(BufferedWriter writer, Class<?> token) throws ImplerException {
        try {
            // :NOTE: System.lineSep
            writer.write(token.getPackage() + ";\n\n");
            writer.write(String.format("public class %sImpl implements %s {\n", esc(token.getSimpleName()), esc(token.getCanonicalName())));
            for (Method method : token.getMethods()) {
                writeMethod(writer, method, 1);
            }
            writer.write("}");
        } catch (IOException e) {
            throw new ImplerException("Cannot implement this interface");
        }
    }

    /**
     * Write interface's methods into file
     * @param writer Write interface's methods into file
     * @param method Method to be implemented
     * @param indent Indent from the beginning of the line
     * @throws IOException If unable to write
     */
    private void writeMethod(BufferedWriter writer, Method method, int indent) throws IOException {
        writer.write(String.format("%s public %s %s (", TAB.repeat(indent), esc(method.getReturnType().getCanonicalName()), esc(method.getName())));
        writeMethodParameters(writer, method);
        writer.write(") {\n");
        writer.write(TAB.repeat(indent + 1) + "return");
        writer.write(writeReturnDefaultValue(method.getReturnType()));
        writer.write(";\n");
        writer.write(TAB.repeat(indent) + "}\n\n");
    }

    /**
     * Gets method parameters and writes them separated by commas
     * @param writer Writes method parameters separated by commas to a file
     * @param method The method from which you want to get the parameters
     * @throws IOException If unable to write
     */
    private void writeMethodParameters(BufferedWriter writer, Method method) throws IOException {
        Parameter[] params = method.getParameters();
        String paramsToString = Arrays.stream(params)
                .map(p -> String.format("%s %s", esc(p.getType().getCanonicalName()), esc(p.getName())))
                .collect(Collectors.joining(", "));
        writer.write(paramsToString);
    }

    /**
     * Escape all non-ASCII symbols by unicode sequence
     *
     * @param seq Any non-null string
     * @return Escaped string
     */
    private static String esc(final String seq) {
        final StringBuilder b = new StringBuilder();

        for (final char c : seq.toCharArray()) {
            if (c >= 128) {
                b.append(String.format("\\u%04X", (int) c));
            } else {
                b.append(c);
            }
        }

        return b.toString();
    }

    /**
     * Returns the default value for the corresponding type
     * @param type The type we want the default value for
     * @return the default value for the corresponding type
     */
    private String writeReturnDefaultValue(Class<?> type) {
        if (type.equals(void.class)) {
            return "";
        } else if (type.equals(boolean.class)) {
            return " false";
        } else if (type.isPrimitive()) {
            return " 0";
        } else {
            return " null";
        }
    }

    /**
     * Produces code implementing interface specified by provided token
     * @param token type token to create implementation for.
     * @param root root directory.
     * @throws ImplerException when implementation cannot be generated
     */
    @Override
    public void implement(Class<?> token, Path root) throws ImplerException {
        if (token == null) {
            throw new ImplerException("token cannot be null");
        }
        if (root == null) {
            throw new ImplerException("root cannot be null");
        }
        if (token.isPrimitive() || token.isArray() || token.isEnum()) {
            throw new ImplerException("token must be interface or class");
        }
        if (Modifier.isPrivate(token.getModifiers())) {
            throw new ImplerException("Cannot implement private interface");
        }
        if (!token.isInterface()) {
            throw new ImplerException("Cannot implement private interface");
        }

        String packageName = token.getPackageName();
        String interfaceName = token.getSimpleName() + "Impl";
        Path interfacePath;
        try {
            Path directory = Path.of(root.toString(), packageName.split("\\."));
            createDirectories(directory);
            interfacePath = directory.resolve(interfaceName + ".java");
        } catch (InvalidPathException e) {
            throw new ImplerException("Invalid path for create class file");
        }

        try (BufferedWriter writer = Files.newBufferedWriter(interfacePath)) {
            writeInterface(writer, token);
        } catch (IOException e) {
            throw new ImplerException("Cannot write interface implement in file");
        }
    }

    /**
     * Compiles the class
     * @param token The class to compile
     * @param root root directory
     * @param sourceFile The name of the class to compile
     * @throws ImplerException If unable to compile
     */
    private void compile(Class<?> token, Path root, String sourceFile) throws ImplerException {
        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new ImplerException("JavaCompiler not found");
            }
            URI uri = token.getProtectionDomain().getCodeSource().getLocation().toURI();
            int exitCode = compiler.run(null, null, null, "-cp", Path.of(uri).toString(), root.resolve(sourceFile + ".java").toString());
            if (exitCode != 0) {
                throw new ImplerException("Nonzero compiler exit code : " + exitCode);
            }
        } catch (URISyntaxException e) {
            throw new ImplerException("Cannot get classpath");
        }
    }

    /**
     * Produces {@code .jar} file implementing class or interface specified by provided token.
     * @param token type token to create implementation for.
     * @param jarFile target <var>.jar</var> file.
     * @throws ImplerException when implementation cannot be generated
     */

    // :NOTE: inherit doc
    @Override
    public void implementJar(final Class<?> token, final Path jarFile) throws ImplerException {
        Path root = Path.of(".");
        Path temp;

        try {
            temp = Files.createTempDirectory(root, null);
            implement(token, temp);

            String sourceFile = Path.of(token.getPackage().getName().replace('.', File.separatorChar))
                    .resolve(token.getSimpleName() + "Impl").toString();
            Path out = temp.resolve(sourceFile + ".class");

            compile(token, temp, sourceFile);

            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            try (JarOutputStream stream = new JarOutputStream(Files.newOutputStream(jarFile), manifest)) {
                ZipEntry zipEntry = new ZipEntry(sourceFile.replace(File.separatorChar, '/') + ".class");
                stream.putNextEntry(zipEntry);
                Files.copy(out, stream);
                stream.closeEntry();
            } catch (IOException e) {
                throw new ImplerException("Failed to write to jar file", e);
            }

        } catch (IOException e) {
            throw new ImplerException("Cannot create temporary directory", e);
        }
    }

    /**
     * Entry point for console application that implements and optionally compile given class or interface.
     * @param args Params for main
     * @throws ImplerException when implementation cannot be generated
     */
    public static void main(String[] args) throws ImplerException {
        if (args == null || (args.length != 2 && args.length != 3)) {
            throw new ImplerException("Expected two or three argument");
        }
        for (String arg : args) {
            if (arg == null) {
                throw new ImplerException("Argument cannot be null");
            }
        }
        try {
            Path path;
            Class<?> token;

            Implementor impl = new Implementor();

            if (args.length == 3) {
                if (!Objects.equals(args[0], "-jar")) {
                    throw new ImplerException("First argument should be -jar");
                }
                token = Class.forName(args[1]);
                path = Path.of(args[2]);
                impl.implementJar(token, path);
            } else {
                token = Class.forName(args[0]);
                path = Path.of(args[1]);
                impl.implement(token, path);
            }
        } catch (ClassNotFoundException e) {
            throw new ImplerException("Class not found");
        } catch (InvalidPathException e) {
            throw new ImplerException("Invalid path " + args[args.length == 3 ? 2 : 1]);
        }
    }
}
