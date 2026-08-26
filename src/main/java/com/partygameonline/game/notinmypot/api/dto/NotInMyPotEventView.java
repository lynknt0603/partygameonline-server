package com.partygameonline.game.notinmypot.api.dto;

import java.util.Map;

public record NotInMyPotEventView(String type, Map<String, Object> payload) {
}
