package org.example.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionMemoryTest {

    private final TokenUsageEstimator tokenUsageEstimator = new TokenUsageEstimator();

    @Test
    void keepsOnlyConfiguredWindowAndReportsTokenSavings() {
        SessionMemory memory = new SessionMemory("session-a", 3);

        for (int i = 1; i <= 10; i++) {
            memory.addMessage(
                    "user question " + i + " " + "x".repeat(80),
                    "assistant answer " + i + " " + "y".repeat(120),
                    tokenUsageEstimator);
        }

        SessionMemory.TokenCompressionStats stats = memory.getCompressionStats(tokenUsageEstimator);

        assertThat(memory.getMessagePairCount()).isEqualTo(3);
        assertThat(memory.getHistory()).hasSize(6);
        assertThat(stats.getPrunedHistoryTokens()).isGreaterThan(0);
        assertThat(stats.getOriginalHistoryTokens())
                .isEqualTo(stats.getCurrentHistoryTokens() + stats.getPrunedHistoryTokens());
        assertThat(stats.getSavingsRatio()).isGreaterThan(0.60);
    }

    @Test
    void clearHistoryResetsCompressionStats() {
        SessionMemory memory = new SessionMemory("session-b", 1);

        memory.addMessage("first " + "x".repeat(50), "answer " + "y".repeat(50), tokenUsageEstimator);
        memory.addMessage("second " + "x".repeat(50), "answer " + "y".repeat(50), tokenUsageEstimator);

        assertThat(memory.getCompressionStats(tokenUsageEstimator).getPrunedHistoryTokens()).isGreaterThan(0);

        memory.clearHistory();

        assertThat(memory.getMessagePairCount()).isZero();
        assertThat(memory.getCompressionStats(tokenUsageEstimator).getSavingsRatio()).isZero();
    }
}
