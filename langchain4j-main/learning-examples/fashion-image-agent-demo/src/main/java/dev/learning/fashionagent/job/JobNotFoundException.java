package dev.learning.fashionagent.job;

import java.util.UUID;

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(UUID id) {
        super("生成任务不存在：" + id);
    }
}
