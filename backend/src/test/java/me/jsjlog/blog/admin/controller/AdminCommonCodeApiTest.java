package me.jsjlog.blog.admin.controller;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import me.jsjlog.blog.common.code.CommonCodeEnums;
import me.jsjlog.blog.common.code.CommonCodes;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.domain.MemberStatusCode;
import me.jsjlog.blog.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공통 코드 관리 화면 API.
 *
 * <p>고치면 커밋된 뒤 메모리의 공통 코드를 다시 읽는다. 그걸 보려고 트랜잭션으로 감싸지 않고,
 * 다른 테스트가 고친 값을 보지 않게 이 테스트만의 DB 를 쓴다.</p>
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:admin_common_code;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AdminCommonCodeApiTest {

    private static final String GROUPS = "/api/v1/admin/common-codes/groups";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MemberRepository members;
    @Autowired CommonCodes commonCodes;
    @Autowired JdbcTemplate jdbc;

    private MemberPrincipal admin;

    @BeforeEach
    void setUp() {
        admin = MemberPrincipal.ofLocal(members.saveAndFlush(
                Member.ofLocalAdmin("admin-" + UUID.randomUUID(), "hash", "관리자")));
    }

    @AfterEach
    void restore() {
        jdbc.update("update common_code set code_name = '탈퇴', sort_order = 3, is_enabled = true "
                + "where group_code = 'MEMBER_STATUS' and code = 'WITHDRAWN'");
        jdbc.update("update common_code_group set group_name = '회원 상태', is_enabled = true "
                + "where group_code = 'MEMBER_STATUS'");
        jdbc.update("delete from common_code where group_code = 'BANK'");
        jdbc.update("delete from common_code_group where group_code = 'BANK'");
        commonCodes.reload();
    }

    @Test
    @DisplayName("그룹 목록에 코드 수와 서버 코드 여부가 나오고, 그룹을 고르면 그 코드가 나온다")
    void listsGroupsAndCodes() throws Exception {
        mvc.perform(get(GROUPS).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].groupCode")
                        .value(contains("ACTOR_TYPE", "MEMBER_STATUS", "MEMBER_STATUS_REASON")))
                .andExpect(jsonPath("$.data.items[?(@.groupCode == 'MEMBER_STATUS')].codeCount").value(contains(3)))
                .andExpect(jsonPath("$.data.items[*].managedByEnum").value(everyItem(is(true))))
                .andExpect(jsonPath("$.data.totalElements").value(3));

        mvc.perform(get(GROUPS + "/MEMBER_STATUS/codes").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].code").value(contains("ACTIVE", "SUSPENDED", "WITHDRAWN")))
                .andExpect(jsonPath("$.data[*].managedByEnum").value(everyItem(is(true))));
    }

    @Test
    @DisplayName("검색어는 그룹과 그 안 코드의 값·이름에서 찾고, 사용 여부와 페이지로 나눠 볼 수 있다")
    void searchesAndPages() throws Exception {
        mvc.perform(get(GROUPS).param("keyword", "탈퇴").with(user(admin)))
                .andExpect(jsonPath("$.data.items[*].groupCode")
                        .value(contains("MEMBER_STATUS", "MEMBER_STATUS_REASON")));
        mvc.perform(get(GROUPS).param("keyword", "actor").with(user(admin)))
                .andExpect(jsonPath("$.data.items[*].groupCode").value(contains("ACTOR_TYPE")));
        mvc.perform(get(GROUPS).param("keyword", "처리자").with(user(admin)))
                .andExpect(jsonPath("$.data.items[*].groupCode").value(contains("ACTOR_TYPE")));

        mvc.perform(get(GROUPS).param("page", "1").param("size", "1").with(user(admin)))
                .andExpect(jsonPath("$.data.items[*].groupCode").value(contains("MEMBER_STATUS")))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(3));

        send(post(GROUPS), Map.of("groupCode", "BANK", "groupName", "은행")).andExpect(status().isOk());
        send(put(GROUPS + "/BANK"), groupBody(groupUpdatedAt("BANK"), "은행", null, false)).andExpect(status().isOk());
        mvc.perform(get(GROUPS).param("enabled", "false").with(user(admin)))
                .andExpect(jsonPath("$.data.items[*].groupCode").value(contains("BANK")));
        mvc.perform(get(GROUPS).param("enabled", "true").with(user(admin)))
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    @Test
    @DisplayName("화면에서 그룹을 만들고 코드를 넣고 끌 수 있다. 서버 시작 대조는 그대로 맞다")
    void createsGroupsAndCodes() throws Exception {
        send(post(GROUPS), Map.of("groupCode", "BANK", "groupName", "은행", "description", "환불 계좌 은행"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.managedByEnum").value(false))
                .andExpect(jsonPath("$.data.codeCount").value(0));

        send(post(GROUPS + "/BANK/codes"), Map.of("code", "004", "codeName", "국민"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sortOrder").value(1))
                .andExpect(jsonPath("$.data.enabled").value(true));
        send(post(GROUPS + "/BANK/codes"), Map.of("code", "088", "codeName", "신한"))
                .andExpect(jsonPath("$.data.sortOrder").value(2));

        JsonNode shinhan = code("BANK", "088");
        send(put(GROUPS + "/BANK/codes/088"), codeBody(shinhan.get("updatedAt").asString(), "신한은행", null, 2, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        mvc.perform(get(GROUPS + "/BANK/codes").with(user(admin)))
                .andExpect(jsonPath("$.data[*].codeName").value(contains("국민", "신한은행")))
                .andExpect(jsonPath("$.data[*].enabled").value(contains(true, false)));
        mvc.perform(get(GROUPS).param("keyword", "BANK").with(user(admin)))
                .andExpect(jsonPath("$.data.items[0].codeCount").value(2));

        assertThat(commonCodes.codesOf("BANK")).hasSize(2);
        assertThat(commonCodes.mismatches(CommonCodeEnums.codesByGroup())).isEmpty();
    }

    @Test
    @DisplayName("같은 키, 형식이 틀린 키, 없는 그룹은 거절한다")
    void rejectsBadKeys() throws Exception {
        send(post(GROUPS), Map.of("groupCode", "BANK", "groupName", "은행")).andExpect(status().isOk());

        expectError(send(post(GROUPS), Map.of("groupCode", "BANK", "groupName", "또 은행")), 409, "COMMON_CODE_GROUP_DUPLICATED");
        expectError(send(post(GROUPS), Map.of("groupCode", "MEMBER_STATUS", "groupName", "겹침")), 409, "COMMON_CODE_GROUP_DUPLICATED");
        expectError(send(post(GROUPS), Map.of("groupCode", "bank code", "groupName", "은행")), 400, "COMMON_CODE_KEY_INVALID");
        expectError(send(post(GROUPS), Map.of("groupCode", "_BANK", "groupName", "은행")), 400, "COMMON_CODE_KEY_INVALID");
        expectError(send(post(GROUPS), Map.of("groupCode", "A".repeat(51), "groupName", "은행")), 400, "COMMON_CODE_KEY_INVALID");
        expectError(send(post(GROUPS), Map.of("groupCode", "CARD", "groupName", " ")), 400, "COMMON_CODE_NAME_REQUIRED");

        send(post(GROUPS + "/BANK/codes"), Map.of("code", "004", "codeName", "국민")).andExpect(status().isOk());
        expectError(send(post(GROUPS + "/BANK/codes"), Map.of("code", "004", "codeName", "또 국민")), 409, "COMMON_CODE_DUPLICATED");
        expectError(send(post(GROUPS + "/BANK/codes"), Map.of("code", "kb", "codeName", "국민")), 400, "COMMON_CODE_KEY_INVALID");
        expectError(send(post(GROUPS + "/NOPE/codes"), Map.of("code", "X", "codeName", "없음")), 404, "COMMON_CODE_GROUP_NOT_FOUND");
        expectError(mvc.perform(get(GROUPS + "/NOPE/codes").with(user(admin))), 404, "COMMON_CODE_GROUP_NOT_FOUND");
    }

    @Test
    @DisplayName("서버 코드가 쓰는 그룹은 코드 추가와 끄기를 막고, 이름은 고칠 수 있다")
    void protectsEnumGroups() throws Exception {
        expectError(send(post(GROUPS + "/MEMBER_STATUS/codes"), Map.of("code", "DORMANT", "codeName", "휴면")),
                409, "COMMON_CODE_MANAGED_BY_SERVER");

        String codeUpdatedAt = code("MEMBER_STATUS", "WITHDRAWN").get("updatedAt").asString();
        expectError(send(put(GROUPS + "/MEMBER_STATUS/codes/WITHDRAWN"), codeBody(codeUpdatedAt, "탈퇴", null, 3, false)),
                409, "COMMON_CODE_MANAGED_BY_SERVER");
        expectError(send(put(GROUPS + "/MEMBER_STATUS"), groupBody(groupUpdatedAt("MEMBER_STATUS"), "회원 상태", null, false)),
                409, "COMMON_CODE_MANAGED_BY_SERVER");

        send(put(GROUPS + "/MEMBER_STATUS"), groupBody(groupUpdatedAt("MEMBER_STATUS"), "회원 상태값", "설명", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupName").value("회원 상태값"));
        assertThat(commonCodes.mismatches(CommonCodeEnums.codesByGroup())).isEmpty();
    }

    @Test
    @DisplayName("이름을 고치면 커밋 뒤 회원 목록의 상태 이름에도 바로 나온다")
    void renameIsUsedEverywhere() throws Exception {
        Member withdrawn = Member.ofSocial(AuthProvider.GOOGLE, "code-" + UUID.randomUUID(), "떠난 독자", null, null);
        withdrawn.withdraw(LocalDateTime.now());
        members.saveAndFlush(withdrawn);
        String updatedAt = code("MEMBER_STATUS", "WITHDRAWN").get("updatedAt").asString();

        send(put(GROUPS + "/MEMBER_STATUS/codes/WITHDRAWN"), codeBody(updatedAt, "탈퇴 회원", "30일 안에는 복원", 3, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.codeName").value("탈퇴 회원"));

        assertThat(commonCodes.nameOf(MemberStatusCode.WITHDRAWN)).isEqualTo("탈퇴 회원");
        mvc.perform(get("/api/v1/admin/blog/members").param("status", "WITHDRAWN").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.data.items[0].statusName").value("탈퇴 회원"));
    }

    @Test
    @DisplayName("빈 이름, 너무 긴 값, 범위 밖 순서, 없는 코드, 그 사이 바뀐 코드는 거절한다")
    void rejectsInvalidChanges() throws Exception {
        String updatedAt = code("MEMBER_STATUS", "WITHDRAWN").get("updatedAt").asString();
        String path = GROUPS + "/MEMBER_STATUS/codes/";

        expectError(send(put(path + "WITHDRAWN"), codeBody(updatedAt, "  ", null, 3, null)), 400, "COMMON_CODE_NAME_REQUIRED");
        expectError(send(put(path + "WITHDRAWN"), codeBody(updatedAt, "가".repeat(101), null, 3, null)), 400, "COMMON_CODE_VALUE_TOO_LONG");
        expectError(send(put(path + "WITHDRAWN"), codeBody(updatedAt, "탈퇴", null, -1, null)), 400, "COMMON_CODE_SORT_ORDER_INVALID");
        expectError(send(put(path + "DORMANT"), codeBody(updatedAt, "휴면", null, 4, null)), 404, "COMMON_CODE_NOT_FOUND");
        expectError(send(put(path + "WITHDRAWN"), codeBody("2000-01-01T00:00:00", "탈퇴", null, 3, null)), 409, "MODIFIED_BY_OTHERS");
        expectError(send(put(GROUPS + "/MEMBER_STATUS"), groupBody("2000-01-01T00:00:00", "회원", null, true)), 409, "MODIFIED_BY_OTHERS");
        assertThat(commonCodes.nameOf(MemberStatusCode.WITHDRAWN)).isEqualTo("탈퇴");
    }

    @Test
    @DisplayName("관리자가 아니면 볼 수도 고칠 수도 없다")
    void onlyAdmins() throws Exception {
        MemberPrincipal reader = MemberPrincipal.ofSocial(members.saveAndFlush(
                Member.ofSocial(AuthProvider.KAKAO, "reader-" + UUID.randomUUID(), "독자", null, null)));

        mvc.perform(get(GROUPS)).andExpect(status().isUnauthorized());
        mvc.perform(get(GROUPS).with(user(reader))).andExpect(status().isForbidden());
        mvc.perform(get(GROUPS + "/MEMBER_STATUS/codes").with(user(reader))).andExpect(status().isForbidden());
        mvc.perform(post(GROUPS).with(user(reader)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"groupCode\":\"BANK\",\"groupName\":\"은행\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(GROUPS + "/MEMBER_STATUS/codes/WITHDRAWN").with(user(reader)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"codeName\":\"바꿈\"}"))
                .andExpect(status().isForbidden());
    }

    private JsonNode code(String groupCode, String code) throws Exception {
        String response = mvc.perform(get(GROUPS + "/" + groupCode + "/codes").with(user(admin)))
                .andReturn().getResponse().getContentAsString();
        for (JsonNode node : json.readTree(response).get("data")) {
            if (node.get("code").asString().equals(code)) {
                return node;
            }
        }
        throw new AssertionError(groupCode + "/" + code + " 이 목록에 없습니다");
    }

    private String groupUpdatedAt(String groupCode) throws Exception {
        String response = mvc.perform(get(GROUPS).param("keyword", groupCode).param("size", "100").with(user(admin)))
                .andReturn().getResponse().getContentAsString();
        for (JsonNode node : json.readTree(response).get("data").get("items")) {
            if (node.get("groupCode").asString().equals(groupCode)) {
                return node.get("updatedAt").asString();
            }
        }
        throw new AssertionError(groupCode + " 그룹이 목록에 없습니다");
    }

    private Map<String, Object> groupBody(String updatedAt, String groupName, String description, Boolean enabled) {
        var values = new LinkedHashMap<String, Object>();
        values.put("updatedAt", updatedAt);
        values.put("groupName", groupName);
        values.put("description", description);
        values.put("enabled", enabled);
        return values;
    }

    private Map<String, Object> codeBody(String updatedAt, String codeName, String description, int sortOrder, Boolean enabled) {
        var values = new LinkedHashMap<String, Object>();
        values.put("updatedAt", updatedAt);
        values.put("codeName", codeName);
        values.put("description", description);
        values.put("sortOrder", sortOrder);
        values.put("enabled", enabled);
        return values;
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Map<String, ?> body) throws Exception {
        return mvc.perform(request.with(user(admin)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private void expectError(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
    }
}
