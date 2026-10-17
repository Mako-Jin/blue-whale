package com.bluewhale.auth.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.bluewhale.auth.dto.PermissionRequest;
import com.bluewhale.auth.entity.SysPermission;
import com.bluewhale.auth.vo.PermissionVO;
import com.bluewhale.common.core.result.PageResult;

import java.util.List;

/**
 * 系统权限点服务
 *
 * @author BlueWhale
 */
public interface SysPermissionService extends IService<SysPermission> {

    /**
     * 分页查询（keyword 模糊匹配权限编码/名称，permType 精确过滤）
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param keyword 权限编码或名称模糊匹配关键字（可空）
     * @param permType 权限类型精确过滤条件（可空）
     * @return 权限点分页结果
     */
    PageResult<PermissionVO> page(long current, long size, String keyword, String permType);

    /**
     * 全部启用权限码（内置超管按此授予全量权限）
     *
     * @return 全部启用的权限码列表
     */
    List<String> listAllCodes();

    /**
     * 权限点详情
     *
     * @param id 权限点 ID
     * @return 权限点视图对象
     */
    PermissionVO getDetail(Long id);

    /**
     * 创建权限点
     *
     * @param request 权限点请求参数
     */
    void create(PermissionRequest request);

    /**
     * 更新权限点
     *
     * @param id 权限点 ID
     * @param request 权限点请求参数
     */
    void update(Long id, PermissionRequest request);

    /**
     * 删除权限点
     *
     * @param id 权限点 ID
     */
    void delete(Long id);
}
