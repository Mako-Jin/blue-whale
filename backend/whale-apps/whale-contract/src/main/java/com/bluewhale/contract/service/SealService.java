package com.bluewhale.contract.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.contract.dto.OperatorContext;
import com.bluewhale.contract.dto.SealGrantRequest;
import com.bluewhale.contract.dto.SealSaveRequest;
import com.bluewhale.contract.vo.SealGrantVO;
import com.bluewhale.contract.vo.SealVO;

import java.util.List;

/**
 * 印章与用章授权服务。
 *
 * @author BlueWhale
 */
public interface SealService {

    /**
     * 分页查询印章。
     *
     * @param current 页码
     * @param size    每页条数
     * @param keyword 印章名称关键字
     * @param status  状态
     * @return 分页结果
     */
    PageResult<SealVO> page(long current, long size, String keyword, String status);

    /**
     * 新建印章。
     *
     * @param request 保存请求
     * @return 印章视图
     */
    SealVO create(SealSaveRequest request);

    /**
     * 更新印章。
     *
     * @param id      印章ID
     * @param request 保存请求
     * @return 印章视图
     */
    SealVO update(Long id, SealSaveRequest request);

    /**
     * 删除印章（逻辑删除）。
     *
     * @param id 印章ID
     */
    void delete(Long id);

    /**
     * 新增用章授权。
     *
     * @param sealId   印章ID
     * @param request  授权请求
     * @param operator 当前操作人
     * @return 授权视图
     */
    SealGrantVO grant(Long sealId, SealGrantRequest request, OperatorContext operator);

    /**
     * 查询印章的授权记录。
     *
     * @param sealId 印章ID
     * @return 授权列表
     */
    List<SealGrantVO> grants(Long sealId);

    /**
     * 上传印章图片（转存 whale-file）。
     *
     * @param filename    原始文件名
     * @param contentType MIME 类型
     * @param content     文件内容
     * @return 文件ID
     */
    Long uploadImage(String filename, String contentType, byte[] content);
}
