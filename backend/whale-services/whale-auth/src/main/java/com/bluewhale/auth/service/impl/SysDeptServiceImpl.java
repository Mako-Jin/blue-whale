package com.bluewhale.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.bluewhale.auth.config.DddMigrationProperties;
import com.bluewhale.auth.domain.dept.repository.DeptRepository;
import com.bluewhale.auth.dto.DeptRequest;
import com.bluewhale.auth.entity.SysDept;
import com.bluewhale.auth.infrastructure.persistence.assembler.DeptPersistenceAssembler;
import com.bluewhale.auth.mapper.SysDeptMapper;
import com.bluewhale.auth.service.SysDeptService;
import com.bluewhale.auth.vo.DeptVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统部门服务实现
 *
 * <p>DDD 迁移（AUTH-16）：读路径（{@code tree}、{@code getDetail} 经 {@code getById}）按开关
 * {@code whale.auth.ddd.dept} 选择「新 {@link DeptRepository} 仓储」或「旧 Mapper」；开关关闭时与迁移前
 * 实现逐字节一致。<b>写路径（create/update/delete）本轮仍走旧实现，接入留待 AUTH-17</b>。</p>
 *
 * @author BlueWhale
 */
@Service
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements SysDeptService {

    /** DDD 迁移灰度开关（字段注入以保持既有构造器签名不变，既有单测可原样构造） */
    @Autowired
    private DddMigrationProperties dddProperties = new DddMigrationProperties();

    /** Dept 聚合仓储（仅当 {@code whale.auth.ddd.dept=true} 时参与读路径） */
    @Autowired
    private DeptRepository deptRepository;

    @Override
    public List<DeptVO> tree() {
        List<DeptVO> all = loadAllDepts().stream().map(DeptVO::from).toList();
        return buildChildren(all, 0L);
    }

    /**
     * 加载全部部门（按 sort、id 升序）：按 DDD 迁移开关选择新仓储或旧 Mapper（关闭时与迁移前逐字节一致）
     *
     * @return 全部部门实体列表
     */
    private List<SysDept> loadAllDepts() {
        if (dddProperties.isDept()) {
            return deptRepository.findAll().stream().map(DeptPersistenceAssembler::toPo).toList();
        }
        return list(new LambdaQueryWrapper<SysDept>()
                .orderByAsc(SysDept::getSort)
                .orderByAsc(SysDept::getId));
    }

    /**
     * 简单递归组树
     *
     * @param all 平铺的部门列表
     * @param parentId 父部门 ID
     * @return 该父部门下的子节点列表
     */
    private List<DeptVO> buildChildren(List<DeptVO> all, Long parentId) {
        return all.stream()
                .filter(vo -> vo.getParentId() != null && parentId.equals(vo.getParentId()))
                .peek(vo -> vo.setChildren(buildChildren(all, vo.getId())))
                .toList();
    }

    @Override
    public DeptVO getDetail(Long id) {
        SysDept dept = loadDeptById(id);
        if (dept == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return DeptVO.from(dept);
    }

    /**
     * 加载部门实体：按 DDD 迁移开关选择新仓储或旧 Mapper（关闭时与迁移前逐字节一致）
     *
     * @param id 部门 ID
     * @return 部门实体；不存在时返回 {@code null}
     */
    private SysDept loadDeptById(Long id) {
        if (dddProperties.isDept()) {
            return deptRepository.findById(id).map(DeptPersistenceAssembler::toPo).orElse(null);
        }
        return getById(id);
    }

    @Override
    public void create(DeptRequest request) {
        SysDept dept = new SysDept();
        copyProperties(request, dept);
        save(dept);
    }

    @Override
    public void update(Long id, DeptRequest request) {
        SysDept dept = getById(id);
        if (dept == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        copyProperties(request, dept);
        updateById(dept);
    }

    @Override
    public void delete(Long id) {
        Long children = baseMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getParentId, id));
        if (children != null && children > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "存在子部门，不允许删除");
        }
        removeById(id);
    }

    /**
     * 将请求参数拷贝到部门实体（空值取默认：parentId=0、sort=0）
     *
     * @param request 部门请求参数
     * @param dept 目标部门实体
     */
    private void copyProperties(DeptRequest request, SysDept dept) {
        dept.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        dept.setDeptName(request.getDeptName());
        dept.setSort(request.getSort() == null ? 0 : request.getSort());
        dept.setLeader(request.getLeader());
    }
}
