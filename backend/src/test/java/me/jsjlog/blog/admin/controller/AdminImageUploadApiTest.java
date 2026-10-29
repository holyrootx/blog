package me.jsjlog.blog.admin.controller;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import com.sksamuel.scrimage.ImmutableImage;
import me.jsjlog.blog.admin.repository.UploadImageRepository;
import me.jsjlog.blog.common.upload.ImageStorage;
import me.jsjlog.blog.common.upload.StoredImage;
import me.jsjlog.blog.support.ImageTestFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminImageUploadApiTest {
	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () ->
				"jdbc:h2:mem:image_upload_validation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
	}

	@Autowired MockMvc mvc;
	@Autowired UploadImageRepository images;
	@MockitoBean ImageStorage storage;

	@BeforeEach
	void cleanDatabase() {
		images.deleteAll();
	}

	@Test
	void rejectsPngClaimedAsJpeg() throws Exception {
		mvc.perform(multipart("/api/v1/admin/blog/images")
						.file(new MockMultipartFile("file", "fake.jpg", "image/jpeg", ImageTestFiles.image("png", 32, 24)))
						.with(user("admin").roles("ADMIN")).with(csrf()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("IMAGE_TYPE_MISMATCH"));
		assertNothingStored();
	}

	@ParameterizedTest
	@CsvSource({"jpeg,image/jpeg,jpg", "png,image/png,png", "webp,image/webp,webp", "webp-lossless,image/webp,webp", "webp-alpha,image/webp,webp"})
	void storesOriginalAndRealWebpThumbnail(String format, String type, String extension) throws Exception {
		assertSuccessfulUpload(ImageTestFiles.image(format, 32, 24), type, extension, 32, 24);
	}

	@ParameterizedTest
	@CsvSource({"1,1,1,1", "16383,1,1800,1", "1,16383,1,16383", "3600,4,1800,2", "3600,6,1800,3",
			"3600,8,1800,4", "3600,10,1800,5", "1801,6,1800,5"})
	void acceptsSmallAndBoundaryImages(int width, int height, int thumbnailWidth, int thumbnailHeight) throws Exception {
		assertSuccessfulUpload(ImageTestFiles.image("png", width, height), "image/png", "png", thumbnailWidth, thumbnailHeight);
	}

	@Test
	void preservesOriginalExifAndReorientsThumbnail() throws Exception {
		assertSuccessfulUpload(ImageTestFiles.rotatedJpeg(), "image/jpeg", "jpg", 40, 80);
	}

	@Test
	void rejectsHeaderWithOversizedPixelsBeforeDecoding() throws Exception {
		assertRejected(ImageTestFiles.pngHeader(6000, 5000), "image/png", "IMAGE_DIMENSIONS_TOO_LARGE");
	}

	@Test
	void rejectsHeaderOnlyImageWithMissingPixelData() throws Exception {
		assertRejected(ImageTestFiles.pngHeader(32, 24), "image/png", "IMAGE_INVALID");
	}

	@Test
	void rejectsJpegAndWebpWithHeadersButMissingPixelData() throws Exception {
		byte[] jpeg = ImageTestFiles.image("jpeg", 32, 24);
		int scanStart = -1;
		for (int index = 2; index < jpeg.length - 1; index++) {
			if ((jpeg[index] & 0xff) == 0xff && (jpeg[index + 1] & 0xff) == 0xda) {
				scanStart = index;
				break;
			}
		}
		assertThat(scanStart).isPositive();
		assertRejected(Arrays.copyOf(jpeg, scanStart), "image/jpeg", "IMAGE_INVALID");
		byte[] header = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
				.put(new byte[]{0, 0, 0, (byte) 0x9d, 1, 0x2a})
				.putShort((short) 32).putShort((short) 24).array();
		assertRejected(ImageTestFiles.webp(ImageTestFiles.chunk("VP8 ", header)), "image/webp", "IMAGE_INVALID");
	}

	@Test
	void rejectsNonImageAndEmptyFile() throws Exception {
		assertRejected("not an image".getBytes(), "image/png", "IMAGE_INVALID");
		assertRejected(new byte[0], "image/png", "IMAGE_EMPTY");
	}

	@Test
	void rejectsByteLimitIndependentlyOfImageDimensions() throws Exception {
		assertRejected(new byte[5 * 1024 * 1024 + 1], "image/png", "IMAGE_TOO_LARGE");
	}

	@Test
	void requiresAdministratorAndCsrf() throws Exception {
		var file = new MockMultipartFile("file", "image.png", "image/png", ImageTestFiles.image("png", 2, 2));
		mvc.perform(multipart("/api/v1/admin/blog/images").file(file).with(csrf()))
				.andExpect(status().isUnauthorized());
		mvc.perform(multipart("/api/v1/admin/blog/images").file(file)
						.with(user("member").roles("USER")).with(csrf()))
				.andExpect(status().isForbidden());
		mvc.perform(multipart("/api/v1/admin/blog/images").file(file)
						.with(user("admin").roles("ADMIN")))
				.andExpect(status().isForbidden());
		assertNothingStored();
	}

	private void assertSuccessfulUpload(byte[] original, String type, String extension, int width, int height) throws Exception {
		Map<String, byte[]> storedBytes = new HashMap<>();
		Map<String, String> storedTypes = new HashMap<>();
		when(storage.store(anyString(), anyString(), anyLong(), any(InputStream.class))).thenAnswer(call -> {
			String key = call.getArgument(0);
			byte[] bytes = ((InputStream) call.getArgument(3)).readAllBytes();
			assertThat((long) call.getArgument(2)).isEqualTo(bytes.length);
			storedBytes.put(key, bytes);
			storedTypes.put(key, call.getArgument(1));
			return new StoredImage(key, "https://images.test/" + key);
		});
		mvc.perform(multipart("/api/v1/admin/blog/images")
						.file(new MockMultipartFile("file", "파일이름.dat", type, original))
						.with(user("admin").roles("ADMIN")).with(csrf()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.originalName").value("파일이름.dat"));
		var records = images.findAll();
		assertThat(records).hasSize(1);
		var saved = records.getFirst();
		assertThat(saved.getStorageKey()).endsWith("." + extension);
		assertThat(saved.getContentType()).isEqualTo(type);
		assertThat(saved.getByteSize()).isEqualTo(original.length);
		assertThat(storedBytes).hasSize(2);
		assertThat(storedBytes.get(saved.getStorageKey())).isEqualTo(original);
		assertThat(storedTypes.get(saved.getStorageKey())).isEqualTo(type);
		String thumbnailKey = saved.getUrl().replace("https://images.test/", "");
		assertThat(thumbnailKey).endsWith("-thumbnail-v1.0.webp");
		assertThat(storedTypes.get(thumbnailKey)).isEqualTo("image/webp");
		var thumbnail = ImmutableImage.loader().fromBytes(storedBytes.get(thumbnailKey));
		assertThat(thumbnail.width).isEqualTo(width);
		assertThat(thumbnail.height).isEqualTo(height);
		if (ImmutableImage.loader().fromBytes(original).awt().getColorModel().hasAlpha()) {
			assertThat(thumbnail.awt().getColorModel().hasAlpha()).isTrue();
			assertThat((thumbnail.awt().getRGB(0, 0) >>> 24) & 0xff).isBetween(1, 254);
		}
	}

	private void assertRejected(byte[] bytes, String type, String code) throws Exception {
		mvc.perform(multipart("/api/v1/admin/blog/images")
						.file(new MockMultipartFile("file", "image", type, bytes))
						.with(user("admin").roles("ADMIN")).with(csrf()))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code))
				.andExpect(jsonPath("$.message").isNotEmpty());
		assertNothingStored();
	}

	private void assertNothingStored() {
		verifyNoInteractions(storage);
		assertThat(images.count()).isZero();
	}
}
