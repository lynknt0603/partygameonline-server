package com.partygameonline.game.liarsnumber.api.dto;

import java.util.Map;

public record LiarsNumberEventView(String type, Map<String, Object> payload) {
}
