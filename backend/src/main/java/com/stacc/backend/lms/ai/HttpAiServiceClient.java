package com.stacc.backend.lms.ai;

import com.stacc.backend.lms.ai.AiServiceException.Reason;
import com.stacc.backend.lms.ai.AiServiceMessages.AskRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.AskResult;
import com.stacc.backend.lms.ai.AiServiceMessages.AssessmentDraftRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.AssessmentDraftResult;
import com.stacc.backend.lms.ai.AiServiceMessages.PracticeRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.PracticeResult;
import com.stacc.backend.lms.ai.AiServiceMessages.ReplaceIndexRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.ReplaceIndexResult;
import com.stacc.backend.lms.ai.AiServiceMessages.SummaryRequest;
import com.stacc.backend.lms.ai.AiServiceMessages.SummaryResult;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls the AI service over HTTP with the shared internal token, never a user's access token. Every call has a
 * connect and a read timeout, so a slow model can never hold a request forever; a knowledge refresh gets a longer
 * read timeout than a question. Failures become {@link AiServiceException}s; the log names only the kind of failure,
 * never the question, course text, or token.
 */
@Component
public class HttpAiServiceClient implements AiServiceClient {

    static final String TOKEN_HEADER = "X-Stacc-AI-Token";
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpAiServiceClient.class);
    private static final Pattern ERROR_CODE = Pattern.compile("\"code\"\\s*:\\s*\"([A-Z_]{1,40})\"");
    private static final int MAX_ERROR_BYTES = 4096;

    private final AiProperties properties;
    private final RestClient client;
    private final RestClient syncClient;

    public HttpAiServiceClient(AiProperties properties) {
        this.properties = properties;
        if (properties.usable()) {
            this.client = build(properties.readTimeout());
            this.syncClient = build(properties.syncReadTimeout());
        } else {
            this.client = null;
            this.syncClient = null;
            if (properties.enabled()) {
                LOGGER.warn("AI_ENABLED is true, but AI_SERVICE_URL or STACC_AI_INTERNAL_TOKEN (at least {} characters) "
                        + "is missing or invalid. AI learning features stay unavailable.", AiProperties.MIN_TOKEN_LENGTH);
            }
        }
    }

    private RestClient build(Duration readTimeout) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                // Plain HTTP/1.1 inside the private network: no upgrade attempts, no redirects.
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(properties.serviceUrl())
                .defaultHeader(TOKEN_HEADER, properties.internalToken())
                .build();
    }

    @Override
    public boolean isEnabled() {
        return client != null;
    }

    @Override
    public boolean isReachable() {
        if (client == null) {
            return false;
        }
        try {
            client.get().uri("/internal/health").retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** Says once, after startup, whether the configured AI service answers. It never delays or stops the startup. */
    @EventListener(ApplicationReadyEvent.class)
    void reportReachability() {
        if (client != null) {
            Thread.ofVirtual().name("ai-service-check").start(() -> {
                if (isReachable()) {
                    LOGGER.info("The AI service is reachable.");
                } else {
                    LOGGER.warn("The AI service is not reachable. AI learning features answer 503 until it is.");
                }
            });
        }
    }

    @Override
    public ReplaceIndexResult replaceIndex(String courseKey, ReplaceIndexRequest request) {
        return post(syncClient, courseKey, "replace", request, ReplaceIndexResult.class);
    }

    @Override
    public AskResult ask(String courseKey, AskRequest request) {
        return post(client, courseKey, "ask", request, AskResult.class);
    }

    @Override
    public SummaryResult summarize(String courseKey, SummaryRequest request) {
        return post(client, courseKey, "summary", request, SummaryResult.class);
    }

    @Override
    public PracticeResult practice(String courseKey, PracticeRequest request) {
        return post(client, courseKey, "practice", request, PracticeResult.class);
    }

    @Override
    public AssessmentDraftResult assessmentDraft(String courseKey, AssessmentDraftRequest request) {
        return post(client, courseKey, "assessment-draft", request, AssessmentDraftResult.class);
    }

    private <T> T post(RestClient restClient, String courseKey, String action, Object body, Class<T> type) {
        if (restClient == null) {
            throw new AiServiceException(Reason.DISABLED);
        }
        if (courseKey == null || !courseKey.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("courseKey must come from AiCourseKeys");
        }
        try {
            T result = restClient.post()
                    .uri("/internal/indexes/{courseKey}/{action}", courseKey, action)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw failure(action, response.getStatusCode(), errorCode(response));
                    })
                    .body(type);
            if (result == null) {
                throw new AiServiceException(Reason.BAD_RESPONSE);
            }
            return result;
        } catch (AiServiceException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            LOGGER.warn("The AI service could not be reached for {}: {}", action, exception.getClass().getSimpleName());
            throw new AiServiceException(Reason.UNAVAILABLE, exception);
        } catch (RestClientException exception) {
            if (exception.getCause() instanceof AiServiceException cause) {
                throw cause;
            }
            LOGGER.warn("The AI service gave an unreadable answer for {}", action);
            throw new AiServiceException(Reason.BAD_RESPONSE, exception);
        }
    }

    private static AiServiceException failure(String action, HttpStatusCode status, String code) {
        int value = status.value();
        if ((value == 404 && "INDEX_NOT_FOUND".equals(code)) || (value == 409 && "INDEX_MISMATCH".equals(code))) {
            return new AiServiceException(Reason.INDEX_NOT_READY);
        }
        if (value == 422 && "NO_INDEXABLE_CONTENT".equals(code)) {
            return new AiServiceException(Reason.NO_CONTENT);
        }
        if (value == 502) {
            return new AiServiceException(Reason.BAD_RESPONSE);
        }
        if (value == 401) {
            LOGGER.warn("The AI service refused the internal token. Check STACC_AI_INTERNAL_TOKEN on both services.");
            return new AiServiceException(Reason.UNAVAILABLE);
        }
        if (status.is4xxClientError()) {
            LOGGER.warn("The AI service refused the {} request with {} {}", action, value, code);
            return new AiServiceException(Reason.BAD_RESPONSE);
        }
        LOGGER.warn("The AI service failed the {} request with {} {}", action, value, code);
        return new AiServiceException(Reason.UNAVAILABLE);
    }

    private static String errorCode(ClientHttpResponse response) {
        try (InputStream body = response.getBody()) {
            String text = new String(body.readNBytes(MAX_ERROR_BYTES), StandardCharsets.UTF_8);
            Matcher matcher = ERROR_CODE.matcher(text);
            return matcher.find() ? matcher.group(1) : "UNKNOWN";
        } catch (IOException exception) {
            return "UNKNOWN";
        }
    }
}
