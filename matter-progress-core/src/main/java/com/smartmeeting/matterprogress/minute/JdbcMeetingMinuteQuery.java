package com.smartmeeting.matterprogress.minute;

import com.smartmeeting.matterprogress.jdbc.JdbcRowHelpers;
import com.smartmeeting.matterprogress.model.MinuteSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** JDBC 实现 {@link MeetingMinuteQuery} */
public class JdbcMeetingMinuteQuery implements MeetingMinuteQuery {

    private static final Logger log = LoggerFactory.getLogger(JdbcMeetingMinuteQuery.class);

    private final JdbcTemplate jdbc;

    public JdbcMeetingMinuteQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<MinuteSnapshot> templateMinutesSinceDays(int presetTypeCode, int days) {
        int d = Math.max(1, days);
        Instant since = Instant.now().minus(d, ChronoUnit.DAYS);
        return jdbc.query(
                """
                SELECT m.meeting_id, im.title, m.preset_type_code, m.generated_at,
                       m.content_markdown, m.content_url, m.generation_status
                FROM int_meeting_minute m
                LEFT JOIN int_meeting im ON im.id = m.meeting_id
                WHERE m.preset_type_code = ?
                  AND m.generation_status = 'READY'
                  AND m.generated_at >= ?
                ORDER BY m.generated_at DESC
                """,
                (rs, rowNum) -> mapRow(rs),
                presetTypeCode,
                Timestamp.from(since));
    }

    @Override
    public List<MinuteSnapshot> byMeetingIds(List<String> meetingIds) {
        if (meetingIds == null || meetingIds.isEmpty()) {
            return List.of();
        }
        List<MinuteSnapshot> out = new ArrayList<>();
        for (String id : meetingIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            List<MinuteSnapshot> rows = jdbc.query(
                    """
                    SELECT m.meeting_id, im.title, m.preset_type_code, m.generated_at,
                           m.content_markdown, m.content_url, m.generation_status
                    FROM int_meeting_minute m
                    LEFT JOIN int_meeting im ON im.id = m.meeting_id
                    WHERE m.meeting_id = ?
                    """,
                    (rs, rowNum) -> mapRow(rs),
                    id.trim());
            if (!rows.isEmpty()) {
                MinuteSnapshot snap = rows.get(0);
                if (snap.presetTypeCode() != null && snap.presetTypeCode() >= 1 && snap.presetTypeCode() <= 5) {
                    log.warn("MEETING_IDS job 命中模板会纪要 meetingId={} preset={}", id, snap.presetTypeCode());
                }
                out.add(snap);
            }
        }
        return out;
    }

    private static MinuteSnapshot mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp gen = rs.getTimestamp("generated_at");
        return new MinuteSnapshot(
                rs.getString("meeting_id"),
                rs.getString("title"),
                JdbcRowHelpers.readInteger(rs, "preset_type_code"),
                gen != null ? gen.toInstant() : null,
                rs.getString("content_markdown"),
                rs.getString("content_url"),
                rs.getString("generation_status"));
    }
}
