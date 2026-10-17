package com.bluewhale.auth.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.bluewhale.auth.dto.DeptRequest;
import com.bluewhale.auth.entity.SysDept;
import com.bluewhale.auth.vo.DeptVO;

import java.util.List;

/**
 * 系统部门服务
 *
 * @author BlueWhale
 */
public interface SysDeptService extends IService<SysDept> {

    /**
     * 部门树（parentId=0 为根，按 sort 升序）
     *
     * @return 部门树节点列表
     */
    List<DeptVO> tree();

    /**
     * 部门详情
     *
     * @param id 部门 ID
     * @return 部门视图对象
     */
    DeptVO getDetail(Long id);

    /**
     * 创建部门
     *
     * @param request 部门请求参数
     */
    void create(DeptRequest request);

    /**
     * 更新部门
     *
     * @param id 部门 ID
     * @param request 部门请求参数
     */
    void update(Long id, DeptRequest request);

    /**
     * 删除部门
     *
     * @param id 部门 ID
     */
    void delete(Long id);
}
