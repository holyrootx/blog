package me.jsjlog.blog.member.controller;

import me.jsjlog.blog.common.response.ApiResponse;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.dto.MemberSessionResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인한 회원이 누구인지 알려 준다.
 *
 * <p>화면을 새로 열면 세션 쿠키만 남고 화면이 들고 있던 값은 사라진다. 그때 여기에 물어본다.</p>
 *
 * <p>로그아웃은 컨트롤러가 아니라 Spring Security 의 LogoutFilter 가 받는다
 * ({@code SecurityConfig} 의 logout 설정). 세션 무효화와 컨텍스트 비우기를 직접 하면
 * 프레임워크가 하는 일을 반쪽만 따라 하게 된다.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class MemberAuthController {

    /**
     * 인증 없이 열어 둔다.
     *
     * <p>공개 화면은 로그인하지 않은 사람이 보는 것이 정상이라, 그 경우를 401 로 돌려주면
     * 방문자가 글을 읽을 때마다 오류가 한 건씩 쌓이고 세션 만료 처리까지 딸려 나간다.
     * "아무도 아님" 은 오류가 아니므로 {@code data: null} 로 답한다.</p>
     *
     * <p>관리자가 이 경로를 불러도 자기 정보가 나온다. 관리자도 회원이기 때문이다 —
     * 다만 아이디·비밀번호로 들어온 세션은 principal 이 달라 null 이 나갈 수 있다.</p>
     */
    @GetMapping("/me")
    public ApiResponse<MemberSessionResponse> me(@AuthenticationPrincipal MemberPrincipal principal) {
        return ApiResponse.ok(principal == null ? null : MemberSessionResponse.from(principal));
    }
}
