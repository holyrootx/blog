package me.jsjlog.blog.home.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.jsjlog.blog.common.domain.BaseEntity;

/** 대문에서 주제 카드들을 감싸는 제목과 안내 문구. */
@Getter
@Entity
@Table(name = "home_page_topic_section")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HomePageTopicSection extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "intro", nullable = false, length = 255)
    private String intro;

    @Column(name = "note_badge", nullable = false, length = 50)
    private String noteBadge;

    @Column(name = "note", nullable = false, length = 255)
    private String note;

    public HomePageTopicSection(String title, String intro, String noteBadge, String note) {
        this.title = title;
        this.intro = intro;
        this.noteBadge = noteBadge;
        this.note = note;
    }

    public void update(String title, String intro, String noteBadge, String note) {
        this.title = title;
        this.intro = intro;
        this.noteBadge = noteBadge;
        this.note = note;
    }
}
