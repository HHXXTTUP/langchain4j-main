package dev.learning.fashionagent.account;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService implements ApplicationRunner {
    public static final String SESSION_ACCOUNT_ID = "fashion.account.id";
    private static final String DEFAULT_ADMIN = "admin";
    private static final String DEFAULT_PASSWORD = "qq1184920089";
    private static final List<String> SECRET_FIELDS = List.of(
            "runninghubKey", "snapanyKey", "qwenKey", "geminiKey", "gptImagesKey", "zhipuKey", "comfyuiToken");
    private static final List<String> DIRECTORY_FIELDS = List.of(
            "clothingDirectory", "videoDirectory", "generatedDirectory", "videoExportDirectory",
            "auditOutputDirectory", "storyOutputDirectory", "bgmDirectory", "qwenOutputDirectory");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final SecretCipher cipher;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);

    public AccountService(JdbcTemplate jdbc, ObjectMapper mapper, SecretCipher cipher) {
        this.jdbc = jdbc; this.mapper = mapper; this.cipher = cipher;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Keep existing H2 installations compatible after adding account-level Gemini keys.
        try { jdbc.execute("ALTER TABLE app_account_setting ADD COLUMN IF NOT EXISTS gemini_key CLOB"); jdbc.execute("ALTER TABLE app_account_setting ADD COLUMN IF NOT EXISTS gpt_images_key CLOB"); }
        catch (Exception ignored) { /* schema-h2.sql handles fresh databases; some drivers reject IF NOT EXISTS */ }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM app_account", Integer.class);
        if (count != null && count == 0) {
            if (importBootstrap()) return;
            Instant now = Instant.now();
            String id = UUID.randomUUID().toString();
            jdbc.update("""
                    INSERT INTO app_account(id, username, password_hash, administrator, enabled, expires_at,
                        allowed_menus, created_at, updated_at) VALUES (?, ?, ?, TRUE, TRUE, NULL, ?, ?, ?)
                    """, id, DEFAULT_ADMIN, passwords.encode(DEFAULT_PASSWORD), json(MenuCatalog.ALL),
                    Timestamp.from(now), Timestamp.from(now));
            insertEmptySettings(id, now);
        }
    }

    public String exportEncryptedBootstrap() {
        List<PortableAccount> portable = jdbc.query("SELECT * FROM app_account ORDER BY created_at", (rs, row) -> {
            Account account = mapAccount(rs, row);
            return new PortableAccount(account.id(), account.username(), account.passwordHash(), account.administrator(),
                    account.enabled(), account.expiresAt(), account.allowedMenus(), settings(account.id(), true));
        });
        return cipher.encrypt(json(portable));
    }

    private boolean importBootstrap() {
        Path file = Path.of("data", "bootstrap-accounts.enc").toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) return false;
        try {
            List<PortableAccount> portable = mapper.readValue(cipher.decrypt(Files.readString(file)),
                    new TypeReference<List<PortableAccount>>() {});
            Instant now = Instant.now();
            for (PortableAccount account : portable) {
                String id = account.id() == null || account.id().isBlank() ? UUID.randomUUID().toString() : account.id();
                jdbc.update("""
                        INSERT INTO app_account(id, username, password_hash, administrator, enabled, expires_at,
                            allowed_menus, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, id, account.username(), account.passwordHash(), account.administrator(), account.enabled(),
                        timestamp(account.expiresAt()), json(normalizeMenus(account.allowedMenus())), Timestamp.from(now), Timestamp.from(now));
                insertEmptySettings(id, now); updateSettings(id, account.settings(), true);
            }
            return !portable.isEmpty();
        } catch (Exception e) { throw new IllegalStateException("导入应用包账号配置失败", e); }
    }

    public Account authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null) return null;
        Account account = findByUsername(username.trim());
        if (account == null || !account.enabled() || account.expired() || !passwords.matches(password, account.passwordHash())) return null;
        return account;
    }

    public Account require(String id) {
        List<Account> result = jdbc.query("SELECT * FROM app_account WHERE id=?", this::mapAccount, id);
        if (result.isEmpty()) throw new IllegalArgumentException("账号不存在");
        return result.get(0);
    }

    public Account findByUsername(String username) {
        return jdbc.query("SELECT * FROM app_account WHERE LOWER(username)=LOWER(?)", this::mapAccount, username)
                .stream().findFirst().orElse(null);
    }

    public List<AccountView> list(Account operator) {
        if (!operator.administrator()) return List.of(view(operator, false));
        return jdbc.query("SELECT * FROM app_account ORDER BY administrator DESC, created_at", this::mapAccount)
                .stream().map(account -> view(account, true)).toList();
    }

    public AccountView view(Account account, boolean administratorView) {
        Map<String, String> settings = settings(account.id(), administratorView);
        return new AccountView(account.id(), administratorView ? account.username() : mask(account.username()),
                administratorView ? "不可读取，可重置" : "********", account.administrator(), account.enabled(),
                administratorView ? account.expiresAt() : null, administratorView && account.expired(),
                administratorView ? account.allowedMenus() : Set.of(), settings);
    }

    public AccountContext.Snapshot snapshot(Account account) {
        return new AccountContext.Snapshot(account.id(), account.username(), account.administrator(), settings(account.id(), true));
    }

    @Transactional
    public AccountView create(CreateAccount request) {
        String username = required(request.username(), "用户名");
        String password = required(request.password(), "密码");
        if (findByUsername(username) != null) throw new IllegalArgumentException("用户名已存在");
        Instant now = Instant.now(); String id = UUID.randomUUID().toString();
        Set<String> menus = normalizeMenus(request.allowedMenus());
        jdbc.update("""
                INSERT INTO app_account(id, username, password_hash, administrator, enabled, expires_at,
                    allowed_menus, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, username, passwords.encode(password), request.administrator(), request.enabled(),
                timestamp(request.expiresAt()), json(menus), Timestamp.from(now), Timestamp.from(now));
        insertEmptySettings(id, now);
        updateSettings(id, request.settings(), true);
        return view(require(id), true);
    }

    @Transactional
    public AccountView updateAsAdmin(String id, UpdateAccount request) {
        Account existing = require(id);
        String username = required(request.username(), "用户名");
        Account duplicate = findByUsername(username);
        if (duplicate != null && !duplicate.id().equals(id)) throw new IllegalArgumentException("用户名已存在");
        if (existing.administrator() && !request.administrator() && administratorCount() <= 1) {
            throw new IllegalStateException("至少保留一个管理员账号");
        }
        jdbc.update("""
                UPDATE app_account SET username=?, administrator=?, enabled=?, expires_at=?, allowed_menus=?,
                    updated_at=? WHERE id=?
                """, username, request.administrator(), request.enabled(), timestamp(request.expiresAt()),
                json(normalizeMenus(request.allowedMenus())), Timestamp.from(Instant.now()), id);
        if (request.password() != null && !request.password().isBlank()) resetPassword(id, request.password());
        updateSettings(id, request.settings(), true);
        return view(require(id), true);
    }

    @Transactional
    public AccountView updateSelf(String id, Map<String, String> settings) {
        updateSettings(id, settings, false);
        return view(require(id), false);
    }

    @Transactional
    public void delete(String id, Account operator) {
        Account target = require(id);
        if (target.id().equals(operator.id())) throw new IllegalStateException("不能删除当前登录账号");
        if (target.administrator() && administratorCount() <= 1) throw new IllegalStateException("至少保留一个管理员账号");
        jdbc.update("DELETE FROM app_account WHERE id=?", id);
    }

    public Map<String, String> settings(String accountId, boolean revealSecrets) {
        List<Map<String, String>> rows = jdbc.query("SELECT * FROM app_account_setting WHERE account_id=?", (rs, row) -> {
            Map<String, String> value = new LinkedHashMap<>();
            for (String field : SECRET_FIELDS) {
                String decrypted = cipher.decrypt(rs.getString(toColumn(field)));
                value.put(field, revealSecrets ? blank(decrypted) : maskSecret(decrypted));
            }
            for (String field : DIRECTORY_FIELDS) value.put(field, blank(rs.getString(toColumn(field))));
            return value;
        }, accountId);
        return rows.isEmpty() ? emptySettings() : rows.get(0);
    }

    @Transactional
    public void updateSettings(String accountId, Map<String, String> values, boolean administrator) {
        if (values == null) return;
        Map<String, String> current = settings(accountId, true);
        for (String field : SECRET_FIELDS) {
            String supplied = values.get(field);
            if (supplied != null && !supplied.isBlank() && !supplied.contains("*")) current.put(field, supplied.trim());
        }
        for (String field : DIRECTORY_FIELDS) {
            if (values.containsKey(field)) current.put(field, normalizeDirectory(values.get(field)));
        }
        jdbc.update("""
                UPDATE app_account_setting SET runninghub_key=?, snapany_key=?, qwen_key=?, gemini_key=?, gpt_images_key=?, zhipu_key=?,
                    comfyui_token=?, clothing_directory=?, video_directory=?, generated_directory=?,
                    video_export_directory=?, audit_output_directory=?, story_output_directory=?, bgm_directory=?,
                    qwen_output_directory=?, updated_at=? WHERE account_id=?
                """, cipher.encrypt(current.get("runninghubKey")), cipher.encrypt(current.get("snapanyKey")),
                cipher.encrypt(current.get("qwenKey")), cipher.encrypt(current.get("geminiKey")), cipher.encrypt(current.get("gptImagesKey")),
                cipher.encrypt(current.get("zhipuKey")),
                cipher.encrypt(current.get("comfyuiToken")), nullIfBlank(current.get("clothingDirectory")),
                nullIfBlank(current.get("videoDirectory")), nullIfBlank(current.get("generatedDirectory")),
                nullIfBlank(current.get("videoExportDirectory")), nullIfBlank(current.get("auditOutputDirectory")),
                nullIfBlank(current.get("storyOutputDirectory")), nullIfBlank(current.get("bgmDirectory")),
                nullIfBlank(current.get("qwenOutputDirectory")), Timestamp.from(Instant.now()), accountId);
    }

    private void resetPassword(String id, String password) {
        if (password.length() < 8) throw new IllegalArgumentException("密码至少8位");
        jdbc.update("UPDATE app_account SET password_hash=?, updated_at=? WHERE id=?",
                passwords.encode(password), Timestamp.from(Instant.now()), id);
    }

    private void insertEmptySettings(String id, Instant now) {
        jdbc.update("INSERT INTO app_account_setting(account_id, updated_at) VALUES (?, ?)", id, Timestamp.from(now));
    }

    private Account mapAccount(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        Timestamp expiry = rs.getTimestamp("expires_at");
        Set<String> menus;
        try { menus = mapper.readValue(rs.getString("allowed_menus"), new TypeReference<LinkedHashSet<String>>() {}); }
        catch (Exception e) { menus = Set.of(); }
        return new Account(rs.getString("id"), rs.getString("username"), rs.getString("password_hash"),
                rs.getBoolean("administrator"), rs.getBoolean("enabled"), expiry == null ? null : expiry.toInstant(), menus);
    }

    private int administratorCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM app_account WHERE administrator=TRUE", Integer.class);
        return count == null ? 0 : count;
    }

    private Set<String> normalizeMenus(Set<String> menus) {
        Set<String> result = new LinkedHashSet<>();
        if (menus != null) menus.stream().filter(MenuCatalog.ALL::contains).forEach(result::add);
        result.add("account-settings");
        return result;
    }

    private Map<String, String> emptySettings() {
        Map<String, String> result = new LinkedHashMap<>();
        SECRET_FIELDS.forEach(field -> result.put(field, "")); DIRECTORY_FIELDS.forEach(field -> result.put(field, ""));
        return result;
    }

    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static String required(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + "不能为空"); return value.trim(); }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static String mask(String value) { if (value == null || value.length() < 3) return "***"; return value.substring(0, 1) + "***" + value.substring(value.length() - 1); }
    private static String maskSecret(String value) { return value == null || value.isBlank() ? "" : "********"; }
    private static String blank(String value) { return value == null ? "" : value; }
    private static String nullIfBlank(String value) { return value == null || value.isBlank() ? null : value; }
    private static String normalizeDirectory(String value) { if (value == null || value.isBlank()) return ""; return Path.of(value.trim()).normalize().toString(); }
    private static String toColumn(String field) {
        return switch (field) {
            case "runninghubKey" -> "runninghub_key"; case "snapanyKey" -> "snapany_key";
            case "qwenKey" -> "qwen_key"; case "zhipuKey" -> "zhipu_key"; case "comfyuiToken" -> "comfyui_token";
            case "geminiKey" -> "gemini_key"; case "gptImagesKey" -> "gpt_images_key";
            case "clothingDirectory" -> "clothing_directory"; case "videoDirectory" -> "video_directory";
            case "generatedDirectory" -> "generated_directory"; case "videoExportDirectory" -> "video_export_directory";
            case "auditOutputDirectory" -> "audit_output_directory"; case "storyOutputDirectory" -> "story_output_directory";
            case "bgmDirectory" -> "bgm_directory"; case "qwenOutputDirectory" -> "qwen_output_directory";
            default -> throw new IllegalArgumentException("未知配置字段: " + field);
        };
    }

    public record Account(String id, String username, String passwordHash, boolean administrator, boolean enabled,
                          Instant expiresAt, Set<String> allowedMenus) {
        public boolean expired() { return expiresAt != null && !expiresAt.isAfter(Instant.now()); }
        public boolean allows(String menu) { return administrator || allowedMenus.contains(menu); }
    }
    public record AccountView(String id, String username, String passwordDisplay, boolean administrator,
                              boolean enabled, Instant expiresAt, boolean expired, Set<String> allowedMenus,
                              Map<String, String> settings) {}
    public record CreateAccount(String username, String password, boolean administrator, boolean enabled,
                                Instant expiresAt, Set<String> allowedMenus, Map<String, String> settings) {}
    public record UpdateAccount(String username, String password, boolean administrator, boolean enabled,
                                Instant expiresAt, Set<String> allowedMenus, Map<String, String> settings) {}
    public record PortableAccount(String id, String username, String passwordHash, boolean administrator,
                                  boolean enabled, Instant expiresAt, Set<String> allowedMenus,
                                  Map<String, String> settings) {}
}
