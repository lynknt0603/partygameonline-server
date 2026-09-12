package com.partygameonline.game.bloodbound.api.dto;

import com.partygameonline.game.bloodbound.domain.BloodClan;

public record BloodBoundSecretCardView(
        BloodClan clan,
        int rank
) {}
