package org.example.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.example.config.RagEvalProperties;
import org.example.service.VectorSearchService.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class RagEvalRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(RagEvalRunner.class);

    private final RagEvalProperties properties;
    private final VectorSearchService vectorSearchService;
    private final ObjectMapper objectMapper;

    public RagEvalRunner(
            RagEvalProperties properties,
            VectorSearchService vectorSearchService,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.vectorSearchService = vectorSearchService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!properties.isEnabled()) {
            return;
        }

        Path datasetPath = Path.of(properties.getDataset());
        if (!Files.exists(datasetPath)) {
            String message = "RAG eval dataset not found: " + datasetPath.toAbsolutePath();
            if (properties.isFailFast()) {
                throw new IllegalStateException(message);
            }
            logger.warn(message);
            return;
        }

        List<RagEvalCase> cases = readCases(datasetPath);
        if (cases.isEmpty()) {
            logger.warn("RAG eval dataset is empty: {}", datasetPath.toAbsolutePath());
            return;
        }

        RagEvalReport report = evaluate(cases);
        Path outputPath = Path.of(properties.getOutput());
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), report);
        logger.info("RAG eval completed. cases={}, hit@1={}, hit@{}={}, mrr={}, output={}",
                report.getTotal(),
                format(report.getHitAt1()),
                properties.getTopK(),
                format(report.getHitAtK()),
                format(report.getMrr()),
                outputPath.toAbsolutePath());

        if (properties.isFailFast() && report.getHitAtK() < 0.85) {
            throw new IllegalStateException("RAG eval hit@K below target 0.85: " + format(report.getHitAtK()));
        }
    }

    private List<RagEvalCase> readCases(Path datasetPath) throws Exception {
        List<RagEvalCase> cases = new ArrayList<>();
        for (String line : Files.readAllLines(datasetPath, StandardCharsets.UTF_8)) {
            if (!StringUtils.hasText(line) || line.trim().startsWith("#")) {
                continue;
            }
            cases.add(objectMapper.readValue(line, RagEvalCase.class));
        }
        return cases;
    }

    private RagEvalReport evaluate(List<RagEvalCase> cases) {
        List<RagEvalResult> results = new ArrayList<>();
        int hitAt1Count = 0;
        int hitAtKCount = 0;
        double reciprocalRankSum = 0.0;

        int topK = Math.max(properties.getTopK(), 1);
        for (RagEvalCase evalCase : cases) {
            List<SearchResult> searchResults = vectorSearchService.searchSimilarDocuments(evalCase.getQuery(), topK);
            int rank = firstMatchRank(evalCase, searchResults);
            boolean hitAt1 = rank == 1;
            boolean hitAtK = rank > 0 && rank <= topK;
            if (hitAt1) {
                hitAt1Count++;
            }
            if (hitAtK) {
                hitAtKCount++;
                reciprocalRankSum += 1.0 / rank;
            }

            RagEvalResult result = new RagEvalResult();
            result.setQuery(evalCase.getQuery());
            result.setExpectedSource(evalCase.getExpectedSource());
            result.setExpectedContains(evalCase.getExpectedContains());
            result.setHitRank(rank);
            result.setHitAt1(hitAt1);
            result.setHitAtK(hitAtK);
            result.setReturnedSources(extractReturnedSources(searchResults));
            results.add(result);
        }

        RagEvalReport report = new RagEvalReport();
        report.setTotal(cases.size());
        report.setTopK(topK);
        report.setHitAt1(hitAt1Count / (double) cases.size());
        report.setHitAtK(hitAtKCount / (double) cases.size());
        report.setMrr(reciprocalRankSum / cases.size());
        report.setResults(results);
        return report;
    }

    private int firstMatchRank(RagEvalCase evalCase, List<SearchResult> results) {
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            if (matches(evalCase, result)) {
                return i + 1;
            }
        }
        return 0;
    }

    private boolean matches(RagEvalCase evalCase, SearchResult result) {
        String haystack = ((result.getMetadata() == null ? "" : result.getMetadata()) + "\n"
                + (result.getContent() == null ? "" : result.getContent())).toLowerCase(Locale.ROOT);
        boolean sourceMatches = !StringUtils.hasText(evalCase.getExpectedSource())
                || haystack.contains(evalCase.getExpectedSource().toLowerCase(Locale.ROOT));
        boolean contentMatches = !StringUtils.hasText(evalCase.getExpectedContains())
                || haystack.contains(evalCase.getExpectedContains().toLowerCase(Locale.ROOT));
        return sourceMatches && contentMatches;
    }

    private List<String> extractReturnedSources(List<SearchResult> results) {
        List<String> sources = new ArrayList<>();
        for (SearchResult result : results) {
            sources.add(result.getMetadata());
        }
        return sources;
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    @Data
    public static class RagEvalCase {
        private String query;
        @JsonProperty("expected_source")
        private String expectedSource;
        @JsonProperty("expected_contains")
        private String expectedContains;
    }

    @Data
    public static class RagEvalReport {
        private int total;
        private int topK;
        @JsonProperty("hit_at_1")
        private double hitAt1;
        @JsonProperty("hit_at_k")
        private double hitAtK;
        private double mrr;
        private List<RagEvalResult> results;
    }

    @Data
    public static class RagEvalResult {
        private String query;
        @JsonProperty("expected_source")
        private String expectedSource;
        @JsonProperty("expected_contains")
        private String expectedContains;
        @JsonProperty("hit_rank")
        private int hitRank;
        @JsonProperty("hit_at_1")
        private boolean hitAt1;
        @JsonProperty("hit_at_k")
        private boolean hitAtK;
        @JsonProperty("returned_sources")
        private List<String> returnedSources;
    }
}
