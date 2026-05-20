package org.example.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TokenUsageEstimator {

    public int estimateMessages(List<Map<String, String>> messages) {
        int total = 0;
        for (Map<String, String> message : messages) {
            total += estimate(message.get("role"));
            total += estimate(message.get("content"));
            total += 4;
        }
        return total;
    }

    public int estimatePair(String userQuestion, String aiAnswer) {
        return estimate(userQuestion) + estimate(aiAnswer) + 8;
    }

    public int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        int tokens = 0;
        int asciiRunLength = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (isCjk(ch)) {
                tokens += Math.max(1, (asciiRunLength + 3) / 4);
                asciiRunLength = 0;
                tokens += 1;
            } else if (Character.isLetterOrDigit(ch)) {
                asciiRunLength++;
            } else {
                tokens += Math.max(1, (asciiRunLength + 3) / 4);
                asciiRunLength = 0;
                if (!Character.isWhitespace(ch)) {
                    tokens += 1;
                }
            }
        }
        tokens += Math.max(1, (asciiRunLength + 3) / 4);
        return tokens;
    }

    private boolean isCjk(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES;
    }
}
