package com.bluewhale.mes.process.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.mes.process.dto.MesProcessRouteReceiveRequest;
import com.bluewhale.mes.process.dto.MesProcessRouteWithdrawRequest;
import com.bluewhale.mes.process.entity.MesProcessRoute;
import com.bluewhale.mes.process.mapper.MesProcessRouteMapper;
import com.bluewhale.mes.process.service.MesProcessRouteService;
import com.bluewhale.mes.process.vo.MesProcessRouteVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MES 工艺路线接收服务实现
 *
 * <p>同一 {@code routeNo + versionNo} 且校验和一致时直接返回既有记录，
 * 不重复落库；校验和变化时覆盖为最新内容。撤回先落本地状态，未接收过
 * 也写入墓碑，避免乱序回调把旧路线重新激活。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesProcessRouteServiceImpl implements MesProcessRouteService {

    /** 生效状态 */
    private static final String STATUS_ACTIVE = "ACTIVE";

    /** 撤回状态 */
    private static final String STATUS_WITHDRAWN = "WITHDRAWN";

    /** JSON 序列化器 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MesProcessRouteMapper routeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesProcessRouteVO receive(MesProcessRouteReceiveRequest request) {
        MesProcessRoute existing = find(request.getRouteNo(), request.getVersionNo());
        if (existing != null && STATUS_ACTIVE.equals(existing.getStatus())
                && StringUtils.hasText(request.getChecksum())
                && request.getChecksum().equals(existing.getChecksum())) {
            log.info("工艺路线重复下发命中幂等: routeNo={}, version={}",
                    request.getRouteNo(), request.getVersionNo());
            return toVo(existing, true);
        }
        MesProcessRoute entity = existing == null ? new MesProcessRoute() : existing;
        entity.setPdmRouteNo(request.getRouteNo());
        entity.setVersionNo(request.getVersionNo());
        entity.setPartCode(request.getPartCode());
        entity.setPartName(request.getPartName());
        entity.setStatus(STATUS_ACTIVE);
        entity.setChecksum(request.getChecksum());
        entity.setStepCount(request.getSteps() == null ? 0 : request.getSteps().size());
        entity.setPayload(toJson(request));
        entity.setPushedAt(LocalDateTime.now());
        entity.setWithdrawnAt(null);
        if (existing == null) {
            routeMapper.insert(entity);
        } else {
            routeMapper.updateById(entity);
        }
        log.info("工艺路线已接收: routeNo={}, version={}, steps={}",
                request.getRouteNo(), request.getVersionNo(), entity.getStepCount());
        return toVo(entity, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesProcessRouteVO withdraw(MesProcessRouteWithdrawRequest request) {
        MesProcessRoute existing = find(request.getRouteNo(), request.getVersionNo());
        MesProcessRoute entity = existing == null ? new MesProcessRoute() : existing;
        entity.setPdmRouteNo(request.getRouteNo());
        entity.setVersionNo(request.getVersionNo());
        entity.setStatus(STATUS_WITHDRAWN);
        entity.setWithdrawnAt(LocalDateTime.now());
        if (existing == null) {
            entity.setStepCount(0);
            routeMapper.insert(entity);
        } else {
            routeMapper.updateById(entity);
        }
        log.info("工艺路线已撤回: routeNo={}, version={}", request.getRouteNo(), request.getVersionNo());
        return toVo(entity, existing == null);
    }

    /**
     * 按幂等键查询接收记录
     *
     * @param routeNo   路线编号
     * @param versionNo 版本号
     * @return 记录，不存在返回 {@code null}
     */
    private MesProcessRoute find(String routeNo, String versionNo) {
        List<MesProcessRoute> records = routeMapper.selectList(new LambdaQueryWrapper<MesProcessRoute>()
                .eq(MesProcessRoute::getPdmRouteNo, routeNo)
                .eq(MesProcessRoute::getVersionNo, versionNo)
                .last("LIMIT 1"));
        return records.isEmpty() ? null : records.get(0);
    }

    /**
     * 序列化下发内容
     *
     * @param request 下发请求
     * @return JSON
     */
    private String toJson(MesProcessRouteReceiveRequest request) {
        try {
            return OBJECT_MAPPER.writeValueAsString(request);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "biz.mes.process.failed-routing-content-serialize", "工艺路线内容序列化失败");
        }
    }

    /**
     * 实体转视图
     *
     * @param entity     实体
     * @param idempotent 是否幂等命中
     * @return 视图
     */
    private MesProcessRouteVO toVo(MesProcessRoute entity, boolean idempotent) {
        MesProcessRouteVO vo = new MesProcessRouteVO();
        vo.setId(entity.getId());
        vo.setPdmRouteNo(entity.getPdmRouteNo());
        vo.setVersionNo(entity.getVersionNo());
        vo.setPartCode(entity.getPartCode());
        vo.setPartName(entity.getPartName());
        vo.setStatus(entity.getStatus());
        vo.setChecksum(entity.getChecksum());
        vo.setStepCount(entity.getStepCount());
        vo.setPushedAt(entity.getPushedAt());
        vo.setWithdrawnAt(entity.getWithdrawnAt());
        vo.setIdempotent(idempotent);
        return vo;
    }
}
