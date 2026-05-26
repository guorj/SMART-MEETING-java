AdminModules.register({
  route: '/weekly-jobs',
  mount: async function (root) {
    const jobs = await AdminApi.fetch('/api/v1/admin/weekly-jobs');
    const sourceOpts = await AdminApi.fetch('/api/v1/admin/weekly-jobs/matter-config-options?role=SOURCE');
    const outputOpts = await AdminApi.fetch('/api/v1/admin/weekly-jobs/matter-config-options?role=OUTPUT');
    const esc = s => (s || '').replace(/"/g, '&quot;');
    const datalistOptions = names => names.map(n => `<option value="${esc(n)}"></option>`).join('');

    root.innerHTML = `
      <div class="panel"><p>保存后需重启 feishu-scheduled-bot 或刷新 Quartz。bot 手动执行见 USER-MANUAL §12。</p>
        <button class="primary" id="wj-new">新建任务</button></div>
      <div class="panel"><table><tr><th>id</th><th>name</th><th>on</th><th>cron</th><th>last</th><th></th></tr>
        <tbody id="wj-tbody"></tbody></table></div>
      <div class="panel hidden" id="wj-editor">
        <h3>编辑任务</h3>
        <input type="hidden" id="wj-id"/>
        <div><label>job_name <input id="wj-name" style="width:240px"/></label></div>
        <div><label>enabled <input type="checkbox" id="wj-enabled" checked/></label></div>
        <div><label>cron <input id="wj-cron" value="0 10 * * MON" style="width:200px"/></label></div>
        <div><label>minute_query_type <select id="wj-mqt"><option>PRESET_LAST_7_DAYS</option><option>MEETING_IDS</option></select></label></div>
        <div><label>minute_query_params <textarea id="wj-mqp" rows="2" style="width:100%">{"presetTypeCode":1,"days":7}</textarea></label></div>
        <div><label>source_config_names（逗号分隔）
          <input id="wj-sources" list="wj-sources-list" style="width:100%" placeholder="preset1-comp-agenda-01,preset1-comp-agenda-02"/>
          <datalist id="wj-sources-list">${datalistOptions(sourceOpts.map(o => o.configName))}</datalist>
        </label></div>
        <div><label>output_config_name
          <input id="wj-output" list="wj-output-list" style="width:100%" placeholder="preset1-weekly-report-out"/>
          <datalist id="wj-output-list">${datalistOptions(outputOpts.map(o => o.configName))}</datalist>
        </label>
        <small class="muted">须为 host_agenda 内 OUTPUT/BOTH 的 configName；若无下拉项可手填</small></div>
        <p id="wj-save-msg" class="msg hidden"></p>
        <button class="primary" id="wj-save">保存</button>
        <button id="wj-cancel">取消</button>
      </div>`;

    const tbody = document.getElementById('wj-tbody');
    const showMsg = (text, isErr) => {
      const el = document.getElementById('wj-save-msg');
      el.classList.remove('hidden');
      el.className = isErr ? 'msg msg-err' : 'msg msg-ok';
      el.textContent = text;
    };

    const showEditor = (job) => {
      document.getElementById('wj-editor').classList.remove('hidden');
      document.getElementById('wj-save-msg').classList.add('hidden');
      document.getElementById('wj-id').value = job ? job.id : '';
      document.getElementById('wj-name').value = job ? job.jobName : '';
      document.getElementById('wj-enabled').checked = job ? job.enabled : true;
      document.getElementById('wj-cron').value = job ? job.cronExpression : '0 10 * * MON';
      document.getElementById('wj-mqt').value = job ? job.minuteQueryType : 'PRESET_LAST_7_DAYS';
      document.getElementById('wj-mqp').value = job ? job.minuteQueryParamsJson : '{"presetTypeCode":1,"days":7}';
      document.getElementById('wj-sources').value = job && job.sourceConfigNames
        ? job.sourceConfigNames.join(',') : '';
      document.getElementById('wj-output').value = job ? (job.outputConfigName || '') : '';
    };

    jobs.forEach(j => {
      const tr = document.createElement('tr');
      tr.innerHTML = '<td>' + j.id + '</td><td>' + j.jobName + '</td><td>' + j.enabled + '</td><td>' + j.cronExpression + '</td><td>' + (j.lastRunStatus || '-') + '</td><td><a href="#" data-id="' + j.id + '">编辑</a></td>';
      tbody.appendChild(tr);
    });
    tbody.querySelectorAll('a').forEach(a => {
      a.onclick = e => { e.preventDefault(); showEditor(jobs.find(x => String(x.id) === a.dataset.id)); };
    });
    document.getElementById('wj-new').onclick = () => showEditor(null);
    document.getElementById('wj-cancel').onclick = () => document.getElementById('wj-editor').classList.add('hidden');
    document.getElementById('wj-save').onclick = async () => {
      const id = document.getElementById('wj-id').value;
      const existing = id ? jobs.find(x => String(x.id) === id) : null;
      const sourcesRaw = document.getElementById('wj-sources').value.trim();
      const body = {
        jobName: document.getElementById('wj-name').value.trim(),
        enabled: document.getElementById('wj-enabled').checked,
        cronExpression: document.getElementById('wj-cron').value.trim(),
        scheduleTimezone: existing && existing.scheduleTimezone ? existing.scheduleTimezone : 'Asia/Shanghai',
        minuteQueryType: document.getElementById('wj-mqt').value,
        minuteQueryParamsJson: document.getElementById('wj-mqp').value.trim(),
        sourceConfigNames: sourcesRaw.split(/[,，\s]+/).map(s => s.trim()).filter(Boolean),
        outputConfigName: document.getElementById('wj-output').value.trim(),
        outputDocTitleTpl: existing && existing.outputDocTitleTpl
          ? existing.outputDocTitleTpl : '事项对比通报-{date}',
        feishuFolderToken: existing ? existing.feishuFolderToken : null
      };
      if (!body.jobName) {
        showMsg('请填写 job_name', true);
        return;
      }
      if (!body.outputConfigName) {
        showMsg('请填写 output_config_name（OUTPUT/BOTH 配置名）', true);
        return;
      }
      if (body.sourceConfigNames.length === 0) {
        showMsg('请填写至少一个 source_config_name', true);
        return;
      }
      try {
        if (id) {
          await AdminApi.fetch('/api/v1/admin/weekly-jobs/' + id, { method: 'PUT', body: JSON.stringify(body) });
        } else {
          await AdminApi.fetch('/api/v1/admin/weekly-jobs', { method: 'POST', body: JSON.stringify(body) });
        }
        showMsg('已保存', false);
        setTimeout(() => location.reload(), 400);
      } catch (e) {
        showMsg('保存失败: ' + (e.message || '未知错误'), true);
      }
    };
  }
});
