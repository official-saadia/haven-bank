package com.havenbank.backend.money.mapper;

import com.havenbank.backend.money.domain.FeeSchedule;
import com.havenbank.backend.money.dto.FeeScheduleResponse;
import org.springframework.stereotype.Component;

/**
 * Maps {@link FeeSchedule} to {@link FeeScheduleResponse}.
 */
@Component
public class FeeScheduleMapper {

    public FeeScheduleResponse toResponse(FeeSchedule f) {
        return new FeeScheduleResponse(f.getId(), f.getAppliesTo().name(), f.getTierMin(), f.getTierMax(),
                f.getFeeFlat(), f.getFeePercent(), f.getEffectiveFrom(), f.getEffectiveTo());
    }
}
