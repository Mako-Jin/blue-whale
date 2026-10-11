package com.bluewhale.sts.auth.tenant;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.sts.auth.common.AuthResultCode;
import com.bluewhale.sts.auth.common.enums.Status;
import com.bluewhale.sts.common.basic.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 租户应用服务。
 *
 * <p><b>唯一性校验放在服务层而不是只靠数据库唯一索引：</b>索引是最后一道防线，
 * 但让用户看到"Duplicate entry 'acme' for key 'uk_bw_auth_tenant_code'"这样的
 * 数据库原文，既没有可操作性也泄露了库结构。服务层先查一次给出明确文案，
 * 索引则负责挡住并发下的漏网之鱼 —— 两层各司其职，不是重复。
 */
@Service
public class TenantService {

    private final TenantMapper tenantMapper;

    public TenantService(TenantMapper tenantMapper) {
        this.tenantMapper = tenantMapper;
    }

    @Transactional(readOnly = true)
    public PageResult<TenantVO> page(TenantQuery query) {
        var wrapper = Wrappers.<TenantEntity>lambdaQuery()
                .and(query.getKeyword() != null && !query.getKeyword().isBlank(), w -> w
                        .like(TenantEntity::getTenantCode, query.getKeyword())
                        .or().like(TenantEntity::getTenantName, query.getKeyword()))
                .eq(query.getStatus() != null && !query.getStatus().isBlank(),
                        TenantEntity::getStatus, query.getStatus())
                .orderByAsc(TenantEntity::getTenantLevel)
                .orderByAsc(TenantEntity::getId);

        Page<TenantEntity> page = tenantMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        List<TenantVO> records = page.getRecords().stream().map(TenantService::toVO).toList();
        return PageResult.of(records, page.getTotal(), query.getPage(), query.getSize());
    }

    @Transactional(readOnly = true)
    public TenantVO detail(Long id) {
        return toVO(requireTenant(id));
    }

    @Transactional
    public TenantVO create(TenantSaveRequest request) {
        Long codeCount = tenantMapper.selectCount(Wrappers.<TenantEntity>lambdaQuery()
                .eq(TenantEntity::getTenantCode, request.getTenantCode()));
        if (codeCount != null && codeCount > 0) {
            throw AuthResultCode.TENANT_CODE_EXISTS.businessException();
        }
        Long nameCount = tenantMapper.selectCount(Wrappers.<TenantEntity>lambdaQuery()
                .eq(TenantEntity::getTenantName, request.getTenantName()));
        if (nameCount != null && nameCount > 0) {
            throw AuthResultCode.TENANT_NAME_EXISTS.businessException();
        }

        TenantEntity parent = resolveParent(request.getParentId());

        TenantEntity entity = new TenantEntity();
        entity.setTenantCode(request.getTenantCode());
        entity.setTenantName(request.getTenantName());
        entity.setParentId(parent == null ? null : parent.getId());
        entity.setTenantLevel(parent == null ? 1 : (parent.getTenantLevel() == null ? 1 : parent.getTenantLevel()) + 1);
        entity.setCodePath(parent == null ? "/" : parent.getCodePath() + parent.getId() + "/");
        entity.setHasChild(0);
        entity.setStatus(resolveStatus(request.getStatus()));
        entity.setDescription(request.getDescription());
        entity.setAllowRegister(orZero(request.getAllowRegister()));
        entity.setAllowAdd(orZero(request.getAllowAdd()));
        tenantMapper.insert(entity);

        markParentHasChild(parent);
        return toVO(entity);
    }

    @Transactional
    public TenantVO update(Long id, TenantSaveRequest request) {
        TenantEntity entity = requireTenant(id);

        Long nameCount = tenantMapper.selectCount(Wrappers.<TenantEntity>lambdaQuery()
                .eq(TenantEntity::getTenantName, request.getTenantName())
                .ne(TenantEntity::getId, id));
        if (nameCount != null && nameCount > 0) {
            throw AuthResultCode.TENANT_NAME_EXISTS.businessException();
        }

        // tenantCode 不在修改项内：它是对外稳定标识，改了等于让所有引用它的地方失效。
        // 若确需变更，应当是"新建 + 迁移"的独立操作，而不是一次普通的字段更新。
        entity.setTenantName(request.getTenantName());
        entity.setDescription(request.getDescription());
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            entity.setStatus(request.getStatus());
        }
        if (request.getAllowRegister() != null) {
            entity.setAllowRegister(request.getAllowRegister());
        }
        if (request.getAllowAdd() != null) {
            entity.setAllowAdd(request.getAllowAdd());
        }
        tenantMapper.updateById(entity);
        return toVO(entity);
    }

    @Transactional
    public void delete(Long id) {
        TenantEntity entity = requireTenant(id);
        Long childCount = tenantMapper.selectCount(Wrappers.<TenantEntity>lambdaQuery()
                .eq(TenantEntity::getParentId, id));
        if (childCount != null && childCount > 0) {
            // 级联删除会把整棵子树连同其数据一并抹掉，这类操作必须显式确认，
            // 不能藏在一个 delete 接口里顺手完成
            throw AuthResultCode.HIERARCHY_CYCLE.businessException("该租户下仍有子租户，请先处理子租户");
        }
        tenantMapper.deleteById(entity.getId());
        markParentHasChild(entity.getParentId() == null ? null : tenantMapper.selectById(entity.getParentId()));
    }

    private TenantEntity requireTenant(Long id) {
        TenantEntity entity = id == null ? null : tenantMapper.selectById(id);
        if (entity == null) {
            throw AuthResultCode.TENANT_NOT_FOUND.businessException();
        }
        return entity;
    }

    private TenantEntity resolveParent(Long parentId) {
        if (parentId == null) {
            return null;
        }
        return requireTenant(parentId);
    }

    private void markParentHasChild(TenantEntity parent) {
        if (parent == null || (parent.getHasChild() != null && parent.getHasChild() == 1)) {
            return;
        }
        parent.setHasChild(1);
        tenantMapper.updateById(parent);
    }

    private static String resolveStatus(String status) {
        Status parsed = Status.parse(status);
        return parsed == null ? Status.ENABLED.wireName() : parsed.wireName();
    }

    private static Integer orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static TenantVO toVO(TenantEntity entity) {
        TenantVO vo = new TenantVO();
        vo.setId(entity.getId());
        vo.setTenantCode(entity.getTenantCode());
        vo.setTenantName(entity.getTenantName());
        vo.setParentId(entity.getParentId());
        vo.setTenantLevel(entity.getTenantLevel());
        vo.setStatus(entity.getStatus());
        vo.setDescription(entity.getDescription());
        vo.setAllowRegister(entity.getAllowRegister());
        vo.setAllowAdd(entity.getAllowAdd());
        vo.setHasChild(entity.getHasChild());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

}
