package me.jsjlog.blog.post.controller;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.post.service.PostPageService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PostPageController {

    private final PostPageService postPageService;

    @GetMapping(value = {"/posts/{id}", "/posts/{id}/"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> postPage(@PathVariable String id) {
        PostPageService.Page page = postPageService.render(id);
        return ResponseEntity.status(page.found() ? HttpStatus.OK : HttpStatus.NOT_FOUND)
                // 비공개 전환·삭제 이후 예전 제목과 이미지가 HTTP 캐시에 남지 않게 한다.
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                .body(page.html());
    }
}
