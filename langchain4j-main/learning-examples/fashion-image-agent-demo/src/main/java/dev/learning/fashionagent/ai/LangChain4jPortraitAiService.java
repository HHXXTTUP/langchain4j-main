package dev.learning.fashionagent.ai;

import dev.langchain4j.data.message.ImageContent;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class LangChain4jPortraitAiService implements PortraitAiService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LangChain4jPortraitAiService.class);
    // Keep the requested adult body silhouette explicit in every final prompt while retaining a fashion-safe presentation.
    private static final String BODY_SHAPE_REQUIREMENT =
            "成年女性，胸部丰满、约F杯比例，上围曲线明显但自然，身体比例协调，穿着完整不透明且合体的时尚服装，不裸露、不透明透视";
    private static final String AUDIT_SAFE_PRESET = "亚洲面孔，20到30岁的成年女性，身材比例自然，"
            + "穿着完整得体的日常时尚服装，正面面向镜头自然站立，双臂自然下垂，双腿自然并拢，"
            + "全身完整入镜，四肢清晰无遮挡，人物居中，9:16竖版商业人像构图";
    private static final List<String> PORTRAIT_VARIANTS = List.of(
            "清新自然风：鹅蛋脸、柔和眉形、明亮眼神、微笑表情；中分黑色长直发，发尾自然垂落；明亮柔和的生活化环境",
            "温柔知性风：小巧脸型、细致五官、温和眼神、自然唇色；侧分大波浪长发，发丝有层次；柔和窗光与安静室内氛围",
            "都市利落风：轮廓清晰的椭圆脸、精神眉眼、坚定但自然的眼神；齐肩锁骨短发，轻微内扣；现代城市背景与干净硬朗光线",
            "活力运动风：自然圆脸、清爽眉眼、轻松笑容；高马尾或低马尾，发丝整洁有动感；通透明亮的户外环境与自然光",
            "复古优雅风：鹅蛋脸、细长眼型、克制表情、精致鼻唇比例；低盘发或复古侧卷发，发色自然；暖色室内光与经典构图",
            "学院清爽风：柔和脸型、清澈眼神、自然眉形；半扎公主头或肩上短发，发饰简洁；清爽明亮的校园或书房环境",
            "艺术电影风：立体但自然的脸部比例、专注眼神、淡雅表情；微卷锁骨发，层次分明；有空间纵深的环境与电影感侧光",
            "轻熟通勤风：比例协调的长脸或椭圆脸、端庄眉眼、沉稳神态；低马尾或简洁盘发；干净办公室环境与均匀柔光");

    private final ModelPortraitAgent agent;
    private final VisionImageEncoder imageEncoder;
    private final FashionAiCallExecutor callExecutor;
    private final PortraitQualityGate qualityGate;
    private final String portraitPreset;
    private final String portraitOutputSize;

    LangChain4jPortraitAiService(
            ModelPortraitAgent agent,
            VisionImageEncoder imageEncoder,
            FashionAiProperties properties) {
        this.agent = agent;
        this.imageEncoder = imageEncoder;
        this.callExecutor = new FashionAiCallExecutor(properties);
        this.qualityGate = new PortraitQualityGate(properties);
        this.portraitPreset = requirePreset(properties.getPortraitPreset());
        this.portraitOutputSize = requirePositive(properties.getPortraitOutputWidth(), "人物输出宽度")
                + "x"
                + requirePositive(properties.getPortraitOutputHeight(), "人物输出高度");
    }

    @Override
    public PortraitPromptSpec enhancePrompt(String description) {
        long startedAt = System.nanoTime();
        LOGGER.info("LangChain4j 人物提示词扩写开始 description={}", description);
        try {
            ModelPortraitAgent.ModelPortraitPrompt result = callExecutor.execute(
                    "人物提示词扩写",
                    () -> agent.enhance(enhancementInstruction(description, portraitPreset)));
            requireText(result.generationPrompt(), "LangChain4j 未返回人物生成提示词");
            String generationPrompt = appendSceneAndVariationHints(
                    applyPreset(result.generationPrompt(), portraitPreset, portraitOutputSize),
                    description);
            PortraitPromptSpec spec = new PortraitPromptSpec(
                    AnalysisMode.MULTIMODAL_AI,
                    description,
                    result.appearance(),
                    result.bodyAndPose(),
                    result.environment(),
                    result.lighting(),
                    result.composition(),
                    result.visualStyle(),
                    generationPrompt);
            LOGGER.info("LangChain4j 人物提示词扩写完成 elapsedMs={} promptLength={}",
                    elapsedMillis(startedAt), spec.generationPrompt().length());
            LOGGER.info("人物提示词场景锚点 originalDescription={} sceneAnchor={}",
                    description, sceneAnchor(description));
            return spec;
        } catch (CancellationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.error("LangChain4j 人物提示词扩写失败，任务立即停止，不会调用人物生成接口", exception);
            throw failFast("人物提示词扩写", exception);
        }
    }

    @Override
    public PortraitPromptSpec rewritePromptAfterAudit(
            PortraitPromptSpec rejectedPrompt,
            int auditRetryNumber) {
        long startedAt = System.nanoTime();
        LOGGER.warn("LangChain4j 正在重写被 RunningHub 审核拒绝的人物提示词 auditRetry={}", auditRetryNumber);
        try {
            ModelPortraitAgent.ModelPortraitPrompt result = callExecutor.execute(
                    "人物审核提示词重写",
                    () -> agent.rewriteAfterAudit(auditRewriteInstruction(rejectedPrompt, auditRetryNumber)));
            requireText(result.generationPrompt(), "LangChain4j 未返回审核重写提示词");
            PortraitPromptSpec rewritten = toPromptSpec(
                    rejectedPrompt.originalDescription(),
                    result,
                    AUDIT_SAFE_PRESET);
            LOGGER.info("LangChain4j 人物审核提示词重写完成 auditRetry={} elapsedMs={} promptLength={}",
                    auditRetryNumber,
                    elapsedMillis(startedAt),
                    rewritten.generationPrompt().length());
            return rewritten;
        } catch (CancellationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.error("LangChain4j 人物审核提示词重写失败，任务立即停止", exception);
            throw failFast("人物审核提示词重写", exception);
        }
    }

    @Override
    public PortraitQualityReport inspectPortrait(
            Path portraitImage,
            PortraitPromptSpec promptSpec,
            String appliedPrompt,
            int attemptNumber) {
        long startedAt = System.nanoTime();
        LOGGER.info("LangChain4j 人物图片质检开始 attempt={} image={}", attemptNumber, portraitImage);
        try {
            String instruction = """
                    本次是第 %d 次人物生成。
                    用户原始描述：%s
                    扩写后的人物规格：%s
                    本次实际生成提示词：%s
                    请判断图片能否作为后续服装替换的人物底图。
                    """.formatted(
                    attemptNumber,
                    promptSpec.originalDescription(),
                    promptSpec,
                    appliedPrompt);
            ImageContent image = imageEncoder.encode(portraitImage);
            ModelPortraitAgent.ModelPortraitQuality assessment = callExecutor.execute(
                    "人物图片质检",
                    () -> agent.inspect(instruction, image));
            boolean passed = qualityGate.passes(
                    assessment.technicallyValid(),
                    assessment.overallScore(),
                    assessment.promptAlignmentScore(),
                    assessment.anatomyScore(),
                    assessment.imageQualityScore());
            PortraitQualityReport report = new PortraitQualityReport(
                    AnalysisMode.MULTIMODAL_AI,
                    true,
                    passed,
                    assessment.technicallyValid(),
                    assessment.overallScore(),
                    assessment.promptAlignmentScore(),
                    assessment.anatomyScore(),
                    assessment.imageQualityScore(),
                    !passed && assessment.retryable(),
                    assessment.summary(),
                    assessment.issues(),
                    assessment.correctionPrompt());
            LOGGER.info("LangChain4j 人物图片质检完成 attempt={} elapsedMs={} passed={} scores=[overall:{}, alignment:{}, anatomy:{}, quality:{}] issues={}",
                    attemptNumber,
                    elapsedMillis(startedAt),
                    report.passed(),
                    report.overallScore(),
                    report.promptAlignmentScore(),
                    report.anatomyScore(),
                    report.imageQualityScore(),
                    report.issues());
            return report;
        } catch (CancellationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            String reason = "LangChain4j 人物图片质检调用失败，已跳过质检并继续流程："
                    + failureDetail(exception);
            LOGGER.error(reason, exception);
            return PortraitQualityReport.notEvaluated(reason);
        }
    }

    @Override
    public boolean aiEnabled() {
        return true;
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
    }

    private static String requirePreset(String preset) {
        if (preset == null || preset.isBlank()) {
            throw new IllegalArgumentException("人物生成固定约束不能为空");
        }
        return preset.trim();
    }

    private static String enhancementInstruction(String description, String preset) {
        return """
                用户原始描述：
                %s

                用户场景锚点（不可省略，必须转化为环境、服饰、光影和氛围）：
                %s

                固定业务约束（优先级高于与其冲突的用户描述，必须全部写入 generationPrompt）：
                %s

                人物外观与风格要求：必须在 appearance 和 generationPrompt 中具体描述成年女性的脸型、眉形、眼睛形状与眼神、鼻唇比例、肤色质感、发色、发长、分缝、卷直程度和发饰，不能只写“美女”。
                从下面的风格方向中选择一个并完整落实，同时让不同的用户描述尽量使用不同的风格和发型；不要把多个风格堆叠成互相冲突的描述：
                %s
                生成提示词需要同时包含人物样貌、发型、身材比例、站立姿态、环境氛围、光线、构图和摄影风格，人物必须是明确的成年女性，服装完整得体，适合公开展示和后续换装。
                """.formatted(
                description,
                sceneAnchor(description),
                preset,
                variationHint(description));
    }

    private String auditRewriteInstruction(PortraitPromptSpec rejectedPrompt, int auditRetryNumber) {
        return """
                这是第 %d 次内容审核后的安全重写。
                用户最初希望生成：%s
                用户场景锚点（必须保留）：%s

                请从用户最初的正常意图重新规划人物图片，不要复制上一版 generationPrompt。
                必须采用以下安全业务约束：%s。
                同时保留场景对应的环境、服饰、光影和氛围，并采用这个差异化外观方向：%s。
                最终画布为%s像素。
                """.formatted(
                auditRetryNumber,
                rejectedPrompt.originalDescription(),
                sceneAnchor(rejectedPrompt.originalDescription()),
                AUDIT_SAFE_PRESET,
                variationHint(rejectedPrompt.originalDescription()),
                portraitOutputSize);
    }

    private PortraitPromptSpec toPromptSpec(
            String originalDescription,
            ModelPortraitAgent.ModelPortraitPrompt result,
            String preset) {
        String generationPrompt = appendSceneAndVariationHints(
                applyPreset(result.generationPrompt(), preset, portraitOutputSize),
                originalDescription);
        return new PortraitPromptSpec(
                AnalysisMode.MULTIMODAL_AI,
                originalDescription,
                result.appearance(),
                result.bodyAndPose(),
                result.environment(),
                result.lighting(),
                result.composition(),
                result.visualStyle(),
                generationPrompt);
    }

    private static String applyPreset(String generationPrompt, String preset, String outputSize) {
        return generationPrompt.trim()
                + "。固定人物要求：" + preset
                + "。固定体型要求：" + BODY_SHAPE_REQUIREMENT
                + "。最终输出画布为" + outputSize + "像素。";
    }

    private static String appendSceneAndVariationHints(String generationPrompt, String description) {
        return generationPrompt.trim()
                + "。用户原始场景锚点（必须保留并具体体现）："
                + sceneAnchor(description)
                + "。本次人物外观差异化方向（请落实到脸部和发型）："
                + variationHint(description);
    }

    private static String sceneAnchor(String description) {
        String original = description == null ? "" : description.trim();
        String normalized = original.toLowerCase();
        if (normalized.contains("卧室")) {
            return original + "；卧室室内环境，床铺、床头柜或柔和家居细节，暖色或晨光照明，安静放松的生活氛围，服饰采用完整得体的居家或轻便日常穿搭";
        }
        if (normalized.contains("沙滩") || normalized.contains("海边") || normalized.contains("海滩")) {
            return original + "；开阔沙滩与海面背景，天空和海风带来的通透感，明亮自然日光，轻松清爽的度假氛围，服饰采用完整得体的夏日休闲穿搭";
        }
        if (normalized.contains("办公室") || normalized.contains("办公")) {
            return original + "；现代办公室环境，办公桌、玻璃窗或简洁室内线条，均匀的窗边自然光，专业利落的工作氛围，服饰采用完整得体的通勤或商务休闲穿搭";
        }
        if (normalized.contains("学校") || normalized.contains("校园") || normalized.contains("教室")) {
            return original + "；校园或教室环境，课桌、走廊或绿化背景，明亮柔和的日光，清爽积极的学习氛围，服饰采用完整得体的成年校园风或休闲穿搭";
        }
        return original + "；请从原始描述中识别明确的地点、时间、天气和活动，并据此匹配环境布置、服饰风格、光线方向、色温和整体氛围，不得替换成无关场景";
    }

    private static String variationHint(String description) {
        int index = Math.floorMod(description == null ? 0 : description.trim().hashCode(), PORTRAIT_VARIANTS.size());
        return PORTRAIT_VARIANTS.get(index);
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + "必须大于0");
        }
        return value;
    }

    private static IllegalStateException failFast(String operation, RuntimeException cause) {
        String detail = failureDetail(cause);
        return new IllegalStateException(
                "LangChain4j " + operation + "失败，任务已停止且不会降级执行：" + detail,
                cause);
    }

    private static String failureDetail(RuntimeException cause) {
        return cause.getMessage() == null || cause.getMessage().isBlank()
                ? cause.getClass().getSimpleName()
                : cause.getMessage();
    }
}
