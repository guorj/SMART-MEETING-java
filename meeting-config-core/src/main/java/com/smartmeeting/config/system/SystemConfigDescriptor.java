package com.smartmeeting.config.system;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
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

    /** 功能依赖级联：父键关闭时子键在后台不可单独开启。 */
    default Optional<String> parentKey() {
        return Optional.empty();
    }

    /** true 时仅展示，须在 YAML/env 修改并重启 meeting-server。 */
    default boolean requiresRestart() {
        return false;
    }

    /** INTEGER 类型合法下限（含）；空表示不限制。 */
    default OptionalInt intMin() {
        return OptionalInt.empty();
    }

    /** INTEGER 类型合法上限（含）；空表示不限制。 */
    default OptionalInt intMax() {
        return OptionalInt.empty();
    }

    /** 浮点阈值合法下限（含）；空表示不限制。 */
    default OptionalDouble doubleMin() {
        return OptionalDouble.empty();
    }

    /** 浮点阈值合法上限（含）；空表示不限制。 */
    default OptionalDouble doubleMax() {
        return OptionalDouble.empty();
    }

    /** Admin UI 展示的取值范围说明；默认由 min/max 自动生成。 */
    default String valueRangeHint() {
        if (type() == ConfigValueType.INTEGER && intMin().isPresent() && intMax().isPresent()) {
            return ConfigJsonValidators.formatIntRange(intMin().getAsInt(), intMax().getAsInt());
        }
        if (doubleMin().isPresent() && doubleMax().isPresent()) {
            return ConfigJsonValidators.formatDoubleRange(doubleMin().getAsDouble(), doubleMax().getAsDouble());
        }
        return "";
    }
}
