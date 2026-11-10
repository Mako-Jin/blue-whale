package com.bluewhale.mall.common.sequence;

import com.bluewhale.mall.common.MallErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 业务编号取号实现（{@code mall_sequence} 行锁 + 原子自增）。
 *
 * <p>为什么不用「查最大单号 + 1」：订单号带日期分段，{@code MAX()} 在删过数据后会回退，
 * 且要先解析全部历史单号格式；流水表只做 +1，与编码格式解耦。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MallSequenceServiceImpl implements MallSequenceService {

    /** 并发冲突时的最大重试次数（正常路径不会走到第二次） */
    private static final int MAX_ATTEMPTS = 5;

    /** 订单号前缀 */
    private static final String ORDER_NO_PREFIX = "MO";

    /** 订单号编号键 */
    private static final String SEQ_KEY_ORDER = "ORDER";

    /** 订单号流水宽度 */
    private static final int ORDER_SERIAL_WIDTH = 6;

    /** 发货单号编号键 */
    private static final String SEQ_KEY_SHIPMENT = "SHIPMENT";

    /** 发货单号前缀 */
    private static final String SHIPMENT_NO_PREFIX = "MS";

    /** 发货单号流水宽度 */
    private static final int SHIPMENT_SERIAL_WIDTH = 6;

    /** 编号键最大长度（与表列宽一致） */
    private static final int KEY_MAX_LENGTH = 64;

    /** 日期格式（按日重置） */
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MallSequenceMapper sequenceMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long next(String seqKey, String seqDate) {
        String key = normalizeKey(seqKey);
        String period = seqDate == null ? "" : seqDate.trim();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Long current = sequenceMapper.lockCurrentSeq(key, period);
            if (current == null) {
                if (createRow(key, period)) {
                    return 1L;
                }
                continue;
            }
            sequenceMapper.increment(key, period);
            return current + 1;
        }
        throw MallErrorCode.exception(MallErrorCode.SEQUENCE_INVALID);
    }

    @Override
    public String nextOrderNo() {
        LocalDate today = LocalDate.now();
        long serial = next(SEQ_KEY_ORDER, today.format(DAY_FORMAT));
        return ORDER_NO_PREFIX + today.format(DAY_FORMAT) + pad(serial);
    }

    @Override
    public String nextShipmentNo() {
        LocalDate today = LocalDate.now();
        long serial = next(SEQ_KEY_SHIPMENT, today.format(DAY_FORMAT));
        return SHIPMENT_NO_PREFIX + today.format(DAY_FORMAT) + pad(serial, SHIPMENT_SERIAL_WIDTH);
    }

    /**
     * 新建流水行（流水号置为 1）。
     *
     * <p>并发下可能被他线程抢先插入，捕获唯一键冲突后由调用方重试：重试会读到对方插好的行，
     * 走自增分支，不会重复发号。</p>
     *
     * @param seqKey 编号键
     * @param seqDate 周期标识
     * @return 插入成功返回 true，被并发抢先返回 false
     */
    private boolean createRow(String seqKey, String seqDate) {
        MallSequenceEntity entity = new MallSequenceEntity();
        entity.setSeqKey(seqKey);
        entity.setSeqDate(seqDate);
        entity.setCurrentSeq(1L);
        entity.setRemark("商城业务编号流水");
        try {
            sequenceMapper.insert(entity);
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    /**
     * 校验并归一编号键。
     *
     * @param seqKey 编号键
     * @return 大写去空白的编号键
     */
    private String normalizeKey(String seqKey) {
        if (seqKey == null || seqKey.isBlank()) {
            throw MallErrorCode.exception(MallErrorCode.SEQUENCE_INVALID, "编号键不能为空");
        }
        String key = seqKey.trim().toUpperCase(Locale.ROOT);
        if (key.length() > KEY_MAX_LENGTH) {
            throw MallErrorCode.exception(MallErrorCode.SEQUENCE_INVALID,
                    "编号键不得超过 " + KEY_MAX_LENGTH + " 字符");
        }
        return key;
    }

    /**
     * 流水号左补零（超过宽度时按实际位数输出，不截断）。
     *
     * @param serial 流水号
     * @return 补零后的文本
     */
    private String pad(long serial) {
        return pad(serial, ORDER_SERIAL_WIDTH);
    }

    /**
     * 补零到指定宽度。
     *
     * @param serial 流水号
     * @param width 宽度
     * @return 补零后的文本
     */
    private String pad(long serial, int width) {
        return String.format("%0" + width + "d", serial);
    }
}
