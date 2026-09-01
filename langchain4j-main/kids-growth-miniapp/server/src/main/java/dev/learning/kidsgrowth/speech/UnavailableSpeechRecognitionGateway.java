package dev.learning.kidsgrowth.speech;

import dev.learning.kidsgrowth.web.ExternalServiceUnavailableException;
import org.springframework.stereotype.Component;

@Component
public class UnavailableSpeechRecognitionGateway implements SpeechRecognitionGateway {

    @Override
    public String recognizeEnglish(byte[] wavAudio) {
        throw new ExternalServiceUnavailableException(
                "当前只启用了 edge-tts 语音合成，语音识别尚未接入，请使用“我读完啦”按钮");
    }

    @Override
    public boolean isReady() {
        return false;
    }
}
