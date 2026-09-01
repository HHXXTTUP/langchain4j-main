package dev.learning.kidsgrowth.animation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnimationSceneFactoryTest {

    @Test
    void shouldMapCommonEnglishWordsToLocalAnimationTemplates() {
        AnimationScene scene = AnimationSceneFactory.create("苹果", "apple", "温柔日常");

        assertThat(scene.template()).isEqualTo("apple");
        assertThat(scene.emoji()).isEqualTo("🍎");
        assertThat(scene.motion()).isEqualTo("bounce");
        assertThat(scene.style()).isEqualTo("温柔日常");
    }

    @Test
    void shouldUseFriendlyGenericTemplateForUnknownWords() {
        AnimationScene scene = AnimationSceneFactory.create("书包", "backpack", "奇幻冒险");

        assertThat(scene.template()).isEqualTo("generic");
        assertThat(scene.emoji()).isEqualTo("✨");
        assertThat(scene.motion()).isEqualTo("bounce");
    }
}
