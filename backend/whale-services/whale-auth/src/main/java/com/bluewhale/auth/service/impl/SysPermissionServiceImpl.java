package com.bluewhale.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.bluewhale.auth.config.DddMigrationProperties;
import com.bluewhale.auth.domain.model.vo.PermCode;
import com.bluewhale.auth.domain.permission.aggregate.Permission;
import com.bluewhale.auth.domain.permission.repository.PermissionRepository;
import com.bluewhale.auth.dto.PermissionRequest;
import com.bluewhale.auth.entity.SysPermission;
import com.bluewhale.auth.infrastructure.persistence.assembler.PermissionPersistenceAssembler;
import com.bluewhale.auth.mapper.SysPermissionMapper;
import com.bluewhale.auth.security.PermissionCache;
import com.bluewhale.auth.service.SysPermissionService;
import com.bluewhale.auth.vo.PermissionVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 系统权限点服务实现
 *
 * <p>DDD 迁移（AUTH-16）：读路径（{@code page}、{@code listAllCodes}、{@code getDetail} 经 {@code getById}）
 * 按开关 {@code whale.auth.ddd.permission} 选择「新 {@link PermissionRepository} 仓储」或「旧 Mapper」；
 * 开关关闭时与迁移前实现逐字节一致。<b>写路径（create/update/delete）本轮仍走旧实现，接入留待 AUTH-17</b>。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class SysPermissionServiceImpl extends ServiceImpl<SysPermissionMapper, SysPermission> implements SysPermissionService {

    private final PermissionCache permissionCache;

    /** DDD 迁移灰度开关（字段注入以保持既有构造器签名不变，既有单测可原样构造） */
    @Autowired
    private DddMigrationProperties dddProperties = new DddMigrationProperties();

    /** Permission 聚合仓储（仅当 {@code whale.auth.ddd.permission=true} 时参与读路径） */
    @Autowired
    private PermissionRepository permissionRepository;

    @Override
    public PageResult<PermissionVO> page(long current, long size, String keyword, String permType) {
        if (dddProperties.isPermission()) {
            PermissionRepository.PermissionPage page = permissionRepository.page(current, size, keyword, permType);
            List<PermissionVO> records = page.records().stream()
                    .map(PermissionPersistenceAssembler::toPo).map(PermissionVO::from).toList();
            return PageResult.of(records, page.total(), current, size);
        }
        LambdaQueryWrapper<SysPermission> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysPermission::getPermCode, keyword)
                    .or().like(SysPermission::getPermName, keyword));
        }
        if (StringUtils.hasText(permType)) {
            wrapper.eq(SysPermission::getPermType, permType);
        }
        wrapper.orderByAsc(SysPermission::getSortNo).orderByDesc(SysPermission::getCreateTime);
        Page<SysPermission> page = baseMapper.selectPage(new Page<>(current, size), wrapper);
        List<PermissionVO> records = page.getRecords().stream().map(PermissionVO::from).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<String> listAllCodes() {
        return loadEnabledPermissionCodes();
    }

    /**
     * 加载全部启用权限码：按 DDD 迁移开关选择新仓储或旧 Mapper（关闭时与迁移前逐字节一致）
     *
     * @return 启用权限码（去重）列表
     */
    private List<String> loadEnabledPermissionCodes() {
        if (dddProperties.isPermission()) {
            return permissionRepository.findEnabledAll().stream()
                    .map(permission -> permission.getPermCode().value()).distinct().toList();
        }
        return list(new LambdaQueryWrapper<SysPermission>()
                        .eq(SysPermission::getStatus, Permission.STATUS_ENABLED))
                .stream().map(SysPermission::getPermCode).distinct().toList();
    }

    @Override
    public PermissionVO getDetail(Long id) {
        SysPermission permission = loadPermissionById(id);
        if (permission == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return PermissionVO.from(permission);
    }

    /**
     * 加载权限点实体：按 DDD 迁移开关选择新仓储或旧 Mapper（关闭时与迁移前逐字节一致）
     *
     * @param id 权限点 ID
     * @return 权限点实体；不存在时返回 {@code null}
     */
    private SysPermission loadPermissionById(Long id) {
        if (dddProperties.isPermission()) {
            return permissionRepository.findById(id).map(PermissionPersistenceAssembler::toPo).orElse(null);
        }
        return getById(id);
    }

    @Override
    public void create(PermissionRequest request) {
        // 值对象前置校验权限码不变量（AUTH-14 / T-DDD-2）
        String permCode = PermCode.of(request.getPermCode()).value();
        Long exists = baseMapper.selectCount(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermCode, permCode));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "权限编码已存在");
        }
        SysPermission permission = new SysPermission();
        copyProperties(request, permission);
        save(permission);
    }

    @Override
    public void update(Long id, PermissionRequest request) {
        SysPermission permission = getById(id);
        if (permission == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        Long exists = baseMapper.selectCount(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermCode, request.getPermCode())
                .ne(SysPermission::getId, id));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "权限编码已存在");
        }
        copyProperties(request, permission);
        updateById(permission);
        // 权限点变更后失效持有该点位的用户缓存（提交后生效）
        permissionCache.evictByPermission(id);
    }

    @Override
    public void delete(Long id) {
        Long children = baseMapper.selectCount(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getParentId, id));
        if (children != null && children > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "存在子权限，不允许删除");
        }
        removeById(id);
        // 权限点删除后失效持有该点位的用户缓存（提交后生效）
        permissionCache.evictByPermission(id);
    }

    /**
     * 将请求参数拷贝到权限点实体（空值取默认：parentId=0、sortNo=0、status=1）
     *
     * @param request 权限点请求参数
     * @param permission 目标权限点实体
     */
    private void copyProperties(PermissionRequest request, SysPermission permission) {
        permission.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        permission.setPermCode(request.getPermCode());
        permission.setPermName(request.getPermName());
        permission.setPermType(request.getPermType());
        permission.setPath(request.getPath());
        permission.setMethod(request.getMethod());
        permission.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        permission.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }
}
