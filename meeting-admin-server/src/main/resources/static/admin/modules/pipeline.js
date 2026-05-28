AdminModules.register({
  route: '/pipeline',
  mount: async function (root) {
    const templates = await AdminApi.fetch('/api/v1/admin/pipeline/templates');
    const executions = await AdminApi.fetch('/api/v1/admin/pipeline/executions');
    const rows = templates.map(t => `
      <tr>
        <td>${t.id}</td>
        <td>${t.templateCode || ''}</td>
        <td>${t.templateName || ''}</td>
        <td>${t.stage || ''}</td>
        <td>${t.enabled}</td>
        <td>
          <a href="#" class="pl-steps" data-id="${t.id}">步骤</a>
          <a href="#" class="pl-step-add" data-id="${t.id}" data-stage="${t.stage || 'PRE'}">新增步骤</a>
        </td>
      </tr>
    `).join('');
    const execRows = (executions || []).slice(0, 30).map(e => `
      <tr>
        <td>${e.id}</td><td>${e.meetingId || ''}</td><td>${e.stage || ''}</td>
        <td>${e.status || ''}</td><td>${e.retryCount || 0}/${e.maxRetries || 0}</td>
        <td>${e.lastError || ''}</td>
      </tr>
    `).join('');
    root.innerHTML = `
      <div class="panel">
        <p>流水线模板与步骤配置（PRE/MID/POST）。</p>
        <button class="primary" id="pl-new-template">新增模板</button>
      </div>
      <div class="panel table-wrap">
        <table><thead><tr>
          <th>ID</th><th>code</th><th>name</th><th>stage</th><th>enabled</th><th>操作</th>
        </tr></thead><tbody>${rows}</tbody></table>
      </div>
      <div class="panel">
        <h3>触发执行</h3>
        <input id="pl-meeting-id" placeholder="meetingId" />
        <select id="pl-stage">
          <option value="PRE">PRE</option><option value="MID">MID</option><option value="POST">POST</option>
        </select>
        <input id="pl-template-code" placeholder="templateCode(可选)" />
        <button id="pl-exec">执行</button>
      </div>
      <div class="panel table-wrap">
        <h3>最近执行记录</h3>
        <table><thead><tr>
          <th>ID</th><th>meetingId</th><th>stage</th><th>status</th><th>retry</th><th>lastError</th>
        </tr></thead><tbody>${execRows}</tbody></table>
      </div>
      <div class="panel"><pre id="pl-result" class="msg"></pre></div>
    `;

    document.getElementById('pl-new-template').onclick = async () => {
      const code = prompt('templateCode');
      if (!code) return;
      const name = prompt('templateName', code) || code;
      const stage = prompt('stage: PRE/MID/POST', 'PRE') || 'PRE';
      await AdminApi.fetch('/api/v1/admin/pipeline/templates', {
        method: 'POST',
        body: JSON.stringify({ templateCode: code, templateName: name, stage, enabled: true, versionNo: 1 })
      });
      location.reload();
    };

    root.querySelectorAll('a.pl-steps').forEach(a => {
      a.onclick = async (e) => {
        e.preventDefault();
        const templateId = a.dataset.id;
        const steps = await AdminApi.fetch('/api/v1/admin/pipeline/steps?templateId=' + templateId);
        const text = (steps || []).map(s => `#${s.orderNo} ${s.stepCode} (${s.stepType})`).join('\n') || '(无步骤)';
        alert(text + '\n\n可用类型: preset-sync, weekly-job, push-notification, settings-reload');
      };
    });
    root.querySelectorAll('a.pl-step-add').forEach(a => {
      a.onclick = async (e) => {
        e.preventDefault();
        const templateId = Number(a.dataset.id);
        const stage = a.dataset.stage || 'PRE';
        const stepCode = prompt('stepCode');
        if (!stepCode) return;
        const stepName = prompt('stepName', stepCode) || stepCode;
        const stepType = prompt('stepType', 'preset-sync') || 'preset-sync';
        const orderNo = Number(prompt('orderNo', '1') || '1');
        const timeoutSeconds = Number(prompt('timeoutSeconds', '120') || '120');
        const configJson = prompt('configJson', '{}') || '{}';
        await AdminApi.fetch('/api/v1/admin/pipeline/steps', {
          method: 'POST',
          body: JSON.stringify({
            templateId, stepCode, stepName, stepType, stage,
            orderNo, timeoutSeconds, configJson, enabled: true
          })
        });
        location.reload();
      };
    });

    document.getElementById('pl-exec').onclick = async () => {
      const meetingId = document.getElementById('pl-meeting-id').value.trim();
      const stage = document.getElementById('pl-stage').value;
      const templateCode = document.getElementById('pl-template-code').value.trim();
      if (!meetingId) {
        alert('meetingId 必填');
        return;
      }
      try {
        await AdminApi.fetch('/api/v1/admin/pipeline/execute', {
          method: 'POST',
          body: JSON.stringify({ meetingId, stage, templateCode })
        });
        document.getElementById('pl-result').textContent = '执行请求已提交';
      } catch (e) {
        document.getElementById('pl-result').textContent = '执行失败: ' + e.message;
      }
    };
  }
});
