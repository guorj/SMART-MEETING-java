package com.smartmeeting.admin.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MinuteSkillCatalogServiceTest {

    @Test
    @DisplayName("扫描仓库 skills 目录应包含 tech-committee-minutes")
    void listCatalog_includesTechCommitteeSkill() {
        MinuteSkillCatalogService service = new MinuteSkillCatalogService();
        Path repoSkills = Path.of("").toAbsolutePath().normalize();
        while (repoSkills != null && !repoSkills.resolve("skills").toFile().isDirectory()) {
            repoSkills = repoSkills.getParent();
        }
        if (repoSkills == null) {
            repoSkills = Path.of("..", "..").toAbsolutePath().normalize();
        }
        ReflectionTestUtils.setField(service, "configuredSkillsDir", repoSkills.resolve("skills").toString());

        List<?> catalog = service.listCatalog();
        assertThat(catalog).isNotEmpty();
        assertThat(service.isKnownSkill("tech-committee-minutes")).isTrue();
    }
}
