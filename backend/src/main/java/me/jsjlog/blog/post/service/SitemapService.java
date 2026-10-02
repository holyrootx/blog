package me.jsjlog.blog.post.service;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.post.domain.PostStatus;
import me.jsjlog.blog.post.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SitemapService {

    private static final String SITEMAP_NAMESPACE = "http://www.sitemaps.org/schemas/sitemap/0.9";
    private static final String SITE_URL = "https://jsjlog.me";

    private final PostRepository postRepository;

    @Transactional(readOnly = true)
    public String publicSitemapXml() {
        var postIds = postRepository.findPublicPostIdsForSitemap(PostStatus.PUBLISHED, LocalDateTime.now());
        var output = new StringWriter();

        try {
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("urlset");
            xml.writeDefaultNamespace(SITEMAP_NAMESPACE);

            writeUrl(xml, SITE_URL + "/");
            writeUrl(xml, SITE_URL + "/posts");

            for (Long postId : postIds) {
                writeUrl(xml, SITE_URL + "/posts/" + postId);
            }

            xml.writeEndElement();
            xml.writeEndDocument();
            xml.close();
        } catch (XMLStreamException exception) {
            throw new IllegalStateException("Failed to generate post sitemap", exception);
        }

        return output.toString();
    }

    private void writeUrl(XMLStreamWriter xml, String url) throws XMLStreamException {
        xml.writeStartElement("url");
        xml.writeStartElement("loc");
        xml.writeCharacters(url);
        xml.writeEndElement();
        xml.writeEndElement();
    }
}
