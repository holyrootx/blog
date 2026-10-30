package me.jsjlog.blog.common.upload;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.support.ImageTestFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static me.jsjlog.blog.support.ImageTestFiles.*;
import static org.assertj.core.api.Assertions.*;

class ImageInputValidatorTest {
	private final ImageInputValidator validator = new ImageInputValidator();

	@ParameterizedTest
	@CsvSource({"jpeg,image/jpeg", "png,image/png", "webp,image/webp", "webp-lossless,image/webp", "webp-alpha,image/webp"})
	void acceptsRealImageFormats(String format, String type) throws Exception {
		assertThat(validator.validate(image(format, 32, 24), type)).isEqualTo(type);
	}

	@ParameterizedTest
	@CsvSource({"png,image/jpeg", "jpeg,image/png", "webp,image/png"})
	void rejectsDifferentDeclaredFormat(String format, String type) throws Exception {
		rejects(image(format, 32, 24), type, ErrorCode.IMAGE_TYPE_MISMATCH);
	}

	@ParameterizedTest
	@CsvSource({"16383,1", "1,16383", "5000,5000", "6000,4000"})
	void acceptsDimensionBoundariesWithoutDecodingPixels(int width, int height) {
		assertThat(validator.validate(pngHeader(width, height), "image/png")).isEqualTo("image/png");
	}

	@ParameterizedTest
	@CsvSource({"16384,1", "1,16384", "5001,5000", "5000,5001", "2147483647,2147483647"})
	void rejectsOversizedPngHeadersWithoutAllocatingPixels(int width, int height) {
		rejects(pngHeader(width, height), "image/png", ErrorCode.IMAGE_DIMENSIONS_TOO_LARGE);
	}

	@ParameterizedTest
	@CsvSource({"0,1", "1,0", "-1,24"})
	void rejectsNonPositiveDimensions(int width, int height) {
		rejects(pngHeader(width, height), "image/png", ErrorCode.IMAGE_INVALID);
	}

	@ParameterizedTest
	@CsvSource({"16384,1", "1,16384", "6000,5000"})
	void rejectsOversizedJpegBeforePixelDecode(int width, int height) throws Exception {
		rejects(jpegWithDeclaredDimensions(width, height), "image/jpeg", ErrorCode.IMAGE_DIMENSIONS_TOO_LARGE);
	}

	@Test
	void rejectsGifDisguisedAsPng() throws Exception {
		rejects(image("gif", 2, 2), "image/png", ErrorCode.IMAGE_TYPE_NOT_ALLOWED);
	}

	@Test
	void rejectsUnknownAndTruncatedHeaders() throws Exception {
		rejects("<svg></svg>".getBytes(), "image/png", ErrorCode.IMAGE_INVALID);
		byte[] png = pngHeader(2, 2);
		for (int length = 0; length < 29; length++) {
			rejects(Arrays.copyOf(png, length), "image/png", ErrorCode.IMAGE_INVALID);
		}
		byte[] webp = image("webp", 32, 24);
		for (int length = 0; length < webp.length; length++) {
			rejects(Arrays.copyOf(webp, length), "image/webp", ErrorCode.IMAGE_INVALID);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"VP8 ", "VP8L", "VP8X"})
	void rejectsOversizedWebpHeaders(String type) throws Exception {
		byte[] bytes = switch (type) {
			case "VP8 " -> webp(vp8(6000, 5000));
			case "VP8L" -> webp(vp8l(6000, 5000));
			default -> webp(vp8x(1 << 23, 1));
		};
		rejects(bytes, "image/webp", ErrorCode.IMAGE_DIMENSIONS_TOO_LARGE);
	}

	@Test
	void checksBitstreamEvenWhenExtendedCanvasClaimsSmallDimensions() throws Exception {
		rejects(webp(vp8x(32, 24), vp8(6000, 5000)), "image/webp", ErrorCode.IMAGE_DIMENSIONS_TOO_LARGE);
		rejects(webp(vp8x(32, 24), vp8l(6000, 5000)), "image/webp", ErrorCode.IMAGE_DIMENSIONS_TOO_LARGE);
		rejects(webp(vp8x(32, 24), vp8(64, 48)), "image/webp", ErrorCode.IMAGE_INVALID);
		rejects(webp(vp8l(16384, 1)), "image/webp", ErrorCode.IMAGE_DIMENSIONS_TOO_LARGE);
	}

	@Test
	void checksExtendedWebpAndOddUnknownChunk() throws Exception {
		byte[] bytes = webp(vp8x(32, 24), chunk("ICCP", new byte[]{1, 2, 3}), vp8l(32, 24));
		assertThat(validator.validate(bytes, "image/webp")).isEqualTo("image/webp");
	}

	@Test
	void rejectsMalformedWebpChunkLengthsAndDuplicateImages() throws Exception {
		byte[] tooLong = webp(vp8(32, 24));
		ByteBuffer.wrap(tooLong).order(ByteOrder.LITTLE_ENDIAN).putInt(16, -1);
		rejects(tooLong, "image/webp", ErrorCode.IMAGE_INVALID);
		rejects(webp(chunk("VP8 ", new byte[]{1})), "image/webp", ErrorCode.IMAGE_INVALID);
		rejects(webp(chunk("VP8L", new byte[4])), "image/webp", ErrorCode.IMAGE_INVALID);
		rejects(webp(chunk("VP8X", new byte[9])), "image/webp", ErrorCode.IMAGE_INVALID);
		rejects(webp(vp8(32, 24), vp8l(32, 24)), "image/webp", ErrorCode.IMAGE_INVALID);
		rejects(webp(vp8x(32, 24)), "image/webp", ErrorCode.IMAGE_INVALID);
	}

	@Test
	void rejectsAnimationBeforeNativeDecoder() throws Exception {
		byte[] header = vp8x(32, 24);
		header[8] = 0x02;
		rejects(webp(header), "image/webp", ErrorCode.IMAGE_TYPE_NOT_ALLOWED);
		rejects(webp(chunk("ANMF", new byte[16])), "image/webp", ErrorCode.IMAGE_TYPE_NOT_ALLOWED);
	}

	@Test
	void acceptsJpegOrientationWithoutChangingOriginal() throws Exception {
		byte[] original = ImageTestFiles.rotatedJpeg();
		byte[] before = original.clone();
		assertThat(validator.validate(original, "image/jpeg")).isEqualTo("image/jpeg");
		assertThat(original).isEqualTo(before);
	}

	private void rejects(byte[] bytes, String type, ErrorCode expected) {
		assertThatThrownBy(() -> validator.validate(bytes, type)).isInstanceOfSatisfying(BlogException.class,
				error -> assertThat(error.getErrorCode()).isEqualTo(expected));
	}

	private byte[] vp8(int width, int height) {
		return chunk("VP8 ", ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
				.put(new byte[]{0, 0, 0, (byte) 0x9d, 1, 0x2a})
				.putShort((short) width).putShort((short) height).array());
	}

	private byte[] vp8l(int width, int height) {
		return chunk("VP8L", ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
				.put((byte) 0x2f).putInt((width - 1) | ((height - 1) << 14)).array());
	}

	private byte[] vp8x(int width, int height) {
		byte[] bytes = new byte[10];
		for (int index = 0; index < 3; index++) {
			bytes[4 + index] = (byte) ((width - 1) >>> (index * 8));
			bytes[7 + index] = (byte) ((height - 1) >>> (index * 8));
		}
		return chunk("VP8X", bytes);
	}
}
