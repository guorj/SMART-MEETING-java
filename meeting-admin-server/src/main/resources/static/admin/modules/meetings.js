AdminModules.register({
  route: '/meetings',
  mount: async function (root) {
    root.innerHTML = `
      <div class="panel">
        <label>状态 <select id="m-status"><option value="">全部</option>
          <option>ISSUE_COLLECTING</option><option>INVITED</option><option>STARTED</option><option>RECORDING</option>
          <option>PROCESSING</option><option>COMPLETED</option><option>PAUSED</option><option>CANCELLED</option></select></label>
        <label>preset <select id="m-preset"><option value="">全部</option><option>1</option><option>2</option><option>3</option><option>4</option><option>5</option></select></label>
        <button class="primary" id="m-reload">刷新</button>
      </div>
      <div class="panel"><table id="m-table"><thead><tr><th>id</th><th>title</th><th>status</th><th>preset</th><th></th></tr></thead><tbody></tbody></table></div>
      <pre id="m-detail" class="panel"></pre>`;
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
        tr.innerHTML = '<td>' + m.id + '</td><td>' + (m.title || '') + '</td><td>' + m.status + '</td><td>' + m.presetTypeCode + '</td><td><a href="#" class="m-det" data-id="' + m.id + '">详情</a> <a href="#" class="m-end" data-id="' + m.id + '">结束</a></td>';
        tbody.appendChild(tr);
      });
      tbody.querySelectorAll('a.m-det').forEach(a => {
        a.onclick = async e => {
          e.preventDefault();
          const d = await AdminApi.fetch('/api/v1/admin/meetings/' + a.dataset.id);
          document.getElementById('m-detail').textContent = JSON.stringify(d, null, 2);
        };
      });
      tbody.querySelectorAll('a.m-end').forEach(a => {
        a.onclick = async e => {
          e.preventDefault();
          if (!confirm('强制结束会议 ' + a.dataset.id + '？')) return;
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
