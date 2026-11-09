package com.peergeek.outbox.events;

import java.math.BigDecimal;

public record ReferralRewardedEvent(
        Long inviterId,
        Long inviteeId,
        BigDecimal inviterReward,
        BigDecimal inviteeReward
) {}

