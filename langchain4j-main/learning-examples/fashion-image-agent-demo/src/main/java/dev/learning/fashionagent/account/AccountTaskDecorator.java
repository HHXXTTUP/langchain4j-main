package dev.learning.fashionagent.account;

import org.springframework.core.task.TaskDecorator;

public class AccountTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        AccountContext.Snapshot captured = AccountContext.capture();
        return () -> {
            AccountContext.Snapshot previous = AccountContext.capture();
            try {
                if (captured == null) AccountContext.clear(); else AccountContext.set(captured);
                runnable.run();
            } finally {
                if (previous == null) AccountContext.clear(); else AccountContext.set(previous);
            }
        };
    }
}
