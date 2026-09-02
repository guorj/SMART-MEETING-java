package com.smartmeeting.admin.service;

import com.smartmeeting.admin.api.dto.MinuteSkillCatalogItemDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 扫描仓库 skills 目录下各 Skill 的 SKILL.md，解析 frontmatter 供 Admin 纪要 Skill 页展示。
 */
@Slf4j
@Service
public class MinuteSkillCatalogService {

    private static final Pattern FRONTMATTER = Pattern.compile("^---\\s*\\n(.*?)\\n---", Pattern.DOTALL);
    private static final Pattern NAME_LINE = Pattern.compile("^name:\\s*\"?([^\"\\n]+)\"?\\s*$", Pattern.MULTILINE);
    private static final Pattern DESC_LINE = Pattern.compile("^description:\\s*\"([^\"]+)\"\\s*$", Pattern.MULTILINE);

    @Value("${smart-meeting.skills-dir:}")
    private String configuredSkillsDir;

    /**
     * 列出可用 Skill 目录项（按 name 排序）。
     *
     * @return Skill 列表；目录不存在时返回空列表
     */
    public List<MinuteSkillCatalogItemDto> listCatalog() {
        Path skillsRoot = resolveSkillsRoot();
        if (skillsRoot == null || !Files.isDirectory(skillsRoot)) {
            log.warn("Skills directory not found: configured={}, cwd={}", configuredSkillsDir, Path.of("").toAbsolutePath());
            return List.of();
        }
        List<MinuteSkillCatalogItemDto> items = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(skillsRoot)) {
            dirs.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(dir -> parseSkillDir(skillsRoot, dir).ifPresent(items::add));
        } catch (IOException e) {
            log.warn("Failed to list skills dir {}: {}", skillsRoot, e.getMessage());
        }
        items.sort(Comparator.comparing(MinuteSkillCatalogItemDto::getName, String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    /**
     * 校验 Skill 名是否存在于目录中。
     *
     * @param skillName Skill 名；null/空白视为合法（表示不绑定）
     * @return 存在或为空时 true
     */
    public boolean isKnownSkill(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return true;
        }
        String trimmed = skillName.trim();
        return listCatalog().stream().anyMatch(item -> trimmed.equals(item.getName()));
    }

    private java.util.Optional<MinuteSkillCatalogItemDto> parseSkillDir(Path skillsRoot, Path dir) {
        Path skillFile = dir.resolve("SKILL.md");
        if (!Files.isRegularFile(skillFile)) {
            return java.util.Optional.empty();
        }
        try {
            String content = Files.readString(skillFile, StandardCharsets.UTF_8);
            String name = extractFrontmatterField(content, NAME_LINE);
            if (name == null || name.isBlank()) {
                name = dir.getFileName().toString();
            }
            String description = extractFrontmatterField(content, DESC_LINE);
            if (description == null) {
                description = "";
            }
            String relativePath = skillsRoot.relativize(skillFile).toString().replace('\\', '/');
            return java.util.Optional.of(new MinuteSkillCatalogItemDto(name.trim(), description.trim(), relativePath));
        } catch (IOException e) {
            log.warn("Failed to read skill file {}: {}", skillFile, e.getMessage());
            return java.util.Optional.empty();
        }
    }

    private static String extractFrontmatterField(String content, Pattern fieldPattern) {
        Matcher fm = FRONTMATTER.matcher(content);
        if (!fm.find()) {
            return null;
        }
        Matcher m = fieldPattern.matcher(fm.group(1));
        return m.find() ? m.group(1).trim() : null;
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
