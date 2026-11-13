package com.bluewhale.auth.domain.model.entity;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDateTime;

/**
 * 用户密保问题实体（对应 {@code sys_user_secret_question}，权限中心扩展 M3）。
 *
 * <p>答案以 AES-GCM 密文（Base64）形式在应用层加密后进入本实体，实体自身只保证
 * 「密文非空」；多条有序，只允许通过 {@code UserProfileAggregate} 整体替换。</p>
 *
 * @param id 主键（新建时为 {@code null}）
 * @param question 密保问题
 * @param answerEncrypted 答案密文（Base64，禁止明文）
 * @param sortNo 排序号
 * @param createTime 创建时间
 * @param updateTime 更新时间
 * @author BlueWhale
 */
public record UserSecretQuestion(Long id, String question, String answerEncrypted, Integer sortNo,
                                 LocalDateTime createTime, LocalDateTime updateTime) implements Entity<Long> {

    /**
     * 工厂方法：新建密保问题（答案密文由应用层 {@code SensitiveFieldCipher} 加密后传入）
     *
     * @param question 密保问题
     * @param answerEncrypted 答案密文（Base64）
     * @param sortNo 排序号
     * @return 密保问题实体
     */
    public static UserSecretQuestion create(String question, String answerEncrypted, Integer sortNo) {
        if (question == null || question.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "密保问题不能为空");
        }
        if (answerEncrypted == null || answerEncrypted.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "密保答案密文不能为空（必须加密后传入）");
        }
        return new UserSecretQuestion(null, question.trim(), answerEncrypted,
                sortNo == null ? 0 : sortNo, LocalDateTime.now(), LocalDateTime.now());
    }

    /**
     * 获取主键
     *
     * @return 主键（新建时为 {@code null}）
     */
    @Override
    public Long getId() {
        return id;
    }
}
