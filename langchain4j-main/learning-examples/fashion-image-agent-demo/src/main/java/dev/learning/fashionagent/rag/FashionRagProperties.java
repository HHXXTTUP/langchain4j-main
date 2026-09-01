package dev.learning.fashionagent.rag;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fashion.rag")
public class FashionRagProperties {

    private boolean enabled = true;
    private Path knowledgeDirectory = Path.of("knowledge", "fashion");
    private int maxResults = 3;
    private double minScore = 0.55;
    private int maxSegmentSize = 450;
    private int segmentOverlap = 60;
    private int maxContextLength = 1200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Path getKnowledgeDirectory() {
        return knowledgeDirectory;
    }

    public void setKnowledgeDirectory(Path knowledgeDirectory) {
        this.knowledgeDirectory = knowledgeDirectory;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    public int getMaxSegmentSize() {
        return maxSegmentSize;
    }

    public void setMaxSegmentSize(int maxSegmentSize) {
        this.maxSegmentSize = maxSegmentSize;
    }

    public int getSegmentOverlap() {
        return segmentOverlap;
    }

    public void setSegmentOverlap(int segmentOverlap) {
        this.segmentOverlap = segmentOverlap;
    }

    public int getMaxContextLength() {
        return maxContextLength;
    }

    public void setMaxContextLength(int maxContextLength) {
        this.maxContextLength = maxContextLength;
    }
}
