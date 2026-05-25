package com.smartmeeting.config.system;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * 系统参数元数据（L3），用于后台自动生成表单。
 */
public interface SystemConfigDescriptor {

    String key();

    String category();

    ConfigValueType type();

    String defaultValue();

    boolean hotReloadable();

    String description();

    default boolean sensitive() {
        return false;
    }

    default Optional<Predicate<String>> validator() {
        return Optional.empty();
    }
}
