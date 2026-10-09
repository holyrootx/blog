package me.jsjlog.blog.common.code;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

/**
 * {@link CommonCodeType} 을 구현한 enum 을 모두 찾아 그룹별 코드 목록으로 만든다.
 *
 * <p>목록을 손으로 적지 않고 찾는다. 새 enum 을 만들고 여기 등록을 잊으면 대조에서 빠져,
 * 이름 없는 코드가 조용히 배포된다.</p>
 */
public final class CommonCodeEnums {

    private static final String BASE_PACKAGE = "me.jsjlog.blog";

    private static volatile Map<String, Set<String>> codesByGroup;

    private CommonCodeEnums() {
    }

    /** 그룹 → 그 그룹 enum 의 코드들. 한 번 찾은 뒤에는 같은 결과를 쓴다 */
    public static Map<String, Set<String>> codesByGroup() {
        Map<String, Set<String>> found = codesByGroup;
        if (found == null) {
            found = scan();
            codesByGroup = found;
        }
        return found;
    }

    /** 이 코드가 enum 에 있는가. 있으면 동작이 걸린 코드라 화면에서 추가·삭제할 수 없다 */
    public static boolean isManagedByEnum(String groupCode, String code) {
        return codesByGroup().getOrDefault(groupCode, Set.of()).contains(code);
    }

    /** 이 그룹을 enum 이 쓰는가. 쓰면 코드 목록은 enum 이 정한다(서버 시작 때 대조) */
    public static boolean isEnumGroup(String groupCode) {
        return codesByGroup().containsKey(groupCode);
    }

    private static Map<String, Set<String>> scan() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(CommonCodeType.class));

        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> type = ClassUtils.resolveClassName(candidate.getBeanClassName(), CommonCodeEnums.class.getClassLoader());
            if (!type.isEnum()) {
                continue;
            }
            CommonCodeType[] constants = (CommonCodeType[]) type.getEnumConstants();
            if (constants.length == 0) {
                continue;
            }
            String group = constants[0].groupCode();
            if (result.containsKey(group)) {
                throw new IllegalStateException("공통 코드 그룹 " + group + " 을 enum 두 개가 같이 쓰고 있습니다: " + type.getName());
            }
            Set<String> codes = new LinkedHashSet<>();
            Arrays.stream(constants).map(CommonCodeType::code).forEach(codes::add);
            result.put(group, Collections.unmodifiableSet(codes));
        }
        return Collections.unmodifiableMap(result);
    }
}
