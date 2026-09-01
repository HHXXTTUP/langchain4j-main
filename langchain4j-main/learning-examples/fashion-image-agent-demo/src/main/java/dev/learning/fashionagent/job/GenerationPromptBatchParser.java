package dev.learning.fashionagent.job;

import java.util.Arrays;
import java.util.List;

public final class GenerationPromptBatchParser {

    public static final int MAX_BATCH_SIZE = 20;

    private GenerationPromptBatchParser() {}

    public static List<String> parse(String rawPrompt) {
        if (rawPrompt == null || rawPrompt.isBlank()) {
            throw new IllegalArgumentException("描述词不能为空");
        }
        List<String> prompts = Arrays.stream(rawPrompt.split("[;；]", -1))
                .map(String::trim)
                .filter(prompt -> !prompt.isBlank())
                .toList();
        if (prompts.isEmpty()) {
            throw new IllegalArgumentException("描述词不能为空");
        }
        if (prompts.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("一次最多批量创建 " + MAX_BATCH_SIZE + " 条图片任务");
        }
        return prompts;
    }
}
