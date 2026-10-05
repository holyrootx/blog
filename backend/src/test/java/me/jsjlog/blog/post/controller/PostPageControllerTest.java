package me.jsjlog.blog.post.controller;

import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "blog.frontend.index-path=../frontend/index.html",
        "blog.frontend.base-url=https://blog.example"
})
class PostPageControllerTest {

    @Autowired MockMvc mvc;
    @Autowired CategoryRepository categories;
    @Autowired PostRepository posts;
    private Post post;

    @BeforeEach
    void setUp() {
        var category = categories.save(new Category("공유 메타", 991L));
        post = new Post("공유할 글", "공유할 설명", "본문", category, null, null);
        post.publish(LocalDateTime.now().minusDays(1));
        posts.saveAndFlush(post);
    }

    @Test
    void anonymousCrawlerReceivesHtmlWithoutCreatingASession() throws Exception {
        mvc.perform(get("/posts/" + post.getId()).header("Host", "untrusted.example"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(content().string(containsString("<title>공유할 글 · 정성주의 기록</title>")))
                .andExpect(content().string(containsString("https://blog.example/posts/" + post.getId())))
                .andExpect(content().string(not(containsString("untrusted.example"))));
    }

    @Test
    void deletedPostReturns404WithNoPrivateMetadata() throws Exception {
        post.delete(LocalDateTime.now());
        posts.flush();
        mvc.perform(get("/posts/" + post.getId()))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string(containsString("noindex,follow")))
                .andExpect(content().string(not(containsString("공유할 글"))))
                .andExpect(content().string(not(containsString("공유할 설명"))));
    }

    @Test
    void malformedIdAlsoReturnsHtml404() throws Exception {
        mvc.perform(get("/posts/not-a-post"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andExpect(content().string(containsString("찾는 글이 없습니다")));
    }
}
