package dev.learning.kidsgrowth.speech;

public interface SpeechRecognitionGateway {

    String recognizeEnglish(byte[] wavAudio);

    boolean isReady();
}
