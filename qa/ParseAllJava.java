import com.sun.source.util.JavacTask;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ParseAllJava {
  public static void main(String[] args) throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null);
         var paths = Files.walk(Path.of("."))) {
      List<java.io.File> sources = paths.filter(p -> p.toString().endsWith(".java") &&
          !p.toString().contains("/qa/") && !p.toString().contains("/build/")).map(Path::toFile).toList();
      Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromFiles(sources);
      JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics,
          List.of("--release", "21", "-proc:none"), null, units);
      task.parse();
      long errors = diagnostics.getDiagnostics().stream()
          .filter(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR).count();
      diagnostics.getDiagnostics().stream()
          .filter(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR)
          .limit(20).forEach(System.err::println);
      System.out.println("Java 21 syntax parsed: " + sources.size() + " sources; " + errors + " syntax errors");
      if (errors > 0) System.exit(1);
    }
  }
}
