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

    /** 部门树（parentId=0 为根，按 sort 升序） */
    List<DeptVO> tree();

    DeptVO getDetail(Long id);

    void create(DeptRequest request);

    void update(Long id, DeptRequest request);

    void delete(Long id);
}
