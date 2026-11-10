package com.bluewhale.lms.cert.application.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发证命令。
 *
 * <p>{@code sourceType} 与 {@code sourceId} 由调用方声明本次发证的依据。模板声明了
 * 非手工依据时，必须给出对应的 {@code sourceId}，服务端会去该来源核实是否真的满足条件
 * （考试须已通过、课程须已学完）。</p>
 *
 * @author BlueWhale
 */
@Data
public class CertificateIssueCommand {

    /** 证书模板 ID */
    @NotNull(message = "证书模板不能为空")
    private Long templateId;

    /** 持证人用户 ID */
    @NotNull(message = "持证人不能为空")
    private Long userId;

    /** 持证人用户名 */
    @Size(max = 64, message = "持证人用户名不得超过 64 字符")
    private String username;

    /** 来源类型：MANUAL / COURSE / EXAM / TRAINING */
    private String sourceType;

    /** 来源业务 ID（考试 / 课程 / 培训班） */
    private Long sourceId;

    /** 来源业务标题快照 */
    @Size(max = 200, message = "来源标题不得超过 200 字符")
    private String sourceTitle;

    /** 备注 */
    @Size(max = 500, message = "备注不得超过 500 字符")
    private String remark;
}
