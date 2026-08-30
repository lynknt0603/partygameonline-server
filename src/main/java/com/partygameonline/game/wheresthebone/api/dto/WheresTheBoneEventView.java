package com.partygameonline.game.wheresthebone.api.dto;

import java.util.Map;

public record WheresTheBoneEventView(String type, Map<String, Object> payload) {
}
