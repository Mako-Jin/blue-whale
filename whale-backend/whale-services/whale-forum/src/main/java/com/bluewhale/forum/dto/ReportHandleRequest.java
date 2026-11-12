package com.bluewhale.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 举报工单处理请求。
 *
 * <p>{@code action=NONE} 表示只结案不处置（举报不成立或无需动作）；
 * 其余动作会同时产生处置记录与处置通知。</p>
 *
 * @author BlueWhale
 */
@Data
public class ReportHandleRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 结案状态：RESOLVED 已处置 / REJECTED 举报不成立 */
    @NotBlank(message = "结案状态不能为空")
    @Pattern(regexp = "RESOLVED|REJECTED", message = "结案状态只能是 RESOLVED/REJECTED")
    private String status;

    /** 处理意见 */
    @Size(max = 512, message = "处理意见不能超过 512 字")
    private String remark;

    /** 附带处置动作：NONE / REMOVE / MUTE / BAN（不填按 NONE） */
    @Pattern(regexp = "NONE|REMOVE|MUTE|BAN", message = "处置动作只能是 NONE/REMOVE/MUTE/BAN")
    private String action;

    /** 禁言/封号天数（action=MUTE/BAN 时生效；≤0 永久） */
    private Integer muteDays;
}
