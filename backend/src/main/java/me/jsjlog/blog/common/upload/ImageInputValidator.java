package me.jsjlog.blog.common.upload;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

/** 픽셀 배열을 만들기 전에 파일의 형식과 크기를 검사한다. 원본 바이트는 변경하지 않는다. */
@Component
public class ImageInputValidator {
    // 6,000 x 4,000 사진은 허용하되 압축 용량이 작은 초대형 이미지의 디코딩을 제한한다.
    // 썸네일을 만드는 libwebp의 최대 가로·세로 크기.
    private static final int MAX_DIMENSION = 16_383;
    private static final long MAX_PIXELS = 25_000_000L;

    public String validate(byte[] original, String claimedContentType) {
        String actualType;
        if (original.length >= 12 && fourCC(original, 0).equals("RIFF")
                && fourCC(original, 8).equals("WEBP")) {
            actualType = "image/webp";
            requireMatchingType(actualType, claimedContentType);
            validateWebp(original);
        } else {
            actualType = validateImageIo(original, claimedContentType);
        }
        return actualType;
    }

    private String validateImageIo(byte[] original, String claimedContentType) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(original))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid();
            ImageReader reader = readers.next();
            try {
                String actualType = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "jpeg", "jpg" -> "image/jpeg";
                    case "png" -> "image/png";
                    default -> throw new BlogException(ErrorCode.IMAGE_TYPE_NOT_ALLOWED);
                };
                requireMatchingType(actualType, claimedContentType);
                reader.setInput(input, true, true);
                dimensions(reader.getWidth(0), reader.getHeight(0));
                return actualType;
            } finally {
                reader.dispose();
            }
        } catch (BlogException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalid();
        }
    }

    /**
     * 기존 WebP 디코더는 헤더 조회 API가 없어 RIFF 청크의 길이와 크기 필드만 읽는다.
     * VP8X 캔버스와 실제 VP8/VP8L 크기를 모두 검사해 서로 다른 크기 선언도 거절한다.
     * 규격: https://developers.google.com/speed/webp/docs/riff_container
     */
    private void validateWebp(byte[] bytes) {
        if (littleEndian(bytes, 4, 4) + 8 != bytes.length) throw invalid();
        Dimensions canvas = null;
        Dimensions image = null;
        int offset = 12;
        while (offset < bytes.length) {
            if (bytes.length - offset < 8) throw invalid();
            String chunk = fourCC(bytes, offset);
            long size = littleEndian(bytes, offset + 4, 4);
            int start = offset + 8;
            long end = start + size + (size & 1);
            if (end > bytes.length) throw invalid();

            switch (chunk) {
                case "VP8X" -> {
                    if (offset != 12 || size != 10) throw invalid();
                    if ((bytes[start] & 0x02) != 0) throw animationNotSupported();
                    canvas = dimensions(littleEndian(bytes, start + 4, 3) + 1,
                            littleEndian(bytes, start + 7, 3) + 1);
                }
                case "VP8 " -> {
                    if (image != null || size < 10 || (bytes[start] & 1) != 0
                            || (bytes[start + 3] & 0xff) != 0x9d
                            || bytes[start + 4] != 1 || bytes[start + 5] != 0x2a) throw invalid();
                    image = dimensions(littleEndian(bytes, start + 6, 2) & 0x3fff,
                            littleEndian(bytes, start + 8, 2) & 0x3fff);
                }
                case "VP8L" -> {
                    if (image != null || size < 5 || bytes[start] != 0x2f) throw invalid();
                    long bits = littleEndian(bytes, start + 1, 4);
                    if ((bits >>> 29) != 0) throw invalid();
                    image = dimensions((bits & 0x3fff) + 1, ((bits >>> 14) & 0x3fff) + 1);
                }
                // 현재 사용하는 dwebp도 애니메이션을 지원하지 않는다.
                case "ANIM", "ANMF" -> throw animationNotSupported();
                default -> { /* 색상·EXIF 등 나머지 청크는 디코더에 그대로 전달한다. */ }
            }
            offset = (int) end;
        }
        if (image == null || (canvas != null && !canvas.equals(image))) throw invalid();
    }

    private Dimensions dimensions(long width, long height) {
        if (width <= 0 || height <= 0) throw invalid();
        if (width > MAX_DIMENSION || height > MAX_DIMENSION || width * height > MAX_PIXELS) {
            throw new BlogException(ErrorCode.IMAGE_DIMENSIONS_TOO_LARGE);
        }
        return new Dimensions(width, height);
    }

    private void requireMatchingType(String actual, String claimed) {
        if (!actual.equals(claimed)) throw new BlogException(ErrorCode.IMAGE_TYPE_MISMATCH);
    }

    private static long littleEndian(byte[] bytes, int offset, int length) {
        long value = 0;
        for (int i = 0; i < length; i++) value |= (long) (bytes[offset + i] & 0xff) << (8 * i);
        return value;
    }

    private static String fourCC(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
    }

    private static BlogException invalid() {
        return new BlogException(ErrorCode.IMAGE_INVALID);
    }

    private static BlogException animationNotSupported() {
        return new BlogException(ErrorCode.IMAGE_TYPE_NOT_ALLOWED, "움직이는 WebP는 지원하지 않습니다. 정지 이미지로 올려 주세요.");
    }

    private record Dimensions(long width, long height) { }
}
