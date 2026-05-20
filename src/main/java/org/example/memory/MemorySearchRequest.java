package org.example.memory;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemorySearchRequest {

    private String serviceName;
    private String alertName;
    private String keyword;
    private int limit = 3;

    public MemorySearchRequest() {
    }

    public MemorySearchRequest(String serviceName, String alertName, String keyword, int limit) {
        this.serviceName = serviceName;
        this.alertName = alertName;
        this.keyword = keyword;
        this.limit = limit;
    }
}
