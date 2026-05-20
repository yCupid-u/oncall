package org.example.memory;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class MemoryRecord {

    private String memoryId;
    private MemoryType memoryType = MemoryType.INCIDENT_SUMMARY;
    private String sessionId;
    private String source;
    private String serviceName;
    private String alertName;
    private String entityKey;
    private String rootCause;
    private String action;
    private String content;
    private String summary;
    private String evidence;
    private double confidence = 0.8;
    private int hitCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastHitAt;
    private LocalDateTime expireAt;
}
