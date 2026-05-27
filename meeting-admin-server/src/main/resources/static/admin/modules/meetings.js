AdminModules.register({
  route: '/meetings',
  mount: async function (root) {
    root.innerHTML = `
      <div class="panel">
        <div class="panel-head"><h2>会议列表</h2><p>${AdminHints.meetings.moduleIntro}</p></div>
        <div class="toolbar">
          <label class="field-inline">状态<select id="m-status" title="${AdminHints.meetings.status.replace(/"/g, '&quot;')}"><option value="">全部</option>
            <option>ISSUE_COLLECTING</option><option>INVITED</option><option>STARTED</option><option>RECORDING</option>
            <option>PROCESSING</option><option>COMPLETED</option><option>PAUSED</option><option>CANCELLED</option></select></label>
          <label class="field-inline">会务<select id="m-preset" title="${AdminHints.meetings.preset.replace(/"/g, '&quot;')}"><option value="">全部</option><option>1</option><option>2</option><option>3</option><option>4</option><option>5</option></select></label>
          <button type="button" class="primary" id="m-reload">刷新</button>
        </div>
      </div>
      <div class="panel table-wrap"><table id="m-table"><thead><tr>
        <th>ID</th><th>标题</th><th title="会议生命周期状态">状态</th><th title="会务类型 presetTypeCode">会务</th><th>操作</th>
      </tr></thead><tbody></tbody></table></div>
      <pre id="m-detail" class="panel hidden"></pre>
      <p id="m-push-link" class="hidden"></p>`;
    const load = async () => {
      const status = document.getElementById('m-status').value;
      const preset = document.getElementById('m-preset').value;
      let url = '/api/v1/admin/meetings?page=1&size=40';
      if (status) url += '&status=' + encodeURIComponent(status);
      if (preset) url += '&presetTypeCode=' + preset;
      const page = await AdminApi.fetch(url);
      const tbody = document.querySelector('#m-table tbody');
      tbody.innerHTML = '';
      (page.records || []).forEach(m => {
        const tr = document.createElement('tr');
        tr.innerHTML = '<td>' + m.id + '</td><td>' + (m.title || '') + '</td><td>' + m.status + '</td><td>' + m.presetTypeCode + '</td><td><a href="#" class="m-det" data-id="' + m.id + '">详情</a> <a href="#" class="m-end" data-id="' + m.id + '" title="' + AdminHints.meetings.forceEnd.replace(/"/g, '&quot;') + '">结束</a></td>';
        tbody.appendChild(tr);
      });
      tbody.querySelectorAll('a.m-det').forEach(a => {
        a.onclick = async e => {
          e.preventDefault();
          const d = await AdminApi.fetch('/api/v1/admin/meetings/' + a.dataset.id);
          const det = document.getElementById('m-detail');
          det.classList.remove('hidden');
          det.textContent = JSON.stringify(d, null, 2);
          const pushLink = document.getElementById('m-push-link');
          pushLink.classList.remove('hidden');
          pushLink.innerHTML = '<a href="#/push-bot?tab=logs&meetingId=' + encodeURIComponent(a.dataset.id) + '">查看该会议推送日志 →</a>';
        };
      });
      tbody.querySelectorAll('a.m-end').forEach(a => {
        a.onclick = async e => {
          e.preventDefault();
          if (!confirm('强制结束会议 ' + a.dataset.id + '？\n\n' + AdminHints.meetings.forceEnd)) return;
          await AdminApi.fetch('/api/v1/admin/meetings/' + a.dataset.id + '/force-end', { method: 'POST' });
          alert('已请求结束');
          load();
        };
      });
    };
    document.getElementById('m-reload').onclick = load;
    load();
  }
});
