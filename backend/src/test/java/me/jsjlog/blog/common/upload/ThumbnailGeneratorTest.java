package me.jsjlog.blog.common.upload;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 썸네일이 실제로 만들어지는지.
 *
 * <p>변환기가 네이티브 바이너리를 부르므로, 이 테스트가 도는지 여부가 곧 "이 환경에서
 * WebP 인코딩이 되는가" 에 대한 답이다.</p>
 */
class ThumbnailGeneratorTest {

    private final ThumbnailGenerator generator = new ThumbnailGenerator();

    @Test
    @DisplayName("큰 사진은 가로 1800px 으로 줄고 WebP 가 된다")
    void shrinksWideImage() throws IOException {
        byte[] original = png(4032, 3024);

        ImageThumbnail thumbnail = generator.generate(original, "image/png");

        assertThat(thumbnail.width()).isEqualTo(1800);
        assertThat(thumbnail.height()).isEqualTo(1350);
        assertThat(thumbnail.contentType()).isEqualTo("image/webp");
        assertThat(thumbnail.size()).isLessThan(original.length);
    }

    @Test
    @DisplayName("상한보다 작은 그림은 키우지 않는다")
    void doesNotEnlarge() throws IOException {
        ImageThumbnail thumbnail = generator.generate(png(800, 600), "image/png");

        assertThat(thumbnail.width()).isEqualTo(800);
        assertThat(thumbnail.height()).isEqualTo(600);
    }

    @Test
    @DisplayName("깨진 파일은 썸네일 변환 실패로 업로드를 중단한다")
    void rejectsBrokenInput() {
        assertThatThrownBy(() -> generator.generate("이건 이미지가 아니다".getBytes(), "image/png"))
                .isInstanceOfSatisfying(BlogException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.IMAGE_THUMBNAIL_GENERATION_FAILED));
    }

    /** 단색이 아니라 그라데이션으로 만든다. 단색은 너무 잘 눌려서 크기 비교가 뜻을 잃는다 */
    private byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();

        for (int x = 0; x < width; x += 4) {
            graphics.setColor(new Color((x * 7) % 256, (x * 13) % 256, (x * 29) % 256));
            graphics.fillRect(x, 0, 4, height);
        }

        graphics.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);

        return out.toByteArray();
    }
}
