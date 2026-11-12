package com.bluewhale.auth.application.model.vo;

import com.bluewhale.auth.domain.user.aggregate.User;
import lombok.Data;

import java.io.Serializable;

/**
 * 内网通讯录用户视图对象：仅承载服务间展示所需的非敏感字段，手机号与邮箱必须脱敏。
 *
 * @author BlueWhale
 */
@Data
public class UserDirectoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 手机号脱敏保留的前缀位数 */
    private static final int PHONE_PREFIX_LENGTH = 3;

    /** 手机号脱敏保留的后缀位数 */
    private static final int PHONE_SUFFIX_LENGTH = 4;

    /** 手机号可脱敏的最短长度，不足时整体替换 */
    private static final int PHONE_MIN_MASKABLE_LENGTH = PHONE_PREFIX_LENGTH + PHONE_SUFFIX_LENGTH;

    /** 脱敏占位符 */
    private static final String MASK = "***";

    /** 手机号中段固定脱敏占位符 */
    private static final String PHONE_MASK = "****";

    private Long id;
    private String username;
    private String nickname;
    private Long deptId;
    private String deptName;
    /** 脱敏后的手机号 */
    private String phone;
    /** 脱敏后的邮箱 */
    private String email;
    private String avatar;

    /**
     * 由用户聚合根与部门名称转换为通讯录视图对象（手机号/邮箱脱敏）
     *
     * @param user 用户聚合根
     * @param deptName 部门名称（可空）
     * @return 通讯录视图对象
     */
    public static UserDirectoryVO from(User user, String deptName) {
        UserDirectoryVO vo = new UserDirectoryVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername().value());
        vo.setNickname(user.getNickname());
        vo.setDeptId(user.getDeptId());
        vo.setDeptName(deptName);
        vo.setPhone(maskPhone(user.getPhone()));
        vo.setEmail(maskEmail(user.getEmail()));
        vo.setAvatar(user.getAvatar());
        return vo;
    }

    /**
     * 手机号脱敏：保留前 3 位与后 4 位，中段以 {@code ****} 替代；长度不足时整体替换为 {@code ***}
     *
     * @param phone 原始手机号
     * @return 脱敏后的手机号；入参为 {@code null} 时返回 {@code null}，长度不足时返回 {@code ***}
     */
    private static String maskPhone(String phone) {
        if (phone == null) {
            return null;
        }
        if (phone.length() < PHONE_MIN_MASKABLE_LENGTH) {
            return MASK;
        }
        return phone.substring(0, PHONE_PREFIX_LENGTH) + PHONE_MASK
                + phone.substring(phone.length() - PHONE_SUFFIX_LENGTH);
    }

    /**
     * 邮箱脱敏：保留本地部分首字符并追加 {@code ***}，域名原样保留；无 {@code @} 时整体替换为 {@code ***}
     *
     * @param email 原始邮箱
     * @return 脱敏后的邮箱；入参为 {@code null} 时返回 {@code null}，无 {@code @} 时返回 {@code ***}
     */
    private static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int atIndex = email.indexOf('@');
        if (atIndex < 0) {
            return MASK;
        }
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        String first = local.isEmpty() ? "" : local.substring(0, 1);
        return first + MASK + domain;
    }
}
