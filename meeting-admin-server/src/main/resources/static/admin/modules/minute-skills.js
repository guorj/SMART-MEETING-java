AdminModules.register({
  route: '/minute-skills',
  mount: async function (root) {
    const esc = s => (s == null ? '' : String(s)).replace(/"/g, '&quot;').replace(/</g, '&lt;');
    const toast = globalThis.toast || (msg => alert(msg));

    const [catalog, bindings] = await Promise.all([
      AdminApi.fetch('/api/v1/admin/minute-skills/catalog'),
      AdminApi.fetch('/api/v1/admin/minute-skills/bindings')
    ]);

    const skillOptions = [{ name: '', label: '（不绑定 · 走 LLM 降级）' }]
      .concat((catalog || []).map(c => ({ name: c.name, label: c.name + ' — ' + (c.description || '').slice(0, 48) })));

    let html = '<div class="panel">'
      + '<p class="module-intro">为各会务类型指定纪要 SKILL.md 模板。保存后写入 <code>int_meeting_type_preset.minute_skill_name</code>；Step 4 将该模板注入 LLM system prompt（直调 <code>meeting.llm</code>，不走 OpenClaw）。</p>'
      + '<p class="muted">Skill 正文在仓库 <code>skills/*/SKILL.md</code> 维护；可通过 <code>smart-meeting.skills-dir</code> 指定目录。</p>'
      + '</div>'
      + '<div class="minute-skills-grid">'
      + '<section class="panel minute-skills-catalog">'
      + '<h2>Skill 目录</h2>';

    if (!catalog || catalog.length === 0) {
      html += '<p class="muted">未扫描到 skills 目录。可配置 <code>smart-meeting.skills-dir</code> 指向仓库 skills 文件夹。</p>';
    } else {
      html += '<ul class="skill-catalog-list">';
      for (const item of catalog) {
        html += '<li class="skill-catalog-item">'
          + '<strong>' + esc(item.name) + '</strong>'
          + '<p class="muted">' + esc(item.description || '（无描述）') + '</p>'
          + '<code class="skill-path">' + esc(item.relativePath) + '</code>'
          + '</li>';
      }
      html += '</ul>';
    }

    html += '</section><section class="panel minute-skills-bindings">'
      + '<h2>会务类型绑定</h2>'
      + '<table class="admin-table"><thead><tr>'
      + '<th>Code</th><th>会务类型</th><th>纪要 Skill</th><th></th>'
      + '</tr></thead><tbody>';

    for (const row of bindings || []) {
      const code = row.presetTypeCode;
      const opts = skillOptions.map(o =>
        '<option value="' + esc(o.name) + '"' + (o.name === (row.minuteSkillName || '') ? ' selected' : '') + '>'
        + esc(o.label) + '</option>'
      ).join('');
      html += '<tr data-preset="' + code + '">'
        + '<td>' + esc(code) + '</td>'
        + '<td>' + esc(row.displayName) + '</td>'
        + '<td><select class="skill-select" id="skill-' + code + '">' + opts + '</select></td>'
        + '<td><button type="button" class="primary skill-save" data-code="' + code + '">保存</button></td>'
        + '</tr>';
    }

    html += '</tbody></table></section></div>';

    root.innerHTML = html;

    root.querySelectorAll('.skill-save').forEach(btn => {
      btn.onclick = async () => {
        const code = btn.dataset.code;
        const sel = document.getElementById('skill-' + code);
        const minuteSkillName = sel ? sel.value : '';
        btn.disabled = true;
        try {
          await AdminApi.fetch('/api/v1/admin/minute-skills/bindings/' + code, {
            method: 'PUT',
            body: JSON.stringify({ minuteSkillName: minuteSkillName || null })
          });
          toast('已保存 preset ' + code);
        } catch (e) {
          toast(e.message || '保存失败');
        } finally {
          btn.disabled = false;
        }
      };
    });
  }
});
