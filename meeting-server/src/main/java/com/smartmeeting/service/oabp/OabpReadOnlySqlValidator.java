package com.smartmeeting.service.oabp;

import com.smartmeeting.exception.BusinessException;

/**
 * oabp 会序资料 SQL 只读校验（委托 meeting-config-core）。
 */
public final class OabpReadOnlySqlValidator {

    private OabpReadOnlySqlValidator() {
    }

    /**
     * @param sql host_agenda 配置的 oabpTaskSql
     * @return 去掉首尾空白后的 SQL
     * @throws BusinessException 非 SELECT、含分号或含禁止关键字时
     */
    public static String validateAndNormalize(String sql) {
        try {
            return com.smartmeeting.config.oabp.OabpReadOnlySqlValidator.validateAndNormalize(sql);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, e.getMessage());
        }
    }
}
