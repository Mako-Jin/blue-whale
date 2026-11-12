package com.bluewhale.auth.domain.model.vo;

/**
 * 领域值对象（Value Object）：不可变、以属性相等判定，构造即校验不变量（AUTH-14 / T-DDD-2）。
 *
 * <p>包含：{@code Username}/{@code RoleCode}/{@code PermCode}（标识类值对象）、
 * {@code EncryptedPassword}（BCrypt 密文）、{@code PasswordPolicy}（口令强度策略，
 * REQ-SEC-021，校验失败抛 {@code DomainException}）。</p>
 *
 * @author BlueWhale
 */
