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
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分不能低于 1")
    @Max(value = 5, message = "评分不能高于 5")
    private Integer rating;
}
