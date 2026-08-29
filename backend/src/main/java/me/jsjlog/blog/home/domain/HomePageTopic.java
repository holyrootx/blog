package me.jsjlog.blog.home.domain;

import me.jsjlog.blog.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "home_page_topic")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HomePageTopic extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "label", nullable = false, length = 50)
    private String label;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "keywords", length = 255)
    private String keywords;

    @Column(name = "sort_order", nullable = false)
    private Long sortOrder;

    public HomePageTopic(
            String label,
            String title,
            String description,
            String keywords,
            Long sortOrder
    ) {
        this.label = label;
        this.title = title;
        this.description = description;
        this.keywords = keywords;
        this.sortOrder = sortOrder;
    }

    public void update(
            String label,
            String title,
            String description,
            String keywords,
            Long sortOrder
    ) {
        this.label = label;
        this.title = title;
        this.description = description;
        this.keywords = keywords;
        this.sortOrder = sortOrder;
    }

}
