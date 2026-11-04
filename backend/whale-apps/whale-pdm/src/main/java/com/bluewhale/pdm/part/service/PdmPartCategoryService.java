package com.bluewhale.pdm.part.service;

import com.bluewhale.pdm.part.dto.PdmPartCategorySaveRequest;
import com.bluewhale.pdm.part.vo.PdmPartCategoryVO;

import java.util.List;

/**
 * 物料分类服务
 *
 * @author BlueWhale
 */
public interface PdmPartCategoryService {

    /**
     * 查询分类树
     *
     * @return 分类树
     */
    List<PdmPartCategoryVO> tree();

    /**
     * 分类详情
     *
     * @param id 分类ID
     * @return 分类视图
     */
    PdmPartCategoryVO get(Long id);

    /**
     * 新增分类
     *
     * @param request 保存请求
     * @return 分类视图
     */
    PdmPartCategoryVO create(PdmPartCategorySaveRequest request);

    /**
     * 更新分类
     *
     * @param id      分类ID
     * @param request 保存请求
     * @return 分类视图
     */
    PdmPartCategoryVO update(Long id, PdmPartCategorySaveRequest request);

    /**
     * 删除分类
     *
     * @param id 分类ID
     */
    void delete(Long id);

    /**
     * 判断分类编码是否存在
     *
     * @param code 分类编码
     * @return 是否存在
     */
    boolean existsCode(String code);
}
