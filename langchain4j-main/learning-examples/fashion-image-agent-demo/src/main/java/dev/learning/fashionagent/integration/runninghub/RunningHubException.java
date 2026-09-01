package dev.learning.fashionagent.integration.runninghub;

public class RunningHubException extends RuntimeException {

    public RunningHubException(String message) {
        super(message);
    }

    public RunningHubException(String message, Throwable cause) {
        super(message, cause);
    }
}
