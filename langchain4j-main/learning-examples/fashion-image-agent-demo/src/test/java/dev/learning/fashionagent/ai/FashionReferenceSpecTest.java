package dev.learning.fashionagent.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FashionReferenceSpecTest {

    @Test
    void shouldPutFixedIdentityAndBackgroundRequirementsBeforeAiClothingDetails() {
        String analysisDetails = "黑色长直发，银色发冠，红色丝绸连衣裙，黑色长手套和金色腿环";

        FashionReferenceSpec spec = new FashionReferenceSpec(
                AnalysisMode.MULTIMODAL_AI,
                "红黑礼服造型",
                List.of("红色连衣裙"),
                List.of("红色", "黑色"),
                List.of("丝绸"),
                List.of("银色发冠"),
                List.of("黑色长手套", "金色腿环"),
                List.of("黑色长直发", "银色发冠", "红色连衣裙"),
                analysisDetails);

        assertTrue(spec.replacementPrompt().startsWith(FashionReferenceSpec.fixedReplacementPrefix()));
        assertTrue(spec.replacementPrompt().contains("背景按上传底图一定不要改变"));
        assertTrue(spec.replacementPrompt().contains("人物的长相一定要按原图一样不要改变"));
        assertTrue(spec.replacementPrompt().contains("图1是人物脸部身份、身体姿势、肢体动作"));
        assertTrue(spec.replacementPrompt().contains("禁止复制或参考图2人物的姿势、动作"));
        assertTrue(spec.replacementPrompt().contains("绝不能采用图2的背景"));
        assertTrue(spec.replacementPrompt().contains("对图2穿搭的视觉分析补充"));
        assertTrue(spec.replacementPrompt().indexOf(analysisDetails)
                > spec.replacementPrompt().indexOf("人物的长相一定要按原图一样不要改变"));
        assertEquals(analysisDetails, spec.analysisSupplement());
    }
}
