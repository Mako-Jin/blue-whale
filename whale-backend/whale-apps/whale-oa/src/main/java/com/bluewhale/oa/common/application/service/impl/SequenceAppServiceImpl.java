package com.bluewhale.oa.common.application.service.impl;

import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.common.application.service.SequenceAppService;
import com.bluewhale.oa.common.domain.repository.SequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 业务序列号应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class SequenceAppServiceImpl implements SequenceAppService {

    /** 期间格式 yyyyMM */
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM", Locale.ROOT);

    /** 序号宽度 */
    private static final int SEQUENCE_WIDTH = 3;

    /** 序号格式：至少三位，超出后自然扩位 */
    private static final String SEQUENCE_FORMAT = "%0" + SEQUENCE_WIDTH + "d";

    /** 单号分隔符 */
    private static final String SEPARATOR = "-";

    private final SequenceRepository sequenceRepository;

    @Override
    public String nextCode(String bizType, String prefix) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(prefix)) {
            throw OaErrorCode.exception(OaErrorCode.SEQUENCE_INVALID, "业务类型与单号前缀不能为空");
        }
        String period = LocalDate.now().format(PERIOD_FORMATTER);
        long value = sequenceRepository.nextValue(bizType, period);
        return prefix + period + SEPARATOR + String.format(Locale.ROOT, SEQUENCE_FORMAT, value);
    }
}
