package me.jsjlog.blog.admin.service;

import java.io.InputStream;
import java.util.Optional;
import me.jsjlog.blog.admin.domain.UploadImage;
import me.jsjlog.blog.admin.repository.UploadImageRepository;
import me.jsjlog.blog.admin.repository.ImageUsageRepository;
import me.jsjlog.blog.common.config.UploadProperties;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.common.upload.ImageStorage;
import me.jsjlog.blog.common.upload.ImageThumbnail;
import me.jsjlog.blog.common.upload.StoredImage;
import me.jsjlog.blog.common.upload.ThumbnailGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminImageServiceTest {

    @Mock
    private ImageStorage imageStorage;

    @Mock
    private UploadImageRepository uploadImageRepository;

    @Mock
    private UploadProperties uploadProperties;

    @Mock
    private ThumbnailGenerator thumbnailGenerator;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    @Mock
    private ImageUsageRepository imageUsageRepository;

    private AdminImageService adminImageService;

    @BeforeEach
    void setUp() {
        adminImageService = new AdminImageService(
                imageStorage,
                uploadImageRepository,
                uploadProperties,
                thumbnailGenerator,
                transactionManager,
                imageUsageRepository
        );
    }

    @Test
    @DisplayName("썸네일 R2 저장이 실패하면 썸네일과 원본 키를 역순으로 삭제한다")
    void deletesThumbnailAndOriginalWhenThumbnailStoreFails() {
        givenThumbnail();
        when(imageStorage.store(anyString(), anyString(), anyLong(), any(InputStream.class)))
                .thenAnswer(invocation -> stored(invocation.getArgument(0)))
                .thenThrow(new BlogException(ErrorCode.IMAGE_UPLOAD_FAILED));

        assertThatThrownBy(() -> adminImageService.upload(file()))
                .isInstanceOfSatisfying(BlogException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IMAGE_UPLOAD_FAILED));

        ArgumentCaptor<String> deletedKeys = ArgumentCaptor.forClass(String.class);
        verify(imageStorage, times(2)).delete(deletedKeys.capture());
        assertThat(deletedKeys.getAllValues().get(0)).endsWith("-thumbnail-v1.0.webp");
        assertThat(deletedKeys.getAllValues().get(1)).doesNotEndWith("-thumbnail-v1.0.webp");
        verify(uploadImageRepository, never()).saveAndFlush(any(UploadImage.class));
    }

    @Test
    @DisplayName("원본 R2 저장이 실패하면 저장을 시도한 원본 키만 삭제한다")
    void deletesOnlyOriginalWhenOriginalStoreFails() {
        givenThumbnail();
        when(imageStorage.store(anyString(), anyString(), anyLong(), any(InputStream.class)))
                .thenThrow(new BlogException(ErrorCode.IMAGE_UPLOAD_FAILED));

        assertThatThrownBy(() -> adminImageService.upload(file()))
                .isInstanceOf(BlogException.class);

        ArgumentCaptor<String> deletedKey = ArgumentCaptor.forClass(String.class);
        verify(imageStorage).delete(deletedKey.capture());
        assertThat(deletedKey.getValue()).endsWith(".png");
        assertThat(deletedKey.getValue()).doesNotEndWith("-thumbnail-v1.0.webp");
    }

    @Test
    @DisplayName("DB 저장이 실패하면 R2 썸네일과 원본을 모두 삭제한다")
    void deletesStoredObjectsWhenDatabaseSaveFails() {
        givenThumbnail();
        givenTransaction();
        when(imageStorage.store(anyString(), anyString(), anyLong(), any(InputStream.class)))
                .thenAnswer(invocation -> stored(invocation.getArgument(0)));
        when(uploadImageRepository.saveAndFlush(any(UploadImage.class)))
                .thenThrow(new IllegalStateException("DB failure"));

        assertThatThrownBy(() -> adminImageService.upload(file()))
                .isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<String> deletedKeys = ArgumentCaptor.forClass(String.class);
        verify(imageStorage, times(2)).delete(deletedKeys.capture());
        assertThat(deletedKeys.getAllValues().get(0)).endsWith("-thumbnail-v1.0.webp");
        assertThat(deletedKeys.getAllValues().get(1)).doesNotEndWith("-thumbnail-v1.0.webp");
        verify(transactionManager).rollback(transactionStatus);
    }

    @Test
    @DisplayName("DB 커밋이 실패해도 R2 썸네일과 원본을 모두 삭제한다")
    void deletesStoredObjectsWhenDatabaseCommitFails() {
        givenThumbnail();
        givenTransaction();
        when(imageStorage.store(anyString(), anyString(), anyLong(), any(InputStream.class)))
                .thenAnswer(invocation -> stored(invocation.getArgument(0)));
        when(uploadImageRepository.saveAndFlush(any(UploadImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("commit failure"))
                .when(transactionManager).commit(transactionStatus);

        assertThatThrownBy(() -> adminImageService.upload(file()))
                .isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<String> deletedKeys = ArgumentCaptor.forClass(String.class);
        verify(imageStorage, times(2)).delete(deletedKeys.capture());
        assertThat(deletedKeys.getAllValues().get(0)).endsWith("-thumbnail-v1.0.webp");
        assertThat(deletedKeys.getAllValues().get(1)).doesNotEndWith("-thumbnail-v1.0.webp");
    }

    @Test
    @DisplayName("썸네일 변환이 실패하면 R2와 DB에 접근하지 않는다")
    void storesNothingWhenThumbnailGenerationFails() {
        when(thumbnailGenerator.generate(any(byte[].class), anyString()))
                .thenThrow(new BlogException(ErrorCode.IMAGE_THUMBNAIL_GENERATION_FAILED));

        assertThatThrownBy(() -> adminImageService.upload(file()))
                .isInstanceOfSatisfying(BlogException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.IMAGE_THUMBNAIL_GENERATION_FAILED));

        verify(imageStorage, never()).store(anyString(), anyString(), anyLong(), any(InputStream.class));
        verify(imageStorage, never()).delete(anyString());
        verify(uploadImageRepository, never()).saveAndFlush(any(UploadImage.class));
    }

    @Test
    @DisplayName("GIF는 썸네일 생성 전에 업로드를 거부한다")
    void rejectsGifBeforeThumbnailGeneration() {
        when(uploadProperties.maxSizeBytes()).thenReturn(5L * 1024L * 1024L);
        when(uploadProperties.allows("image/gif")).thenReturn(false);
        MockMultipartFile gif = new MockMultipartFile(
                "file",
                "animated.gif",
                "image/gif",
                new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> adminImageService.upload(gif))
                .isInstanceOfSatisfying(BlogException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IMAGE_TYPE_NOT_ALLOWED));

        verify(thumbnailGenerator, never()).generate(any(byte[].class), anyString());
        verify(imageStorage, never()).store(anyString(), anyString(), anyLong(), any(InputStream.class));
        verify(uploadImageRepository, never()).saveAndFlush(any(UploadImage.class));
    }

    @Test
    @DisplayName("신규 업로드는 원본 키를 보관하고 썸네일 URL을 반환한다")
    void storesOriginalKeyAndReturnsThumbnailUrl() {
        givenThumbnail();
        givenTransaction();
        when(imageStorage.store(anyString(), anyString(), anyLong(), any(InputStream.class)))
                .thenAnswer(invocation -> stored(invocation.getArgument(0)));
        when(uploadImageRepository.saveAndFlush(any(UploadImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = adminImageService.upload(file());

        ArgumentCaptor<UploadImage> savedImage = ArgumentCaptor.forClass(UploadImage.class);
        verify(uploadImageRepository).saveAndFlush(savedImage.capture());
        assertThat(savedImage.getValue().getStorageKey()).endsWith(".png");
        assertThat(savedImage.getValue().getStorageKey()).doesNotEndWith("-thumbnail-v1.0.webp");
        assertThat(savedImage.getValue().getUrl()).endsWith("-thumbnail-v1.0.webp");
        assertThat(response.url()).isEqualTo(savedImage.getValue().getUrl());
    }

    @Test
    @DisplayName("일반 삭제는 원본, DB URL의 실제 썸네일, DB 기록 순서로 처리한다")
    void deletesOriginalThenThumbnailThenDatabaseRecord() {
        UploadImage image = uploadImage("https://images.example.com/2026/09/image-thumbnail-v0.9.webp");
        when(uploadImageRepository.findById(1L)).thenReturn(Optional.of(image));

        adminImageService.delete(1L);

        InOrder order = inOrder(imageStorage, uploadImageRepository);
        order.verify(imageStorage).delete("2026/09/image.png");
        order.verify(imageStorage).delete("2026/09/image-thumbnail-v0.9.webp");
        order.verify(uploadImageRepository).delete(image);
    }

    @Test
    @DisplayName("원본 삭제가 실패하면 썸네일과 DB 기록을 삭제하지 않는다")
    void keepsThumbnailAndDatabaseRecordWhenOriginalDeleteFails() {
        UploadImage image = uploadImage("https://images.example.com/2026/09/image-thumbnail-v1.0.webp");
        when(uploadImageRepository.findById(1L)).thenReturn(Optional.of(image));
        doThrow(new BlogException(ErrorCode.IMAGE_DELETE_FAILED))
                .when(imageStorage).delete("2026/09/image.png");

        assertThatThrownBy(() -> adminImageService.delete(1L))
                .isInstanceOfSatisfying(BlogException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IMAGE_DELETE_FAILED));

        verify(imageStorage, times(1)).delete(anyString());
        verify(uploadImageRepository, never()).delete(any(UploadImage.class));
    }

    @Test
    @DisplayName("기존 데이터의 URL이 원본을 가리키면 R2 객체를 한 번만 삭제한다")
    void deletesLegacyOriginalUrlOnlyOnce() {
        UploadImage image = uploadImage("https://images.example.com/2026/09/image.png");
        when(uploadImageRepository.findById(1L)).thenReturn(Optional.of(image));

        adminImageService.delete(1L);

        verify(imageStorage, times(1)).delete("2026/09/image.png");
        verify(uploadImageRepository).delete(image);
    }

    @Test
    @DisplayName("DB 이미지 URL에서 객체 키를 찾을 수 없으면 R2와 DB를 삭제하지 않는다")
    void deletesNothingWhenStoredUrlHasNoObjectKey() {
        UploadImage image = uploadImage("https://images.example.com");
        when(uploadImageRepository.findById(1L)).thenReturn(Optional.of(image));

        assertThatThrownBy(() -> adminImageService.delete(1L))
                .isInstanceOfSatisfying(BlogException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IMAGE_DELETE_FAILED));

        verify(imageStorage, never()).delete(anyString());
        verify(uploadImageRepository, never()).delete(any(UploadImage.class));
    }

    private void givenThumbnail() {
        when(thumbnailGenerator.generate(any(byte[].class), anyString()))
                .thenReturn(new ImageThumbnail(new byte[]{4, 5, 6}, "image/webp", 100, 100));
    }

    private void givenTransaction() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
    }

    private MockMultipartFile file() {
        when(uploadProperties.maxSizeBytes()).thenReturn(5L * 1024L * 1024L);
        when(uploadProperties.allows("image/png")).thenReturn(true);

        return new MockMultipartFile(
                "file",
                "sample.png",
                "image/png",
                new byte[]{1, 2, 3}
        );
    }

    private StoredImage stored(String storageKey) {
        return new StoredImage(storageKey, "https://images.example.com/" + storageKey);
    }

    private UploadImage uploadImage(String url) {
        return new UploadImage(
                "2026/09/image.png",
                url,
                "sample.png",
                "image/png",
                1024L
        );
    }
}
