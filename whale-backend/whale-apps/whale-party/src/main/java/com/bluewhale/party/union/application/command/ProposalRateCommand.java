package com.bluewhale.party.union.application.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 职代会提案满意度评价命令。
 *
 * @author BlueWhale
 */
@Data
public class ProposalRateCommand {

    /** 评分（1–5） */
    @NotNull(message = "{validation.party.proposal-rate.rating-required}")
    @Min(value = 1, message = "{validation.party.proposal-rate.rating-min}")
    @Max(value = 5, message = "{validation.party.proposal-rate.rating-max}")
    private Integer rating;
}
