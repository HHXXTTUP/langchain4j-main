package dev.learning.fashionagent.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.learning.fashionagent.config.RunningHubProperties;
import dev.learning.fashionagent.integration.runninghub.RunningHubClient;
import dev.learning.fashionagent.integration.runninghub.RunningHubException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImageTransferServiceTest {

    @Test
    void shouldRetryAndPersistRemotePersonImageLocally() throws Exception {
        RunningHubClient client = mock(RunningHubClient.class);
        RunningHubProperties properties = new RunningHubProperties();
        properties.setGeneratedDirectory(
                Path.of("target", "test-generated", UUID.randomUUID().toString()).toAbsolutePath());
        properties.setDownloadMaxAttempts(2);
        properties.setDownloadRetryDelay(Duration.ZERO);
        String imageUrl = "https://example.com/person.png";
        byte[] imageBytes = new byte[] {1, 2, 3, 4};
        when(client.download(imageUrl))
                .thenThrow(new RunningHubException("Connection reset"))
                .thenReturn(imageBytes);
        ImageTransferService service = new ImageTransferService(client, properties);

        UUID jobId = UUID.randomUUID();
        Path localImage = service.downloadRemote(URI.create(imageUrl), jobId, "original");

        assertTrue(Files.isRegularFile(localImage));
        assertTrue(localImage.startsWith(properties.getGeneratedDirectory().toAbsolutePath().normalize()));
        assertArrayEquals(imageBytes, Files.readAllBytes(localImage));
        verify(client, org.mockito.Mockito.times(2)).download(imageUrl);
    }

    @Test
    void shouldDownloadTrustedRunningHubCosHostDirectlyOverHttp() throws Exception {
        RunningHubClient client = mock(RunningHubClient.class);
        RunningHubProperties properties = new RunningHubProperties();
        properties.setGeneratedDirectory(
                Path.of("target", "test-generated", UUID.randomUUID().toString()).toAbsolutePath());
        properties.setDownloadMaxAttempts(2);
        properties.setDownloadRetryDelay(Duration.ZERO);
        String httpsUrl = "https://rh-images-1252422369.cos.ap-beijing.myqcloud.com/output/person.png";
        String httpUrl = "http://rh-images-1252422369.cos.ap-beijing.myqcloud.com/output/person.png";
        byte[] imageBytes = new byte[] {5, 6, 7, 8};
        when(client.download(httpUrl)).thenReturn(imageBytes);
        ImageTransferService service = new ImageTransferService(client, properties);

        Path localImage = service.downloadRemote(URI.create(httpsUrl), UUID.randomUUID(), "original");

        assertArrayEquals(imageBytes, Files.readAllBytes(localImage));
        verify(client, never()).download(httpsUrl);
        verify(client).download(httpUrl);
    }
}
