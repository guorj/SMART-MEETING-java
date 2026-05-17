package com.smartmeeting.tools;

import com.smartmeeting.service.FeishuService;
import com.smartmeeting.service.feishu.FeishuResourceRef;
import com.smartmeeting.service.feishu.FeishuResourceResolver;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

/**
 * 一次性：从生产库读取 preset=1 会序 URL，调用飞书 API 拉取正文片段。
 * 运行: mvn -q -DskipTests compile exec:java -Dexec.mainClass=com.smartmeeting.tools.FeishuAgendaFetchProbe -Dexec.classpathScope=test
 */
public final class FeishuAgendaFetchProbe {

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        Map<String, String> ds = ProdSchemaProbeDatasource.load();
        FeishuService feishu = FeishuProbeFactory.createFeishuService();

        try (Connection conn = DriverManager.getConnection(ds.get("url"), ds.get("username"), ds.get("password"));
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT agenda_index, feishu_doc_url FROM int_matter_progress_doc_config "
                             + "WHERE preset_type_code = 1 AND agenda_index IN (1,2,3,4) ORDER BY agenda_index")) {
            while (rs.next()) {
                int idx = rs.getInt("agenda_index");
                String url = rs.getString("feishu_doc_url");
                System.out.println("=== agenda_index=" + idx + " (会序" + (idx + 1) + ") ===");
                System.out.println("url=" + url);
                FeishuResourceRef ref = FeishuResourceResolver.resolve(url);
                if (ref == null) {
                    System.out.println("resolve: null");
                    continue;
                }
                System.out.println("kind=" + ref.kind() + " token=" + ref.primaryToken()
                        + " table=" + ref.tableId() + " canFetch=" + ref.canFetchPlainText());
                try {
                    String text = feishu.fetchResourcePlainText(ref);
                    int len = text == null ? 0 : text.length();
                    String preview = text == null ? "" : text.substring(0, Math.min(120, len)).replace('\n', ' ');
                    System.out.println("OK len=" + len + " preview=" + preview);
                } catch (Exception e) {
                    System.out.println("FAIL: " + e.getMessage());
                }
                System.out.println();
            }
        }
    }

    private FeishuAgendaFetchProbe() {
    }
}
