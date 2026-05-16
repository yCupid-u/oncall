package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.RagRerankProperties;
import org.example.service.VectorSearchService.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DashScopeRerankService {

    private static final Logger logger = LoggerFactory.getLogger(DashScopeRerankService.class);

    private final RagRerankProperties properties;
    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DashScopeRerankService(
            RagRerankProperties properties,
            @Value("${dashscope.api.key}") String apiKey,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
    }

    public List<SearchResult> rerank(String query, List<SearchResult> candidates, int topK) {
        int resultLimit = Math.min(Math.max(topK, 1), candidates.size());

        if (!properties.isEnabled() || candidates.isEmpty()) {
            return limit(candidates, resultLimit);
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", properties.getModel());
            payload.put("query", query);
            payload.put("documents", candidates.stream()
                    .map(SearchResult::getContent)
                    .map(content -> content == null ? "" : content)
                    .toList());
            payload.put("top_n", resultLimit);

            if (StringUtils.hasText(properties.getInstruct())) {
                payload.put("instruct", properties.getInstruct());
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getEndpoint()))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logger.warn("DashScope rerank failed, status: {}, body: {}", response.statusCode(), abbreviate(response.body()));
                return limit(candidates, resultLimit);
            }

            JsonNode results = extractResults(objectMapper.readTree(response.body()));
            if (!results.isArray()) {
                logger.warn("DashScope rerank response does not contain results: {}", abbreviate(response.body()));
                return limit(candidates, resultLimit);
            }

            List<SearchResult> reranked = new ArrayList<>();
            Set<Integer> usedIndexes = new HashSet<>();

            for (JsonNode resultNode : results) {
                int index = resultNode.path("index").asInt(-1);
                if (index < 0 || index >= candidates.size() || usedIndexes.contains(index)) {
                    continue;
                }

                SearchResult result = candidates.get(index);
                JsonNode scoreNode = resultNode.path("relevance_score");
                if (scoreNode.isMissingNode()) {
                    scoreNode = resultNode.path("score");
                }
                if (scoreNode.isNumber()) {
                    result.setRerankScore((float) scoreNode.asDouble());
                }

                reranked.add(result);
                usedIndexes.add(index);

                if (reranked.size() >= resultLimit) {
                    break;
                }
            }

            fillMissingCandidates(reranked, candidates, usedIndexes, resultLimit);
            logger.info("DashScope rerank completed, candidates: {}, returned: {}", candidates.size(), reranked.size());
            return reranked;

        } catch (Exception e) {
            logger.warn("DashScope rerank failed, fallback to vector search order", e);
            return limit(candidates, resultLimit);
        }
    }

    private JsonNode extractResults(JsonNode root) {
        JsonNode openAiCompatibleResults = root.path("results");
        if (openAiCompatibleResults.isArray()) {
            return openAiCompatibleResults;
        }
        return root.path("output").path("results");
    }

    private void fillMissingCandidates(
            List<SearchResult> reranked,
            List<SearchResult> candidates,
            Set<Integer> usedIndexes,
            int resultLimit) {
        for (int i = 0; i < candidates.size() && reranked.size() < resultLimit; i++) {
            if (!usedIndexes.contains(i)) {
                reranked.add(candidates.get(i));
            }
        }
    }

    private List<SearchResult> limit(List<SearchResult> results, int topK) {
        if (results.size() <= topK) {
            return results;
        }
        return new ArrayList<>(results.subList(0, topK));
    }

    private String abbreviate(String value) {
        if (value == null || value.length() <= 500) {
            return value;
        }
        return value.substring(0, 500) + "...";
    }
}
