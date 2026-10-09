package me.jsjlog.blog.common.code;

/**
 * 공통 코드와 짝을 이루는 enum 이 구현한다.
 *
 * <p>값마다 동작이 다른 코드(예: 탈퇴면 로그인을 막는다)는 코드가 그 값을 알아야 해서 enum 으로 둔다.
 * 화면에 보일 이름·순서·설명은 enum 에 넣지 않고 공통 코드 표({@code common_code})에서 가져온다 —
 * 두 곳에 이름을 두면 한쪽만 고쳐져 어긋난다.</p>
 *
 * <p>enum 상수 이름이 곧 공통 코드의 {@code code} 다. 같은 코드 값이 다른 그룹에도 있을 수 있어서
 * 그룹은 enum 마다 한 번 적는다. 이 인터페이스를 구현한 enum 은 서버가 뜰 때 공통 코드 표와
 * 대조된다({@link CommonCodes}). 하나라도 어긋나면 서버를 띄우지 않는다.</p>
 */
public interface CommonCodeType {

    /** 이 enum 이 속한 공통 코드 그룹 */
    String groupCode();

    /** 공통 코드의 코드 값. enum 상수 이름과 같다 */
    default String code() {
        return ((Enum<?>) this).name();
    }
}
