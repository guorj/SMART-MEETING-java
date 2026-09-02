package com.smartmeeting.service.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 按 Skill 名加载 SKILL.md 正文，供 Step 4 直调 LLM 时注入 system prompt。
 *
 * <p>不走 OpenClaw Gateway；Admin 绑定的 {@code minute_skill_name} 仅决定加载哪份模板。
 */
@Slf4j
@Component
public class MinuteSkillPromptLoader {

    private static final Pattern FRONTMATTER = Pattern.compile("^---\\s*\\r?\\n.*?\\r?\\n---\\s*\\r?\\n?", Pattern.DOTALL);

    @Value("${smart-meeting.skills-dir:}")
    private String configuredSkillsDir;

    /**
     * 加载 Skill 模板正文（已去除 YAML frontmatter）。
     *
     * @param skillName Skill 名（与目录名或 frontmatter name 一致）
     * @return 模板正文；未找到或读失败时 empty
     */
    public Optional<String> loadPromptBody(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return Optional.empty();
        }
        Path skillsRoot = resolveSkillsRoot();
        if (skillsRoot == null) {
            log.warn("Skills directory not found for prompt load: skill={}", skillName);
            return Optional.empty();
        }
        String trimmed = skillName.trim();

        Path direct = skillsRoot.resolve(trimmed).resolve("SKILL.md");
        if (Files.isRegularFile(direct)) {
            return readBody(direct);
        }

        try (Stream<Path> dirs = Files.list(skillsRoot)) {
            Optional<String> matched = dirs.filter(Files::isDirectory)
                    .map(dir -> dir.resolve("SKILL.md"))
                    .filter(Files::isRegularFile)
                    .map(path -> readBody(path).flatMap(body -> matchesSkillName(path, trimmed, body) ? Optional.of(body) : Optional.<String>empty()))
                    .flatMap(Optional::stream)
                    .findFirst();
            if (matched.isPresent()) {
                return matched;
            }
        } catch (IOException e) {
            log.warn("Failed to scan skills dir {}: {}", skillsRoot, e.getMessage());
        }
        log.warn("Skill prompt not found: skill={}, root={}", trimmed, skillsRoot);
        return Optional.empty();
    }

    private Optional<String> readBody(Path skillFile) {
        try {
            String raw = Files.readString(skillFile, StandardCharsets.UTF_8);
            String body = stripFrontmatter(raw).trim();
            if (body.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(body);
        } catch (IOException e) {
            log.warn("Failed to read skill file {}: {}", skillFile, e.getMessage());
            return Optional.empty();
        }
    }

    private static boolean matchesSkillName(Path skillFile, String skillName, String body) {
        if (skillFile.getParent() != null && skillName.equalsIgnoreCase(skillFile.getParent().getFileName().toString())) {
            return true;
        }
        return body.toLowerCase().contains("name: " + skillName.toLowerCase())
                || body.toLowerCase().contains("name: \"" + skillName.toLowerCase() + "\"");
    }

    static String stripFrontmatter(String raw) {
        if (raw == null) {
            return "";
        }
        return FRONTMATTER.matcher(raw).replaceFirst("");
    }

    private Path resolveSkillsRoot() {
        if (configuredSkillsDir != null && !configuredSkillsDir.isBlank()) {
            Path configured = Path.of(configuredSkillsDir.trim());
            if (Files.isDirectory(configured)) {
                return configured.normalize();
            }
        }
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path[] candidates = {
                cwd.resolve("skills"),
                cwd.resolve("../skills"),
                cwd.resolve("../../skills"),
                cwd.getParent() != null ? cwd.getParent().resolve("skills") : null
        };
        for (Path candidate : candidates) {
            if (candidate != null && Files.isDirectory(candidate)) {
                return candidate.normalize();
            }
        }
        return null;
    }
}
