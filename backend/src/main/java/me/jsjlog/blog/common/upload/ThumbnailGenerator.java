package me.jsjlog.blog.common.upload;

import java.io.IOException;

import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.webp.WebpWriter;
import org.springframework.stereotype.Component;

import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;

/**
 * 원본에서 화면용 한 장을 뽑는다.
 *
 * <p>두 가지를 동시에 한다 — <b>줄이고</b>, <b>WebP 로 바꾼다.</b> 둘은 서로 다른 문제를
 * 고친다. 줄이는 것은 안 쓰는 픽셀을 버리는 일이고(4032px 을 받아서 882px 로 보여주고
 * 있었다), WebP 는 같은 픽셀을 더 적은 바이트로 담는 일이다. 실측으로 카메라 사진
 * 1,932KB 가 132KB 가 됐다.</p>
 *
 * <p>WebP 가 TTF·PNG 보다 훨씬 작은 이유는 압축기가 좋아서가 아니라 <b>압축하기 전에
 * 재배열</b>하기 때문이다. 비디오 코덱에서 온 포맷이라 주변 블록으로 예측하고 틀린
 * 만큼만 담는다.</p>
 */
@Component
public class ThumbnailGenerator {

    /**
     * 가로 상한.
     *
     * <p>화면을 실제로 재서 나온 값이다. 본문 이미지는 882px 에서 멈추고(레이아웃 폭이
     * 1440px 에서 잠긴다) 레티나 화면은 그 두 배를 요구하므로 1,764px 이 필요하다.
     * 거기에 여유를 둔 값이 1800 이다. 2000 이상은 어디서도 안 쓰인다.</p>
     *
     * <p>원본이 이보다 작으면 <b>키우지 않는다.</b> 늘려 봐야 용량만 늘고 화질은 그대로다.</p>
     */
    private static final int MAX_WIDTH = 1800;

    /**
     * WebP 품질.
     *
     * <p>85 는 확대해야 겨우 차이가 보이는 지점이다. 95 로 올리면 용량이 두 배가 되고,
     * 75 로 내리면 하늘이나 그라데이션에서 얼룩이 보이기 시작한다.</p>
     */
    private static final int QUALITY = 85;

    public ImageThumbnail generate(byte[] original, String contentType) {
        try {
            ImmutableImage source = ImmutableImage.loader().fromBytes(original);
            ImmutableImage resized = source.width > MAX_WIDTH
                    ? source.scaleToWidth(MAX_WIDTH)
                    : source;

            byte[] content = resized.bytes(WebpWriter.DEFAULT.withQ(QUALITY));

            return new ImageThumbnail(content, "image/webp", resized.width, resized.height);
        } catch (IOException | RuntimeException exception) {
            throw new BlogException(ErrorCode.IMAGE_THUMBNAIL_GENERATION_FAILED);
        }
    }
}
