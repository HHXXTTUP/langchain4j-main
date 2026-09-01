package dev.learning.fashionagent.web;

import dev.learning.fashionagent.ai.FashionAiProperties;
import dev.learning.fashionagent.ai.FashionVisionService;
import dev.learning.fashionagent.ai.PortraitAiService;
import dev.learning.fashionagent.config.RunningHubProperties;
import dev.learning.fashionagent.job.GenerationJobService;
import dev.learning.fashionagent.service.ClothingCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
class SystemReadinessController {

    private final RunningHubProperties properties;
    private final ClothingCatalog clothingCatalog;
    private final FashionAiProperties aiProperties;
    private final FashionVisionService fashionVisionService;
    private final PortraitAiService portraitAiService;
    private final GenerationJobService jobService;

    SystemReadinessController(
            RunningHubProperties properties,
            ClothingCatalog clothingCatalog,
            FashionAiProperties aiProperties,
            FashionVisionService fashionVisionService,
            PortraitAiService portraitAiService,
            GenerationJobService jobService) {
        this.properties = properties;
        this.clothingCatalog = clothingCatalog;
        this.aiProperties = aiProperties;
        this.fashionVisionService = fashionVisionService;
        this.portraitAiService = portraitAiService;
        this.jobService = jobService;
    }

    @GetMapping("/readiness")
    ReadinessResponse readiness() {
        boolean keyConfigured = properties.isApiKeyConfigured();
        int clothingImageCount = clothingCatalog.availableImageCount();
        boolean allAiCapabilitiesEnabled = fashionVisionService.aiEnabled() && portraitAiService.aiEnabled();
        boolean ready = keyConfigured && clothingImageCount > 0 && allAiCapabilitiesEnabled;
        boolean historyDatabaseReady = jobService.isHistoryDatabaseReady();
        String message;
        if (!keyConfigured) {
            message = "尚未配置 RunningHub API Key";
        } else if (clothingImageCount == 0) {
            message = "API Key 已配置，请向 clothing 目录加入服装图片";
        } else if (!allAiCapabilitiesEnabled) {
            message = "尚未配置可用的 ZHIPU_API_KEY；当前禁止 AI 降级，任务不会使用默认提示词执行";
        } else {
            String aiMode = "LangChain4j 提示词扩写与双重视觉质检已启用（"
                    + aiProperties.getModelName() + "），人物生成与换装均使用 RunningHub AI 应用";
            String historyMode = historyDatabaseReady
                    ? "本地 H2 历史记录已启用"
                    : "本地历史表未就绪，当前使用内存模式";
            message = "系统已就绪，共有 " + clothingImageCount + " 张服装图片；" + aiMode + "；" + historyMode;
        }
        return new ReadinessResponse(
                ready,
                keyConfigured,
                clothingImageCount,
                fashionVisionService.aiEnabled(),
                portraitAiService.aiEnabled(),
                aiProperties.getModelName(),
                historyDatabaseReady,
                message);
    }

    record ReadinessResponse(
            boolean ready,
            boolean apiKeyConfigured,
            int clothingImageCount,
            boolean fashionAiEnabled,
            boolean portraitAiEnabled,
            String fashionAiModel,
            boolean historyDatabaseReady,
            String message) {}
}
