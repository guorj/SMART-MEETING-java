AdminModules.register({
  route: '/weekly-jobs',
  mount: async function (root) {
    const jobs = await AdminApi.fetch('/api/v1/admin/weekly-jobs');
    const sourceOpts = await AdminApi.fetch('/api/v1/admin/weekly-jobs/matter-config-options?role=SOURCE');
    const outputOpts = await AdminApi.fetch('/api/v1/admin/weekly-jobs/matter-config-options?role=OUTPUT');
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
        <div><label>source names（逗号） <input id="wj-sources" style="width:100%"/></label>
          <small>${sourceOpts.map(o => o.configName).join(', ')}</small></div>
        <div><label>output_config_name <select id="wj-output"><option value=""></option>
          ${outputOpts.map(o => '<option>' + o.configName + '</option>').join('')}</select></label></div>
        <button class="primary" id="wj-save">保存</button>
        <button id="wj-cancel">取消</button>
      </div>`;
    const tbody = document.getElementById('wj-tbody');
    const showEditor = (job) => {
      document.getElementById('wj-editor').classList.remove('hidden');
      document.getElementById('wj-id').value = job ? job.id : '';
      document.getElementById('wj-name').value = job ? job.jobName : '';
      document.getElementById('wj-enabled').checked = job ? job.enabled : true;
      document.getElementById('wj-cron').value = job ? job.cronExpression : '0 10 * * MON';
      document.getElementById('wj-mqt').value = job ? job.minuteQueryType : 'PRESET_LAST_7_DAYS';
      document.getElementById('wj-mqp').value = job ? job.minuteQueryParamsJson : '{"presetTypeCode":1,"days":7}';
      document.getElementById('wj-sources').value = job && job.sourceConfigNames ? job.sourceConfigNames.join(',') : '';
      document.getElementById('wj-output').value = job ? job.outputConfigName : '';
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
      const body = {
        jobName: document.getElementById('wj-name').value.trim(),
        enabled: document.getElementById('wj-enabled').checked,
        cronExpression: document.getElementById('wj-cron').value.trim(),
        scheduleTimezone: 'Asia/Shanghai',
        minuteQueryType: document.getElementById('wj-mqt').value,
        minuteQueryParamsJson: document.getElementById('wj-mqp').value.trim(),
        sourceConfigNames: document.getElementById('wj-sources').value.split(',').map(s => s.trim()).filter(Boolean),
        outputConfigName: document.getElementById('wj-output').value,
        outputDocTitleTpl: '事项对比通报-{date}'
      };
      const id = document.getElementById('wj-id').value;
      if (id) {
        await AdminApi.fetch('/api/v1/admin/weekly-jobs/' + id, { method: 'PUT', body: JSON.stringify(body) });
      } else {
        await AdminApi.fetch('/api/v1/admin/weekly-jobs', { method: 'POST', body: JSON.stringify(body) });
      }
      alert('已保存');
      location.reload();
    };
  }
});
