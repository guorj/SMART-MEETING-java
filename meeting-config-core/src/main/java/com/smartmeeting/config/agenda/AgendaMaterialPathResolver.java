package com.smartmeeting.config.agenda;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 解析会序资料落盘目录：admin-server / meeting-server 模块 cwd 不同时，读路径可回退到兄弟模块或仓库 data/。
 */
public final class AgendaMaterialPathResolver {

    private AgendaMaterialPathResolver() {
    }

    public static List<Path> candidateStorageDirs(String configuredDir) {
        Set<Path> out = new LinkedHashSet<>();
        String raw = configuredDir != null && !configuredDir.isBlank()
                ? configuredDir.trim() : "./data/agenda-materials";
        Path primary = Path.of(raw);
        if (!primary.isAbsolute()) {
            primary = Path.of(System.getProperty("user.dir")).resolve(primary).normalize();
        }
        out.add(primary);
        Path userDir = Path.of(System.getProperty("user.dir"));
        if (userDir.getParent() != null) {
            out.add(userDir.getParent().resolve("data").resolve("agenda-materials").normalize());
        }
        out.add(userDir.resolve("..").resolve("meeting-admin-server").resolve("data")
                .resolve("agenda-materials").normalize());
        out.add(userDir.resolve("..").resolve("meeting-server").resolve("data")
                .resolve("agenda-materials").normalize());
        return new ArrayList<>(out);
    }
}
