package me.jsjlog.blog.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.springframework.test.context.DynamicPropertyRegistry;

/** CI와 로컬의 전용 MySQL에서만 실행한다. 운영 JDBC URL은 입력받지 않는다. */
public final class MySqlTestDatabase {
    private MySqlTestDatabase() {}

    public static boolean enabled() {
        return System.getenv("BLOG_TEST_MYSQL_PORT") != null;
    }

    public static void configure(DynamicPropertyRegistry properties, String name) {
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        if (!enabled()) {
            properties.add("spring.datasource.url", () -> "jdbc:h2:mem:" + name
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
            return;
        }
        String schema = prepare(name);
        properties.add("spring.datasource.url", () -> url(schema));
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        properties.add("spring.datasource.username", MySqlTestDatabase::username);
        properties.add("spring.datasource.password", MySqlTestDatabase::password);
    }

    public static String prepare(String name) {
        if (!name.matches("[a-z][a-z0-9_]{0,45}")) throw new IllegalArgumentException("Invalid test database name");
        String schema = "blog_ci_test_" + name;
        try (Connection connection = connect(""); var statement = connection.createStatement()) {
            if (!"MySQL".equals(connection.getMetaData().getDatabaseProductName()))
                throw new IllegalStateException("MySQL verification requires an actual MySQL server");
            statement.execute("CREATE DATABASE IF NOT EXISTS `" + schema
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            System.out.println("MySQL verification: " + connection.getMetaData().getDatabaseProductVersion() + " / " + schema);
            return schema;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot prepare the isolated MySQL test database", exception);
        }
    }

    public static Connection connect(String schema) throws SQLException {
        return DriverManager.getConnection(url(schema), username(), password());
    }

    public static String url(String schema) {
        if (!schema.isEmpty() && !schema.matches("blog_ci_test_[a-z][a-z0-9_]{0,45}"))
            throw new IllegalArgumentException("Only isolated test schemas are allowed");
        String port = System.getenv("BLOG_TEST_MYSQL_PORT");
        if (port == null || !port.matches("[0-9]{4,5}") || Integer.parseInt(port) < 1024 || Integer.parseInt(port) > 65535)
            throw new IllegalArgumentException("BLOG_TEST_MYSQL_PORT must be an explicit local test port");
        return "jdbc:mysql://127.0.0.1:" + port + "/" + schema + "?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=Asia/Seoul";
    }

    public static String username() {
        return System.getenv().getOrDefault("BLOG_TEST_MYSQL_USERNAME", "root");
    }

    public static String password() {
        String password = System.getenv("BLOG_TEST_MYSQL_PASSWORD");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("A test MySQL password is required");
        return password;
    }
}
