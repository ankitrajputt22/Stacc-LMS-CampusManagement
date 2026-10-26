package com.stacc.backend.lms.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stacc.backend.lms.ai.AiServiceException.Reason;
import com.stacc.backend.lms.ai.AiServiceMessages.AskRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.AskResult;
import com.stacc.backend.lms.ai.AiServiceMessages.IndexSource;
import com.stacc.backend.lms.ai.AiServiceMessages.ReplaceIndexRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.util.unit.DataSize;

/** The AI client against a real local HTTP server standing in for the AI service: no AI service or model runs. */
class HttpAiServiceClientTest {

    private static final String TOKEN = "internal-token-for-tests-0123456789abcdef";
    private static final String COURSE_KEY = AiCourseKeys.forLmsCourse(7);
    private static final String FINGERPRINT = "a".repeat(64);

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastToken = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private interface Reply {
        void send(HttpExchange exchange) throws IOException;
    }

    private String start(Reply reply) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            lastToken.set(exchange.getRequestHeaders().getFirst("X-Stacc-AI-Token"));
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply.send(exchange);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static AiProperties properties(boolean enabled, String url, String token, Duration readTimeout) {
        return new AiProperties(enabled, url, token, Duration.ofSeconds(1), readTimeout, readTimeout, 100,
                DataSize.ofMegabytes(40), Duration.ofMinutes(30));
    }

    private static HttpAiServiceClient client(String url) {
        return new HttpAiServiceClient(properties(true, url, TOKEN, Duration.ofSeconds(2)));
    }

    private static AskRequest question() {
        return new AskRequest(FINGERPRINT, "What does third normal form remove?");
    }

    @Test
    void disabledAiCallsNothing() {
        HttpAiServiceClient disabled = new HttpAiServiceClient(properties(false, "http://127.0.0.1:1", TOKEN, Duration.ofSeconds(1)));
        assertThat(disabled.isEnabled()).isFalse();
        assertThat(disabled.isReachable()).isFalse();
        assertThatThrownBy(() -> disabled.ask(COURSE_KEY, question()))
                .isInstanceOfSatisfying(AiServiceException.class, e -> assertThat(e.reason()).isEqualTo(Reason.DISABLED));
    }

    @Test
    void enabledWithoutAUsableTokenOrAddressStaysDisabled() {
        assertThat(new HttpAiServiceClient(properties(true, "http://ai:8000", "short", Duration.ofSeconds(1))).isEnabled()).isFalse();
        assertThat(new HttpAiServiceClient(properties(true, "not a url", TOKEN, Duration.ofSeconds(1))).isEnabled()).isFalse();
        assertThat(new HttpAiServiceClient(properties(true, "file:///etc", TOKEN, Duration.ofSeconds(1))).isEnabled()).isFalse();
        assertThat(properties(true, "http://ai:8000", TOKEN, Duration.ofSeconds(1)).toString()).doesNotContain(TOKEN);
    }

    @Test
    void successfulAnswerSendsTheInternalTokenAndOnlyTheQuestion() throws IOException {
        String url = start(exchange -> json(exchange, 200, """
                {"answer": "It removes transitive dependencies.", "insufficientContext": false,
                 "citations": [{"sourceType": "TOPIC", "sourceId": 3, "topicId": 3, "title": "Normal forms",
                                "fileName": null, "pageNumber": null, "slideNumber": null}]}
                """));
        AskResult result = client(url).ask(COURSE_KEY, question());
        assertThat(result.answer()).isEqualTo("It removes transitive dependencies.");
        assertThat(result.citations()).hasSize(1);
        assertThat(lastPath.get()).isEqualTo("/internal/indexes/" + COURSE_KEY + "/ask");
        assertThat(lastToken.get()).isEqualTo(TOKEN);
        assertThat(lastAuthorization.get()).isNull();
        // The personal-data boundary: the question and the fingerprint, nothing about the student.
        assertThat(lastBody.get()).isEqualTo(
                "{\"indexFingerprint\":\"" + FINGERPRINT + "\",\"question\":\"What does third normal form remove?\"}");
    }

