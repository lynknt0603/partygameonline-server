package com.partygameonline.game.nob.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "games.nob")
public record NobGameProperties(
        int targetScore,
        MoonMarks moonMarks,
        Timeout timeout,
        boolean timeoutSchedulerEnabled
) {

    public NobGameProperties {
        if (targetScore <= 0) {
            targetScore = 10;
        }
        if (moonMarks == null) {
            moonMarks = new MoonMarks(15, 12, 8);
        }
        if (timeout == null) {
            timeout = new Timeout(30, 30, 30, 10);
        }
    }

    public static NobGameProperties defaults() {
        return new NobGameProperties(10, new MoonMarks(15, 12, 8), new Timeout(30, 30, 30, 10), true);
    }

    public record MoonMarks(int value2Count, int value3Count, int value4Count) {
        public MoonMarks {
            if (value2Count < 0) {
                value2Count = 15;
            }
            if (value3Count < 0) {
                value3Count = 12;
            }
            if (value4Count < 0) {
                value4Count = 8;
            }
        }
    }

    public record Timeout(int draftSeconds, int phaseSubmitSeconds, int decisionSeconds, int reactionSeconds) {
        public Timeout {
            if (draftSeconds <= 0) {
                draftSeconds = 30;
            }
            if (phaseSubmitSeconds <= 0) {
                phaseSubmitSeconds = 30;
            }
            if (decisionSeconds <= 0) {
                decisionSeconds = 30;
            }
            if (reactionSeconds <= 0) {
                reactionSeconds = 10;
            }
        }
    }
}
