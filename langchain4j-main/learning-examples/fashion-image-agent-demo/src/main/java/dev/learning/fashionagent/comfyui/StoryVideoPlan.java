package dev.learning.fashionagent.comfyui;

import java.util.List;

public record StoryVideoPlan(List<Shot> shots, String planningNotes) {
    public StoryVideoPlan {
        shots = shots == null ? List.of() : List.copyOf(shots);
        planningNotes = planningNotes == null ? "" : planningNotes;
    }

    public record Shot(
            int sequence,
            int duration,
            String interfaceType,
            String prompt,
            String environment,
            List<String> characters,
            List<DialogueLine> dialogueLines,
            String firstFrameSource,
            String lastFrameSource,
            boolean characterImageRequired,
            String characterImageHint,
            String dialogue) {
        public Shot {
            environment = environment == null ? "" : environment;
            characters = characters == null ? List.of() : List.copyOf(characters);
            dialogueLines = dialogueLines == null ? List.of() : List.copyOf(dialogueLines);
        }
    }

    public record DialogueLine(String speaker, String text, String tone) {}
}