    @Test
    void indexReplacementCarriesOnlyPublishedContent() throws IOException {
        String url = start(exchange -> json(exchange, 200,
                "{\"indexedSourceCount\": 2, \"skippedSourceCount\": 0, \"chunkCount\": 4, \"contentFingerprint\": \"" + FINGERPRINT + "\"}"));
        var result = client(url).replaceIndex(COURSE_KEY, new ReplaceIndexRequest(FINGERPRINT, List.of(
                IndexSource.topic(1, "Normal forms", "Third normal form..."),
                IndexSource.material(5, 1, "Notes", "notes.pdf", "PDF", "JVBERi0="))));
        assertThat(result.indexedSourceCount()).isEqualTo(2);
        assertThat(lastBody.get())
                .contains("\"sourceType\":\"TOPIC\"", "\"sourceType\":\"STUDY_MATERIAL\"", "notes.pdf")
                .doesNotContain("objectKey", "lms/courses", "loginId", "studentProfile", "grade", "attendance");
    }

    @Test
    void unreachableServiceIsUnavailable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        HttpAiServiceClient client = client("http://127.0.0.1:" + closedPort);
        assertThat(client.isReachable()).isFalse();
        assertThatThrownBy(() -> client.ask(COURSE_KEY, question()))
                .isInstanceOfSatisfying(AiServiceException.class, e -> assertThat(e.reason()).isEqualTo(Reason.UNAVAILABLE));
    }

    @Test
    void slowServiceTimesOut() throws IOException {
        String url = start(exchange -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            json(exchange, 200, "{}");
        });
        HttpAiServiceClient client = new HttpAiServiceClient(properties(true, url, TOKEN, Duration.ofMillis(300)));
        long started = System.nanoTime();
        assertThatThrownBy(() -> client.ask(COURSE_KEY, question()))
                .isInstanceOfSatisfying(AiServiceException.class, e -> assertThat(e.reason()).isEqualTo(Reason.UNAVAILABLE));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(1800));
    }

    @Test
    void malformedResponsesAreBadResponses() throws IOException {
        String url = start(exchange -> json(exchange, 200, "this is not json"));
        assertThatThrownBy(() -> client(url).ask(COURSE_KEY, question()))
                .isInstanceOfSatisfying(AiServiceException.class, e -> assertThat(e.reason()).isEqualTo(Reason.BAD_RESPONSE));
    }

    @ParameterizedTest
    @CsvSource({
        "404, INDEX_NOT_FOUND, INDEX_NOT_READY",
        "409, INDEX_MISMATCH, INDEX_NOT_READY",
        "422, NO_INDEXABLE_CONTENT, NO_CONTENT",
        "502, MODEL_OUTPUT_INVALID, BAD_RESPONSE",
        "503, PROVIDER_UNAVAILABLE, UNAVAILABLE",
        "500, INTERNAL_ERROR, UNAVAILABLE",
        "401, UNAUTHORIZED, UNAVAILABLE",
        "400, INVALID_REQUEST, BAD_RESPONSE",
        "404, SOMETHING_ELSE, BAD_RESPONSE",
    })
    void errorsMapToSafeReasons(int status, String code, Reason expected) throws IOException {
        String url = start(exchange -> json(exchange, status,
                "{\"code\": \"" + code + "\", \"message\": \"internal detail at /data/indexes/x\"}"));
        assertThatThrownBy(() -> client(url).ask(COURSE_KEY, question()))
                .isInstanceOfSatisfying(AiServiceException.class, e -> {
                    assertThat(e.reason()).isEqualTo(expected);
                    assertThat(e.getMessage()).doesNotContain("/data/indexes");
                });
    }

    @Test
    void healthCheckUsesTheToken() throws IOException {
        String url = start(exchange -> json(exchange, 200, "{\"status\": \"ok\", \"provider\": \"fake\"}"));
        assertThat(client(url).isReachable()).isTrue();
        assertThat(lastPath.get()).isEqualTo("/internal/health");
        assertThat(lastToken.get()).isEqualTo(TOKEN);
    }

    @Test
    void courseKeysAreOpaqueAndStable() {
        assertThat(AiCourseKeys.forLmsCourse(7)).matches("[0-9a-f]{32}").isEqualTo(COURSE_KEY)
                .isNotEqualTo(AiCourseKeys.forLmsCourse(8));
    }
}
