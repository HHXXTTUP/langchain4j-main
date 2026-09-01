package dev.learning.kidsgrowth.speech;

import dev.learning.kidsgrowth.config.EdgeTtsProperties;
import dev.learning.kidsgrowth.web.ExternalServiceUnavailableException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EdgeTtsClient implements TextToSpeechGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(EdgeTtsClient.class);
    private static final long READINESS_TIMEOUT_SECONDS = 5;

    private final EdgeTtsProperties properties;

    public EdgeTtsClient(EdgeTtsProperties properties) {
        this.properties = properties;
        LOGGER.info("edge-tts 使用 Python：{}", properties.resolvedPythonCommand());
    }

    @Override
    public byte[] synthesize(String text, SpeechVoice voice) {
        if (!properties.isEnabled()) {
            throw new ExternalServiceUnavailableException("edge-tts 已被禁用");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("待转换的文字不能为空");
        }

        Path audioFile = null;
        Path logFile = null;
        Process process = null;
        List<String> command = buildCommand(text, voice, null);
        try {
            audioFile = Files.createTempFile("kids-edge-tts-", ".mp3");
            logFile = Files.createTempFile("kids-edge-tts-", ".log");
            command = buildCommand(text, voice, audioFile);
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start();

            boolean finished = process.waitFor(properties.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ExternalServiceUnavailableException("edge-tts 生成语音超时，请稍后再试");
            }
            if (process.exitValue() != 0) {
                throw new ExternalServiceUnavailableException(
                        "edge-tts 生成语音失败（退出码 " + process.exitValue() + "，Python "
                                + command.get(0) + "）：" + readProcessLog(logFile));
            }
            if (!Files.isRegularFile(audioFile) || Files.size(audioFile) == 0) {
                throw new ExternalServiceUnavailableException("edge-tts 返回了空音频");
            }
            return Files.readAllBytes(audioFile);
        } catch (ExternalServiceUnavailableException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceUnavailableException("edge-tts 语音生成被中断", exception);
        } catch (IOException exception) {
            throw new ExternalServiceUnavailableException(
                    "无法启动 edge-tts，请先运行 setup-edge-tts.ps1 或配置 EDGE_TTS_PYTHON", exception);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            deleteQuietly(audioFile);
            deleteQuietly(logFile);
        }
    }

    @Override
    public boolean isReady() {
        if (!properties.isEnabled()) {
            return false;
        }
        try {
            Process process = new ProcessBuilder(
                    properties.resolvedPythonCommand(), "-m", "edge_tts", "--version")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean finished = process.waitFor(READINESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException exception) {
            LOGGER.debug("edge-tts readiness check failed", exception);
            return false;
        }
    }

    @Override
    public String providerName() {
        return "edge-tts";
    }

    List<String> buildCommand(String text, SpeechVoice voice, Path outputFile) {
        String voiceName = voice == SpeechVoice.ENGLISH_CHILD
                ? properties.getEnglishVoice()
                : properties.getChineseVoice();
        List<String> command = new ArrayList<>();
        command.add(properties.resolvedPythonCommand());
        command.add("-m");
        command.add("edge_tts");
        command.add("--voice");
        command.add(voiceName);
        command.add("--rate=" + properties.getRate());
        command.add("--pitch=" + properties.getPitch());
        command.add("--text");
        command.add(text);
        if (outputFile != null) {
            command.add("--write-media");
            command.add(outputFile.toString());
        }
        return command;
    }

    private static String readProcessLog(Path logFile) {
        if (logFile == null || !Files.isRegularFile(logFile)) {
            return "没有错误详情";
        }
        try {
            String output = Files.readString(logFile, StandardCharsets.UTF_8).trim();
            if (output.isBlank()) {
                return "没有错误详情";
            }
            return output.length() > 500 ? output.substring(0, 500) : output;
        } catch (IOException exception) {
            return "无法读取错误详情";
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            LOGGER.debug("Unable to delete edge-tts temporary file {}", path, exception);
        }
    }
}
