package com.ironwill.core.client;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AgentClient {

    @Value("${app.agent.base-url}")
    private String agentBaseUrl;

    @Value("${app.agent.internal-secret}")
    private String internalSecret;

    private final WebClient.Builder webClientBuilder;

    @Data
    public static class AgentRequest {
        private String request_id;
        private String user_id;
        private String goal_id;
        private Map<String, Object> goal_context;
        private Map<String, Object> criteria;
        private String proof_url;
        private String timezone;
        private String current_time_local;
        private String user_context_summary;
    }

    @Data
    public static class AgentResponse {
        private String verdict;
        private String remarks;
        private Map<String, Object> extracted_metrics;
        private Double score_impact;
        private Double confidence;
        private Integer processing_time_ms;
    }

    public AgentResponse audit(AgentRequest req) {
        return webClientBuilder.build()
                .post()
                .uri(agentBaseUrl + "/internal/judge/audit")
                .header("X-Internal-Secret", internalSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .bodyToMono(AgentResponse.class)
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(ex -> {
                    // Treat errors as technical difficulty; caller decides penalty
                    return Mono.empty();
                })
                .block();
    }
}

