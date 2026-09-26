package me.jsjlog.blog.common.upload;

/**
 * 화면에 내보낼 썸네일 한 장.
 *
 * <p>원본과 따로 둔다. 카메라 사진은 4032px 인데 글 본문에서는 882px 로 보인다 —
 * 브라우저는 큰 그림을 줄여서 <b>보여줄</b> 수는 있어도 줄여서 <b>받을</b> 수는 없어서,
 * 줄이는 일이 다운로드가 끝난 뒤에 일어난다. 받는 양을 줄이려면 미리 줄여 두는 수밖에 없다.</p>
 */
public record ImageThumbnail(byte[] content, String contentType, int width, int height) {

    public long size() {
        return content.length;
    }
}
