package dev.learning.fashionagent.account;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Global, database-backed menu labels/order. IDs remain stable for permissions and routes. */
@Service
public class MenuConfigService {
    private final JdbcTemplate jdbc;

    public MenuConfigService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<MenuOptionView> options() {
        ensureDefaults();
        return jdbc.query("SELECT menu_id, label, sort_order, enabled FROM app_menu_config WHERE enabled=TRUE ORDER BY sort_order, menu_id",
                (rs, row) -> new MenuOptionView(rs.getString("menu_id"), rs.getString("label"), rs.getInt("sort_order"), true));
    }

    public List<MenuOptionView> all() {
        ensureDefaults();
        return jdbc.query("SELECT menu_id, label, sort_order, enabled FROM app_menu_config ORDER BY sort_order, menu_id",
                (rs, row) -> new MenuOptionView(rs.getString("menu_id"), rs.getString("label"), rs.getInt("sort_order"), rs.getBoolean("enabled")));
    }

    public boolean enabled(String id) {
        ensureDefaults();
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM app_menu_config WHERE menu_id=? AND enabled=TRUE", Integer.class, id);
        return count != null && count > 0;
    }

    @Transactional
    public List<MenuOptionView> update(List<MenuUpdate> updates) {
        ensureDefaults();
        if (updates == null) throw new IllegalArgumentException("菜单配置不能为空");
        Set<String> seen = new HashSet<>();
        Instant now = Instant.now();
        for (int index = 0; index < updates.size(); index++) {
            MenuUpdate update = updates.get(index);
            if (update == null || update.id() == null || !MenuCatalog.ALL.contains(update.id()) || !seen.add(update.id())) continue;
            String label = update.label() == null ? "" : update.label().trim();
            if (label.isBlank()) throw new IllegalArgumentException("菜单名称不能为空");
            if (label.length() > 40) throw new IllegalArgumentException("菜单名称不能超过40个字符");
            int order = update.sortOrder() == null ? index : Math.max(0, Math.min(9999, update.sortOrder()));
            boolean enabled = update.enabled() == null || update.enabled();
            if ("account-settings".equals(update.id()) || "menu-settings".equals(update.id())) enabled = true;
            jdbc.update("UPDATE app_menu_config SET label=?, sort_order=?, enabled=?, updated_at=? WHERE menu_id=?",
                    label, order, enabled, Timestamp.from(now), update.id());
        }
        return all();
    }

    private synchronized void ensureDefaults() {
        Instant now = Instant.now();
        int order = 0;
        for (var entry : MenuCatalog.MENUS.entrySet()) {
            try {
                jdbc.update("INSERT INTO app_menu_config(menu_id, label, sort_order, enabled, updated_at) SELECT ?, ?, ?, TRUE, ? WHERE NOT EXISTS (SELECT 1 FROM app_menu_config WHERE menu_id=?)",
                        entry.getKey(), entry.getValue(), order++, Timestamp.from(now), entry.getKey());
            } catch (RuntimeException ignored) {
                // The schema initializer or another request may have inserted the row concurrently.
            }
        }
    }

    public record MenuOptionView(String id, String label, int sortOrder, boolean enabled) {}
    public record MenuUpdate(String id, String label, Integer sortOrder, Boolean enabled) {}
}
