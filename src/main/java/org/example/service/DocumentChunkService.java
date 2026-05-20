package org.example.service;

import org.example.config.DocumentChunkConfig;
import org.example.dto.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 文档切片服务。
 *
 * <p>策略：优先保留 Markdown 标题层级，再按段落、句子和固定长度兜底切分。
 * chunk 内容会前置标题路径，让 embedding 能保留章节上下文。</p>
 */
@Service
public class DocumentChunkService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentChunkService.class);
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$", Pattern.MULTILINE);
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[^。！？!?；;\\n]+[。！？!?；;]?");

    @Autowired
    private DocumentChunkConfig chunkConfig;

    public List<DocumentChunk> chunkDocument(String content, String filePath) {
        List<DocumentChunk> chunks = new ArrayList<>();
        if (content == null || content.trim().isEmpty()) {
            logger.warn("文档内容为空: {}", filePath);
            return chunks;
        }

        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        List<Section> sections = splitByHeadings(normalized);

        int chunkIndex = 0;
        for (Section section : sections) {
            List<DocumentChunk> sectionChunks = chunkSection(section, chunkIndex);
            chunks.addAll(sectionChunks);
            chunkIndex += sectionChunks.size();
        }

        logger.info("文档切片完成: {} -> {} 个切片", filePath, chunks.size());
        return chunks;
    }

    private List<Section> splitByHeadings(String content) {
        List<Section> sections = new ArrayList<>();
        Matcher matcher = HEADING_PATTERN.matcher(content);
        List<Heading> headingStack = new ArrayList<>();

        int bodyStart = 0;
        Heading currentHeading = null;

        while (matcher.find()) {
            if (matcher.start() > bodyStart) {
                addSection(sections, currentHeading, headingStack, content.substring(bodyStart, matcher.start()), bodyStart);
            }

            int level = matcher.group(1).length();
            String title = matcher.group(2).trim();
            while (!headingStack.isEmpty() && headingStack.get(headingStack.size() - 1).level >= level) {
                headingStack.remove(headingStack.size() - 1);
            }

            currentHeading = new Heading(level, title);
            headingStack.add(currentHeading);
            bodyStart = matcher.end();
        }

        if (bodyStart < content.length()) {
            addSection(sections, currentHeading, headingStack, content.substring(bodyStart), bodyStart);
        }

        if (sections.isEmpty() && !content.trim().isEmpty()) {
            sections.add(new Section(null, null, 0, content.trim(), 0));
        }

        return sections;
    }

    private void addSection(List<Section> sections, Heading currentHeading, List<Heading> headingStack,
                            String rawBody, int startIndex) {
        String body = rawBody.trim();
        if (body.isEmpty()) {
            return;
        }

        String headingPath = headingStack.stream()
                .map(heading -> heading.title)
                .collect(Collectors.joining(" > "));
        String title = currentHeading == null ? null : currentHeading.title;
        int level = currentHeading == null ? 0 : currentHeading.level;
        sections.add(new Section(title, blankToNull(headingPath), level, body, startIndex));
    }

    private List<DocumentChunk> chunkSection(Section section, int startChunkIndex) {
        List<String> units = splitToSemanticUnits(section.body);
        List<DocumentChunk> chunks = new ArrayList<>();

        StringBuilder currentBody = new StringBuilder();
        int chunkIndex = startChunkIndex;
        int currentStartIndex = section.startIndex;
        String strategy = strategyName(section, units);

        for (String unit : units) {
            if (unit.isBlank()) {
                continue;
            }

            int nextLength = decoratedLength(section, currentBody, unit);
            if (currentBody.length() > 0 && nextLength > chunkConfig.getMaxSize()) {
                chunks.add(buildChunk(section, currentBody.toString().trim(), currentStartIndex, chunkIndex++, strategy));
                String overlap = sentenceAwareOverlap(currentBody.toString());
                currentBody = new StringBuilder(overlap);
                if (!overlap.isEmpty()) {
                    currentBody.append("\n\n");
                }
                currentStartIndex = Math.max(section.startIndex, currentStartIndex + currentBody.length());
            }

            currentBody.append(unit.trim()).append("\n\n");
        }

        if (currentBody.length() > 0) {
            chunks.add(buildChunk(section, currentBody.toString().trim(), currentStartIndex, chunkIndex, strategy));
        }

        return chunks;
    }

    private int decoratedLength(Section section, StringBuilder currentBody, String unit) {
        String body = currentBody + unit;
        return decorateWithHeadingPath(section.headingPath, body).length();
    }

    private List<String> splitToSemanticUnits(String content) {
        List<String> units = new ArrayList<>();
        for (String paragraph : content.split("\\n\\s*\\n+")) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() <= chunkConfig.getMaxSize()) {
                units.add(trimmed);
                continue;
            }
            units.addAll(splitLongParagraph(trimmed));
        }
        return units;
    }

    private List<String> splitLongParagraph(String paragraph) {
        List<String> sentences = new ArrayList<>();
        Matcher matcher = SENTENCE_PATTERN.matcher(paragraph);
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (sentence.isEmpty()) {
                continue;
            }
            if (sentence.length() <= chunkConfig.getMaxSize()) {
                sentences.add(sentence);
            } else {
                sentences.addAll(splitFixedLength(sentence));
            }
        }
        if (sentences.isEmpty()) {
            sentences.addAll(splitFixedLength(paragraph));
        }
        return sentences;
    }

    private List<String> splitFixedLength(String text) {
        List<String> parts = new ArrayList<>();
        int maxSize = Math.max(1, chunkConfig.getMaxSize());
        for (int i = 0; i < text.length(); i += maxSize) {
            parts.add(text.substring(i, Math.min(i + maxSize, text.length())));
        }
        return parts;
    }

    private String sentenceAwareOverlap(String text) {
        int overlapSize = Math.min(chunkConfig.getOverlap(), text.length());
        if (overlapSize <= 0) {
            return "";
        }

        String tail = text.substring(text.length() - overlapSize).trim();
        int sentenceStart = Math.max(
                Math.max(tail.lastIndexOf('。'), tail.lastIndexOf('！')),
                Math.max(tail.lastIndexOf('？'), Math.max(tail.lastIndexOf('.'), tail.lastIndexOf(';')))
        );
        if (sentenceStart > 0 && sentenceStart + 1 < tail.length()) {
            return tail.substring(sentenceStart + 1).trim();
        }
        return tail;
    }

    private DocumentChunk buildChunk(Section section, String body, int startIndex, int chunkIndex, String strategy) {
        String content = decorateWithHeadingPath(section.headingPath, body);
        DocumentChunk chunk = new DocumentChunk(content, startIndex, startIndex + body.length(), chunkIndex);
        chunk.setTitle(section.title);
        chunk.setHeadingPath(section.headingPath);
        chunk.setHeadingLevel(section.headingLevel);
        chunk.setChunkStrategy(strategy);
        return chunk;
    }

    private String decorateWithHeadingPath(String headingPath, String body) {
        if (headingPath == null || headingPath.isBlank()) {
            return body.trim();
        }
        return headingPath + "\n\n" + body.trim();
    }

    private String strategyName(Section section, List<String> units) {
        String base = section.headingPath == null ? "paragraph" : "heading-path+paragraph";
        boolean usedSentenceSplit = units.stream().anyMatch(unit -> unit.length() < section.body.length())
                && section.body.length() > chunkConfig.getMaxSize();
        if (usedSentenceSplit) {
            return base + "+sentence";
        }
        return base;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record Heading(int level, String title) {
    }

    private record Section(String title, String headingPath, int headingLevel, String body, int startIndex) {
    }
}
