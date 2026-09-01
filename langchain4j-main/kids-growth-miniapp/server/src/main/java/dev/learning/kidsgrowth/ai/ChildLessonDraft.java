package dev.learning.kidsgrowth.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;

@Description("为3到6岁儿童生成的英语单词互动课")
public record ChildLessonDraft(
        @JsonProperty(required = true) @Description("用户中文词语最常用、最简单的英文表达")
                String englishText,
        @JsonProperty(required = true) @Description("给家长看的简短中文发音提示，不使用生僻术语")
                String pronunciationTip,
        @JsonProperty(required = true) @Description("不超过6个英文单词的低龄示例句")
                String exampleSentence,
        @JsonProperty(required = true) @Description("示例句的自然中文意思")
                String exampleTranslation,
        @JsonProperty(required = true) @Description("2到3个非常简单、友善、有具体画面的中文互动问题")
                List<String> questionsChinese,
        @JsonProperty(required = true) @Description("希望孩子开口说出的简短英文答案")
                String expectedAnswer) {}
