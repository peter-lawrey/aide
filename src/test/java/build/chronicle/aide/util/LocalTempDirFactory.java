package build.chronicle.aide.util;

import org.junit.jupiter.api.extension.AnnotatedElementContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDirFactory;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocalTempDirFactory implements TempDirFactory {
    @Override
    public Path createTempDirectory(AnnotatedElementContext annotatedElementContext, ExtensionContext extensionContext) throws Exception {
        String testName = extensionContext.getTestMethod().stream().map(Method::getName).findFirst().orElse("test");
        Path temp = Path.of("temp", testName + "-" + System.nanoTime());
        Files.createDirectories(temp);
        return temp;
    }
}
