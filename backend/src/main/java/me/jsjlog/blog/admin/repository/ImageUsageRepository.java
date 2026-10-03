package me.jsjlog.blog.admin.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import me.jsjlog.blog.admin.domain.UploadImage;
import me.jsjlog.blog.admin.dto.AdminImageUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImageUsageRepository extends JpaRepository<UploadImage, Long> {

    /**
     * 넘긴 URL 중 아직 참조되는 것만 반환한다.
     *
     * upload_image 와 post 사이에 FK 가 없다. URL 이 본문 마크다운 문자열 안에 있어서
     * LIKE 로 찾는다.
     *
     * "참조 안 됨" 이 아니라 "참조 됨" 을 조회한다. 조건이 빠지면 삭제 대상에서 빠지는데,
     * 반대로 짜면 쓰고 있는 이미지를 지운다.
     */
    @Query("""
            select distinct u from UploadImage u
            where u.url in :urls
              and (
                   exists (select 1 from Post p where p.content like concat('%', u.url, '%'))
                or exists (select 1 from Post p where p.thumbnailImageUrl = u.url)
                or exists (select 1 from ContentHistory h where
                    locate(u.url, h.beforeSnapshot) > 0 or locate(u.url, h.afterSnapshot) > 0)
                or exists (select 1 from HomePageHero h where h.heroImageUrl = u.url)
                or exists (select 1 from BlogProfile b where b.avatarImageUrl = u.url)
              )
            """)
    List<UploadImage> findStillUsed(@Param("urls") Collection<String> urls);

    List<UploadImage> findByUrlIn(Collection<String> urls);

    /**
     * 올린 지 uploadedBefore 보다 오래된 전체 목록.
     *
     * 최근 것을 빼는 이유는 사진만 올리고 아직 저장하지 않은 글 때문이다. 서버에는 그 글이
     * 없어서 "안 쓰임" 으로 보인다. 편집기 임시저장이 브라우저에만 있어 서버가 알 수 없다.
     */
    @Query("select u from UploadImage u where u.createdAt < :uploadedBefore")
    Page<UploadImage> findOlderThan(
            @Param("uploadedBefore") LocalDateTime uploadedBefore, Pageable pageable);

    /** 위와 같은 조건에 참조가 하나도 없는 것만 */
    @Query("""
            select u from UploadImage u
            where u.createdAt < :uploadedBefore
              and not exists (select 1 from Post p where p.content like concat('%', u.url, '%'))
              and not exists (select 1 from Post p where p.thumbnailImageUrl = u.url)
              and not exists (select 1 from ContentHistory h where
                  locate(u.url, h.beforeSnapshot) > 0 or locate(u.url, h.afterSnapshot) > 0)
              and not exists (select 1 from HomePageHero h where h.heroImageUrl = u.url)
              and not exists (select 1 from BlogProfile b where b.avatarImageUrl = u.url)
            """)
    Page<UploadImage> findUnusedOlderThan(
            @Param("uploadedBefore") LocalDateTime uploadedBefore, Pageable pageable);

    /*
     * 아래 넷은 "어디에 쓰이는지" 를 채운다. 화면 한 페이지(20장) 분량만 넘기므로
     * 본문 LIKE 도 그 범위에서만 돈다.
     */

    @Query("""
            select new me.jsjlog.blog.admin.dto.AdminImageUsage(
                u.url, me.jsjlog.blog.admin.dto.AdminImageUsage$Where.POST_CONTENT, p.title)
            from UploadImage u, Post p
            where u.url in :urls and p.content like concat('%', u.url, '%')
            """)
    List<AdminImageUsage> findContentUsages(@Param("urls") Collection<String> urls);

    @Query("""
            select new me.jsjlog.blog.admin.dto.AdminImageUsage(
                u.url, me.jsjlog.blog.admin.dto.AdminImageUsage$Where.POST_THUMBNAIL, p.title)
            from UploadImage u, Post p
            where u.url in :urls and p.thumbnailImageUrl = u.url
            """)
    List<AdminImageUsage> findThumbnailUsages(@Param("urls") Collection<String> urls);

    @Query("""
            select distinct new me.jsjlog.blog.admin.dto.AdminImageUsage(
                u.url, me.jsjlog.blog.admin.dto.AdminImageUsage$Where.CONTENT_HISTORY, null)
            from UploadImage u, ContentHistory h
            where u.url in :urls
              and (locate(u.url, h.beforeSnapshot) > 0 or locate(u.url, h.afterSnapshot) > 0)
            """)
    List<AdminImageUsage> findHistoryUsages(@Param("urls") Collection<String> urls);

    @Query("""
            select new me.jsjlog.blog.admin.dto.AdminImageUsage(
                h.heroImageUrl, me.jsjlog.blog.admin.dto.AdminImageUsage$Where.HOME_HERO, null)
            from HomePageHero h
            where h.heroImageUrl in :urls
            """)
    List<AdminImageUsage> findHeroUsages(@Param("urls") Collection<String> urls);

    @Query("""
            select new me.jsjlog.blog.admin.dto.AdminImageUsage(
                b.avatarImageUrl, me.jsjlog.blog.admin.dto.AdminImageUsage$Where.PROFILE, null)
            from BlogProfile b
            where b.avatarImageUrl in :urls
            """)
    List<AdminImageUsage> findProfileUsages(@Param("urls") Collection<String> urls);
}
