package dev.learning.kidsgrowth.ai;

import dev.learning.kidsgrowth.web.ExternalServiceUnavailableException;

final class UnavailableChildLessonGenerator implements ChildLessonGenerator {

    @Override
    public ChildLessonDraft generate(String chineseText) {
        throw new ExternalServiceUnavailableException("尚未配置 ZHIPU_API_KEY，儿童英语对话暂不可用");
    }
}
