package com.bluewhale.oa.drive.domain.gateway;

/**
 * 组织架构网关：按账号解析用户部门。
 *
 * @author BlueWhale
 */
public interface DriveDeptGateway {

    /**
     * 查询账号所属部门。
     *
     * @param username 账号
     * @return 部门ID；解析失败返回 null
     */
    Long deptIdOf(String username);
}
