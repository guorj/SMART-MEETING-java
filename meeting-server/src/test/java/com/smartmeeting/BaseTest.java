package com.smartmeeting;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.testcontainers.containers.MySQLContainer;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.repository.TodoMapper;
import com.smartmeeting.repository.TranscriptMapper;

/**
 * 集成测试基类：
 * <ul>
 *   <li>默认尝试 Testcontainers 独立 MySQL，避免误连 dev 库。</li>
 *   <li>无 Docker 时自动回退到内存 H2 + {@code classpath:test-schema-h2.sql}。</li>
 *   <li>也可显式指定 {@code SMART_MEETING_TEST_JDBC_URL}（及可选 USER/PASSWORD）或 JVM 参数
 *       {@code -Dtest.jdbc.url=} 指向<strong>仅用于测试</strong>的 MySQL。</li>
 *   <li>使用 {@code @Transactional} 每测回滚，不再全表 delete。</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
@Transactional
public abstract class BaseTest {

    private static final String EXTERNAL_JDBC_URL = resolveExternalJdbcUrl();
    private static final boolean H2_FALLBACK;
    private static final MySQLContainer<?> MYSQL;

    static {
        if (StringUtils.hasText(EXTERNAL_JDBC_URL)) {
            MYSQL = null;
            H2_FALLBACK = false;
        } else {
            MySQLContainer<?> started = null;
            boolean h2 = false;
            try {
                started = new MySQLContainer<>("mysql:8.0")
                        .withDatabaseName("smart_meeting_junit")
                        .withUsername("junit")
                        .withPassword("junit");
                started.start();
            } catch (Throwable t) {
                h2 = true;
                if (started != null) {
                    try {
                        started.stop();
                    } catch (Exception ignored) {
                        // ignore
                    }
                }
                started = null;
                LoggerFactory.getLogger(BaseTest.class).warn(
                        "Testcontainers MySQL unavailable ({}: {}), falling back to in-memory H2.",
                        t.getClass().getSimpleName(), t.getMessage());
            }
            MYSQL = started;
            H2_FALLBACK = h2;
        }
    }

    private static String resolveExternalJdbcUrl() {
        String p = System.getProperty("test.jdbc.url");
        if (StringUtils.hasText(p)) {
            return p.trim();
        }
        String e = System.getenv("SMART_MEETING_TEST_JDBC_URL");
        return StringUtils.hasText(e) ? e.trim() : "";
    }

    @DynamicPropertySource
    static void registerTestDatasource(DynamicPropertyRegistry registry) {
        if (StringUtils.hasText(EXTERNAL_JDBC_URL)) {
            registry.add("spring.datasource.url", () -> EXTERNAL_JDBC_URL);
            registry.add("spring.datasource.username", BaseTest::externalUsername);
            registry.add("spring.datasource.password", BaseTest::externalPassword);
            registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
            return;
        }
        if (H2_FALLBACK) {
            registry.add("spring.datasource.url", () ->
                    "jdbc:h2:mem:smart_meeting_junit;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=FALSE");
            registry.add("spring.datasource.username", () -> "sa");
            registry.add("spring.datasource.password", () -> "");
            registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
            registry.add("spring.sql.init.mode", () -> "always");
            registry.add("spring.sql.init.schema-locations", () -> "classpath:test-schema-h2.sql");
            return;
        }
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    private static String externalUsername() {
        String p = System.getProperty("test.jdbc.username");
        if (StringUtils.hasText(p)) {
            return p.trim();
        }
        String e = System.getenv("SMART_MEETING_TEST_JDBC_USER");
        if (StringUtils.hasText(e)) {
            return e.trim();
        }
        return "root";
    }

    private static String externalPassword() {
        if (System.getProperty("test.jdbc.password") != null) {
            return System.getProperty("test.jdbc.password");
        }
        if (System.getenv("SMART_MEETING_TEST_JDBC_PASSWORD") != null) {
            return System.getenv("SMART_MEETING_TEST_JDBC_PASSWORD");
        }
        return "";
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected MeetingMapper meetingMapper;

    @Autowired
    protected ParticipantMapper participantMapper;

    @Autowired
    protected TodoMapper todoMapper;

    @Autowired
    protected TranscriptMapper transcriptMapper;
}
