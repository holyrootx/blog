package me.jsjlog.blog.post.controller;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.post.service.SitemapService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SitemapController {

    private final SitemapService sitemapService;

    @GetMapping(value = "/api/v1/blog/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String getSitemap() {
        return sitemapService.publicSitemapXml();
    }
}
