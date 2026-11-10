package com.bluewhale.erp.hr.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.hr.application.command.OrgUnitSaveCommand;
import com.bluewhale.erp.hr.application.view.OrgUnitView;
import com.bluewhale.erp.hr.domain.model.HrQuery;
import com.bluewhale.erp.hr.domain.model.HrStatus;
import com.bluewhale.erp.hr.domain.model.OrgUnit;
import com.bluewhale.erp.hr.domain.repository.HrOrgRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OrgAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class OrgAppServiceImplTest {

    private final HrOrgRepository orgRepository = mock(HrOrgRepository.class);

    private final OrgAppServiceImpl service = new OrgAppServiceImpl(orgRepository);

    @Test
    @DisplayName("新增顶级组织默认启用")
    void shouldCreateTopOrg() {
        when(orgRepository.findByCode("ORG-01")).thenReturn(Optional.empty());

        OrgUnitView view = service.create(command("ORG-01", "总部", null));

        assertEquals(HrStatus.ORG_ENABLED, view.getStatus());
        verify(orgRepository).insert(any(OrgUnit.class));
    }

    @Test
    @DisplayName("组织编码重复拒绝")
    void shouldRejectDuplicateCode() {
        when(orgRepository.findByCode("ORG-01")).thenReturn(Optional.of(org()));

        BizException exception = assertThrows(BizException.class,
                () -> service.create(command("ORG-01", "总部", null)));

        assertEquals(ErpErrorCode.ORG_CODE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("上级组织不存在拒绝")
    void shouldRejectMissingParent() {
        when(orgRepository.findByCode("ORG-02")).thenReturn(Optional.empty());
        when(orgRepository.findByCode("ORG-01")).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> service.create(command("ORG-02", "分部", "ORG-01")));

        assertEquals(ErpErrorCode.ORG_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("上级组织停用拒绝")
    void shouldRejectDisabledParent() {
        when(orgRepository.findByCode("ORG-02")).thenReturn(Optional.empty());
        OrgUnit parent = org();
        parent.setStatus(HrStatus.ORG_DISABLED);
        when(orgRepository.findByCode("ORG-01")).thenReturn(Optional.of(parent));

        BizException exception = assertThrows(BizException.class,
                () -> service.create(command("ORG-02", "分部", "ORG-01")));

        assertEquals(ErpErrorCode.HR_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("编辑组织")
    void shouldUpdateOrg() {
        when(orgRepository.findByCode("ORG-01")).thenReturn(Optional.of(org()));

        OrgUnitView view = service.update("ORG-01", command("ORG-01", "集团总部", null));

        assertEquals("集团总部", view.getName());
        verify(orgRepository).update(any(OrgUnit.class));
    }

    @Test
    @DisplayName("编辑不存在的组织按 404 处理")
    void shouldRejectMissingOrg() {
        when(orgRepository.findByCode("ORG-09")).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> service.update("ORG-09", command("ORG-09", "分部", null)));

        assertEquals(ErpErrorCode.ORG_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("组织列表返回视图")
    void shouldListOrgs() {
        when(orgRepository.list(any())).thenReturn(List.of(org()));

        List<OrgUnitView> views = service.list(new HrQuery());

        assertEquals(1, views.size());
        assertEquals("ORG-01", views.get(0).getCode());
    }

    /**
     * 构造组织保存命令。
     *
     * @param code 编码
     * @param name 名称
     * @param parentCode 上级编码
     * @return 命令
     */
    private OrgUnitSaveCommand command(String code, String name, String parentCode) {
        OrgUnitSaveCommand command = new OrgUnitSaveCommand();
        command.setCode(code);
        command.setName(name);
        command.setParentCode(parentCode);
        command.setManager("李四");
        return command;
    }

    /**
     * 构造组织。
     *
     * @return 组织
     */
    private OrgUnit org() {
        OrgUnit org = new OrgUnit();
        org.setId(1L);
        org.setCode("ORG-01");
        org.setName("总部");
        org.setStatus(HrStatus.ORG_ENABLED);
        return org;
    }
}
