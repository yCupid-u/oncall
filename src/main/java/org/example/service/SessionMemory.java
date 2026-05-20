package org.example.service;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe per-session chat memory with a bounded context window.
 */
public class SessionMemory {

    private static final Logger logger = LoggerFactory.getLogger(SessionMemory.class);

    private final String sessionId;
    private final int maxWindowPairs;
    private final List<Map<String, String>> messageHistory;
    private final long createTime;
    private final ReentrantLock lock;
    private int prunedHistoryTokens;

    public SessionMemory(String sessionId, int maxWindowPairs) {
        if (maxWindowPairs < 1) {
            throw new IllegalArgumentException("maxWindowPairs must be greater than 0");
        }
        this.sessionId = sessionId;
        this.maxWindowPairs = maxWindowPairs;
        this.messageHistory = new ArrayList<>();
        this.createTime = System.currentTimeMillis();
        this.lock = new ReentrantLock();
    }

    public void addMessage(String userQuestion, String aiAnswer, TokenUsageEstimator tokenUsageEstimator) {
        lock.lock();
        try {
            messageHistory.add(message("user", userQuestion));
            messageHistory.add(message("assistant", aiAnswer));

            int maxMessages = maxWindowPairs * 2;
            while (messageHistory.size() > maxMessages) {
                Map<String, String> removedUser = messageHistory.remove(0);
                prunedHistoryTokens += estimateMessageTokens(removedUser, tokenUsageEstimator);
                if (!messageHistory.isEmpty()) {
                    Map<String, String> removedAssistant = messageHistory.remove(0);
                    prunedHistoryTokens += estimateMessageTokens(removedAssistant, tokenUsageEstimator);
                }
            }

            logger.debug("Session {} updated, current message pairs: {}", sessionId, messageHistory.size() / 2);
        } finally {
            lock.unlock();
        }
    }

    public List<Map<String, String>> getHistory() {
        lock.lock();
        try {
            return new ArrayList<>(messageHistory);
        } finally {
            lock.unlock();
        }
    }

    public void clearHistory() {
        lock.lock();
        try {
            messageHistory.clear();
            prunedHistoryTokens = 0;
            logger.info("Session {} history cleared", sessionId);
        } finally {
            lock.unlock();
        }
    }

    public int getMessagePairCount() {
        lock.lock();
        try {
            return messageHistory.size() / 2;
        } finally {
            lock.unlock();
        }
    }

    public long getCreateTime() {
        return createTime;
    }

    public TokenCompressionStats getCompressionStats(TokenUsageEstimator tokenUsageEstimator) {
        lock.lock();
        try {
            int currentHistoryTokens = tokenUsageEstimator.estimateMessages(messageHistory);
            int originalHistoryTokens = currentHistoryTokens + prunedHistoryTokens;
            double savingsRatio = originalHistoryTokens == 0
                    ? 0.0
                    : prunedHistoryTokens / (double) originalHistoryTokens;

            TokenCompressionStats stats = new TokenCompressionStats();
            stats.setCurrentHistoryTokens(currentHistoryTokens);
            stats.setPrunedHistoryTokens(prunedHistoryTokens);
            stats.setOriginalHistoryTokens(originalHistoryTokens);
            stats.setSavingsRatio(savingsRatio);
            return stats;
        } finally {
            lock.unlock();
        }
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private int estimateMessageTokens(Map<String, String> message, TokenUsageEstimator tokenUsageEstimator) {
        return tokenUsageEstimator.estimate(message.get("role"))
                + tokenUsageEstimator.estimate(message.get("content"))
                + 4;
    }

    @Setter
    @Getter
    public static class TokenCompressionStats {
        private int currentHistoryTokens;
        private int prunedHistoryTokens;
        private int originalHistoryTokens;
        private double savingsRatio;
    }
}
