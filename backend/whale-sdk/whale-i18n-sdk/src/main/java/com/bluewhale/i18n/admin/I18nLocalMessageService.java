package com.bluewhale.i18n.admin;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.i18n.admin.dto.I18nMessageQueryRequest;
import com.bluewhale.i18n.admin.dto.I18nMessageSaveRequest;
import com.bluewhale.i18n.admin.vo.I18nMessageVO;

/**
 * 服务本地国际化文案管理服务
 *
 * <p>读写当前服务自己数据库中的 {@code sys_i18n_message}，无需集中式服务。</p>
 *
 * @author BlueWhale
 */
public interface I18nLocalMessageService {

    /**
     * 分页查询文案
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<I18nMessageVO> page(I18nMessageQueryRequest request);

    /**
     * 查询文案详情
     *
     * @param id 主键ID
     * @return 文案视图对象
     */
    I18nMessageVO getDetail(Long id);

    /**
     * 新增文案
     *
     * @param request 新增请求
     * @return 新增后的文案
     */
    I18nMessageVO create(I18nMessageSaveRequest request);

    /**
     * 更新文案
     *
     * @param id      主键ID
     * @param request 更新请求
     * @return 更新后的文案
     */
    I18nMessageVO update(Long id, I18nMessageSaveRequest request);

    /**
     * 逻辑删除文案
     *
     * @param id 主键ID
     */
    void delete(Long id);
}
