package com.partygameonline.realtime;

import com.partygameonline.room.application.RoomService;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class DisconnectGraceService {

    private static final Logger log = LoggerFactory.getLogger(DisconnectGraceService.class);

    private final ConcurrentHashMap<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    private final TaskScheduler taskScheduler;
    private final RealtimeProperties properties;
    private final RoomService roomService;

    public DisconnectGraceService(
            TaskScheduler taskScheduler,
            RealtimeProperties properties,
            RoomService roomService
    ) {
        this.taskScheduler = taskScheduler;
        this.properties = properties;
        this.roomService = roomService;
    }

    public void schedule(String playerId) {
        cancel(playerId);
        Instant when = Instant.now().plus(properties.disconnectGrace());
        ScheduledFuture<?> future = taskScheduler.schedule(() -> expire(playerId), when);
        pending.put(playerId, future);
        log.info("Disconnect grace scheduled playerId={} expiresAt={}", playerId, when);
    }

    public void cancel(String playerId) {
        ScheduledFuture<?> future = pending.remove(playerId);
        if (future != null) {
            future.cancel(false);
        }
    }

    void expire(String playerId) {
        pending.remove(playerId);
        log.info("Disconnect grace expired playerId={}", playerId);
        roomService.expireDisconnect(playerId);
    }

    boolean isPending(String playerId) {
        return pending.containsKey(playerId);
    }
}
