package org.example.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
@ConfigurationProperties(prefix = "kcs")
public class KcsProperties {

    private boolean enabled = true;
    private String path = "./uploads/kcs";
    private String reviewPath = "./uploads/kcs-review";
    private boolean indexResolved = true;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setReviewPath(String reviewPath) {
        this.reviewPath = reviewPath;
    }

    public void setIndexResolved(boolean indexResolved) {
        this.indexResolved = indexResolved;
    }
}
