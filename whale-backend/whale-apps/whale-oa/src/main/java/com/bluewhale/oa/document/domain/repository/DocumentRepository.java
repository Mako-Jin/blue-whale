package com.bluewhale.oa.document.domain.repository;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.document.domain.model.DocArchiveRef;
import com.bluewhale.oa.document.domain.model.DocAttachment;
import com.bluewhale.oa.document.domain.model.DocIncoming;
import com.bluewhale.oa.document.domain.model.DocNoRule;
import com.bluewhale.oa.document.domain.model.DocOutgoing;
import com.bluewhale.oa.document.domain.model.DocQuery;
import com.bluewhale.oa.document.domain.model.DocTemplate;

import java.util.List;

/**
 * 公文仓储。
 *
 * @author BlueWhale
 */
public interface DocumentRepository {

    /**
     * 分页查询模板。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<DocTemplate> pageTemplates(DocQuery query);

    /**
     * 查询全部模板（可按范围过滤）。
     *
     * @param docType 适用范围，可为空
     * @return 模板
     */
    List<DocTemplate> listTemplates(String docType);

    /**
     * 查询模板。
     *
     * @param id 模板ID
     * @return 模板；不存在返回 null
     */
    DocTemplate findTemplate(Long id);

    /**
     * 判断同编码同版本模板是否存在。
     *
     * @param code      编码
     * @param version   版本
     * @param excludeId 排除的模板ID（更新时传自身ID）
     * @return 是否存在
     */
    boolean templateExists(String code, Integer version, Long excludeId);

    /**
     * 新增模板。
     *
     * @param template 模板
     */
    void saveTemplate(DocTemplate template);

    /**
     * 更新模板。
     *
     * @param template 模板
     */
    void updateTemplate(DocTemplate template);

    /**
     * 删除模板。
     *
     * @param id 模板ID
     */
    void deleteTemplate(Long id);

    /**
     * 查询文号规则。
     *
     * @param id 规则ID
     * @return 规则；不存在返回 null
     */
    DocNoRule findRule(Long id);

    /**
     * 按编码查询文号规则。
     *
     * @param code 规则编码
     * @return 规则；不存在返回 null
     */
    DocNoRule findRuleByCode(String code);

    /**
     * 查询全部文号规则。
     *
     * @return 规则
     */
    List<DocNoRule> listRules();

    /**
     * 新增文号规则。
     *
     * @param rule 规则
     */
    void saveRule(DocNoRule rule);

    /**
     * 更新文号规则。
     *
     * @param rule 规则
     */
    void updateRule(DocNoRule rule);

    /**
     * 查询发文。
     *
     * @param id 发文ID
     * @return 发文；不存在返回 null
     */
    DocOutgoing findOutgoing(Long id);

    /**
     * 分页查询发文。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<DocOutgoing> pageOutgoing(DocQuery query);

    /**
     * 新增发文。
     *
     * @param outgoing 发文
     */
    void saveOutgoing(DocOutgoing outgoing);

    /**
     * 更新发文。
     *
     * @param outgoing 发文
     */
    void updateOutgoing(DocOutgoing outgoing);

    /**
     * 删除发文。
     *
     * @param id 发文ID
     */
    void deleteOutgoing(Long id);

    /**
     * 查询收文。
     *
     * @param id 收文ID
     * @return 收文；不存在返回 null
     */
    DocIncoming findIncoming(Long id);

    /**
     * 分页查询收文。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<DocIncoming> pageIncoming(DocQuery query);

    /**
     * 新增收文。
     *
     * @param incoming 收文
     */
    void saveIncoming(DocIncoming incoming);

    /**
     * 更新收文。
     *
     * @param incoming 收文
     */
    void updateIncoming(DocIncoming incoming);

    /**
     * 删除收文。
     *
     * @param id 收文ID
     */
    void deleteIncoming(Long id);

    /**
     * 查询单据附件。
     *
     * @param docType 单据类型
     * @param docId   单据ID
     * @return 附件
     */
    List<DocAttachment> listAttachments(String docType, Long docId);

    /**
     * 查询附件。
     *
     * @param id 附件ID
     * @return 附件；不存在返回 null
     */
    DocAttachment findAttachment(Long id);

    /**
     * 新增附件。
     *
     * @param attachment 附件
     */
    void saveAttachment(DocAttachment attachment);

    /**
     * 删除单据全部附件（逻辑删除）。
     */
    void deleteAttachments(String docType, Long docId);

    /**
     * 查询归档映射。
     *
     * @param docType 单据类型
     * @param docId   单据ID
     * @return 映射；不存在返回 null
     */
    DocArchiveRef findArchiveRef(String docType, Long docId);

    /**
     * 按主键查询归档映射。
     *
     * @param id 映射ID
     * @return 映射；不存在返回 null
     */
    DocArchiveRef findArchiveRef(Long id);

    /**
     * 分页查询归档映射。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<DocArchiveRef> pageArchiveRefs(DocQuery query);

    /**
     * 新增归档映射。
     *
     * @param ref 映射
     */
    void saveArchiveRef(DocArchiveRef ref);

    /**
     * 更新归档映射。
     *
     * @param ref 映射
     */
    void updateArchiveRef(DocArchiveRef ref);
}
