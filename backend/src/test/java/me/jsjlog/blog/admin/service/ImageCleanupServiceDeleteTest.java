package me.jsjlog.blog.admin.service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import me.jsjlog.blog.admin.domain.UploadImage;
import me.jsjlog.blog.admin.repository.ImageUsageRepository;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.common.upload.ImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageCleanupServiceDeleteTest {

    private static final String ORIGINAL_KEY = "2026/09/image.png";
    private static final String LEGACY_THUMBNAIL_KEY = "2026/09/image-thumbnail.webp";
    private static final String LEGACY_THUMBNAIL_URL = "https://images.example.com/" + LEGACY_THUMBNAIL_KEY;

    @Mock
    private ImageUsageRepository imageUsageRepository;

    @Mock
    private ImageStorage imageStorage;

    @Mock
    private UploadImage image;

    private ImageCleanupService service;

    @BeforeEach
    void setUp() {
        service = new ImageCleanupService(imageUsageRepository, imageStorage);
    }

    @Test
    @DisplayName("현재 규칙이 아니라 DB URL의 과거 썸네일 키를 삭제한다")
    void deletesActualLegacyThumbnailAndReturnsDeletedCount() {
        givenImageIdentity();
        when(image.getStorageKey()).thenReturn(ORIGINAL_KEY);
        when(imageUsageRepository.findAllById(Set.of(1L))).thenReturn(List.of(image));
        when(imageUsageRepository.findStillUsed(Set.of(LEGACY_THUMBNAIL_URL))).thenReturn(List.of());

        var result = service.cleanup(List.of(1L));

        assertThat(result.deletedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();

        InOrder order = inOrder(imageStorage, imageUsageRepository);
        order.verify(imageStorage).delete(ORIGINAL_KEY);
        order.verify(imageStorage).delete(LEGACY_THUMBNAIL_KEY);
        order.verify(imageUsageRepository).delete(image);
    }

    @Test
    @DisplayName("삭제 직전에 사용 중이면 파일을 남기고 건너뜀 건수를 반환한다")
    void skipsImageThatBecameUsed() {
        givenImageIdentity();
        when(imageUsageRepository.findAllById(Set.of(1L))).thenReturn(List.of(image));
        when(imageUsageRepository.findStillUsed(Set.of(LEGACY_THUMBNAIL_URL))).thenReturn(List.of(image));

        var result = service.cleanup(List.of(1L));

        assertThat(result.deletedCount()).isZero();
        assertThat(result.skippedUsedCount()).isEqualTo(1);
        verify(imageStorage, never()).delete(any());
        verify(imageUsageRepository, never()).delete(any(UploadImage.class));
    }

    @Test
    @DisplayName("R2 삭제 실패는 성공으로 세지 않고 DB 기록을 남긴다")
    void reportsStorageDeleteFailure() {
        givenImageIdentity();
        when(image.getStorageKey()).thenReturn(ORIGINAL_KEY);
        when(imageUsageRepository.findAllById(Set.of(1L))).thenReturn(List.of(image));
        when(imageUsageRepository.findStillUsed(Set.of(LEGACY_THUMBNAIL_URL))).thenReturn(List.of());
        doThrow(new BlogException(ErrorCode.IMAGE_DELETE_FAILED))
                .when(imageStorage).delete(ORIGINAL_KEY);

        var result = service.cleanup(List.of(1L));

        assertThat(result.deletedCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(1);
        verify(imageUsageRepository, never()).delete(any(UploadImage.class));
    }

    @Test
    @DisplayName("목록을 띄운 뒤 이미 사라진 ID는 별도로 센다")
    void reportsAlreadyMissingImage() {
        when(imageUsageRepository.findAllById(Set.of(1L))).thenReturn(List.of());

        var result = service.cleanup(Arrays.asList(1L, 1L, null));

        assertThat(result.requestedCount()).isEqualTo(1);
        assertThat(result.notFoundCount()).isEqualTo(1);
        assertThat(result.deletedCount()).isZero();
        verify(imageUsageRepository, never()).findStillUsed(any());
    }

    private void givenImageIdentity() {
        when(image.getId()).thenReturn(1L);
        when(image.getUrl()).thenReturn(LEGACY_THUMBNAIL_URL);
    }
}
