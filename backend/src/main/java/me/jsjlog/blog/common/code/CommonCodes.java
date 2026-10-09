package me.jsjlog.blog.common.code;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.jsjlog.blog.common.code.domain.CommonCode;
import me.jsjlog.blog.common.code.repository.CommonCodeRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 공통 코드를 메모리에 들고 있다가 이름을 내준다.
 *
 * <p>거의 바뀌지 않는 작은 표라 통째로 들고 있는다. 행 1,000개여도 1MB 안쪽이다.
 * 관리 화면에서 고치면 {@link #reload()} 로 다시 읽는다.</p>
 *
 * <p><b>서버가 뜰 때 enum 과 표를 대조한다.</b> 어긋나면 서버를 띄우지 않는다.</p>
 * <ul>
 *   <li>enum 에 있는데 표에 없다 — 동작은 있는데 화면에 보일 이름이 없다</li>
 *   <li>그 그룹의 표에 있는데 enum 에 없다 — 이름은 있는데 서버가 처리하지 못하는 값이 저장될 수 있다</li>
 * </ul>
 * 배포 스크립트는 상태 확인에 실패하면 이전 버전으로 되돌리므로, 시드 SQL 을 빠뜨린 배포는 그대로 무른다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommonCodes implements ApplicationRunner {

    private final CommonCodeRepository commonCodeRepository;

    private volatile Map<String, Map<String, CommonCode>> byGroup;

    @Override
    public void run(ApplicationArguments args) {
        reload();
        List<String> problems = mismatches(CommonCodeEnums.codesByGroup());
        if (!problems.isEmpty()) {
            throw new IllegalStateException("공통 코드 표가 enum 과 다릅니다. 시드 SQL 을 확인하세요. " + problems);
        }
        log.info("[공통 코드] {}개 그룹을 enum 과 대조했습니다", CommonCodeEnums.codesByGroup().size());
    }

    /** 표를 다시 읽는다. 관리 화면에서 고친 뒤, 트랜잭션이 커밋된 다음에 부른다 */
    public void reload() {
        Map<String, Map<String, CommonCode>> loaded = new LinkedHashMap<>();
        for (CommonCode code : commonCodeRepository.findAllByOrderByGroupCodeAscSortOrderAscCodeAsc()) {
            loaded.computeIfAbsent(code.getGroupCode(), group -> new LinkedHashMap<>()).put(code.getCode(), code);
        }
        byGroup = loaded;
    }

    /**
     * 화면에 보일 이름. 표에 없으면 코드 값을 그대로 돌려준다 — 이름 하나 때문에 목록 전체가
     * 깨지지 않게 한다. 정상이라면 서버 시작 시 대조에서 이미 걸렸을 일이다.
     */
    public String nameOf(CommonCodeType type) {
        if (type == null) {
            return null;
        }
        CommonCode code = groups().getOrDefault(type.groupCode(), Map.of()).get(type.code());
        return code == null ? type.code() : code.getCodeName();
    }

    /** 그룹의 코드들. 표시 순서대로 */
    public List<CommonCode> codesOf(String groupCode) {
        return groups().getOrDefault(groupCode, Map.of()).values().stream()
                .sorted(Comparator.comparingInt(CommonCode::getSortOrder).thenComparing(CommonCode::getCode))
                .toList();
    }

    /** enum 과 표가 어긋난 내용. 비어 있으면 맞다. 테스트도 같은 기준을 쓴다 */
    public List<String> mismatches(Map<String, Set<String>> enumCodes) {
        List<String> problems = new ArrayList<>();
        enumCodes.forEach((group, codes) -> {
            Set<String> stored = groups().getOrDefault(group, Map.of()).keySet();
            Set<String> missing = new TreeSet<>(codes);
            missing.removeAll(stored);
            Set<String> unknown = new TreeSet<>(stored);
            unknown.removeAll(codes);
            if (!missing.isEmpty()) {
                problems.add(group + " 표에 없음 " + missing);
            }
            if (!unknown.isEmpty()) {
                problems.add(group + " enum 에 없음 " + unknown);
            }
        });
        return problems;
    }

    private Map<String, Map<String, CommonCode>> groups() {
        Map<String, Map<String, CommonCode>> current = byGroup;
        if (current == null) {
            // 서버가 요청을 받기 시작한 뒤 대조가 끝나기 전에 불린 경우
            reload();
            current = byGroup;
        }
        return current;
    }
}
