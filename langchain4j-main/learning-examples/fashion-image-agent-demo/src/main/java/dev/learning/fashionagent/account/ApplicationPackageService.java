package dev.learning.fashionagent.account;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

@Service
public class ApplicationPackageService {
    /** Native Windows launcher. Keep this file ASCII-only so it renders correctly
     * on machines whose CMD code page is not UTF-8. */
    private static final String WINDOWS_START_SCRIPT = """
            @echo off
            setlocal EnableExtensions
            set "APP_HOME=%~dp0"
            set "JAR=%APP_HOME%atelier-flow.jar"
            set "JAVA_EXE=%APP_HOME%runtime\\bin\\java.exe"

            if not exist "%JAR%" goto missing_jar
            if not exist "%JAVA_EXE%" set "JAVA_EXE=java"
            if /I "%JAVA_EXE%"=="java" (
              where java >nul 2>nul
              if errorlevel 1 goto missing_java
            )

            rem pushd handles drive changes without the CMD-specific "cd /d" switch.
            pushd "%APP_HOME%" || goto invalid_home
            start "Atelier Flow" /min "%JAVA_EXE%" -Dfile.encoding=UTF-8 -jar "%JAR%"
            if errorlevel 1 goto launch_failed

            powershell -NoProfile -ExecutionPolicy Bypass -Command "$deadline=(Get-Date).AddMinutes(2); do { try { $response=Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8088/login.html' -TimeoutSec 2; if ($response.StatusCode -eq 200) { exit 0 } } catch {}; Start-Sleep -Milliseconds 700 } while ((Get-Date) -lt $deadline); exit 1"
            if errorlevel 1 goto startup_timeout
            start "" "http://127.0.0.1:8088/login.html"
            popd
            endlocal
            exit /b 0

            :missing_jar
            echo Application files are incomplete.
            echo Missing: %JAR%
            goto startup_error
            :missing_java
            echo Java 17 or newer is required. Install Java and try again.
            goto startup_error
            :invalid_home
            echo Unable to access the extracted application directory.
            goto startup_error
            :launch_failed
            echo Unable to start the application.
            goto startup_error
            :startup_timeout
            echo Application startup timed out. Check the server window for details.
            :startup_error
            pause
            endlocal
            exit /b 1
            """;

    /** Bash launcher for Linux/macOS and Git Bash. */
    private static final String UNIX_START_SCRIPT = """
            #!/usr/bin/env bash
            set -euo pipefail
            APP_HOME="$(cd -- "$(dirname -- "$0")" && pwd)"
            JAR="$APP_HOME/atelier-flow.jar"
            JAVA_EXE="$APP_HOME/runtime/bin/java"
            [[ -x "$JAVA_EXE" ]] || JAVA_EXE="$(command -v java || true)"
            if [[ ! -f "$JAR" ]]; then
              echo "Application files are incomplete. Missing: $JAR" >&2
              exit 1
            fi
            if [[ -z "$JAVA_EXE" || ! -x "$JAVA_EXE" ]]; then
              echo "Java 17 or newer is required." >&2
              exit 1
            fi
            cd "$APP_HOME"
            "$JAVA_EXE" -Dfile.encoding=UTF-8 -jar "$JAR" &
            SERVER_PID=$!
            deadline=$((SECONDS + 120))
            until curl -fsS http://127.0.0.1:8088/login.html >/dev/null 2>&1; do
              if (( SECONDS >= deadline )); then
                echo "Application startup timed out (pid $SERVER_PID)." >&2
                exit 1
              fi
              sleep 1
            done
            if command -v xdg-open >/dev/null 2>&1; then xdg-open http://127.0.0.1:8088/login.html >/dev/null 2>&1 &
            elif command -v open >/dev/null 2>&1; then open http://127.0.0.1:8088/login.html >/dev/null 2>&1 &
            fi
            """;
    private final AccountService accounts;
    public ApplicationPackageService(AccountService accounts) { this.accounts = accounts; }

