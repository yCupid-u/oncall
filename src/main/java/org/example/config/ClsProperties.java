package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "cls")
public class ClsProperties {

    private boolean mockEnabled = false;
    private String secretId = "";
    private String secretKey = "";
    private String endpoint = "cls.tencentcloudapi.com";
    private String defaultRegion = "ap-guangzhou";
    private long defaultLookbackMinutes = 30;
    private Map<String, String> topicIds = new HashMap<>();

    public boolean isMockEnabled() {
        return mockEnabled;
    }

    public void setMockEnabled(boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
    }

    public String getSecretId() {
        return secretId;
    }

    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getDefaultRegion() {
        return defaultRegion;
    }

    public void setDefaultRegion(String defaultRegion) {
        this.defaultRegion = defaultRegion;
    }

    public long getDefaultLookbackMinutes() {
        return defaultLookbackMinutes;
    }

    public void setDefaultLookbackMinutes(long defaultLookbackMinutes) {
        this.defaultLookbackMinutes = defaultLookbackMinutes;
    }

    public Map<String, String> getTopicIds() {
        return topicIds;
    }

    public void setTopicIds(Map<String, String> topicIds) {
        this.topicIds = topicIds;
    }
}
