package dev.learning.fashionagent.learning;

import dev.learning.fashionagent.ai.ClothingCatalogAnalysis;
import dev.learning.fashionagent.ai.FashionAiProperties;
import dev.learning.fashionagent.ai.FashionVisionService;
import dev.learning.fashionagent.service.ClothingCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ClothingCatalogLearningService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClothingCatalogLearningService.class);

    private final ClothingCatalog clothingCatalog;
    private final FashionVisionService visionService;
    private final FashionLearningRepository repository;
    private final FashionAiProperties aiProperties;
    private final Executor executor;
    private final AtomicReference<CatalogRefreshView> state = new AtomicReference<>(new CatalogRefreshView(
            "IDLE", "尚未生成服装资料", 0, 0, List.of(), Instant.now()));

    public ClothingCatalogLearningService(
            ClothingCatalog clothingCatalog,
            @Qualifier("fashionVisionService") FashionVisionService visionService,
            FashionLearningRepository repository,
            FashionAiProperties aiProperties,
            @Qualifier("fashionPipelineExecutor") Executor executor) {
        this.clothingCatalog = clothingCatalog;
        this.visionService = visionService;
        this.repository = repository;
        this.aiProperties = aiProperties;
        this.executor = executor;
    }

    public synchronized CatalogRefreshView startRefresh() {
        if (state.get().running()) {
            return state.get();
        }
        if (!visionService.aiEnabled()) {
            throw new IllegalStateException("服装资料生成需要可用的 GLM 多模态模型和 ZHIPU_API_KEY");
        }
        List<Path> images = clothingCatalog.images();
        if (images.isEmpty()) {
            throw new IllegalStateException("服装目录中没有可分析的图片");
        }
        // Fail before starting an expensive AI job when the learning tables are missing.
        repository.listClothingProfiles();
        CatalogRefreshView started = new CatalogRefreshView(
                "RUNNING", "准备分析 " + images.size() + " 张服装图片", 0, images.size(), List.of(), Instant.now());
        state.set(started);
        executor.execute(() -> refresh(images));
        return started;
    }

    public CatalogRefreshView status() {
        return state.get();
    }

    public List<ClothingProfileView> profiles() {
        return repository.listClothingProfiles().stream().map(ClothingProfileView::from).toList();
    }

    public Path image(String id) {
        ClothingProfile profile = repository.findClothingProfile(id)
                .orElseThrow(() -> new IllegalArgumentException("服装资料不存在：" + id));
        Path image = profile.imagePath().toAbsolutePath().normalize();
        Path catalogDirectory = clothingCatalog.images().stream()
                .findFirst()
                .map(path -> path.toAbsolutePath().normalize().getParent())
                .orElseThrow(() -> new IllegalStateException("服装目录为空"));
        if (!image.startsWith(catalogDirectory) || !Files.isRegularFile(image)) {
            throw new IllegalStateException("服装资料对应的本地图片已不存在：" + profile.fileName());
        }
        return image;
    }

    private void refresh(List<Path> images) {
        List<String> errors = new ArrayList<>();
        List<String> activeIds = new ArrayList<>();
        int processed = 0;
        try {
            for (Path image : images) {
                String id = sha256(image);
                activeIds.add(id);
                String progress = "正在分析 " + image.getFileName() + "（" + (processed + 1) + "/" + images.size() + "）";
                state.set(new CatalogRefreshView(
                        "RUNNING", progress, processed, images.size(), errors, Instant.now()));
                LOGGER.info("服装目录资料生成进度 {}", progress);
                try {
                    ClothingCatalogAnalysis analysis = visionService.analyzeCatalogImage(image);
                    repository.saveClothingProfile(new ClothingProfile(
                            id,
                            image.getFileName().toString(),
                            image.toAbsolutePath().normalize(),
                            id,
                            analysis,
                            aiProperties.getModelName(),
                            Instant.now()));
                } catch (RuntimeException exception) {
                    String error = image.getFileName() + "：" + rootMessage(exception);
                    errors.add(error);
                    LOGGER.error("服装目录资料生成单图失败 image={}", image, exception);
                }
                processed++;
                state.set(new CatalogRefreshView(
                        "RUNNING", "已处理 " + processed + "/" + images.size(),
                        processed, images.size(), errors, Instant.now()));
            }
            repository.deleteClothingProfilesNotIn(activeIds);
            String finalStatus = errors.isEmpty() ? "SUCCESS" : processed == errors.size() ? "FAILED" : "PARTIAL";
            String message = errors.isEmpty()
                    ? "已生成并保存 " + processed + " 套服装资料"
                    : "处理完成，成功 " + (processed - errors.size()) + " 套，失败 " + errors.size() + " 套";
            state.set(new CatalogRefreshView(
                    finalStatus, message, processed, images.size(), errors, Instant.now()));
            LOGGER.info("服装目录资料生成结束 status={} processed={} errors={}", finalStatus, processed, errors.size());
        } catch (RuntimeException exception) {
            errors.add(rootMessage(exception));
            state.set(new CatalogRefreshView(
                    "FAILED", "服装资料生成失败：" + rootMessage(exception),
                    processed, images.size(), errors, Instant.now()));
            LOGGER.error("服装目录资料生成任务失败", exception);
        }
    }

    private static String sha256(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("计算服装图片哈希失败：" + path, exception);
        }
    }

    private static String rootMessage(Throwable throwable) {
        String message = throwable.getClass().getSimpleName();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return message;
    }
}
