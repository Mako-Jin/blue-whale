package com.bluewhale.auth.application.model.command;

/**
 * 创建用户命令：承载管理端「创建用户」用例的输入参数。
 *
 * <p>字段与 {@code UserCreateRequest} 一一对应；用户名格式与口令加密由应用层在落库前完成校验与转换。</p>
 *
 * @param username 用户名（明文，经 {@code Username} 值对象校验格式与全局唯一性）
 * @param rawPassword 明文口令（落库前经 BCrypt 加密为密文）
 * @param nickname 昵称
 * @param phone 手机号
 * @param email 邮箱
 * @param avatar 头像地址
 * @param deptId 所属部门 ID
 * @param status 状态（1 启用 / 0 停用），为空时按启用处理
 * @author BlueWhale
 */
public record CreateUserCommand(String username, String rawPassword, String nickname, String phone,
                                String email, String avatar, Long deptId, Integer status) {
}
