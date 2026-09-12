package com.partygameonline.game.bloodbound.api.dto;

import com.partygameonline.game.bloodbound.domain.BloodClan;

public record BloodBoundClueView(
        BloodClan clan,
        String crest
) {}
