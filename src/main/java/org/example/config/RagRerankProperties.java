package org.example.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
@ConfigurationProperties(prefix = "rag.rerank")
public class RagRerankProperties {

    private boolean enabled = true;
    private String model = "qwen3-rerank";
    private String endpoint = "https://dashscope.aliyuncs.com/compatible-api/v1/reranks";
    private int candidateTopK = 20;
    private int timeoutSeconds = 30;
    private String instruct = "";

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public void setCandidateTopK(int candidateTopK) {
        this.candidateTopK = Math.max(1, candidateTopK);
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
    }

    public void setInstruct(String instruct) {
        this.instruct = instruct;
    }
}
