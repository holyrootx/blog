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

        private Comment() {
        }
    }
}
