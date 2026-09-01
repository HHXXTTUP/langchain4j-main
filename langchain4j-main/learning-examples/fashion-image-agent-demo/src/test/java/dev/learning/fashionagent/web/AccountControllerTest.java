package dev.learning.fashionagent.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.learning.fashionagent.account.AccountService;
import dev.learning.fashionagent.account.ApplicationPackageService;
import dev.learning.fashionagent.account.NativeDirectoryPicker;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class AccountControllerTest {

    @Test
    void returnsNativeDirectorySelection() {
        NativeDirectoryPicker picker = mock(NativeDirectoryPicker.class);
        when(picker.choose("D:/videos")).thenReturn("E:\\AI影视复刻");
        AccountController controller = new AccountController(
                mock(AccountService.class), mock(ApplicationPackageService.class), picker);

        ResponseEntity<?> response = controller.selectDirectory(new AccountController.DirectoryRequest("D:/videos"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("path", "E:\\AI影视复刻"), response.getBody());
    }

    @Test
    void cancellationKeepsCurrentDirectoryUntouched() {
        NativeDirectoryPicker picker = mock(NativeDirectoryPicker.class);
        when(picker.choose("D:/videos")).thenReturn(null);
        AccountController controller = new AccountController(
                mock(AccountService.class), mock(ApplicationPackageService.class), picker);

        ResponseEntity<?> response = controller.selectDirectory(new AccountController.DirectoryRequest("D:/videos"));

        assertEquals(204, response.getStatusCode().value());
        assertNull(response.getBody());
    }
}
