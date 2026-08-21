package com.partygameonline.realtime;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.realtime")
public record RealtimeProperties(Duration disconnectGrace) {

    public RealtimeProperties {
        if (disconnectGrace == null || disconnectGrace.isNegative()) {
            disconnectGrace = Duration.ofSeconds(30);
        }
    }
}
