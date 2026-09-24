package me.jsjlog.blog.common.config;

public final class ApiPaths {

    public static final String API_ALL = "/api/**";

    private ApiPaths() {
    }

    public static final class Public {

        public static final String HEALTH = "/api/health";
        public static final String BLOG_ALL = "/api/v1/blog/**";

        private Public() {
        }
    }

    public static final class Auth {

        public static final String CSRF = "/api/v1/auth/csrf";
        public static final String LOGIN = "/api/v1/admin/auth/login";
        public static final String LOGOUT = "/api/v1/admin/auth/logout";
        /** 회원 세션 조회. 로그인하지 않은 사람도 부르는 자리라 인증 없이 열어 둔다 */
        public static final String MEMBER_ME = "/api/v1/auth/me";
        public static final String MEMBER_LOGOUT = "/api/v1/auth/logout";
        public static final String MEMBER_NICKNAME = "/api/v1/auth/me/nickname";
        /** 알림은 하위 경로가 여럿이라 묶어서 건다. 전부 로그인이 필요하다 */
        public static final String MEMBER_NOTIFICATIONS = "/api/v1/auth/me/notifications";
        public static final String MEMBER_NOTIFICATIONS_ALL = "/api/v1/auth/me/notifications/**";
        public static final String MEMBER_COMMENTS = "/api/v1/auth/me/comments";
        public static final String MEMBER_WITHDRAW = "/api/v1/auth/withdraw";
        public static final String OAUTH_PENDING = "/api/v1/auth/oauth/pending";
        public static final String OAUTH_SIGNUP = "/api/v1/auth/oauth/signup";
        public static final String OAUTH_REACTIVATE = "/api/v1/auth/oauth/reactivate";
        public static final String OAUTH_REJOIN = "/api/v1/auth/oauth/rejoin";
        public static final String OAUTH2_AUTHORIZATION_ALL = "/oauth2/**";
        public static final String OAUTH2_CALLBACK_ALL = "/login/oauth2/**";

        private Auth() {
        }
    }

    public static final class Admin {

        public static final String ALL = "/api/v1/admin/**";

        private Admin() {
        }
    }

    public static final class Comment {

        public static final String CREATE = "/api/v1/blog/posts/*/comments";
        public static final String REACTION = "/api/v1/blog/comments/*/reaction";
        public static final String REPORT = "/api/v1/blog/comments/*/reports";

        /**
         * 댓글 하나를 가리키는 자리. 내 댓글을 고치거나 지울 때 쓴다.
         *
         * <p>별 하나는 경로 한 마디만 받으므로 {@code .../reaction} 이나 {@code .../reports}
         * 와 겹치지 않는다.</p>
         */
        public static final String ITEM = "/api/v1/blog/comments/*";

        private Comment() {
        }
    }

    public static final class Post {

        public static final String REACTION = "/api/v1/blog/posts/*/reaction";

        private Post() {
        }
    }
}
