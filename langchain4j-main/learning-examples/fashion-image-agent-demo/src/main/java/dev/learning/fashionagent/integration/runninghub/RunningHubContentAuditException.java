package dev.learning.fashionagent.integration.runninghub;

public class RunningHubContentAuditException extends RunningHubException {

    private final String errorCode;

    public RunningHubContentAuditException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
