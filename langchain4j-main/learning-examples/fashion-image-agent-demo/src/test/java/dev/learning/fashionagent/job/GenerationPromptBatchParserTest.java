package dev.learning.fashionagent.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class GenerationPromptBatchParserTest {

    @Test
    void shouldSplitEnglishAndChineseSemicolonsAndIgnoreEmptyParts() {
        assertEquals(
                List.of("卧室美女", "沙滩美女", "办公室美女", "学校美女"),
                GenerationPromptBatchParser.parse(" 卧室美女;沙滩美女； ;办公室美女;学校美女 "));
    }

    @Test
    void shouldKeepDuplicatePromptsAsIndependentTasks() {
        assertEquals(
                List.of("沙滩美女", "沙滩美女"),
                GenerationPromptBatchParser.parse("沙滩美女;沙滩美女"));
    }

    @Test
    void shouldRejectMoreThanTwentyTasks() {
        String prompts = String.join(";", java.util.Collections.nCopies(21, "人物"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> GenerationPromptBatchParser.parse(prompts));

        assertEquals("一次最多批量创建 20 条图片任务", exception.getMessage());
    }
}
