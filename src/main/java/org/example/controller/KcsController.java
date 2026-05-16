package org.example.controller;

import lombok.Getter;
import lombok.Setter;
import org.example.dto.KcsCaptureRequest;
import org.example.dto.KcsCaptureResult;
import org.example.service.KcsKnowledgeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kcs")
public class KcsController {

    private final KcsKnowledgeService kcsKnowledgeService;

    public KcsController(KcsKnowledgeService kcsKnowledgeService) {
        this.kcsKnowledgeService = kcsKnowledgeService;
    }

    @PostMapping("/capture")
    public ResponseEntity<ApiResponse<KcsCaptureResult>> capture(@RequestBody KcsCaptureRequest request) {
        try {
            KcsCaptureResult result = kcsKnowledgeService.capture(request);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(500, e.getMessage()));
        }
    }

    @Getter
    @Setter
    public static class ApiResponse<T> {
        private int code;
        private String message;
        private T data;

        public static <T> ApiResponse<T> success(T data) {
            ApiResponse<T> response = new ApiResponse<>();
            response.setCode(200);
            response.setMessage("success");
            response.setData(data);
            return response;
        }

        public static <T> ApiResponse<T> error(int code, String message) {
            ApiResponse<T> response = new ApiResponse<>();
            response.setCode(code);
            response.setMessage(message);
            return response;
        }
    }
}
