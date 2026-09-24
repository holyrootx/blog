package me.jsjlog.blog.admin.dto;

public record AdminCommentVisibilityRequest(
        Boolean hidden,

        /**
         * 왜 가리는지 남기는 메모. 없어도 된다.
         *
         * <p>조치 이력에 함께 쌓인다. 나중에 글쓴이가 "왜 사라졌냐" 물었을 때
         * 가리킬 것이 있으려면 그때 적어 두는 수밖에 없다.</p>
         */
        String reason
) {
}
