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
    @NotNull(message = "{validation.lms.certificate-issue.template-id-required}")
    private Long templateId;

    /** 持证人用户 ID */
    @NotNull(message = "{validation.lms.certificate-issue.user-id-required}")
    private Long userId;

    /** 持证人用户名 */
    @Size(max = 64, message = "{validation.lms.certificate-issue.username-size}")
    private String username;

    /** 来源类型：MANUAL / COURSE / EXAM / TRAINING */
    private String sourceType;

    /** 来源业务 ID（考试 / 课程 / 培训班） */
    private Long sourceId;

    /** 来源业务标题快照 */
    @Size(max = 200, message = "{validation.lms.certificate-issue.source-title-size}")
    private String sourceTitle;

    /** 备注 */
    @Size(max = 500, message = "{validation.lms.certificate-issue.remark-size}")
    private String remark;
}
