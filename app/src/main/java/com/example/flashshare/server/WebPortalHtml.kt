package com.example.flashshare.server

object WebPortalHtml {

  fun render(deviceName: String, token: String, localAddress: String): String {
    val safeName = deviceName
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
    return """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>FlashShare • $safeName</title>
<style>
  :root {
    --bg: #07111f;
    --card: #0f1d33;
    --card2: #152845;
    --border: #253d63;
    --text: #eef5ff;
    --muted: #93a8c7;
    --cyan: #22d3ee;
    --blue: #2979ff;
    --ok: #10b981;
    --err: #ef4444;
  }
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    background: radial-gradient(circle at top right, #0d2749, var(--bg) 60%);
    color: var(--text);
    min-height: 100vh;
    padding: 20px 16px 48px;
  }
  .wrap { max-width: 960px; margin: 0 auto; }
  .header {
    display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between;
    gap: 12px; padding: 20px 24px; border-radius: 20px;
    background: linear-gradient(135deg, rgba(41,121,255,0.22), rgba(34,211,238,0.12));
    border: 1px solid rgba(34,211,238,0.28); margin-bottom: 20px;
  }
  .brand { display: flex; align-items: center; gap: 12px; }
  .bolt {
    width: 44px; height: 44px; border-radius: 14px;
    background: linear-gradient(135deg, var(--blue), var(--cyan));
    display: grid; place-items: center; font-weight: 800; font-size: 20px; color: #041324;
  }
  h1 { font-size: 1.35rem; letter-spacing: -0.02em; }
  .sub { color: var(--muted); font-size: 0.88rem; margin-top: 2px; }
  .pill {
    display: inline-flex; align-items: center; gap: 8px; padding: 8px 14px;
    border-radius: 999px; background: rgba(16,185,129,0.14);
    border: 1px solid rgba(16,185,129,0.35); color: #6ee7b7; font-size: 0.85rem; font-weight: 600;
  }
  .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--ok); }
  .grid { display: grid; grid-template-columns: 1fr; gap: 18px; }
  @media (min-width: 780px) { .grid { grid-template-columns: 1fr 1fr; } }
  .card {
    background: var(--card); border: 1px solid var(--border);
    border-radius: 20px; padding: 20px;
  }
  .card h2 { font-size: 1.05rem; margin-bottom: 6px; }
  .card p.desc { color: var(--muted); font-size: 0.85rem; margin-bottom: 14px; }
  .dropzone {
    border: 2px dashed rgba(34,211,238,0.45); border-radius: 16px;
    padding: 32px 16px; text-align: center; cursor: pointer;
    background: rgba(34,211,238,0.04); transition: 0.2s ease;
  }
  .dropzone.drag { background: rgba(34,211,238,0.14); border-color: var(--cyan); transform: scale(1.01); }
  .btn {
    display: inline-flex; align-items: center; justify-content: center; gap: 8px;
    border: none; border-radius: 12px; padding: 11px 18px; font-weight: 600;
    cursor: pointer; font-size: 0.9rem; text-decoration: none;
  }
  .btn-primary {
    background: linear-gradient(135deg, var(--blue), var(--cyan)); color: #031526;
  }
  .btn-outline {
    background: var(--card2); color: var(--text); border: 1px solid var(--border);
  }
  .list { display: flex; flex-direction: column; gap: 10px; margin-top: 14px; max-height: 420px; overflow-y: auto; }
  .item {
    background: var(--card2); border: 1px solid var(--border);
    border-radius: 14px; padding: 12px 14px; display: flex; flex-direction: column; gap: 8px;
  }
  .row { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
  .fname { font-weight: 600; font-size: 0.9rem; word-break: break-all; }
  .meta { font-size: 0.78rem; color: var(--muted); font-family: ui-monospace, monospace; }
  .bar-bg { width: 100%; height: 7px; border-radius: 99px; background: #091426; overflow: hidden; }
  .bar-fill { height: 100%; width: 0%; background: linear-gradient(90deg, var(--blue), var(--cyan)); transition: width 0.12s; }
  .empty { text-align: center; padding: 28px 12px; color: var(--muted); font-size: 0.88rem; }
  .ok { color: #6ee7b7; }
  .err { color: #fca5a5; }
</style>
</head>
<body>
<div class="wrap">
  <div class="header">
    <div class="brand">
      <div class="bolt">⚡</div>
      <div>
        <h1>FlashShare Web Portal</h1>
        <div class="sub">Connected to <strong>$safeName</strong> • <code>$localAddress</code></div>
      </div>
    </div>
    <div class="pill" id="connPill"><span class="dot"></span><span id="connText">Local Wi-Fi Ready</span></div>
  </div>

  <div class="grid">
    <section class="card">
      <h2>PC → Phone (Upload Files)</h2>
      <p class="desc">Drag & drop any files or select multiple files to stream directly to Android storage.</p>
      <div class="dropzone" id="dropzone">
        <p style="font-weight:600;margin-bottom:8px;">Drop files here</p>
        <p style="color:var(--muted);font-size:0.82rem;margin-bottom:14px;">All file types supported • No size limit • Direct LAN speed</p>
        <button type="button" class="btn btn-primary" onclick="document.getElementById('fileInput').click()">Select Files from PC</button>
        <input type="file" id="fileInput" multiple style="display:none" />
      </div>
      <div class="list" id="uploadList"></div>
    </section>

    <section class="card">
      <div class="row" style="margin-bottom:8px;">
        <div>
          <h2>Phone → PC (Download Files)</h2>
          <p class="desc" style="margin-bottom:0;">Files shared from $safeName appear below.</p>
        </div>
        <button type="button" class="btn btn-outline" onclick="loadFiles()">Refresh</button>
      </div>
      <div class="list" id="downloadList"><div class="empty">Loading shared files...</div></div>
    </section>
  </div>
</div>

<script>
const TOKEN = "$token";

function fmtBytes(b) {
  if (!b || b <= 0) return "0 B";
  const u = ["B","KB","MB","GB","TB"];
  const i = Math.min(Math.floor(Math.log(b)/Math.log(1024)), u.length - 1);
  return (i === 0 ? b : (b/Math.pow(1024,i)).toFixed(2)) + " " + u[i];
}

const dropzone = document.getElementById("dropzone");
const fileInput = document.getElementById("fileInput");
const uploadList = document.getElementById("uploadList");

dropzone.addEventListener("dragover", e => { e.preventDefault(); dropzone.classList.add("drag"); });
dropzone.addEventListener("dragleave", () => dropzone.classList.remove("drag"));
dropzone.addEventListener("drop", e => {
  e.preventDefault();
  dropzone.classList.remove("drag");
  if (e.dataTransfer.files.length) queueUploads(e.dataTransfer.files);
});
fileInput.addEventListener("change", () => {
  if (fileInput.files.length) queueUploads(fileInput.files);
  fileInput.value = "";
});

function queueUploads(fileList) {
  Array.from(fileList).forEach(uploadSingleFile);
}

function uploadSingleFile(file) {
  const item = document.createElement("div");
  item.className = "item";
  item.innerHTML = `
    <div class="row">
      <span class="fname"></span>
      <span class="meta status">Queued</span>
    </div>
    <div class="bar-bg"><div class="bar-fill"></div></div>
    <div class="row meta">
      <span class="bytes">0 B / ${'$'}{fmtBytes(file.size)}</span>
      <span class="speed">-- MB/s</span>
    </div>`;
  item.querySelector(".fname").textContent = file.name;
  uploadList.prepend(item);

  const bar = item.querySelector(".bar-fill");
  const statusEl = item.querySelector(".status");
  const bytesEl = item.querySelector(".bytes");
  const speedEl = item.querySelector(".speed");

  const xhr = new XMLHttpRequest();
  let startTime = Date.now();
  xhr.open("POST", "/api/upload?token=" + encodeURIComponent(TOKEN), true);
  xhr.setRequestHeader("X-File-Name", encodeURIComponent(file.name));
  xhr.setRequestHeader("X-File-Size", String(file.size));
  xhr.setRequestHeader("Content-Type", file.type || "application/octet-stream");

  xhr.upload.onprogress = e => {
    if (!e.lengthComputable) return;
    const pct = Math.min(100, Math.round((e.loaded / e.total) * 100));
    bar.style.width = pct + "%";
    const elapsed = Math.max(0.1, (Date.now() - startTime) / 1000);
    const mbps = (e.loaded / (1024 * 1024)) / elapsed;
    statusEl.textContent = pct + "%";
    bytesEl.textContent = fmtBytes(e.loaded) + " / " + fmtBytes(e.total);
    speedEl.textContent = mbps.toFixed(2) + " MB/s";
  };

  xhr.onload = () => {
    if (xhr.status >= 200 && xhr.status < 300) {
      bar.style.width = "100%";
      statusEl.textContent = "Saved ✓";
      statusEl.className = "meta status ok";
    } else {
      statusEl.textContent = "Failed (" + xhr.status + ")";
      statusEl.className = "meta status err";
    }
  };
  xhr.onerror = () => {
    statusEl.textContent = "Network Error";
    statusEl.className = "meta status err";
  };
  xhr.send(file);
}

async function loadFiles() {
  const listEl = document.getElementById("downloadList");
  try {
    const res = await fetch("/api/files?token=" + encodeURIComponent(TOKEN));
    if (!res.ok) throw new Error("HTTP " + res.status);
    const files = await res.json();
    if (!files.length) {
      listEl.innerHTML = '<div class="empty">No files queued on phone yet.<br/>Select files in FlashShare &rarr; Send &rarr; Phone to PC.</div>';
      return;
    }
    listEl.innerHTML = "";
    files.forEach(f => {
      const div = document.createElement("div");
      div.className = "item";
      const dlUrl = "/api/download/" + encodeURIComponent(f.id) + "?token=" + encodeURIComponent(TOKEN);
      div.innerHTML = `
        <div class="row">
          <div>
            <div class="fname"></div>
            <div class="meta">${'$'}{f.ext} • ${'$'}{fmtBytes(f.size)}</div>
          </div>
          <a class="btn btn-primary" href="${'$'}{dlUrl}" download>Download</a>
        </div>`;
      div.querySelector(".fname").textContent = f.name;
      listEl.appendChild(div);
    });
  } catch (e) {
    document.getElementById("connText").textContent = "Connection Interrupted";
    listEl.innerHTML = '<div class="empty err">Unable to reach FlashShare server. Check Wi-Fi connection.</div>';
  }
}

loadFiles();
setInterval(loadFiles, 5000);
</script>
</body>
</html>"""
  }
}
