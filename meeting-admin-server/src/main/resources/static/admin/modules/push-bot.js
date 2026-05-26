AdminModules.register({
  route: '/push-bot',
  mount: async function (root) {
    const params = new URLSearchParams(location.hash.split('?')[1] || '');
    let tab = params.get('tab') || 'tasks';
    let logMeetingId = params.get('meetingId') || '';

    root.innerHTML = `
      <div class="panel toolbar">
        <button type="button" class="primary" id="pb-tab-tasks">推送任务</button>
        <button type="button" id="pb-tab-logs">推送日志</button>
        <button type="button" id="pb-reload">同步 Quartz</button>
        <span id="pb-msg" class="msg hidden"></span>
      </div>
      <div id="pb-tasks-panel"></div>
      <div id="pb-logs-panel" class="hidden"></div>`;

    const showMsg = (text, isErr) => {
      const el = document.getElementById('pb-msg');
      el.classList.remove('hidden');
      el.className = isErr ? 'msg msg-err' : 'msg msg-ok';
      el.textContent = text;
    };

    const setTab = (name) => {
      tab = name;
      document.getElementById('pb-tasks-panel').classList.toggle('hidden', name !== 'tasks');
      document.getElementById('pb-logs-panel').classList.toggle('hidden', name !== 'logs');
      document.getElementById('pb-tab-tasks').classList.toggle('primary', name === 'tasks');
      document.getElementById('pb-tab-logs').classList.toggle('primary', name === 'logs');
      if (name === 'tasks') loadTasks();
      else loadLogs();
    };

    document.getElementById('pb-tab-tasks').onclick = () => setTab('tasks');
    document.getElementById('pb-tab-logs').onclick = () => setTab('logs');
    document.getElementById('pb-reload').onclick = async () => {
      try {
        await AdminApi.fetch('/api/v1/admin/bot/reload-schedule', { method: 'POST' });
        showMsg('Quartz 已同步', false);
      } catch (e) {
        showMsg('同步失败: ' + e.message, true);
      }
    };

    async function loadTasks() {
      const panel = document.getElementById('pb-tasks-panel');
      panel.innerHTML = '<p class="muted">加载中…</p>';
      try {
        const page = await AdminApi.fetch('/api/v1/admin/push-tasks?page=0&size=50');
        const tasks = page.content || [];
        panel.innerHTML = `
          <div class="panel"><button class="primary" id="pb-new">新建任务</button></div>
          <div class="panel table-wrap"><table><thead><tr>
            <th>名称</th><th>启用</th><th>Cron</th><th>模式</th><th>目标</th><th>操作</th>
          </tr></thead><tbody id="pb-task-tbody"></tbody></table></div>
          <div class="panel hidden" id="pb-editor">
            <h3 id="pb-editor-title">编辑任务</h3>
            <input type="hidden" id="pb-id"/>
            <div><label>任务名 <input id="pb-name" style="width:280px"/></label></div>
            <div><label>Cron <input id="pb-cron" placeholder="0 30 9 * * MON-FRI" style="width:200px"/>
              <button type="button" id="pb-cron-preview">预览</button></label>
              <span id="pb-cron-hint" class="muted"></span></div>
            <div><label>调度模式 <select id="pb-mode"><option>INTERNAL</option><option>EXTERNAL</option></select></label></div>
            <div><label>目标类型 <select id="pb-ttype"><option>USER</option><option>GROUP</option></select></label></div>
            <div><label>目标 ID <input id="pb-tid" style="width:320px"/></label></div>
            <div><label>消息 <textarea id="pb-msg-text" rows="3" style="width:100%"></textarea></label></div>
            <div><label>跳过假日 <input type="checkbox" id="pb-skip-hol"/></label>
              <label>启用 <input type="checkbox" id="pb-enabled" checked/></label></div>
            <p id="pb-save-msg" class="msg hidden"></p>
            <button class="primary" id="pb-save">保存</button>
            <button id="pb-cancel">取消</button>
          </div>`;

        const tbody = document.getElementById('pb-task-tbody');
        tasks.forEach(t => {
          const tr = document.createElement('tr');
          const enabled = t.enabled !== false;
          tr.innerHTML = '<td>' + (t.taskName || '') + '</td><td>' + (enabled ? '是' : '否') + '</td><td>' + (t.cronExpression || '-') + '</td>'
            + '<td>' + (t.scheduleMode || '') + '</td><td>' + (t.targetType || '') + ' ' + (t.targetId || '') + '</td>'
            + '<td class="actions"><span class="btn-group">'
            + '<button type="button" class="secondary pb-edit" data-id="' + t.id + '">编辑</button> '
            + '<button type="button" class="secondary pb-exec" data-id="' + t.id + '">执行</button> '
            + '<button type="button" class="secondary pb-toggle" data-id="' + t.id + '" data-enabled="' + enabled + '">'
            + (enabled ? '停用' : '启用') + '</button></span></td>';
          tbody.appendChild(tr);
        });

        const showEditor = (task) => {
          document.getElementById('pb-editor').classList.remove('hidden');
          document.getElementById('pb-editor-title').textContent = task ? '编辑任务' : '新建任务';
          document.getElementById('pb-save-msg').classList.add('hidden');
          document.getElementById('pb-id').value = task ? task.id : '';
          document.getElementById('pb-name').value = task ? task.taskName : '';
          document.getElementById('pb-cron').value = task ? (task.cronExpression || '') : '';
          document.getElementById('pb-mode').value = task ? (task.scheduleMode || 'INTERNAL') : 'INTERNAL';
          document.getElementById('pb-ttype').value = task ? (task.targetType || 'USER') : 'USER';
          document.getElementById('pb-tid').value = task ? (task.targetId || '') : '';
          document.getElementById('pb-msg-text').value = task ? (task.messageContent || '') : '';
          document.getElementById('pb-skip-hol').checked = task ? !!task.skipHolidays : false;
          document.getElementById('pb-enabled').checked = task ? task.enabled !== false : true;
        };

        tbody.querySelectorAll('.pb-edit').forEach(btn => {
          btn.onclick = async e => {
            e.preventDefault();
            const t = await AdminApi.fetch('/api/v1/admin/push-tasks/' + btn.dataset.id);
            showEditor(t);
          };
        });
        tbody.querySelectorAll('.pb-exec').forEach(btn => {
          btn.onclick = async e => {
            e.preventDefault();
            if (btn.disabled) return;
            if (!confirm('立即执行推送任务？')) return;
            btn.disabled = true;
            const oldText = btn.textContent;
            btn.textContent = '执行中…';
            try {
              const r = await AdminApi.fetch('/api/v1/admin/push-tasks/' + btn.dataset.id + '/execute', { method: 'POST' });
              const st = r.status || '';
              if (st === 'SUCCESS') {
                showMsg('推送成功', false);
              } else if (st === 'SKIPPED') {
                showMsg('已跳过: ' + (r.skipReason || ''), false);
              } else if (st === 'REJECTED') {
                showMsg('未执行: ' + (r.errorMessage || st), true);
              } else if (st === 'FAILED') {
                showMsg('推送失败: ' + (r.errorMessage || st), true);
              } else {
                showMsg('结果: ' + (r.errorMessage || r.skipReason || st || JSON.stringify(r)), st === 'SUCCESS');
              }
            } catch (err) {
              showMsg('执行失败: ' + err.message, true);
            } finally {
              btn.disabled = false;
              btn.textContent = oldText;
            }
          };
        });
        tbody.querySelectorAll('.pb-toggle').forEach(btn => {
          btn.onclick = async e => {
            e.preventDefault();
            if (btn.disabled) return;
            btn.disabled = true;
            const oldText = btn.textContent;
            btn.textContent = '…';
            try {
              await AdminApi.fetch('/api/v1/admin/push-tasks/' + btn.dataset.id + '/toggle', { method: 'PATCH' });
              showMsg('已' + (btn.dataset.enabled === 'true' ? '停用' : '启用') + '任务', false);
              await loadTasks();
            } catch (err) {
              showMsg('启停失败: ' + err.message, true);
              btn.disabled = false;
              btn.textContent = oldText;
            }
          };
        });
        document.getElementById('pb-new').onclick = () => showEditor(null);
        document.getElementById('pb-cancel').onclick = () => document.getElementById('pb-editor').classList.add('hidden');
        document.getElementById('pb-cron-preview').onclick = async () => {
          const cron = document.getElementById('pb-cron').value.trim();
          if (!cron) return;
          try {
            const r = await AdminApi.fetch('/api/v1/admin/push-tasks/cron-preview', {
              method: 'POST',
              body: JSON.stringify({ cronExpression: cron })
            });
            document.getElementById('pb-cron-hint').textContent = (r.nextFireTimes || []).join(' · ');
          } catch (e) {
            document.getElementById('pb-cron-hint').textContent = e.message;
          }
        };
        document.getElementById('pb-save').onclick = async () => {
          const id = document.getElementById('pb-id').value;
          const body = {
            taskName: document.getElementById('pb-name').value.trim(),
            cronExpression: document.getElementById('pb-cron').value.trim(),
            scheduleMode: document.getElementById('pb-mode').value,
            targetType: document.getElementById('pb-ttype').value,
            targetId: document.getElementById('pb-tid').value.trim(),
            messageContent: document.getElementById('pb-msg-text').value,
            skipHolidays: document.getElementById('pb-skip-hol').checked,
            enabled: document.getElementById('pb-enabled').checked
          };
          if (!body.taskName || !body.messageContent) {
            document.getElementById('pb-save-msg').className = 'msg msg-err';
            document.getElementById('pb-save-msg').textContent = '请填写任务名与消息';
            document.getElementById('pb-save-msg').classList.remove('hidden');
            return;
          }
          try {
            if (id) {
              await AdminApi.fetch('/api/v1/admin/push-tasks/' + id, { method: 'PUT', body: JSON.stringify(body) });
            } else {
              await AdminApi.fetch('/api/v1/admin/push-tasks', { method: 'POST', body: JSON.stringify(body) });
            }
            document.getElementById('pb-editor').classList.add('hidden');
            loadTasks();
          } catch (e) {
            document.getElementById('pb-save-msg').className = 'msg msg-err';
            document.getElementById('pb-save-msg').textContent = e.message;
            document.getElementById('pb-save-msg').classList.remove('hidden');
          }
        };
      } catch (e) {
        panel.innerHTML = '<p class="msg msg-err">无法加载任务（请确认 bot 已启动且配置了 API Key）：' + e.message + '</p>';
      }
    }

    async function loadLogs() {
      const panel = document.getElementById('pb-logs-panel');
      panel.innerHTML = `
        <div class="panel toolbar">
          <label>会议 ID <input id="pb-log-mid" value="${logMeetingId.replace(/"/g, '')}" style="width:280px"/></label>
          <label>任务 ID <input id="pb-log-task" style="width:200px"/></label>
          <label>状态 <select id="pb-log-status"><option value="">全部</option><option>SUCCESS</option><option>FAILED</option><option>SKIPPED</option></select></label>
          <button class="primary" id="pb-log-search">查询</button>
        </div>
        <div class="panel table-wrap"><table><thead><tr>
          <th>时间</th><th>任务</th><th>会议</th><th>状态</th><th>触发</th><th>目标</th>
        </tr></thead><tbody id="pb-log-tbody"></tbody></table></div>`;
      document.getElementById('pb-log-search').onclick = async () => {
        const mid = document.getElementById('pb-log-mid').value.trim();
        const tid = document.getElementById('pb-log-task').value.trim();
        const st = document.getElementById('pb-log-status').value;
        let url = '/api/v1/admin/push-logs?page=0&size=50';
        if (mid) url += '&meetingId=' + encodeURIComponent(mid);
        if (tid) url += '&taskId=' + encodeURIComponent(tid);
        if (st) url += '&status=' + encodeURIComponent(st);
        try {
          const page = await AdminApi.fetch(url);
          const tbody = document.getElementById('pb-log-tbody');
          tbody.innerHTML = '';
          (page.content || []).forEach(l => {
            const tr = document.createElement('tr');
            tr.innerHTML = '<td>' + (l.sendTime || '') + '</td><td>' + (l.taskName || l.taskId) + '</td>'
              + '<td>' + (l.meetingId || '-') + '</td><td>' + l.status + '</td><td>' + (l.triggerType || '') + '</td>'
              + '<td>' + (l.targetType || '') + ' ' + (l.targetId || '') + '</td>';
            tbody.appendChild(tr);
          });
        } catch (e) {
          alert('查询失败: ' + e.message);
        }
      };
      document.getElementById('pb-log-search').click();
    }

    setTab(tab);
  }
});
