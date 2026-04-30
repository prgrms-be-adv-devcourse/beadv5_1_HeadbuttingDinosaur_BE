package com.devticket.event.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

class GlobalExceptionHandlerTest {

    private final FaultyController controller = new FaultyController();
    private MockMvc mockMvc;
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        controller.toThrow = null;
    }

    @Test
    @DisplayName("[1] ClientAbortException 발생 시 WARN 로깅, ERROR 미발생")
    void clientAbortException_logsWarnOnly() throws Exception {
        controller.toThrow = new ClientAbortException(new IOException("Broken pipe"));

        mockMvc.perform(get("/test/fault"));

        assertWarnOnly();
    }

    @Test
    @DisplayName("[2] AsyncRequestNotUsableException 발생 시 WARN 로깅, ERROR 미발생")
    void asyncRequestNotUsableException_logsWarnOnly() throws Exception {
        controller.toThrow = new AsyncRequestNotUsableException("flush failed");

        mockMvc.perform(get("/test/fault"));

        assertWarnOnly();
    }

    @Test
    @DisplayName("[3] HttpMessageNotWritableException + IOException(Broken pipe) → WARN")
    void httpMessageNotWritable_withBrokenPipe_logsWarnOnly() throws Exception {
        controller.toThrow = new HttpMessageNotWritableException(
            "write failed", new IOException("Broken pipe"));

        mockMvc.perform(get("/test/fault"));

        assertWarnOnly();
    }

    @Test
    @DisplayName("[4] HttpMessageNotWritableException + ClientAbortException → WARN")
    void httpMessageNotWritable_withClientAbort_logsWarnOnly() throws Exception {
        controller.toThrow = new HttpMessageNotWritableException(
            "write failed", new ClientAbortException());

        mockMvc.perform(get("/test/fault"));

        assertWarnOnly();
    }

    @Test
    @DisplayName("[5] HttpMessageNotWritableException 진짜 직렬화 실패 → ERROR + 500")
    void httpMessageNotWritable_serializationFailure_logsErrorAnd500() throws Exception {
        controller.toThrow = new HttpMessageNotWritableException(
            "serialization fail", new RuntimeException("boom"));

        mockMvc.perform(get("/test/fault"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("COMMON_006"));

        assertErrorOnly();
    }

    @Test
    @DisplayName("[6] catch-all RuntimeException 회귀 → ERROR + 500")
    void runtimeException_fallsThroughToCatchAll() throws Exception {
        controller.toThrow = new RuntimeException("boom");

        mockMvc.perform(get("/test/fault"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("COMMON_006"));

        assertErrorOnly();
    }

    @Test
    @DisplayName("[7] cause 체인 깊이 — RuntimeException 안에 IOException(Broken pipe) → WARN")
    void httpMessageNotWritable_nestedBrokenPipeInChain_logsWarnOnly() throws Exception {
        Throwable deepCause = new IOException("Broken pipe");
        Throwable middle = new RuntimeException("wrap", deepCause);
        controller.toThrow = new HttpMessageNotWritableException("write failed", middle);

        mockMvc.perform(get("/test/fault"));

        assertWarnOnly();
    }

    @Test
    @DisplayName("[8] HttpMessageNotWritableException + IOException(Connection reset by peer) → WARN")
    void httpMessageNotWritable_withConnectionReset_logsWarnOnly() throws Exception {
        controller.toThrow = new HttpMessageNotWritableException(
            "write failed", new IOException("Connection reset by peer"));

        mockMvc.perform(get("/test/fault"));

        assertWarnOnly();
    }

    @Test
    @DisplayName("[9] case-insensitive 매칭 — IOException(CONNECTION RESET) → WARN")
    void httpMessageNotWritable_withUpperCaseMessage_logsWarnOnly() throws Exception {
        controller.toThrow = new HttpMessageNotWritableException(
            "write failed", new IOException("CONNECTION RESET"));

        mockMvc.perform(get("/test/fault"));

        assertWarnOnly();
    }

    private void assertWarnOnly() {
        assertThat(countByLevel(Level.WARN)).as("WARN count").isEqualTo(1);
        assertThat(countByLevel(Level.ERROR)).as("ERROR count").isZero();
    }

    private void assertErrorOnly() {
        assertThat(countByLevel(Level.ERROR)).as("ERROR count").isEqualTo(1);
        assertThat(countByLevel(Level.WARN)).as("WARN count").isZero();
    }

    private long countByLevel(Level level) {
        return appender.list.stream().filter(e -> e.getLevel() == level).count();
    }

    @RestController
    static class FaultyController {
        Exception toThrow;

        @GetMapping("/test/fault")
        public String fault() throws Exception {
            if (toThrow != null) {
                throw toThrow;
            }
            return "ok";
        }
    }
}
