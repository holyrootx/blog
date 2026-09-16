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

        public static final String CSRF = "/api/v1/admin/auth/csrf";
        public static final String LOGIN = "/api/v1/admin/auth/login";
        public static final String LOGOUT = "/api/v1/admin/auth/logout";

        private Auth() {
        }
    }

    public static final class Admin {

        public static final String ALL = "/api/v1/admin/**";

        private Admin() {
        }
    }
}
