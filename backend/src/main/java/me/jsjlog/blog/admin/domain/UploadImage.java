package me.jsjlog.blog.admin.domain;

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

/**
 * 올린 이미지 한 장의 기록.
 *
 * 파일 자체는 저장소(R2 또는 로컬)에 있고 여기에는 그 파일을 찾고 지우는 데 필요한 정보만 둔다.
 * 기록을 남기지 않으면 글에서 이미지를 지웠을 때 저장소에 남은 파일을 찾을 방법이 없다.
 *
 * 어느 글이 이 이미지를 쓰는지는 담지 않는다. 주소가 본문 마크다운 문자열 안에 있어서
 * 정확히 추적하려면 글 본문을 뒤져야 하고, 이미지를 다른 글로 옮기는 순간 어긋난다.
 * 지금은 업로드 대장으로만 쓰고, 정리는 목록 화면에서 보고 지우는 방식으로 한다.
 */
@Getter
@Entity
@Table(name = "upload_image")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UploadImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 저장소 안에서의 위치. 파일을 지울 때 이 값이 필요하다 */
    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    /** 글 본문에 박히는 공개 주소 */
    @Column(name = "url", nullable = false, length = 500)
    private String url;

    /** 목록 화면에서 사람이 알아볼 이름. 저장 경로에는 쓰지 않는다 */
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    public UploadImage(String storageKey, String url, String originalName, String contentType, long byteSize) {
        this.storageKey = storageKey;
        this.url = url;
        this.originalName = originalName;
        this.contentType = contentType;
        this.byteSize = byteSize;
    }
}
