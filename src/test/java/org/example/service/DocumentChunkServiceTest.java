package org.example.service;

import org.example.config.DocumentChunkConfig;
import org.example.dto.DocumentChunk;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkServiceTest {

    @Test
    void chunksMarkdownByHeadingPathAndPrependsContext() {
        DocumentChunkService service = serviceWithConfig(120, 30);
        String markdown = """
                # CPU 使用率过高告警处理方案

                ## 排查步骤

                先查看 Pod CPU 使用率，再检查线程数和热点方法。

                ### top 命令检查

                使用 top -Hp 查看高 CPU 线程，并结合 jstack 定位业务代码。
                """;

        List<DocumentChunk> chunks = service.chunkDocument(markdown, "cpu_high_usage.md");

        assertThat(chunks).isNotEmpty();
        assertThat(chunks)
                .extracting(DocumentChunk::getHeadingPath)
                .contains("CPU 使用率过高告警处理方案 > 排查步骤 > top 命令检查");
        assertThat(chunks)
                .anySatisfy(chunk -> {
                    assertThat(chunk.getContent()).startsWith("CPU 使用率过高告警处理方案 > 排查步骤");
                    assertThat(chunk.getTitle()).isEqualTo("排查步骤");
                    assertThat(chunk.getHeadingLevel()).isEqualTo(2);
                    assertThat(chunk.getChunkStrategy()).contains("heading");
                });
    }

    @Test
    void splitsLongChineseParagraphBySentenceBeforeFixedFallback() {
        DocumentChunkService service = serviceWithConfig(90, 20);
        String markdown = """
                # 慢 SQL 排障

                ## 处理步骤

                第一步确认慢 SQL 的 query_time 和 rows_examined。第二步查看是否发生全表扫描和缺失索引。第三步检查连接池是否被打满。第四步对高频 SQL 增加联合索引并观察 P99 延迟。
                """;

        List<DocumentChunk> chunks = service.chunkDocument(markdown, "slow_sql.md");

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks)
                .allSatisfy(chunk -> {
                    assertThat(chunk.getContent().length()).isLessThanOrEqualTo(120);
                    assertThat(chunk.getHeadingPath()).isEqualTo("慢 SQL 排障 > 处理步骤");
                    assertThat(chunk.getChunkStrategy()).contains("sentence");
                });
    }

    private DocumentChunkService serviceWithConfig(int maxSize, int overlap) {
        DocumentChunkConfig config = new DocumentChunkConfig();
        config.setMaxSize(maxSize);
        config.setOverlap(overlap);

        DocumentChunkService service = new DocumentChunkService();
        ReflectionTestUtils.setField(service, "chunkConfig", config);
        return service;
    }
}
