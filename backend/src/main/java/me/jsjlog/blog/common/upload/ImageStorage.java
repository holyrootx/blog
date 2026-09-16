package me.jsjlog.blog.common.upload;

import java.io.InputStream;

/**
 * 이미지 파일이 실제로 놓이는 곳.
 *
 * 저장소를 바꿔도 서비스가 바뀌지 않도록 가른다.
 * 키를 정하는 일은 여기 없다 — 저장 방식과 무관한 규칙이라 서비스가 정해서 넘겨준다.
 */
public interface ImageStorage {

    StoredImage store(String storageKey, String contentType, long size, InputStream content);

    void delete(String storageKey);
}
