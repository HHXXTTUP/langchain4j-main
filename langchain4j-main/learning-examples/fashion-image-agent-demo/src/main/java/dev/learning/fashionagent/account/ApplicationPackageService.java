package dev.learning.fashionagent.account;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

@Service
public class ApplicationPackageService {
    private static final String START_SCRIPT = """
            @echo off
            setlocal
            cd /d "%~dp0"
            start "Atelier Flow Server" /min java -jar atelier-flow.jar
            powershell -NoProfile -ExecutionPolicy Bypass -Command "$deadline=(Get-Date).AddMinutes(2); do { try { $response=Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8088/login.html' -TimeoutSec 2; if ($response.StatusCode -eq 200) { exit 0 } } catch {}; Start-Sleep -Milliseconds 700 } while ((Get-Date) -lt $deadline); exit 1"
            if errorlevel 1 (
              echo Application startup timed out. Check the server window for details.
              pause
              exit /b 1
            )
            start "" "http://127.0.0.1:8088/login.html"
            endlocal
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
                addText(zip, "atelier-flow/start.bat", START_SCRIPT);
                addText(zip, "atelier-flow/README.txt", "双击 start.bat 启动，服务就绪后会自动打开登录页。首次启动会导入管理员配置的账号、菜单、有效期和加密模型配置。\r\n需要安装 Java 17 或更高版本。\r\n账号密码使用 BCrypt 保存，模型密钥使用 AES-GCM 加密保存。请勿删除 data/application-secret.key。\r\n");
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

    private long modified(Path path) { try { return Files.getLastModifiedTime(path).toMillis(); } catch (IOException e) { return 0; } }
    private static void addFile(ZipOutputStream zip, Path file, String name) throws IOException { zip.putNextEntry(new ZipEntry(name)); Files.copy(file, zip); zip.closeEntry(); }
    private static void addText(ZipOutputStream zip, String name, String content) throws IOException { zip.putNextEntry(new ZipEntry(name)); zip.write(content.getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
    public record PackageFile(String name, byte[] bytes) {}
}
