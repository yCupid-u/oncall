package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KcsCaptureResult {
    private boolean captured;
    private boolean indexed;
    private boolean reviewOnly;
    private String status;
    private String filePath;
    private String message;
}
