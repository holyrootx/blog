package me.jsjlog.blog.common.code;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import me.jsjlog.blog.member.domain.MemberStatusCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 공통 코드 표와 서버 enum 이 맞는지.
 *
 * <p>enum 에 값을 더하고 시드 SQL 을 빠뜨리면 여기서 걸린다. 운영에서는 같은 대조가 서버 시작 때
 * 돌아 서버가 뜨지 않는다. 표를 직접 지우거나 더하는 경우를 흉내 내야 해서 이 테스트만의 DB 를 쓴다.</p>
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:common_code_check;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class CommonCodeConsistencyTest {

    @Autowired CommonCodes commonCodes;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @AfterEach
    void restoreSeed() {
        jdbc.update("delete from common_code where group_code = 'MEMBER_STATUS' and code = 'DORMANT'");
        new ResourceDatabasePopulator(new ClassPathResource("db/common-code-seed.sql")).execute(dataSource);
        commonCodes.reload();
    }

    @Test
    @DisplayName("서버 enum 과 시드한 공통 코드 표가 같다")
    void enumsMatchSeededTable() {
        Map<String, Set<String>> enums = CommonCodeEnums.codesByGroup();

        assertThat(enums).containsOnlyKeys("MEMBER_STATUS", "MEMBER_STATUS_REASON", "ACTOR_TYPE");
        assertThat(enums.get("MEMBER_STATUS")).containsExactly("ACTIVE", "SUSPENDED", "WITHDRAWN");
        assertThat(commonCodes.mismatches(enums)).isEmpty();
    }

    @Test
    @DisplayName("어긋나면 양쪽 방향을 모두 알려 준다")
    void reportsBothDirections() {
        Map<String, Set<String>> enums = new LinkedHashMap<>(CommonCodeEnums.codesByGroup());
        Set<String> memberStatus = new LinkedHashSet<>(enums.get("MEMBER_STATUS"));
        memberStatus.add("DORMANT");
        memberStatus.remove("WITHDRAWN");
        enums.put("MEMBER_STATUS", memberStatus);

        assertThat(commonCodes.mismatches(enums)).containsExactly(
                "MEMBER_STATUS 표에 없음 [DORMANT]",
                "MEMBER_STATUS enum 에 없음 [WITHDRAWN]"
        );
    }

    @Test
    @DisplayName("enum 에 있는 코드가 표에서 빠지면 서버 시작을 거부한다")
    void refusesStartWhenCodeIsMissing() {
        jdbc.update("delete from common_code where group_code = 'ACTOR_TYPE' and code = 'SYSTEM'");

        assertThatThrownBy(() -> commonCodes.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACTOR_TYPE 표에 없음 [SYSTEM]");
    }

    @Test
    @DisplayName("enum 에 없는 코드가 표에 생기면 서버 시작을 거부한다")
    void refusesStartWhenTableHasUnknownCode() {
        jdbc.update("""
                insert into common_code (group_code, code, code_name, sort_order, is_enabled, created_at, updated_at)
                values ('MEMBER_STATUS', 'DORMANT', '휴면', 9, true, current_timestamp, current_timestamp)
                """);

        assertThatThrownBy(() -> commonCodes.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MEMBER_STATUS enum 에 없음 [DORMANT]");
    }

    @Test
    @DisplayName("이름은 표에서 가져온다")
    void namesComeFromTable() {
        assertThat(commonCodes.nameOf(MemberStatusCode.WITHDRAWN)).isEqualTo("탈퇴");
        assertThat(commonCodes.nameOf(ActorTypeCode.SYSTEM)).isEqualTo("시스템");
        assertThat(commonCodes.codesOf("MEMBER_STATUS"))
                .extracting(code -> code.getCode())
                .containsExactly("ACTIVE", "SUSPENDED", "WITHDRAWN");
    }
}
