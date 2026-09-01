package dev.learning.fashionagent.account;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AccountContextTest {
    @AfterEach void clear() { AccountContext.clear(); }

    @Test
    void ordinaryAccountDoesNotInheritGlobalSecret() {
        AccountContext.set(new AccountContext.Snapshot("1", "user", false, Map.of("qwenKey", "")));
        assertEquals("", AccountContext.secretValue("qwenKey", "global-key"));
    }

    @Test
    void administratorKeepsLegacyEnvironmentFallback() {
        AccountContext.set(new AccountContext.Snapshot("1", "admin", true, Map.of("qwenKey", "")));
        assertEquals("global-key", AccountContext.secretValue("qwenKey", "global-key"));
    }

    @Test
    void accountSpecificSecretAlwaysWins() {
        AccountContext.set(new AccountContext.Snapshot("1", "user", false, Map.of("qwenKey", "account-key")));
        assertEquals("account-key", AccountContext.secretValue("qwenKey", "global-key"));
    }
}
