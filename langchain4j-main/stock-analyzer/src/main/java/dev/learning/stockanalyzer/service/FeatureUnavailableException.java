package dev.learning.stockanalyzer.service;

public class FeatureUnavailableException extends RuntimeException {

    public FeatureUnavailableException(String message) {
        super(message);
    }
}
