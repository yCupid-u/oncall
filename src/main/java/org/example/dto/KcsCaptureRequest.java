package org.example.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class KcsCaptureRequest {
    private String sessionId;
    private String question;
    private String answer;
    private boolean resolved = true;
    private String feedback;
    private List<String> tags = new ArrayList<>();
}
