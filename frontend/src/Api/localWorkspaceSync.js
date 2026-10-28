const LOCAL_SYNC_URL = "http://127.0.0.1:4179/api";

async function request(path, options = {}) {
  let response;
  try {
    response = await fetch(`${LOCAL_SYNC_URL}${path}`, {
      ...options,
      headers: {
        "Content-Type": "application/json",
        ...(options.headers || {}),
      },
    });
  } catch {
    throw new Error("Local Workspace Sync is not running. Start the LocalWorkspaceSync service on your PC.");
  }

  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.error || `Local sync failed (${response.status}).`);
  return data;
}

export const getLocalWorkspaceStatus = () => request("/status");
export const selectLocalWorkspaceFolder = () =>
  request("/select-folder", { method: "POST", body: "{}" });
export const syncLocalFile = (fileName, content) =>
  request("/sync-file", {
    method: "POST",
    body: JSON.stringify({ fileName, content }),
  });
export const deleteLocalFile = (fileName) =>
  request("/delete-file", {
    method: "POST",
    body: JSON.stringify({ fileName }),
  });
export const renameLocalFile = (oldFileName, newFileName) =>
  request("/rename-file", {
    method: "POST",
    body: JSON.stringify({ oldFileName, newFileName }),
  });

// Used by the editor's debounced change handler: if the local service is not
// running or no folder has been selected, normal online editing still works.
export async function syncEditorChangeIfEnabled(fileName, content) {
  try {
    const status = await getLocalWorkspaceStatus();
    if (!status.folderSelected) return { skipped: true };
    return await syncLocalFile(fileName, content);
  } catch {
    return { skipped: true };
  }
}

export async function downloadLocalWorkspaceZip(fileNames) {
  let response;
  try {
    response = await fetch(`${LOCAL_SYNC_URL}/export-zip`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ fileNames }),
    });
  } catch {
    throw new Error("Local Workspace Sync is not running. Start the service first.");
  }
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    throw new Error(data.error || "ZIP export failed.");
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = "workspace.zip";
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}
