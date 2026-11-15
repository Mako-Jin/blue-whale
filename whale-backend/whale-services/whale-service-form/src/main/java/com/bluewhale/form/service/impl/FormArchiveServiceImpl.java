package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.form.converter.FormConverter;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormTableRegistryMapper;
import com.bluewhale.form.model.vo.FormTableRegistryVO;
import com.bluewhale.form.po.FormDefinitionPo;
import com.bluewhale.form.po.FormTableRegistryPo;
import com.bluewhale.form.service.FormArchiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 表单物理表归档服务实现（只标记，不导出、不删表）。
 *
 * <p>归档对象只能是「非当前生效版本」的历史物理表：当前版本仍在承接读写，
 * 误标记会造成运维误判，因此在标记前做一次活跃版本校验。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class FormArchiveServiceImpl implements FormArchiveService {

    /** 登记状态：生效 */
    private static final int STATUS_NORMAL = 1;

    /** 归档状态：未归档 */
    private static final int ARCHIVE_NONE = 0;

    /** 归档状态：已归档 */
    private static final int ARCHIVE_DONE = 1;

    private final FormTableRegistryMapper formTableRegistryMapper;

    private final FormDefinitionMapper formDefinitionMapper;

    @Override
    public FormTableRegistryVO archive(Long tableDefinitionId, Long registryId) {
        FormTableRegistryPo registry = requireRegistry(tableDefinitionId, registryId);
        assertNotActive(registry);
        if (isArchived(registry)) {
            return toVo(registry);
        }
        registry.setArchiveStatus(ARCHIVE_DONE);
        registry.setArchiveTime(LocalDateTime.now());
        formTableRegistryMapper.updateById(registry);
        return toVo(registry);
    }

    @Override
    public FormTableRegistryVO unarchive(Long tableDefinitionId, Long registryId) {
        FormTableRegistryPo registry = requireRegistry(tableDefinitionId, registryId);
        if (registry.getArchiveStatus() == null || registry.getArchiveStatus() == ARCHIVE_NONE) {
            return toVo(registry);
        }
        registry.setArchiveStatus(ARCHIVE_NONE);
        registry.setArchiveTime(null);
        formTableRegistryMapper.updateById(registry);
        return toVo(registry);
    }

    /**
     * 查询登记并校验归属：登记不存在或不属于指定表定义时抛出 {@code NOT_FOUND}
     *
     * @param tableDefinitionId 表定义ID（可空，旧模型不校验）
     * @param registryId 登记ID
     * @return 登记实体
     */
    private FormTableRegistryPo requireRegistry(Long tableDefinitionId, Long registryId) {
        FormTableRegistryPo registry = formTableRegistryMapper.selectById(registryId);
        if (registry == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (tableDefinitionId != null && !Objects.equals(tableDefinitionId, registry.getTableDefinitionId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return registry;
    }

    /**
     * 校验登记不是当前生效版本（当前版本仍在读写，不允许归档）
     *
     * @param registry 登记实体
     * @throws BizException 当前版本或发布失败登记时抛出 {@code PARAM_ERROR}
     */
    private void assertNotActive(FormTableRegistryPo registry) {
        if (registry.getStatus() == null || registry.getStatus() != STATUS_NORMAL) {
            throw paramError("仅生效（status=1）的物理表登记可归档，发布失败的登记无需归档");
        }
        if (registry.getTableCode() != null) {
            FormTableRegistryPo latest = latestByTableCode(registry.getTableCode());
            if (latest != null && Objects.equals(latest.getId(), registry.getId())) {
                throw paramError("当前生效版本物理表不允许归档，请先发布新版本");
            }
            return;
        }
        FormDefinitionPo definition = formDefinitionMapper.selectById(registry.getDefinitionId());
        Integer latestVersion = definition == null ? null : definition.getVersion();
        if (Objects.equals(latestVersion, registry.getDefinitionVersion())) {
            throw paramError("当前生效版本物理表不允许归档，请先发布新版本");
        }
    }

    /**
     * 查询某表编码下版本号最大的登记
     *
     * @param tableCode 表编码
     * @return 登记实体；无登记返回 {@code null}
     */
    private FormTableRegistryPo latestByTableCode(String tableCode) {
        LambdaQueryWrapper<FormTableRegistryPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormTableRegistryPo::getTableCode, tableCode);
        wrapper.eq(FormTableRegistryPo::getStatus, STATUS_NORMAL);
        wrapper.orderByDesc(FormTableRegistryPo::getTableVersion);
        wrapper.last("LIMIT 1");
        return formTableRegistryMapper.selectOne(wrapper);
    }

    /**
     * 判断登记是否已归档
     *
     * @param registry 登记实体
     * @return 已归档返回 {@code true}
     */
    private static boolean isArchived(FormTableRegistryPo registry) {
        return registry.getArchiveStatus() != null && registry.getArchiveStatus() == ARCHIVE_DONE;
    }

    /**
     * 登记实体转视图对象
     *
     * @param registry 登记实体
     * @return 登记视图对象
     */
    private static FormTableRegistryVO toVo(FormTableRegistryPo registry) {
        return FormConverter.INSTANCE.toRegistryVo(registry);
    }

    /**
     * 构造参数错误异常
     *
     * @param message 错误消息
     * @return 参数错误业务异常
     */
    private static BizException paramError(String message) {
        return new BizException(ErrorCode.PARAM_ERROR.getCode(), message);
    }
}
