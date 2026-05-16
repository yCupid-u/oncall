package org.example.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.example.service.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 内部文档查询工具
 * 使用 RAG (Retrieval-Augmented Generation) 从内部知识库检索相关文档
 */
@Component
public class InternalDocsTools {
    
    private static final Logger logger = LoggerFactory.getLogger(InternalDocsTools.class);
    
    /** 工具名常量，用于动态构建提示词 */
    public static final String TOOL_QUERY_INTERNAL_DOCS = "queryInternalDocs";
    
    private final VectorSearchService vectorSearchService;
    
    @Value("${rag.top-k:3}")
    private int topK = 3; // 默认值
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 构造函数注入依赖
     * Spring 会自动注入 VectorSearchService
     */
    @Autowired
    public InternalDocsTools(VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }
    
    /**
     * 查询内部文档工具
     *
     * @param query 搜索查询，描述您要查找的信息
     * @return JSON 格式的搜索结果，包含相关文档内容、相似度分数和元数据
     */
    @Tool(description = "Use this tool to search internal documentation and knowledge base for relevant information. " +
            "It performs RAG (Retrieval-Augmented Generation) to find similar documents and extract processing steps. " +
            "This is useful when you need to understand internal procedures, best practices, or step-by-step guides " +
            "stored in the company's documentation.")
    public String queryInternalDocs(
            @ToolParam(description = "Search query describing what information you are looking for") 
            String query) {
        

        try {
            if (query == null || query.trim().isEmpty()) {
                return objectMapper.writeValueAsString(ToolOutput.error(
                        "queryInternalDocs",
                        "Search query cannot be empty. Ask the user for a service name, alert name, error keyword, or runbook topic."
                ));
            }

            // 使用向量搜索服务检索相关文档
            List<VectorSearchService.SearchResult> searchResults = 
                    vectorSearchService.searchSimilarDocuments(query.trim(), topK);
            
            if (searchResults.isEmpty()) {
                ToolOutput output = ToolOutput.success("queryInternalDocs", query.trim(), topK, searchResults);
                output.setStatus("no_results");
                output.setMessage("No relevant documents found in the knowledge base.");
                return objectMapper.writeValueAsString(output);
            }
            
            return objectMapper.writeValueAsString(ToolOutput.success("queryInternalDocs", query.trim(), topK, searchResults));
            
        } catch (Exception e) {
            logger.error("[工具错误] queryInternalDocs 执行失败", e);
            try {
                return objectMapper.writeValueAsString(ToolOutput.error("queryInternalDocs", "Failed to query internal docs: " + e.getMessage()));
            } catch (Exception jsonException) {
                return "{\"success\":false,\"status\":\"error\",\"tool\":\"queryInternalDocs\",\"message\":\"Failed to query internal docs\"}";
            }
        }
    }

    @Data
    private static class ToolOutput {
        @JsonProperty("success")
        private boolean success;
        @JsonProperty("status")
        private String status;
        @JsonProperty("tool")
        private String tool;
        @JsonProperty("query")
        private String query;
        @JsonProperty("top_k")
        private Integer topK;
        @JsonProperty("results")
        private List<VectorSearchService.SearchResult> results;
        @JsonProperty("message")
        private String message;

        static ToolOutput success(String tool, String query, int topK, List<VectorSearchService.SearchResult> results) {
            ToolOutput output = new ToolOutput();
            output.setSuccess(true);
            output.setStatus("ok");
            output.setTool(tool);
            output.setQuery(query);
            output.setTopK(topK);
            output.setResults(results);
            output.setMessage(String.format("Found %d relevant document chunks.", results.size()));
            return output;
        }

        static ToolOutput error(String tool, String message) {
            ToolOutput output = new ToolOutput();
            output.setSuccess(false);
            output.setStatus("error");
            output.setTool(tool);
            output.setMessage(message);
            return output;
        }
    }
}
