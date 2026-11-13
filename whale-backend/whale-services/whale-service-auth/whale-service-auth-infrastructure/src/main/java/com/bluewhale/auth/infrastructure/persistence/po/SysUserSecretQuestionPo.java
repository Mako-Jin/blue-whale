package com.bluewhale.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户密保问题子表实体（权限中心扩展 M3：对应 {@code sys_user_secret_question}，答案密文存储）。
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_user_secret_question")
public class SysUserSecretQuestionPo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 租户 ID */
    private Long tenantId;
    /** 用户 ID */
    private Long userId;
    /** 密保问题 */
    private String question;
    /** 密保答案（AES-GCM 密文 Base64，禁止明文） */
    private String answerEncrypted;
    /** 排序号 */
    private Integer sortNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
