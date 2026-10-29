package me.jsjlog.blog.common.time;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** 서버가 저장한 시각을 받는 쪽이 같은 순간으로 읽을 수 있게 바꾼다. */
public final class ServerTime {

	private ServerTime() {
	}

	/**
	 * 저장된 시각은 서버 시간대의 값이다. 시간대 없이 내보내면 브라우저가 자기 시간대로 읽어서,
	 * 보는 사람의 PC 시간대에 따라 다른 시각이 보인다. 그래서 서버 시간대를 붙여 내보낸다.
	 */
	public static OffsetDateTime toOffset(LocalDateTime value) {
		return value == null ? null : value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
	}
}