    public PackageFile build() {
        Path jar = locateJar();
        Path key = Path.of("data", "application-secret.key").toAbsolutePath().normalize();
        if (!Files.isRegularFile(key)) throw new IllegalStateException("本地配置加密密钥不存在");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                addFile(zip, jar, "atelier-flow/atelier-flow.jar");
                addFile(zip, key, "atelier-flow/data/application-secret.key");
                addText(zip, "atelier-flow/data/bootstrap-accounts.enc", accounts.exportEncryptedBootstrap());
                addRuntimeIfConfigured(zip);
                addText(zip, "atelier-flow/start.bat", WINDOWS_START_SCRIPT);
                addText(zip, "atelier-flow/start.cmd", WINDOWS_START_SCRIPT);
                addText(zip, "atelier-flow/start.sh", UNIX_START_SCRIPT);
                addText(zip, "atelier-flow/README.txt", "Windows：请完整解压 ZIP 后双击 start.bat（或 start.cmd），不要用 CMD 直接运行 start.sh。\r\nLinux/macOS/Git Bash：执行 bash start.sh。\r\n服务就绪后会自动打开登录页。首次启动会导入管理员配置的账号、菜单、有效期和加密模型配置。\r\n如果压缩包内包含 runtime\\bin\\java.exe，则无需在电脑安装 JDK；否则需要 Java 17 或更高版本。打包时可设置 FASHION_RUNTIME_DIRECTORY 指向便携 Java 运行时目录。\r\n账号密码使用 BCrypt 保存，模型密钥使用 AES-GCM 加密保存。请勿删除 data/application-secret.key。\r\n");
            }
            return new PackageFile("atelier-flow-application.zip", bytes.toByteArray());
        } catch (IOException e) { throw new IllegalStateException("应用包生成失败", e); }
    }

    private Path locateJar() {
        try {
            CodeSource source = ApplicationPackageService.class.getProtectionDomain().getCodeSource();
            if (source != null) {
                Path path = Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
                if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) return path;
            }
            Path target = Path.of("target").toAbsolutePath().normalize();
            if (Files.isDirectory(target)) try (var files = Files.list(target)) {
                return files.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".jar"))
                        .filter(path -> !path.getFileName().toString().contains("original"))
                        .max(Comparator.comparingLong(this::modified)).orElseThrow();
            }
        } catch (Exception ignored) {}
        throw new IllegalStateException("未找到可执行 JAR，请先运行 mvn package 后再打包");
    }

    private void addRuntimeIfConfigured(ZipOutputStream zip) throws IOException {
        String configured = System.getenv("FASHION_RUNTIME_DIRECTORY");
        Path runtime = configured == null || configured.isBlank()
                ? Path.of("runtime")
                : Path.of(configured.trim());
        runtime = runtime.toAbsolutePath().normalize();
        if (!Files.isDirectory(runtime)) {
            return;
        }
        Path javaExecutable = runtime.resolve("bin").resolve("java.exe");
        if (!Files.isRegularFile(javaExecutable)) {
            javaExecutable = runtime.resolve("bin").resolve("java");
        }
        if (!Files.isRegularFile(javaExecutable)) {
            throw new IllegalStateException("便携 Java 运行时目录缺少 bin/java：" + runtime);
        }
        try (Stream<Path> files = Files.walk(runtime)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String relative = runtime.relativize(file).toString().replace('\\', '/');
                addFile(zip, file, "atelier-flow/runtime/" + relative);
            }
        }
    }

    private long modified(Path path) { try { return Files.getLastModifiedTime(path).toMillis(); } catch (IOException e) { return 0; } }
    private static void addFile(ZipOutputStream zip, Path file, String name) throws IOException { zip.putNextEntry(new ZipEntry(name)); Files.copy(file, zip); zip.closeEntry(); }
    private static void addText(ZipOutputStream zip, String name, String content) throws IOException { zip.putNextEntry(new ZipEntry(name)); zip.write(content.getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
    public record PackageFile(String name, byte[] bytes) {}
}
