package me.jsjlog.blog.admin.service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.dto.AdminCommonCodeCreateRequest;
import me.jsjlog.blog.admin.dto.AdminCommonCodeGroupCreateRequest;
import me.jsjlog.blog.admin.dto.AdminCommonCodeGroupListResponse;
import me.jsjlog.blog.admin.dto.AdminCommonCodeGroupSearchCondition;
import me.jsjlog.blog.admin.dto.AdminCommonCodeGroupSummaryResponse;
import me.jsjlog.blog.admin.dto.AdminCommonCodeGroupUpdateRequest;
import me.jsjlog.blog.admin.dto.AdminCommonCodeResponse;
import me.jsjlog.blog.admin.dto.AdminCommonCodeUpdateRequest;
import me.jsjlog.blog.admin.repository.AdminCommonCodeQueryRepository;
import me.jsjlog.blog.common.code.CommonCodeEnums;
import me.jsjlog.blog.common.code.CommonCodes;
import me.jsjlog.blog.common.code.domain.CommonCode;
import me.jsjlog.blog.common.code.domain.CommonCodeGroup;
import me.jsjlog.blog.common.code.domain.CommonCodeId;
import me.jsjlog.blog.common.code.repository.CommonCodeGroupRepository;
import me.jsjlog.blog.common.code.repository.CommonCodeRepository;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ConcurrencyGuard;
import me.jsjlog.blog.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * 공통 코드 관리 화면.
 *
 * <p>그룹과 코드를 추가하고 이름·설명·순서·사용 여부를 고친다. 코드 값과 그룹 코드는 바꾸지 않고,
 * 지우는 대신 사용 여부를 끈다 — 지난 데이터와 이력이 그 값을 가리킨다.</p>
 *
 * <p>서버 코드(enum)가 쓰는 그룹은 코드 목록을 enum 이 정한다. 그래서 코드 추가와 사용 여부 변경을 막고
 * 이름·설명·순서만 고친다. 화면에서 만든 그룹은 enum 대조 대상이 아니라 자유롭게 고친다.</p>
 */
@Service
@RequiredArgsConstructor
public class AdminCommonCodeService {

    private static final Pattern KEY = Pattern.compile("[A-Z0-9][A-Z0-9_]{0,49}");
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_SORT_ORDER = 9999;

    private final CommonCodeGroupRepository groupRepository;
    private final CommonCodeRepository codeRepository;
    private final AdminCommonCodeQueryRepository queryRepository;
    private final CommonCodes commonCodes;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public AdminCommonCodeGroupListResponse getGroups(AdminCommonCodeGroupSearchCondition condition) {
        List<CommonCodeGroup> groups = queryRepository.getGroups(condition);
        Map<String, Long> codeCounts = queryRepository.countCodes(
                groups.stream().map(CommonCodeGroup::getGroupCode).toList());
        long totalElements = queryRepository.countGroups(condition);
        int size = condition.sizeOrDefault();

        return new AdminCommonCodeGroupListResponse(
                groups.stream()
                        .map(group -> AdminCommonCodeGroupSummaryResponse.of(group,
                                codeCounts.getOrDefault(group.getGroupCode(), 0L)))
                        .toList(),
                condition.pageOrDefault(),
                size,
                totalElements,
                (int) Math.ceil((double) totalElements / size)
        );
    }

    /** 그룹의 코드 전부. 사용하지 않는 코드도 보여 준다(다시 켤 수 있게) */
    @Transactional(readOnly = true)
    public List<AdminCommonCodeResponse> getCodes(String groupCode) {
        requireGroup(groupCode);
        return codeRepository.findAllByGroupCodeOrderBySortOrderAscCodeAsc(groupCode).stream()
                .map(AdminCommonCodeResponse::from)
                .toList();
    }

    @Transactional
    public AdminCommonCodeGroupSummaryResponse createGroup(AdminCommonCodeGroupCreateRequest request) {
        String groupCode = key(request.groupCode());
        String name = name(request.groupName());
        String description = description(request.description());
        validate(name, description, 0);

        if (groupRepository.existsById(groupCode)) {
            throw new BlogException(ErrorCode.COMMON_CODE_GROUP_DUPLICATED);
        }
        CommonCodeGroup group = CommonCodeGroup.create(groupCode, name, description);
        insert(group, ErrorCode.COMMON_CODE_GROUP_DUPLICATED);
        reloadAfterCommit();
        return AdminCommonCodeGroupSummaryResponse.of(group, 0);
    }

