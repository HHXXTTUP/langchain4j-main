package dev.learning.stockanalyzer.service;

import dev.learning.stockanalyzer.entity.SectorWatchlistEntity;
import dev.learning.stockanalyzer.repository.SectorWatchlistRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SectorWatchlistServiceTest {

    private final SectorWatchlistRepository repository = mock(SectorWatchlistRepository.class);
    private final SectorWatchlistService service = new SectorWatchlistService(repository);

    @Test
    void shouldPersistSecondLevelSectorWithoutChangingItsIdentity() {
        when(repository.findBySectorId("ths-l2-storage-chip")).thenReturn(Optional.empty());
        when(repository.save(any(SectorWatchlistEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SectorWatchlistService.SectorWatchlistItem item = service.add(
                "ths-l2-storage-chip", "存储芯片", "同花顺二级行业", null);

        assertThat(item.sectorId()).isEqualTo("ths-l2-storage-chip");
        assertThat(item.sectorName()).isEqualTo("存储芯片");
        assertThat(item.sectorType()).isEqualTo("同花顺二级行业");
        assertThat(item.addedTime()).isNotNull();
    }

    @Test
    void shouldUpdateExistingSectorAndItsLastRefreshTime() {
        SectorWatchlistEntity entity = new SectorWatchlistEntity(
                "881121", "半导体", "行业", null, LocalDateTime.now().minusDays(1));
        when(repository.findBySectorId("881121")).thenReturn(Optional.of(entity));
        when(repository.save(any(SectorWatchlistEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.add("881121", "半导体", "同花顺二级行业", "sh600584");
        service.markRefreshed("881121");

        assertThat(entity.getSectorType()).isEqualTo("同花顺二级行业");
        assertThat(entity.getSelectedCode()).isEqualTo("sh600584");
        assertThat(entity.getLastRefreshedAt()).isNotNull();
    }

    @Test
    void shouldRemoveSectorByItsStableId() {
        service.remove("881121");

        verify(repository).deleteBySectorId("881121");
    }
}
