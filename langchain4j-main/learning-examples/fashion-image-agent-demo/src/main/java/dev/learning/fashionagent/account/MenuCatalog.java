package dev.learning.fashionagent.account;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

public final class MenuCatalog {
    public static final Map<String, String> MENUS;
    public static final Set<String> ALL;

    static {
        Map<String, String> menus = new LinkedHashMap<>();
        menus.put("workbench", "工作台");
        menus.put("tasks", "任务列表");
        menus.put("video-canvas", "视频工作台");
        menus.put("dialogue-extraction", "提取台词");
        menus.put("video-bgm", "视频合成");
        menus.put("direct-outfit", "人物换装");
        menus.put("audit-redraw", "过审重绘");
        menus.put("gpt-images", "GPT 文生图");
        menus.put("video-script", "视频脚本");
        menus.put("short-drama-director", "短剧导演");
        menus.put("my-scripts", "我的剧本");
        menus.put("script-replication", "剧本复刻");
        menus.put("knowledge", "资料库");
        menus.put("logs", "日志");
        menus.put("account-settings", "账号配置");
        menus.put("menu-settings", "菜单配置");
        MENUS = Collections.unmodifiableMap(menus);
        ALL = MENUS.keySet();
    }

    private MenuCatalog() {}

    public static List<MenuOption> options() {
        return MENUS.entrySet().stream().map(entry -> new MenuOption(entry.getKey(), entry.getValue())).toList();
    }

    public record MenuOption(String id, String label) {}
}
