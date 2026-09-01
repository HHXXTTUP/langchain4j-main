package dev.learning.kidsgrowth.speech;

public interface TextToSpeechGateway {

    byte[] synthesize(String text, SpeechVoice voice);

    boolean isReady();

    String providerName();
}
