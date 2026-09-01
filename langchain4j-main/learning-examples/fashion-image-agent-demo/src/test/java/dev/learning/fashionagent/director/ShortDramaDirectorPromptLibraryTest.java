package dev.learning.fashionagent.director;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.learning.fashionagent.account.MenuCatalog;
import org.junit.jupiter.api.Test;

class ShortDramaDirectorPromptLibraryTest {
    private final ShortDramaDirectorPromptLibrary library = new ShortDramaDirectorPromptLibrary();

    @Test
    void routesOnlyRelevantRulesForDialogueDiagnosis() {
        String prompt = library.instructions(ShortDramaDirectorMode.DIALOGUE_DOCTOR, "R1", "红果", "16:9");
        assertThat(prompt).contains("dialogue-doctor-7d.md", "dialogue-speed-check.md", "当前执行模式：台词诊断", "红果", "16:9");
        assertThat(prompt).doesNotContain("专项规则：seedance-render-engine.md", "专项规则：asset-spatial-ledger.md");
    }

    @Test
    void routesSeedanceRulesForVideoPrompt() {
        String prompt = library.instructions(ShortDramaDirectorMode.VIDEO_PROMPT, "R3", "抖音", "9:16");
        assertThat(prompt).contains("seedance-render-engine.md", "model-adapters.md", "camera-transitions-6types.md", "R3");
    }

    @Test
    void exposesMenuAndRejectsUnknownMode() {
        assertThat(MenuCatalog.MENUS).containsEntry("short-drama-director", "短剧导演");
        assertThatThrownBy(() -> ShortDramaDirectorMode.parse("unknown")).isInstanceOf(IllegalArgumentException.class);
    }
}
