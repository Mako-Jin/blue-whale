package com.bluewhale.lms.cert.application.view;

import com.bluewhale.lms.cert.domain.model.CertSource;
import com.bluewhale.lms.cert.domain.model.CertTemplate;
import com.bluewhale.lms.cert.domain.model.CertTemplateStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 证书模板视图。
 *
 * @author BlueWhale
 */
@Data
public class CertTemplateView {

    /** 模板 ID */
    private Long id;

    /** 模板编码 */
    private String templateCode;

    /** 证书名称 */
    private String name;

    /** 类别 */
    private String category;

    /** 发证依据 */
    private String issueBasis;

    /** 有效期月数 */
    private Integer validMonths;

    /** 是否长期有效 */
    private boolean longTerm;

    /** 编号前缀 */
    private String certPrefix;

    /** 正文模板 */
    private String content;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 由领域对象构建视图。
     *
     * @param template 模板
     * @return 视图
     */
    public static CertTemplateView from(CertTemplate template) {
        CertTemplateView view = new CertTemplateView();
        view.setId(template.getId());
        view.setTemplateCode(template.getTemplateCode());
        view.setName(template.getName());
        view.setCategory(template.getCategory());
        CertSource basis = template.getIssueBasis() == null ? CertSource.MANUAL : template.getIssueBasis();
        view.setIssueBasis(basis.name());
        view.setValidMonths(template.getValidMonths());
        view.setLongTerm(template.isLongTerm());
        view.setCertPrefix(template.getCertPrefix());
        view.setContent(template.getContent());
        CertTemplateStatus status = template.getStatus() == null
                ? CertTemplateStatus.DISABLED : template.getStatus();
        view.setStatus(status.name());
        view.setRemark(template.getRemark());
        view.setCreateTime(template.getCreateTime());
        view.setUpdateTime(template.getUpdateTime());
        return view;
    }
}
