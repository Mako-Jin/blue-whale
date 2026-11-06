package com.bluewhale.lms.cert.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.cert.application.command.CertificateIssueCommand;
import com.bluewhale.lms.cert.application.command.CertificateRenewCommand;
import com.bluewhale.lms.cert.application.command.CertificateRevokeCommand;
import com.bluewhale.lms.cert.application.view.CertificateView;
import com.bluewhale.lms.cert.domain.model.CertificateQuery;
import com.bluewhale.lms.common.LmsActor;

/**
 * 证书应用服务。
 *
 * @author BlueWhale
 */
public interface CertificateAppService {

    /**
     * 分页查询证书（管理端）。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<CertificateView> page(CertificateQuery query, LmsActor actor);

    /**
     * 证书详情。
     *
     * @param id 证书 ID
     * @param actor 操作人
     * @return 详情
     */
    CertificateView detail(Long id, LmsActor actor);

    /**
     * 发证。
     *
     * @param command 发证命令
     * @param actor 操作人
     * @return 证书 ID
     */
    Long issue(CertificateIssueCommand command, LmsActor actor);

    /**
     * 撤销证书。
     *
     * @param id 证书 ID
     * @param command 撤销命令
     * @param actor 操作人
     */
    void revoke(Long id, CertificateRevokeCommand command, LmsActor actor);

    /**
     * 换发证书。
     *
     * @param id 原证书 ID
     * @param command 换发命令
     * @param actor 操作人
     * @return 新证书 ID
     */
    Long renew(Long id, CertificateRenewCommand command, LmsActor actor);

    /**
     * 我的证书。
     *
     * @param query 查询条件（持证人强制为当前用户）
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<CertificateView> mine(CertificateQuery query, LmsActor actor);
}
