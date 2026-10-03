import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 依賴方向檢查（Java 專案用，相容 JDK 1.7，不需任何套件）。
 *
 * 用法：
 *   javac -encoding UTF-8 -d target/check CheckArch.java
 *   java -cp target/check CheckArch <原始碼根目錄> <base package> [原始碼編碼]
 *   例：java -cp target/check CheckArch src/main/java com.example.library MS950
 *
 * 檢查項目：
 *   1. domain 不得 import application / adapter / config / infrastructure，也不得 import 框架
 *   2. application 不得 import adapter / config / infrastructure，也不得 import 框架
 *   3. by-feature：模組之間只能透過對方的 api package 互動（sharedkernel 例外）
 */
public class CheckArch {

    // 內層禁止 import 的框架與技術套件（依專案調整）
    private static final List<String> FORBIDDEN_PREFIXES = Arrays.asList(
            "org.springframework.", "javax.persistence.", "jakarta.persistence.", "javax.servlet.",
            "jakarta.servlet.", "java.sql.", "javax.sql.", "org.hibernate.", "org.mybatis.",
            "com.fasterxml.jackson.", "org.apache.commons.csv.");

    private static final Set<String> LAYERS = new HashSet<String>(
            Arrays.asList("domain", "application", "adapter", "config", "infrastructure"));
    private static final Set<String> SHARED = new HashSet<String>(Arrays.asList("sharedkernel", "infrastructure"));

    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+?)(?:\\.\\*)?\\s*;", Pattern.MULTILINE);

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("用法：java CheckArch <原始碼根目錄> <base package> [原始碼編碼]");
            System.exit(2);
        }
        final String base = args[1];
        final Charset charset = Charset.forName(args.length >= 3 ? args[2] : "UTF-8");
        final List<String> violations = new ArrayList<String>();

        Files.walkFileTree(Paths.get(args[0]), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    check(file, new String(Files.readAllBytes(file), charset), base, violations);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        for (String v : violations) {
            System.out.println("違反：" + v);
        }
        if (!violations.isEmpty()) {
            System.out.println("共 " + violations.size() + " 處違反依賴方向");
            System.exit(1);
        }
        System.out.println("依賴方向檢查通過");
    }

    private static void check(Path file, String source, String base, List<String> violations) {
        Matcher pm = PACKAGE.matcher(source);
        if (!pm.find() || !pm.group(1).startsWith(base)) {
            return;
        }
        String fromPackage = pm.group(1);
        String fromLayer = layerOf(fromPackage, base);
        String fromModule = moduleOf(fromPackage, base);

        Matcher im = IMPORT.matcher(source);
        while (im.find()) {
            String imported = im.group(1);

            // 1、2. 內層不得 import 外層或框架
            if ("domain".equals(fromLayer) || "application".equals(fromLayer)) {
                for (String prefix : FORBIDDEN_PREFIXES) {
                    if (imported.startsWith(prefix)) {
                        violations.add(file + "：" + fromLayer + " 不得 import 框架 " + imported);
                    }
                }
                if (imported.startsWith(base + ".")) {
                    String toLayer = layerOf(imported, base);
                    boolean outer = "domain".equals(fromLayer)
                            ? !"domain".equals(toLayer) && toLayer != null
                            : "adapter".equals(toLayer) || "config".equals(toLayer) || "infrastructure".equals(toLayer);
                    if (outer) {
                        violations.add(file + "：" + fromLayer + " 不得 import " + toLayer + "（" + imported + "）");
                    }
                }
            }

            // 3. 模組之間只能透過 api package
            if (fromModule != null && imported.startsWith(base + ".")) {
                String toModule = moduleOf(imported, base);
                if (toModule != null && !toModule.equals(fromModule)
                        && !imported.startsWith(base + "." + toModule + ".api.")) {
                    violations.add(file + "：模組 " + fromModule + " 只能透過 " + toModule + ".api 使用模組 " + toModule
                            + "（" + imported + "）");
                }
            }
        }
    }

    // base 之後的第一個層級名稱（domain / application / adapter / config / infrastructure）
    private static String layerOf(String pkg, String base) {
        for (String segment : relative(pkg, base)) {
            if (LAYERS.contains(segment)) {
                return segment;
            }
        }
        return null;
    }

    // by-feature：base 之後的第一段若不是層級或共用 package，就是模組名稱
    private static String moduleOf(String pkg, String base) {
        String[] rel = relative(pkg, base);
        if (rel.length == 0 || LAYERS.contains(rel[0]) || SHARED.contains(rel[0])) {
            return null;
        }
        return rel[0];
    }

    private static String[] relative(String pkg, String base) {
        if (pkg.length() <= base.length() + 1) {
            return new String[0];
        }
        return pkg.substring(base.length() + 1).split("\\.");
    }
}
