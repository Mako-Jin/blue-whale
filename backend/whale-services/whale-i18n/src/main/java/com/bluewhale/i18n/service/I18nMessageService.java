package com.bluewhale.i18n.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.i18n.dto.I18nMessageQueryRequest;
import com.bluewhale.i18n.dto.I18nMessageSaveRequest;
import com.bluewhale.i18n.vo.I18nMessageVO;

/**
 * 国际化消息服务
 *
 * @author BlueWhale
 */
public interface I18nMessageService {

    /**
     * 分页查询国际化消息
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<I18nMessageVO> page(I18nMessageQueryRequest request);

    /**
     * 消息详情
     *
     * @param id 消息ID
     * @return 消息视图对象
     */
    I18nMessageVO getDetail(Long id);

    /**
     * 新增消息
     *
     * @param request 新增请求
     * @return 新增后的消息视图对象
     */
    I18nMessageVO create(I18nMessageSaveRequest request);

    /**
     * 更新消息
     *
     * @param id      消息ID
     * @param request 更新请求
     * @return 更新后的消息视图对象
     */
    I18nMessageVO update(Long id, I18nMessageSaveRequest request);

    /**
     * 删除消息（逻辑删除）
     *
     * @param id 消息ID
     */
    void delete(Long id);
}
