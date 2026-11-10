package com.bluewhale.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mes.quality.dto.MesReworkActionRequest;
import com.bluewhale.mes.quality.entity.MesReworkOrder;
import com.bluewhale.mes.quality.mapper.MesReworkOrderMapper;
import com.bluewhale.mes.quality.vo.MesReworkOrderVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MES 返工单服务单元测试（M3）。
 *
 * <p>覆盖返工状态机 CREATED → IN_PROGRESS → COMPLETED 与非法跳转拒绝。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MesReworkServiceImplTest {

    @Mock
    private MesReworkOrderMapper reworkMapper;

    @InjectMocks
    private MesReworkServiceImpl reworkService;

    /**
     * 初始化 MyBatis-Plus 表元数据
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, MesReworkOrder.class);
    }

    /**
     * 开工：CREATED → IN_PROGRESS
     */
    @Test
    @DisplayName("返工开工：CREATED 可开工")
    void startShouldAdvanceFromCreated() {
        when(reworkMapper.selectById(1L)).thenReturn(rework("CREATED"));
        when(reworkMapper.updateById(any(MesReworkOrder.class))).thenReturn(1);

        MesReworkOrderVO vo = reworkService.start(1L, action());

        assertEquals("IN_PROGRESS", vo.getStatus());
        assertNotNull(vo.getStartedAt());
        assertEquals("tester", vo.getOperator());
    }

    /**
     * 开工：已开工不可重复开工
     */
    @Test
    @DisplayName("返工开工：IN_PROGRESS 拒绝重复开工")
    void startShouldRejectWhenInProgress() {
        when(reworkMapper.selectById(1L)).thenReturn(rework("IN_PROGRESS"));

        assertThrows(BizException.class, () -> reworkService.start(1L, action()));
        verify(reworkMapper, never()).updateById(any(MesReworkOrder.class));
    }

    /**
     * 完工：IN_PROGRESS → COMPLETED
     */
    @Test
    @DisplayName("返工完工：IN_PROGRESS 可完工")
    void completeShouldAdvanceFromInProgress() {
        when(reworkMapper.selectById(1L)).thenReturn(rework("IN_PROGRESS"));
        when(reworkMapper.updateById(any(MesReworkOrder.class))).thenReturn(1);

        MesReworkOrderVO vo = reworkService.complete(1L, action());

        assertEquals("COMPLETED", vo.getStatus());
        assertNotNull(vo.getCompletedAt());
    }

    /**
     * 完工：未开工不可直接完工
     */
    @Test
    @DisplayName("返工完工：CREATED 不可直接完工")
    void completeShouldRejectWhenCreated() {
        when(reworkMapper.selectById(1L)).thenReturn(rework("CREATED"));

        assertThrows(BizException.class, () -> reworkService.complete(1L, action()));
    }

    /**
     * 构建返工单
     *
     * @param status 状态
     * @return 返工单
     */
    private MesReworkOrder rework(String status) {
        MesReworkOrder rework = new MesReworkOrder();
        rework.setId(1L);
        rework.setReworkNo("RWO-1");
        rework.setNcrId(1L);
        rework.setNcrNo("NCR-1");
        rework.setStatus(status);
        return rework;
    }

    /**
     * 构建动作请求
     *
     * @return 动作请求
     */
    private MesReworkActionRequest action() {
        MesReworkActionRequest request = new MesReworkActionRequest();
        request.setOperator("tester");
        return request;
    }
}
