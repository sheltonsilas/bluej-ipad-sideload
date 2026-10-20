package org.bluej.ipad.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProjectCompilerTest {

    private static final ProjectCompiler COMPILER = new ProjectCompiler();

    @Test
    void compilesAWholeProjectInMemory() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("Animal.java", """
                public class Animal {
                    private String name;
                    public Animal(String name) { this.name = name; }
                    public String getName() { return name; }
                    public String speak() { return "..."; }
                }
                """);
        sources.put("Dog.java", """
                public class Dog extends Animal {
                    public Dog(String name) { super(name); }
                    @Override public String speak() { return getName() + " says woof"; }
                }
                """);

        ProjectCompiler.Result result = COMPILER.compile(sources);

        assertTrue(result.success(), () -> "diagnostics: " + result.diagnostics());
        assertTrue(result.diagnostics().isEmpty());
        assertEquals(2, result.classes().size());
        assertArrayEquals(new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE},
                java.util.Arrays.copyOf(result.classes().get("Dog"), 4));
    }

    @Test
    void reportsErrorsWithFileLineAndColumn() {
        ProjectCompiler.Result result = COMPILER.compile(Map.of("Broken.java", """
                public class Broken {
                    public int value() {
                        return missing + 1;
                    }
                }
                """));

        assertFalse(result.success());
        assertTrue(result.classes().isEmpty());
        ProjectCompiler.Diagnostic error = result.diagnostics().stream()
                .filter(d -> d.severity() == ProjectCompiler.Severity.ERROR)
                .findFirst().orElseThrow();
        assertEquals("Broken.java", error.path());
        assertEquals(3, error.line());
        assertTrue(error.message().contains("missing"), error.message());
    }

    @Test
    void staleClassFilesAreNotHandedBackWhenOneUnitFails() {
        ProjectCompiler.Result result = COMPILER.compile(Map.of(
                "Good.java", "public class Good { public int answer() { return 42; } }",
                "Bad.java", "public class Bad { int ) }"));

        assertFalse(result.success());
        assertTrue(result.classes().isEmpty(), "a failed pass must not return partially compiled classes");
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.path().equals("Bad.java")));
    }

    @Test
    void readsDeclaredTypeAndPackageWithoutBeingFooledByComments() {
        String source = """
                // class NotThis
                /* interface NorThis */
                package shapes;

                /** class AlsoNotThis */
                public class Circle {
                    String label = "class Fake";
                }
                """;
        assertEquals("Circle", ProjectCompiler.declaredTypeName(source));
        assertEquals("shapes", ProjectCompiler.declaredPackage(source));
        assertNull(ProjectCompiler.declaredTypeName("// nothing here\n"));
    }
}
