package com.smartmeeting.service.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MinuteSkillPromptLoaderTest {

    @Test
    @DisplayName("stripFrontmatter 去除 YAML 头")
    void stripFrontmatter_removesYamlHeader() {
        String raw = "---\nname: demo\n---\n\n# Title\nbody";
        assertThat(MinuteSkillPromptLoader.stripFrontmatter(raw)).isEqualTo("# Title\nbody");
    }

    @Test
    @DisplayName("可加载 tech-committee-minutes 模板")
    void loadPromptBody_techCommitteeSkill() {
        MinuteSkillPromptLoader loader = new MinuteSkillPromptLoader();
        Path repoSkills = Path.of("").toAbsolutePath().normalize();
        while (repoSkills != null && !repoSkills.resolve("skills").toFile().isDirectory()) {
            repoSkills = repoSkills.getParent();
        }
        if (repoSkills == null) {
            repoSkills = Path.of("..", "..").toAbsolutePath().normalize();
        }
        ReflectionTestUtils.setField(loader, "configuredSkillsDir", repoSkills.resolve("skills").toString());

        assertThat(loader.loadPromptBody("tech-committee-minutes"))
                .isPresent()
                .get()
                .asString()
                .contains("吉青技术委员会");
    }
}
