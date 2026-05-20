package org.example.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 文档切片。
 */
@Setter
@Getter
public class DocumentChunk {

    /**
     * 切片内容。用于 embedding 和入库。
     */
    private String content;

    /**
     * 切片在原文档中的起始位置。
     */
    private int startIndex;

    /**
     * 切片在原文档中的结束位置。
     */
    private int endIndex;

    /**
     * 切片序号，从 0 开始。
     */
    private int chunkIndex;

    /**
     * 当前章节标题。
     */
    private String title;

    /**
     * 标题层级路径，例如：CPU 使用率过高 > 排查步骤。
     */
    private String headingPath;

    /**
     * Markdown 标题层级，# 为 1，## 为 2。无标题时为 0。
     */
    private int headingLevel;

    /**
     * 生成该切片使用的策略，便于调试和评测。
     */
    private String chunkStrategy;

    public DocumentChunk() {
    }

    public DocumentChunk(String content, int startIndex, int endIndex, int chunkIndex) {
        this.content = content;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.chunkIndex = chunkIndex;
    }

    @Override
    public String toString() {
        return "DocumentChunk{" +
                "chunkIndex=" + chunkIndex +
                ", title='" + title + '\'' +
                ", headingPath='" + headingPath + '\'' +
                ", headingLevel=" + headingLevel +
                ", chunkStrategy='" + chunkStrategy + '\'' +
                ", contentLength=" + (content != null ? content.length() : 0) +
                ", startIndex=" + startIndex +
                ", endIndex=" + endIndex +
                '}';
    }
}
