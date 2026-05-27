AdminModules.register({
  route: '/weekly-jobs',
  mount: async function (root) {
    const jobs = await AdminApi.fetch('/api/v1/admin/weekly-jobs');
    const sourceOpts = await AdminApi.fetch('/api/v1/admin/weekly-jobs/matter-config-options?role=SOURCE');
    const outputOpts = await AdminApi.fetch('/api/v1/admin/weekly-jobs/matter-config-options?role=OUTPUT');
    const esc = s => (s || '').replace(/"/g, '&quot;');
    const datalistOptions = names => names.map(n => `<option value="${esc(n)}"></option>`).join('');

    root.innerHTML = `
      <div class="panel">
        <p class="module-intro">${AdminHints.weeklyJobs.moduleIntro}</p>
        <button class="primary" id="wj-new">新建任务</button>
        <button type="button" id="wj-reload">同步 Quartz</button>
      </div>
      <div class="panel table-wrap"><table><thead><tr>
        <th title="数据库主键">id</th><th title="任务名">name</th><th title="是否参与 Cron">on</th>
        <th title="Quartz 表达式">cron</th><th title="最近一次执行状态">last</th><th>操作</th>
      </tr></thead><tbody id="wj-tbody"></tbody></table></div>
      <div class="panel hidden form-editor" id="wj-editor">
        <h3 id="wj-editor-title">编辑对比任务</h3>
        <input type="hidden" id="wj-id"/>
        ${AdminForm.field('任务名', '<input id="wj-name" type="text"/>', AdminHints.weeklyJobs.jobName)}
        <div class="form-field">
          <span class="form-field-caption">启用</span>
          <div class="form-check-row">
            <label class="check-label"><input type="checkbox" id="wj-enabled" checked/> 参与 Cron 调度</label>
          </div>
          ${AdminForm.hint(AdminHints.weeklyJobs.enabled)}
        </div>
        ${AdminForm.field('Cron', '<input id="wj-cron" type="text" value="0 10 * * MON"/>', AdminHints.weeklyJobs.cron)}
        <div class="form-field">
          <label>纪要查询类型</label>
          <select id="wj-mqt">
            <option value="PRESET_LAST_7_DAYS">PRESET_LAST_7_DAYS — 按会务类型 + 最近 N 天</option>
            <option value="MEETING_IDS">MEETING_IDS — 指定会议 ID 列表</option>
          </select>
          <p id="wj-mqt-hint" class="form-hint"></p>
        </div>
        ${AdminForm.field('纪要查询参数 (JSON)', '<textarea id="wj-mqp" rows="3"></textarea>', AdminHints.weeklyJobs.minuteQueryParams)}
        <div class="form-field">
          <label>源资料 configName</label>
          <input id="wj-sources" list="wj-sources-list" type="text" placeholder="preset1-comp-agenda-01,preset1-comp-agenda-02"/>
          <datalist id="wj-sources-list">${datalistOptions(sourceOpts.map(o => o.configName))}</datalist>
          ${AdminForm.hint(AdminHints.weeklyJobs.sourceConfigNames)}
        </div>
        <div class="form-field">
          <label>产出 configName</label>
          <input id="wj-output" list="wj-output-list" type="text" placeholder="preset1-weekly-report-out"/>
          <datalist id="wj-output-list">${datalistOptions(outputOpts.map(o => o.configName))}</datalist>
          ${AdminForm.hint(AdminHints.weeklyJobs.outputConfigName)}
        </div>
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

    document.getElementById('wj-reload').onclick = async () => {
      try {
        await AdminApi.fetch('/api/v1/admin/weekly-jobs/reload-schedule', { method: 'POST' });
        alert('Quartz 已同步');
      } catch (e) {
        alert('同步失败: ' + e.message);
      }
    };

    const showEditor = (job) => {
      document.getElementById('wj-editor').classList.remove('hidden');
      document.getElementById('wj-editor-title').textContent = job ? '编辑对比任务' : '新建对比任务';
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
      AdminForm.bindSelectHint('wj-mqt', 'wj-mqt-hint', AdminHints.weeklyJobs.minuteQueryType);
    };

    jobs.forEach(j => {
      const tr = document.createElement('tr');
      tr.innerHTML = '<td>' + j.id + '</td><td>' + j.jobName + '</td><td>' + j.enabled + '</td><td>' + j.cronExpression + '</td><td>' + (j.lastRunStatus || '-') + '</td><td>'
        + '<a href="#" class="wj-edit" data-id="' + j.id + '">编辑</a> '
        + '<a href="#" class="wj-exec" data-id="' + j.id + '">执行</a></td>';
      tbody.appendChild(tr);
    });
    tbody.querySelectorAll('a.wj-edit').forEach(a => {
      a.onclick = e => { e.preventDefault(); showEditor(jobs.find(x => String(x.id) === a.dataset.id)); };
    });
    tbody.querySelectorAll('a.wj-exec').forEach(a => {
      a.onclick = async e => {
        e.preventDefault();
        if (!confirm('立即执行对比任务 #' + a.dataset.id + '？')) return;
        try {
          const r = await AdminApi.fetch('/api/v1/admin/weekly-jobs/' + a.dataset.id + '/execute', { method: 'POST' });
          alert('结果: ' + (r.status || JSON.stringify(r)) + (r.generatedReportUrl ? '\n' + r.generatedReportUrl : ''));
          location.reload();
        } catch (err) {
          alert('执行失败: ' + err.message);
        }
      };
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
        showMsg('请填写任务名', true);
        return;
      }
      if (!body.outputConfigName) {
        showMsg('请填写产出 configName（OUTPUT/BOTH 配置名）', true);
        return;
      }
      if (body.sourceConfigNames.length === 0) {
        showMsg('请填写至少一个源资料 configName', true);
        return;
      }
      try {
        if (id) {
          await AdminApi.fetch('/api/v1/admin/weekly-jobs/' + id, { method: 'PUT', body: JSON.stringify(body) });
        } else {
          await AdminApi.fetch('/api/v1/admin/weekly-jobs', { method: 'POST', body: JSON.stringify(body) });
        }
        try {
          await AdminApi.fetch('/api/v1/admin/weekly-jobs/reload-schedule', { method: 'POST' });
        } catch (_) { /* bot 未配置时仍视为保存成功 */ }
        showMsg('已保存并已请求同步 Quartz', false);
        setTimeout(() => location.reload(), 500);
      } catch (e) {
        showMsg('保存失败: ' + (e.message || '未知错误'), true);
      }
    };
  }
});
