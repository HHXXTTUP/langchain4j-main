package dev.learning.kidsgrowth.animation;

import dev.learning.kidsgrowth.ai.ChildLessonDraft;
import dev.learning.kidsgrowth.ai.ChildLessonGenerator;
import org.springframework.stereotype.Service;

@Service
public class AnimationSceneService {

    private static final int MAX_INPUT_LENGTH = 30;

    private final ChildLessonGenerator lessonGenerator;

    public AnimationSceneService(ChildLessonGenerator lessonGenerator) {
        this.lessonGenerator = lessonGenerator;
    }

    public AnimationScene generate(String rawChineseText, String style) {
        return generate(rawChineseText, null, style);
    }

    public AnimationScene generate(String rawChineseText, String existingEnglishText, String style) {
        String chineseText = normalizeInput(rawChineseText);
        String englishText = existingEnglishText;
        if (englishText == null || englishText.isBlank()) {
            ChildLessonDraft lesson = lessonGenerator.generate(chineseText);
            englishText = lesson.englishText();
        }
        return AnimationSceneFactory.create(chineseText, englishText.trim(), style);
    }

    private static String normalizeInput(String rawChineseText) {
        if (rawChineseText == null || rawChineseText.isBlank()) {
            throw new IllegalArgumentException("请先输入想做成动画的中文单词");
        }
        String text = rawChineseText.trim();
        if (text.length() > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("请输入30个字以内的单词或短语");
        }
        return text;
    }
}
