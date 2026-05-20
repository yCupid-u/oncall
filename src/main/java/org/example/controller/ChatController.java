package org.example.controller;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.Getter;
import lombok.Setter;
import org.example.service.AiOpsService;
import org.example.service.ChatService;
import org.example.service.SessionMemory;
import org.example.service.TokenUsageEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unified API controller for chat, streaming chat, session state, and AIOps.
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    private static final int MAX_WINDOW_SIZE = 6;

    @Autowired
    private AiOpsService aiOpsService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private TokenUsageEstimator tokenUsageEstimator;

    @Autowired
    private ToolCallbackProvider tools;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, SessionMemory> sessions = new ConcurrentHashMap<>();

    /**
     * Non-streaming ReactAgent chat endpoint.
     */
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@RequestBody ChatRequest request) {
        try {
            logger.info("Received chat request, sessionId={}, question={}", request.getId(), request.getQuestion());

            if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
                logger.warn("Question content is empty");
                return ResponseEntity.ok(ApiResponse.success(ChatResponse.error("Question content cannot be empty")));
            }

            SessionMemory session = getOrCreateSession(request.getId());
            List<Map<String, String>> history = session.getHistory();
            logSessionStats("chat", request.getId(), session);

            DashScopeApi dashScopeApi = chatService.createDashScopeApi();
            DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);

            chatService.logAvailableTools();
            String systemPrompt = chatService.buildSystemPrompt(history);
            ReactAgent agent = chatService.createReactAgent(chatModel, systemPrompt);

            String fullAnswer = chatService.executeChat(agent, request.getQuestion());

            session.addMessage(request.getQuestion(), fullAnswer, tokenUsageEstimator);
            logger.info("Updated session history, sessionId={}, messagePairs={}",
                    request.getId(), session.getMessagePairCount());

            return ResponseEntity.ok(ApiResponse.success(ChatResponse.success(fullAnswer)));
        } catch (Exception e) {
            logger.error("Chat failed", e);
            return ResponseEntity.ok(ApiResponse.success(ChatResponse.error(e.getMessage())));
        }
    }

    /**
     * Clear one session's chat history.
     */
    @PostMapping("/chat/clear")
    public ResponseEntity<ApiResponse<String>> clearChatHistory(@RequestBody ClearRequest request) {
        try {
            logger.info("Received clear-history request, sessionId={}", request.getId());

            if (request.getId() == null || request.getId().isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error("Session id cannot be empty"));
            }

            SessionMemory session = sessions.get(request.getId());
            if (session == null) {
                return ResponseEntity.ok(ApiResponse.error("Session not found"));
            }

            session.clearHistory();
            return ResponseEntity.ok(ApiResponse.success("Chat history cleared"));
        } catch (Exception e) {
            logger.error("Failed to clear chat history", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Streaming ReactAgent chat endpoint.
     */
    @PostMapping(value = "/chat_stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(300000L);
        AtomicBoolean emitterClosed = new AtomicBoolean(false);
        attachEmitterLifecycle(emitter, emitterClosed);

        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            logger.warn("Question content is empty");
            sendEvent(emitter, emitterClosed, SseMessage.error("Question content cannot be empty"));
            completeEmitter(emitter, emitterClosed);
            return emitter;
        }

        executor.execute(() -> {
            try {
                logger.info("Received streaming chat request, sessionId={}, question={}",
                        request.getId(), request.getQuestion());

                SessionMemory session = getOrCreateSession(request.getId());
                List<Map<String, String>> history = session.getHistory();
                logSessionStats("chat_stream", request.getId(), session);

                DashScopeApi dashScopeApi = chatService.createDashScopeApi();
                DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);

                chatService.logAvailableTools();
                String systemPrompt = chatService.buildSystemPrompt(history);
                ReactAgent agent = chatService.createReactAgent(chatModel, systemPrompt);

                StringBuilder fullAnswerBuilder = new StringBuilder();
                Flux<NodeOutput> stream = agent.stream(request.getQuestion());

                stream.subscribe(
                        output -> handleStreamingOutput(output, fullAnswerBuilder, emitter, emitterClosed),
                        error -> {
                            logger.error("ReactAgent streaming chat failed", error);
                            sendEvent(emitter, emitterClosed, SseMessage.error(error.getMessage()));
                            completeEmitterWithError(emitter, emitterClosed, error);
                        },
                        () -> {
                            String fullAnswer = fullAnswerBuilder.toString();
                            logger.info("ReactAgent streaming chat completed, sessionId={}, answerLength={}",
                                    request.getId(), fullAnswer.length());

                            session.addMessage(request.getQuestion(), fullAnswer, tokenUsageEstimator);
                            logger.info("Updated session history, sessionId={}, messagePairs={}",
                                    request.getId(), session.getMessagePairCount());

                            sendEvent(emitter, emitterClosed, SseMessage.done());
                            completeEmitter(emitter, emitterClosed);
                        }
                );
            } catch (Exception e) {
                logger.error("Failed to initialize streaming chat", e);
                sendEvent(emitter, emitterClosed, SseMessage.error(e.getMessage()));
                completeEmitterWithError(emitter, emitterClosed, e);
            }
        });

        return emitter;
    }

    /**
     * AI Ops endpoint. It starts the multi-agent alert analysis flow and streams progress.
     */
    @PostMapping(value = "/ai_ops", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter aiOps() {
        SseEmitter emitter = new SseEmitter(600000L);
        AtomicBoolean emitterClosed = new AtomicBoolean(false);
        attachEmitterLifecycle(emitter, emitterClosed);

        executor.execute(() -> {
            try {
                logger.info("Received AI Ops request, starting multi-agent orchestration");

                DashScopeApi dashScopeApi = chatService.createDashScopeApi();
                DashScopeChatModel chatModel = DashScopeChatModel.builder()
                        .dashScopeApi(dashScopeApi)
                        .defaultOptions(DashScopeChatOptions.builder()
                                .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME)
                                .withTemperature(0.3)
                                .withMaxToken(8000)
                                .withTopP(0.9)
                                .build())
                        .build();

                ToolCallback[] toolCallbacks = tools.getToolCallbacks();
                sendEvent(emitter, emitterClosed, SseMessage.content("Reading alerts and planning investigation tasks...\n"));

                CompletableFuture<Optional<OverAllState>> analysisFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        return aiOpsService.executeAiOpsAnalysis(chatModel, toolCallbacks);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }, executor);

                Optional<OverAllState> overAllStateOptional = waitForAiOpsResult(analysisFuture, emitter, emitterClosed);
                if (overAllStateOptional == null) {
                    return;
                }

                if (overAllStateOptional.isEmpty()) {
                    sendEvent(emitter, emitterClosed, SseMessage.error("Multi-Agent flow did not return a valid state"));
                    completeEmitter(emitter, emitterClosed);
                    return;
                }

                OverAllState state = overAllStateOptional.get();
                logger.info("AI Ops orchestration completed, extracting final report");

                Optional<String> finalReportOptional = aiOpsService.extractFinalReport(state);
                if (finalReportOptional.isPresent()) {
                    streamFinalReport(finalReportOptional.get(), emitter, emitterClosed);
                } else {
                    logger.warn("Failed to extract Planner final report");
                    sendEvent(emitter, emitterClosed,
                            SseMessage.content("Multi-Agent flow completed, but no final report was generated."));
                }

                sendEvent(emitter, emitterClosed, SseMessage.done());
                completeEmitter(emitter, emitterClosed);
                logger.info("AI Ops multi-agent flow completed");
            } catch (Exception e) {
                logger.error("AI Ops multi-agent flow failed", e);
                sendEvent(emitter, emitterClosed, SseMessage.error("AI Ops flow failed: " + e.getMessage()));
                completeEmitterWithError(emitter, emitterClosed, e);
            }
        });

        return emitter;
    }

    /**
     * Get session state and token compression stats.
     */
    @GetMapping("/chat/session/{sessionId}")
    public ResponseEntity<ApiResponse<SessionInfoResponse>> getSessionInfo(@PathVariable String sessionId) {
        try {
            logger.info("Received session-info request, sessionId={}", sessionId);

            SessionMemory session = sessions.get(sessionId);
            if (session == null) {
                return ResponseEntity.ok(ApiResponse.error("Session not found"));
            }

            SessionInfoResponse response = new SessionInfoResponse();
            response.setSessionId(sessionId);
            response.setMessagePairCount(session.getMessagePairCount());
            response.setCreateTime(session.getCreateTime());
            response.setTokenCompression(session.getCompressionStats(tokenUsageEstimator));
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            logger.error("Failed to get session info", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    private void handleStreamingOutput(NodeOutput output, StringBuilder fullAnswerBuilder,
                                       SseEmitter emitter, AtomicBoolean emitterClosed) {
        if (!(output instanceof StreamingOutput streamingOutput)) {
            return;
        }

        OutputType type = streamingOutput.getOutputType();
        if (type == OutputType.AGENT_MODEL_STREAMING) {
            String chunk = streamingOutput.message().getText();
            if (chunk != null && !chunk.isEmpty()) {
                fullAnswerBuilder.append(chunk);
                sendEvent(emitter, emitterClosed, SseMessage.content(chunk));
                logger.debug("Sent streaming chunk, length={}", chunk.length());
            }
        } else if (type == OutputType.AGENT_MODEL_FINISHED) {
            logger.info("Model output finished");
        } else if (type == OutputType.AGENT_TOOL_FINISHED) {
            logger.info("Tool call finished: {}", output.node());
        } else if (type == OutputType.AGENT_HOOK_FINISHED) {
            logger.debug("Agent hook finished: {}", output.node());
        }
    }

    private Optional<OverAllState> waitForAiOpsResult(CompletableFuture<Optional<OverAllState>> analysisFuture,
                                                      SseEmitter emitter,
                                                      AtomicBoolean emitterClosed) {
        long startedAt = System.currentTimeMillis();
        long maxWaitMillis = TimeUnit.MINUTES.toMillis(5);
        int progressCount = 0;

        while (true) {
            try {
                return analysisFuture.get(8, TimeUnit.SECONDS);
            } catch (TimeoutException timeout) {
                long elapsedSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startedAt);
                if (elapsedSeconds >= TimeUnit.MILLISECONDS.toSeconds(maxWaitMillis)) {
                    analysisFuture.cancel(true);
                    sendEvent(emitter, emitterClosed,
                            SseMessage.error("AI Ops analysis timed out after " + elapsedSeconds
                                    + " seconds. Check model/MCP logs for the slow step."));
                    completeEmitter(emitter, emitterClosed);
                    return null;
                }
                progressCount++;
                if (!sendEvent(emitter, emitterClosed,
                        SseMessage.content("Still investigating... elapsed " + elapsedSeconds
                                + "s, step " + progressCount + "\n"))) {
                    analysisFuture.cancel(true);
                    return null;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                analysisFuture.cancel(true);
                sendEvent(emitter, emitterClosed, SseMessage.error("AI Ops analysis was interrupted"));
                completeEmitterWithError(emitter, emitterClosed, interrupted);
                return null;
            } catch (Exception e) {
                analysisFuture.cancel(true);
                throw new CompletionException(e);
            }
        }
    }

    private void streamFinalReport(String finalReportText, SseEmitter emitter, AtomicBoolean emitterClosed) {
        logger.info("Extracted Planner final report, length={}", finalReportText.length());

        if (!sendEvent(emitter, emitterClosed, SseMessage.content("\n\n" + "=".repeat(60) + "\n"))) {
            return;
        }
        if (!sendEvent(emitter, emitterClosed, SseMessage.content("**Alert Analysis Report**\n\n"))) {
            return;
        }

        int chunkSize = 500;
        for (int i = 0; i < finalReportText.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, finalReportText.length());
            if (!sendEvent(emitter, emitterClosed, SseMessage.content(finalReportText.substring(i, end)))) {
                return;
            }
        }

        sendEvent(emitter, emitterClosed, SseMessage.content("\n" + "=".repeat(60) + "\n\n"));
        logger.info("Final report streamed to client");
    }

    private void attachEmitterLifecycle(SseEmitter emitter, AtomicBoolean emitterClosed) {
        emitter.onCompletion(() -> emitterClosed.set(true));
        emitter.onTimeout(() -> emitterClosed.set(true));
        emitter.onError(error -> emitterClosed.set(true));
    }

    private boolean sendEvent(SseEmitter emitter, AtomicBoolean emitterClosed, SseMessage message) {
        if (emitterClosed.get()) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event().name("message").data(message, MediaType.APPLICATION_JSON));
            return true;
        } catch (IOException | IllegalStateException e) {
            logger.warn("SSE connection closed while sending message type={}", message.getType(), e);
            completeEmitterWithError(emitter, emitterClosed, e);
            return false;
        }
    }

    private void completeEmitter(SseEmitter emitter, AtomicBoolean emitterClosed) {
        if (emitterClosed.compareAndSet(false, true)) {
            emitter.complete();
        }
    }

    private void completeEmitterWithError(SseEmitter emitter, AtomicBoolean emitterClosed, Throwable error) {
        if (emitterClosed.compareAndSet(false, true)) {
            emitter.completeWithError(error);
        }
    }

    private SessionMemory getOrCreateSession(String sessionId) {
        String key = (sessionId == null || sessionId.isEmpty()) ? UUID.randomUUID().toString() : sessionId;
        return sessions.computeIfAbsent(key, id -> new SessionMemory(id, MAX_WINDOW_SIZE));
    }

    private void logSessionStats(String endpoint, String sessionId, SessionMemory session) {
        SessionMemory.TokenCompressionStats tokenStats = session.getCompressionStats(tokenUsageEstimator);
        logger.info("{} session stats, sessionId={}, messagePairs={}, currentTokens={}, prunedTokens={}, savingsRatio={}",
                endpoint,
                sessionId,
                session.getMessagePairCount(),
                tokenStats.getCurrentHistoryTokens(),
                tokenStats.getPrunedHistoryTokens(),
                tokenStats.getSavingsRatio());
    }

    @Setter
    @Getter
    public static class ChatRequest {
        @com.fasterxml.jackson.annotation.JsonProperty(value = "Id")
        @com.fasterxml.jackson.annotation.JsonAlias({"id", "ID"})
        private String Id;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "Question")
        @com.fasterxml.jackson.annotation.JsonAlias({"question", "QUESTION"})
        private String Question;
    }

    @Setter
    @Getter
    public static class ClearRequest {
        @com.fasterxml.jackson.annotation.JsonProperty(value = "Id")
        @com.fasterxml.jackson.annotation.JsonAlias({"id", "ID"})
        private String Id;
    }

    @Setter
    @Getter
    public static class SessionInfoResponse {
        private String sessionId;
        private int messagePairCount;
        private long createTime;
        private SessionMemory.TokenCompressionStats tokenCompression;
    }

    @Setter
    @Getter
    public static class ChatResponse {
        private boolean success;
        private String answer;
        private String errorMessage;

        public static ChatResponse success(String answer) {
            ChatResponse response = new ChatResponse();
            response.setSuccess(true);
            response.setAnswer(answer);
            return response;
        }

        public static ChatResponse error(String errorMessage) {
            ChatResponse response = new ChatResponse();
            response.setSuccess(false);
            response.setErrorMessage(errorMessage);
            return response;
        }
    }

    @Setter
    @Getter
    public static class SseMessage {
        private String type;
        private String data;

        public static SseMessage content(String data) {
            SseMessage message = new SseMessage();
            message.setType("content");
            message.setData(data);
            return message;
        }

        public static SseMessage error(String errorMessage) {
            SseMessage message = new SseMessage();
            message.setType("error");
            message.setData(errorMessage);
            return message;
        }

        public static SseMessage done() {
            SseMessage message = new SseMessage();
            message.setType("done");
            message.setData(null);
            return message;
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

        public static <T> ApiResponse<T> error(String message) {
            ApiResponse<T> response = new ApiResponse<>();
            response.setCode(500);
            response.setMessage(message);
            return response;
        }
    }
}
