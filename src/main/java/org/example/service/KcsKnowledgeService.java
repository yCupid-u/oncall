package org.example.service;

import org.example.config.KcsProperties;
import org.example.dto.KcsCaptureRequest;
import org.example.dto.KcsCaptureResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class KcsKnowledgeService {

    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DOCUMENT_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final KcsProperties properties;
    private final VectorIndexService vectorIndexService;

    public KcsKnowledgeService(KcsProperties properties, VectorIndexService vectorIndexService) {
        this.properties = properties;
        this.vectorIndexService = vectorIndexService;
    }

    public KcsCaptureResult capture(KcsCaptureRequest request) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("KCS capture is disabled");
        }
        if (!StringUtils.hasText(request.getQuestion())) {
            throw new IllegalArgumentException("question is required");
        }
        if (!StringUtils.hasText(request.getAnswer())) {
            throw new IllegalArgumentException("answer is required");
        }

        KcsCaptureResult result = new KcsCaptureResult();
        result.setReviewOnly(!request.isResolved());
        result.setStatus(request.isResolved() ? "verified" : "needs_review");

        try {
            Path baseDir = Paths.get(request.isResolved() ? properties.getPath() : properties.getReviewPath()).normalize();
            Files.createDirectories(baseDir);

            String fileName = buildFileName(request);
            Path filePath = baseDir.resolve(fileName).normalize();
            Files.writeString(filePath, buildKnowledgeArticle(request, result.getStatus()), StandardCharsets.UTF_8);

            result.setCaptured(true);
            result.setFilePath(filePath.toString());

            if (request.isResolved() && properties.isIndexResolved()) {
                try {
                    vectorIndexService.indexSingleFile(filePath.toString());
                    result.setIndexed(true);
                    result.setMessage("captured and indexed");
                } catch (Exception e) {
                    result.setIndexed(false);
                    result.setMessage("captured but indexing failed: " + e.getMessage());
                }
            } else {
                result.setIndexed(false);
                result.setMessage(request.isResolved() ? "captured without indexing" : "captured for review");
            }

            return result;
        } catch (Exception e) {
            throw new RuntimeException("KCS capture failed: " + e.getMessage(), e);
        }
    }

    private String buildFileName(KcsCaptureRequest request) {
        String timestamp = FILE_TIME_FORMATTER.format(LocalDateTime.now());
        String slug = slugify(request.getQuestion());
        String hash = Integer.toHexString(Objects.hash(
                request.getSessionId(),
                request.getQuestion(),
                request.getAnswer()));
        return timestamp + "-" + slug + "-" + hash + ".md";
    }

    private String buildKnowledgeArticle(KcsCaptureRequest request, String status) {
        String now = DOCUMENT_TIME_FORMATTER.format(LocalDateTime.now());
        List<String> tags = new ArrayList<>();
        tags.add("kcs");
        tags.add(status);
        if (request.getTags() != null) {
            request.getTags().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(tags::add);
        }

        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(buildTitle(request.getQuestion())).append("\n\n");
        builder.append("- Status: ").append(status).append("\n");
        builder.append("- Source: chat\n");
        builder.append("- Session: ").append(blankToDash(request.getSessionId())).append("\n");
        builder.append("- Captured At: ").append(now).append("\n");
        builder.append("- Tags: ").append(String.join(", ", tags)).append("\n\n");
        builder.append("## Issue\n\n");
        builder.append(request.getQuestion().trim()).append("\n\n");
        builder.append("## Resolution\n\n");
        builder.append(request.getAnswer().trim()).append("\n\n");
        builder.append("## Validation\n\n");
        if (request.isResolved()) {
            builder.append("User marked this answer as solved in the workflow.\n");
        } else {
            builder.append("User marked this answer as not solved. Review before publishing to the searchable knowledge base.\n");
        }
        if (StringUtils.hasText(request.getFeedback())) {
            builder.append("\nFeedback:\n\n").append(request.getFeedback().trim()).append("\n");
        }
        builder.append("\n## Reuse Notes\n\n");
        builder.append("Use this article when a future issue matches the issue description and constraints above.\n");
        return builder.toString();
    }

    private String buildTitle(String question) {
        String compact = question.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 80) {
            return compact;
        }
        return compact.substring(0, 80);
    }

    private String slugify(String value) {
        String slug = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isBlank()) {
            slug = "knowledge";
        }
        if (slug.length() > 48) {
            slug = slug.substring(0, 48).replaceAll("-+$", "");
        }
        return slug;
    }

    private String blankToDash(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }
}
