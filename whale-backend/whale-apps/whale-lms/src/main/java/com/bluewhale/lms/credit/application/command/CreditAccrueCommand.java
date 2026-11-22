package com.bluewhale.lms.credit.application.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学分记账命令。
 *
 * <p>{@code sourceId} 的语义按来源区分：{@code EXAM} 用<b>考试 ID</b>而不是作答记录 ID。
 * 补考是同一门考试，用作答 ID 会让补考通过再记一次学分；幂等键也是按这个 ID 建的，
 * 换成语义错误的值会直接让「同一事件只记一次」失效。</p>
 *
 * @author BlueWhale
 */
@Data
public class CreditAccrueCommand {

    /** 学员用户 ID */
    @NotNull(message = "{validation.lms.credit-accrue.user-id-required}")
    private Long userId;

    /** 学员用户名 */
    @Size(max = 64, message = "{validation.lms.credit-accrue.username-size}")
    private String username;

    /** 来源类型：COURSE / EXAM / TRAINING */
    @NotNull(message = "{validation.lms.credit-accrue.source-type-required}")
    private String sourceType;

    /** 来源业务 ID（课程 / 考试 / 培训班 ID） */
    @NotNull(message = "{validation.lms.credit-accrue.source-id-required}")
    private Long sourceId;

    /** 业务发生时间（不传取当前时间，用于跨年补记） */
    private LocalDateTime occurredAt;

    /** 备注 */
    @Size(max = 500, message = "{validation.lms.credit-accrue.remark-size}")
    private String remark;
}
