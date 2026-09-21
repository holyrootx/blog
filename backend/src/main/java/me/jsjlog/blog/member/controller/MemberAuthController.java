package me.jsjlog.blog.member.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.response.ApiResponse;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.dto.MemberSessionResponse;
import me.jsjlog.blog.member.dto.MyCommentResponse;
import me.jsjlog.blog.member.dto.NicknameUpdateRequest;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.member.service.MemberService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인한 회원이 자기 계정을 보고 고치는 자리.
 *
 * <p>로그아웃은 여기에 없다. Spring Security 의 LogoutFilter 가 받는다
 * ({@code SecurityConfig} 의 logout 설정). 세션 무효화와 컨텍스트 비우기를 직접 하면
 * 프레임워크가 하는 일을 반쪽만 따라 하게 된다.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class MemberAuthController {

    private final MemberService memberService;
    private final MemberRepository memberRepository;

    /**
     * 지금 로그인한 회원. 인증 없이 열어 둔다.
     *
     * <p>공개 화면은 로그인하지 않은 사람이 보는 것이 정상이라, 그 경우를 401 로 돌려주면
     * 방문자가 글을 읽을 때마다 오류가 한 건씩 쌓이고 세션 만료 처리까지 딸려 나간다.
     * "아무도 아님" 은 오류가 아니므로 {@code data: null} 로 답한다.</p>
     *
     * <p><b>principal 이 아니라 DB 에서 읽는다.</b> principal 은 로그인하던 순간의 사본이라
     * 닉네임을 바꿔도 그대로다. 그대로 쓰면 바꾼 이름이 화면에 보이다가 새로고침하는 순간
     * 옛 이름으로 돌아간다. 로그인한 사람에게만 나가는 조회라 비용도 크지 않다.</p>
     */
    @GetMapping("/me")
    public ApiResponse<MemberSessionResponse> me(@AuthenticationPrincipal MemberPrincipal principal) {
        if (principal == null) {
            return ApiResponse.ok(null);
        }

        return ApiResponse.ok(
                memberRepository
                        .findById(principal.getId())
                        .map(MemberSessionResponse::from)
                        // 세션은 살아 있는데 회원 행이 사라진 경우다. 로그인하지 않은 것으로 다룬다
                        .orElse(null)
        );
    }

    /** 가입 화면에서 "나중에 바꿀 수 있습니다" 라고 알린 그 기능 */
    @PutMapping("/me/nickname")
    public ApiResponse<MemberSessionResponse> changeNickname(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody NicknameUpdateRequest request
    ) {
        Member member = memberService.changeNickname(principal.getId(), request.nickname());

        return ApiResponse.ok("닉네임을 변경했습니다.", MemberSessionResponse.from(member));
    }

    @GetMapping("/me/comments")
    public ApiResponse<List<MyCommentResponse>> myComments(
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        return ApiResponse.ok(memberService.findMyComments(principal.getId()));
    }

    /**
     * 탈퇴.
     *
     * <p>끝나면 세션을 끊는다. 남겨 두면 탈퇴한 사람이 그대로 로그인 상태로 돌아다니고,
     * 다음 요청에서 이미 {@code WITHDRAWN} 인 회원으로 댓글을 쓸 수 있다.</p>
     */
    @PostMapping("/withdraw")
    public ApiResponse<Void> withdraw(
            @AuthenticationPrincipal MemberPrincipal principal,
            HttpServletRequest request
    ) {
        memberService.withdraw(principal.getId());

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        SecurityContextHolder.clearContext();

        return ApiResponse.ok();
    }
}
