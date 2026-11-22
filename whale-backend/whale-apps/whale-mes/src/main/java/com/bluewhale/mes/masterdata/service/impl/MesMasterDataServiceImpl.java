package com.bluewhale.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.masterdata.converter.MesMasterDataConverter;
import com.bluewhale.mes.masterdata.dto.MesMasterDataQueryRequest;
import com.bluewhale.mes.masterdata.dto.MesMasterDataSaveRequest;
import com.bluewhale.mes.masterdata.entity.MesDefectCode;
import com.bluewhale.mes.masterdata.entity.MesMasterDataEntity;
import com.bluewhale.mes.masterdata.entity.MesOperation;
import com.bluewhale.mes.masterdata.entity.MesProductionLine;
import com.bluewhale.mes.masterdata.entity.MesReasonCode;
import com.bluewhale.mes.masterdata.entity.MesShift;
import com.bluewhale.mes.masterdata.entity.MesStation;
import com.bluewhale.mes.masterdata.entity.MesTeam;
import com.bluewhale.mes.masterdata.entity.MesWorkCenter;
import com.bluewhale.mes.masterdata.entity.MesWorkshop;
import com.bluewhale.mes.masterdata.enums.MasterDataKind;
import com.bluewhale.mes.masterdata.mapper.MesDefectCodeMapper;
import com.bluewhale.mes.masterdata.mapper.MesOperationMapper;
import com.bluewhale.mes.masterdata.mapper.MesProductionLineMapper;
import com.bluewhale.mes.masterdata.mapper.MesReasonCodeMapper;
import com.bluewhale.mes.masterdata.mapper.MesShiftMapper;
import com.bluewhale.mes.masterdata.mapper.MesStationMapper;
import com.bluewhale.mes.masterdata.mapper.MesTeamMapper;
import com.bluewhale.mes.masterdata.mapper.MesWorkCenterMapper;
import com.bluewhale.mes.masterdata.mapper.MesWorkshopMapper;
import com.bluewhale.mes.masterdata.service.MesMasterDataService;
import com.bluewhale.mes.masterdata.vo.MesMasterDataVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * MES 车间主数据应用服务实现
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MesMasterDataServiceImpl implements MesMasterDataService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final MesWorkshopMapper workshopMapper;

    private final MesProductionLineMapper lineMapper;

    private final MesWorkCenterMapper workCenterMapper;

    private final MesStationMapper stationMapper;

    private final MesTeamMapper teamMapper;

    private final MesShiftMapper shiftMapper;

    private final MesOperationMapper operationMapper;

    private final MesDefectCodeMapper defectCodeMapper;

    private final MesReasonCodeMapper reasonCodeMapper;

    private final MesMasterDataConverter converter;

    @Override
    public PageResult<MesMasterDataVO> page(MasterDataKind kind, MesMasterDataQueryRequest request) {
        QueryWrapper<MesMasterDataEntity> wrapper = new QueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getCode()), "code", request.getCode());
        wrapper.like(StringUtils.hasText(request.getName()), "name", request.getName());
        wrapper.eq(StringUtils.hasText(request.getStatus()), "status", request.getStatus());
        if (kind.isWorkshopDimension() && request.getWorkshopId() != null) {
            wrapper.eq("workshop_id", request.getWorkshopId());
        }
        if (kind.isLineDimension() && request.getLineId() != null) {
            wrapper.eq("line_id", request.getLineId());
        }
        if (kind.isWorkCenterDimension() && request.getWorkCenterId() != null) {
            wrapper.eq("work_center_id", request.getWorkCenterId());
        }
        wrapper.orderByAsc("code");
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<MesMasterDataEntity> page = mapper(kind).selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<MesMasterDataVO> records = page.getRecords().stream().map(converter::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public MesMasterDataVO detail(MasterDataKind kind, Long id) {
        return converter.toVo(requireEntity(kind, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesMasterDataVO create(MasterDataKind kind, MesMasterDataSaveRequest request) {
        MesMasterDataEntity entity = newEntity(kind);
        apply(entity, request);
        if (entity.getStatus() == null) {
            entity.setStatus("ENABLED");
        }
        validate(kind, entity, null);
        mapper(kind).insert(entity);
        return converter.toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesMasterDataVO update(MasterDataKind kind, Long id, MesMasterDataSaveRequest request) {
        MesMasterDataEntity entity = requireEntity(kind, id);
        apply(entity, request);
        validate(kind, entity, id);
        mapper(kind).updateById(entity);
        return converter.toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(MasterDataKind kind, Long id) {
        MesMasterDataEntity entity = requireEntity(kind, id);
        assertNotReferenced(kind, entity.getId());
        mapper(kind).deleteById(id);
    }

    @Override
    public List<MesMasterDataVO> options(MasterDataKind kind) {
        QueryWrapper<MesMasterDataEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "ENABLED").orderByAsc("code");
        return mapper(kind).selectList(wrapper).stream().map(converter::toVo).toList();
    }

    /**
     * 按主键查询主数据，不存在时抛 404。
     *
     * @param kind 主数据类型
     * @param id   主键 ID
     * @return 主数据实体
     */
    private MesMasterDataEntity requireEntity(MasterDataKind kind, Long id) {
        MesMasterDataEntity entity = id == null ? null : mapper(kind).selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.masterdata.found", "{0}不存在：{1}", kind.getLabel(), id);
        }
        return entity;
    }

    /**
     * 将请求字段按实体具体类型写入实体。
     *
     * @param entity  待写入实体
     * @param request 保存请求
     */
    private void apply(MesMasterDataEntity entity, MesMasterDataSaveRequest request) {
        entity.setCode(request.getCode());
        entity.setName(request.getName());
        entity.setRemark(request.getRemark());
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        if (entity instanceof MesWorkshop workshop) {
            applyWorkshop(workshop, request);
        } else if (entity instanceof MesProductionLine line) {
            applyLine(line, request);
        } else if (entity instanceof MesWorkCenter workCenter) {
            applyWorkCenter(workCenter, request);
        } else if (entity instanceof MesStation station) {
            applyStation(station, request);
        } else if (entity instanceof MesTeam team) {
            applyTeam(team, request);
        } else if (entity instanceof MesShift shift) {
            applyShift(shift, request);
        } else if (entity instanceof MesOperation operation) {
            applyOperation(operation, request);
        } else if (entity instanceof MesDefectCode defectCode) {
            defectCode.setCategory(request.getCategory());
        } else if (entity instanceof MesReasonCode reasonCode) {
            applyReasonCode(reasonCode, request);
        }
    }

    /**
     * 写入车间专属字段。
     *
     * @param entity  车间实体
     * @param request 保存请求
     */
    private void applyWorkshop(MesWorkshop entity, MesMasterDataSaveRequest request) {
        entity.setAddress(request.getAddress());
        entity.setManagerName(request.getManagerName());
    }

    /**
     * 写入产线专属字段。
     *
     * @param entity  产线实体
     * @param request 保存请求
     */
    private void applyLine(MesProductionLine entity, MesMasterDataSaveRequest request) {
        if (request.getWorkshopId() != null) {
            entity.setWorkshopId(request.getWorkshopId());
        }
    }

    /**
     * 写入工作中心专属字段。
     *
     * @param entity  工作中心实体
     * @param request 保存请求
     */
    private void applyWorkCenter(MesWorkCenter entity, MesMasterDataSaveRequest request) {
        if (request.getWorkshopId() != null) {
            entity.setWorkshopId(request.getWorkshopId());
        }
        if (request.getLineId() != null) {
            entity.setLineId(request.getLineId());
        }
        if (request.getCenterType() != null) {
            entity.setCenterType(request.getCenterType());
        } else if (entity.getCenterType() == null) {
            entity.setCenterType("MACHINE");
        }
        entity.setCapacityPerShift(request.getCapacityPerShift() == null
                ? BigDecimal.ZERO : request.getCapacityPerShift());
    }

    /**
     * 写入工位专属字段。
     *
     * @param entity  工位实体
     * @param request 保存请求
     */
    private void applyStation(MesStation entity, MesMasterDataSaveRequest request) {
        if (request.getLineId() != null) {
            entity.setLineId(request.getLineId());
        }
        if (request.getWorkCenterId() != null) {
            entity.setWorkCenterId(request.getWorkCenterId());
        }
    }

    /**
     * 写入班组专属字段。
     *
     * @param entity  班组实体
     * @param request 保存请求
     */
    private void applyTeam(MesTeam entity, MesMasterDataSaveRequest request) {
        if (request.getWorkshopId() != null) {
            entity.setWorkshopId(request.getWorkshopId());
        }
        entity.setLeaderName(request.getLeaderName());
    }

    /**
     * 写入班次专属字段。
     *
     * @param entity  班次实体
     * @param request 保存请求
     */
    private void applyShift(MesShift entity, MesMasterDataSaveRequest request) {
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setCrossDay(request.getCrossDay() == null ? 0 : request.getCrossDay());
    }

    /**
     * 写入工序专属字段。
     *
     * @param entity  工序实体
     * @param request 保存请求
     */
    private void applyOperation(MesOperation entity, MesMasterDataSaveRequest request) {
        if (request.getOperationType() != null) {
            entity.setOperationType(request.getOperationType());
        } else if (entity.getOperationType() == null) {
            entity.setOperationType("PROCESS");
        }
        entity.setStandardMinutes(request.getStandardMinutes() == null
                ? BigDecimal.ZERO : request.getStandardMinutes());
    }

    /**
     * 写入异常原因专属字段。
     *
     * @param entity  异常原因实体
     * @param request 保存请求
     */
    private void applyReasonCode(MesReasonCode entity, MesMasterDataSaveRequest request) {
        entity.setCategory(request.getCategory());
        entity.setAffectAvailability(request.getAffectAvailability() == null
                ? 0 : request.getAffectAvailability());
    }

    /**
     * 校验编码唯一性与上下级引用一致性。
     *
     * @param kind      主数据类型
     * @param entity    待保存实体
     * @param excludeId 更新时排除自身 ID，新增传 {@code null}
     */
    private void validate(MasterDataKind kind, MesMasterDataEntity entity, Long excludeId) {
        assertCodeUnique(kind, entity.getCode(), excludeId);
        switch (kind) {
            case LINE -> requireParent(MasterDataKind.WORKSHOP, ((MesProductionLine) entity).getWorkshopId(), "所属车间");
            case WORK_CENTER -> {
                MesWorkCenter workCenter = (MesWorkCenter) entity;
                requireParent(MasterDataKind.WORKSHOP, workCenter.getWorkshopId(), "所属车间");
                MesProductionLine line = (MesProductionLine) requireParent(
                        MasterDataKind.LINE, workCenter.getLineId(), "所属产线");
                if (!workCenter.getWorkshopId().equals(line.getWorkshopId())) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.masterdata.selected-production-line-belong-selected-workshop",
                                  "所选产线不属于所选车间");
                }
            }
            case STATION -> {
                MesStation station = (MesStation) entity;
                MesProductionLine line = (MesProductionLine) requireParent(
                        MasterDataKind.LINE, station.getLineId(), "所属产线");
                MesWorkCenter workCenter = (MesWorkCenter) requireParent(
                        MasterDataKind.WORK_CENTER, station.getWorkCenterId(), "所属工作中心");
                if (!line.getId().equals(workCenter.getLineId())) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.masterdata.selected-work-center-belong-selected-production",
                                  "所选工作中心不属于所选产线");
                }
            }
            case TEAM -> requireParent(MasterDataKind.WORKSHOP, ((MesTeam) entity).getWorkshopId(), "所属车间");
            case SHIFT -> {
                MesShift shift = (MesShift) entity;
                if (shift.getStartTime() == null || shift.getEndTime() == null) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.masterdata.shift-start-time-end-time-empty", "班次开始时间和结束时间不能为空");
                }
                if (shift.getStartTime().equals(shift.getEndTime())) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.masterdata.shift-start-time-end-time-same", "班次开始时间和结束时间不能相同");
                }
            }
            default -> {
                // 其他类型无跨表必填校验
            }
        }
    }

    /**
     * 校验编码在租户内唯一（逻辑删除行仍占用，避免复用冲突）。
     *
     * @param kind      主数据类型
     * @param code      编码
     * @param excludeId 更新时排除自身 ID，新增传 {@code null}
     */
    private void assertCodeUnique(MasterDataKind kind, String code, Long excludeId) {
        QueryWrapper<MesMasterDataEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("code", code);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        if (mapper(kind).selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.masterdata.code-already-exists", "{0}编码已存在：{1}", kind.getLabel(), code);
        }
    }

    /**
     * 校验上级主数据存在。
     *
     * @param parentKind 上级主数据类型
     * @param id         上级主键 ID
     * @param label      上级中文名称，用于错误提示
     * @return 上级主数据实体
     */
    private MesMasterDataEntity requireParent(MasterDataKind parentKind, Long id, String label) {
        if (id == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.masterdata.empty", "{0}不能为空", label);
        }
        MesMasterDataEntity parent = mapper(parentKind).selectById(id);
        if (parent == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.masterdata.found-2", "{0}不存在：{1}", label, id);
        }
        return parent;
    }

    /**
     * 校验主数据未被下级引用，避免删除后产生悬空引用。
     *
     * @param kind 主数据类型
     * @param id   主键 ID
     */
    private void assertNotReferenced(MasterDataKind kind, Long id) {
        long references;
        switch (kind) {
            case WORKSHOP -> references = count("mes_md_line", "workshop_id", id)
                    + count("mes_md_work_center", "workshop_id", id)
                    + count("mes_md_team", "workshop_id", id);
            case LINE -> references = count("mes_md_work_center", "line_id", id)
                    + count("mes_md_station", "line_id", id);
            case WORK_CENTER -> references = count("mes_md_station", "work_center_id", id);
            default -> references = 0;
        }
        if (references > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.masterdata.referenced-cannot-deleted", "{0}已被引用，不能删除", kind.getLabel());
        }
    }

    /**
     * 统计指定表按列引用某个主数据 ID 的行数。
     *
     * @param table  逻辑表名（仅支持主数据子表）
     * @param column 引用列名
     * @param id     被引用主键
     * @return 引用行数
     */
    private long count(String table, String column, Long id) {
        // 复用同一套 Mapper 无法跨表统计，这里按表名分派到对应 Mapper 查询。
        return switch (table) {
            case "mes_md_line" -> lineMapper.selectCount(new QueryWrapper<MesProductionLine>().eq(column, id));
            case "mes_md_work_center" -> workCenterMapper.selectCount(
                    new QueryWrapper<MesWorkCenter>().eq(column, id));
            case "mes_md_station" -> stationMapper.selectCount(new QueryWrapper<MesStation>().eq(column, id));
            case "mes_md_team" -> teamMapper.selectCount(new QueryWrapper<MesTeam>().eq(column, id));
            default -> 0L;
        };
    }

    /**
     * 按主数据类型取得共用实体视角的 Mapper。
     *
     * @param kind 主数据类型
     * @return 基类 Mapper
     */
    @SuppressWarnings("unchecked")
    private BaseMapper<MesMasterDataEntity> mapper(MasterDataKind kind) {
        return switch (kind) {
            case WORKSHOP -> (BaseMapper<MesMasterDataEntity>) (BaseMapper<?>) workshopMapper;
            case LINE -> (BaseMapper<MesMasterDataEntity>) (BaseMapper<?>) lineMapper;
            case WORK_CENTER -> (BaseMapper<MesMasterDataEntity>) (BaseMapper<?>) workCenterMapper;
            case STATION -> (BaseMapper<MesMasterDataEntity>) (BaseMapper<?>) stationMapper;
            case TEAM -> (BaseMapper<MesMasterDataEntity>) (BaseMapper<?>) teamMapper;
            case SHIFT -> (BaseMapper<MesMasterDataEntity>) (BaseMapper<?>) shiftMapper;
            case OPERATION -> (BaseMapper<MesMasterDataEntity>) (BaseMapper<?>) operationMapper;
            case DEFECT_CODE -> (BaseMapper<MesMasterDataEntity>) (BaseMapper<?>) defectCodeMapper;
            case REASON_CODE -> (BaseMapper<MesMasterDataEntity>) (BaseMapper<?>) reasonCodeMapper;
        };
    }

    /**
     * 按主数据类型实例化实体。
     *
     * @param kind 主数据类型
     * @return 空实体
     */
    private MesMasterDataEntity newEntity(MasterDataKind kind) {
        return switch (kind) {
            case WORKSHOP -> new MesWorkshop();
            case LINE -> new MesProductionLine();
            case WORK_CENTER -> new MesWorkCenter();
            case STATION -> new MesStation();
            case TEAM -> new MesTeam();
            case SHIFT -> new MesShift();
            case OPERATION -> new MesOperation();
            case DEFECT_CODE -> new MesDefectCode();
            case REASON_CODE -> new MesReasonCode();
        };
    }
}