    @Transactional
    public AdminCommonCodeGroupSummaryResponse updateGroup(String groupCode, AdminCommonCodeGroupUpdateRequest request) {
        CommonCodeGroup group = requireGroup(groupCode);
        ConcurrencyGuard.check(request.updatedAt(), group.getUpdatedAt());

        String name = name(request.groupName());
        String description = description(request.description());
        boolean enabled = request.enabled() == null ? group.isEnabled() : request.enabled();
        validate(name, description, 0);
        if (!enabled && CommonCodeEnums.isEnumGroup(groupCode)) {
            throw new BlogException(ErrorCode.COMMON_CODE_MANAGED_BY_SERVER);
        }

        group.change(name, description, enabled);
        groupRepository.flush();
        reloadAfterCommit();
        return AdminCommonCodeGroupSummaryResponse.of(group,
                queryRepository.countCodes(List.of(groupCode)).getOrDefault(groupCode, 0L));
    }

    @Transactional
    public AdminCommonCodeResponse createCode(String groupCode, AdminCommonCodeCreateRequest request) {
        requireGroup(groupCode);
        if (CommonCodeEnums.isEnumGroup(groupCode)) {
            throw new BlogException(ErrorCode.COMMON_CODE_MANAGED_BY_SERVER);
        }

        String code = key(request.code());
        String name = name(request.codeName());
        String description = description(request.description());
        int sortOrder = request.sortOrder() != null
                ? request.sortOrder()
                : Math.min(codeRepository.findMaxSortOrder(groupCode) + 1, MAX_SORT_ORDER);
        validate(name, description, sortOrder);

        if (codeRepository.existsById(new CommonCodeId(groupCode, code))) {
            throw new BlogException(ErrorCode.COMMON_CODE_DUPLICATED);
        }
        CommonCode commonCode = CommonCode.create(groupCode, code, name, description, sortOrder);
        insert(commonCode, ErrorCode.COMMON_CODE_DUPLICATED);
        reloadAfterCommit();
        return AdminCommonCodeResponse.from(commonCode);
    }

    @Transactional
    public AdminCommonCodeResponse updateCode(String groupCode, String code, AdminCommonCodeUpdateRequest request) {
        CommonCode commonCode = codeRepository.findById(new CommonCodeId(groupCode, code))
                .orElseThrow(() -> new BlogException(ErrorCode.COMMON_CODE_NOT_FOUND));
        ConcurrencyGuard.check(request.updatedAt(), commonCode.getUpdatedAt());

        String name = name(request.codeName());
        String description = description(request.description());
        int sortOrder = request.sortOrder() == null ? commonCode.getSortOrder() : request.sortOrder();
        boolean enabled = request.enabled() == null ? commonCode.isEnabled() : request.enabled();
        validate(name, description, sortOrder);
        if (!enabled && CommonCodeEnums.isManagedByEnum(groupCode, code)) {
            throw new BlogException(ErrorCode.COMMON_CODE_MANAGED_BY_SERVER);
        }

        commonCode.change(name, description, sortOrder, enabled);
        codeRepository.flush();
        reloadAfterCommit();
        return AdminCommonCodeResponse.from(commonCode);
    }

    private CommonCodeGroup requireGroup(String groupCode) {
        return groupRepository.findById(groupCode)
                .orElseThrow(() -> new BlogException(ErrorCode.COMMON_CODE_GROUP_NOT_FOUND));
    }

    /**
     * 새 행으로만 넣는다. save() 는 키를 직접 정한 엔티티를 merge 하므로, 같은 키를 동시에 만들면
     * 나중 요청이 먼저 만든 행을 덮어쓴다. persist 는 키가 겹치면 실패한다.
     */
    private void insert(Object entity, ErrorCode duplicated) {
        try {
            entityManager.persist(entity);
            entityManager.flush();
        } catch (PersistenceException e) {
            throw new BlogException(duplicated);
        }
    }

    private static String key(String value) {
        String key = value == null ? "" : value.trim();
        if (!KEY.matcher(key).matches()) {
            throw new BlogException(ErrorCode.COMMON_CODE_KEY_INVALID);
        }
        return key;
    }

    private static String name(String value) {
        return value == null ? "" : value.trim();
    }

    private static String description(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static void validate(String name, String description, int sortOrder) {
        if (name.isEmpty()) {
            throw new BlogException(ErrorCode.COMMON_CODE_NAME_REQUIRED);
        }
        if (name.length() > MAX_NAME_LENGTH
                || (description != null && description.length() > MAX_DESCRIPTION_LENGTH)) {
            throw new BlogException(ErrorCode.COMMON_CODE_VALUE_TOO_LONG);
        }
        if (sortOrder < 0 || sortOrder > MAX_SORT_ORDER) {
            throw new BlogException(ErrorCode.COMMON_CODE_SORT_ORDER_INVALID);
        }
    }

    /** 커밋된 값만 메모리에 올린다. 저장이 롤백됐는데 이름이 바뀌어 보이면 안 된다 */
    private void reloadAfterCommit() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                commonCodes.reload();
            }
        });
    }
}
