package com.bluewhale.erp.hr.application.service.impl;

import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.hr.application.command.OrgUnitSaveCommand;
import com.bluewhale.erp.hr.application.service.OrgAppService;
import com.bluewhale.erp.hr.application.view.OrgUnitView;
import com.bluewhale.erp.hr.domain.model.HrQuery;
import com.bluewhale.erp.hr.domain.model.HrStatus;
import com.bluewhale.erp.hr.domain.model.OrgUnit;
import com.bluewhale.erp.hr.domain.repository.HrOrgRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 组织单元应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class OrgAppServiceImpl implements OrgAppService {

    private final HrOrgRepository orgRepository;

    @Override
    public List<OrgUnitView> list(HrQuery query) {
        return orgRepository.list(query).stream().map(OrgUnitView::of).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrgUnitView create(OrgUnitSaveCommand command) {
        if (orgRepository.findByCode(command.getCode()).isPresent()) {
            throw ErpErrorCode.exception(ErpErrorCode.ORG_CODE_CONFLICT);
        }
        requireEnabledParent(command.getParentCode());
        OrgUnit org = new OrgUnit();
        org.setCode(command.getCode().trim());
        org.setName(command.getName().trim());
        org.setParentCode(command.getParentCode());
        org.setManager(command.getManager());
        org.setStatus(command.getStatus() == null ? HrStatus.ORG_ENABLED : command.getStatus());
        org.setRemark(command.getRemark());
        org.validate();
        orgRepository.insert(org);
        return OrgUnitView.of(org);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrgUnitView update(String code, OrgUnitSaveCommand command) {
        OrgUnit org = orgRepository.findByCode(code)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.ORG_NOT_FOUND));
        requireEnabledParent(command.getParentCode());
        org.setName(command.getName().trim());
        org.setParentCode(command.getParentCode());
        org.setManager(command.getManager());
        if (command.getStatus() != null) {
            org.setStatus(command.getStatus());
        }
        org.setRemark(command.getRemark());
        org.validate();
        orgRepository.update(org);
        return OrgUnitView.of(org);
    }

    /**
     * 校验上级组织存在且启用（顶层为空时跳过）。
     *
     * @param parentCode 上级组织编码
     */
    private void requireEnabledParent(String parentCode) {
        if (parentCode == null || parentCode.isBlank()) {
            return;
        }
        OrgUnit parent = orgRepository.findByCode(parentCode)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.ORG_NOT_FOUND, "上级组织不存在"));
        if (!parent.enabled()) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "上级组织已停用");
        }
    }
}
