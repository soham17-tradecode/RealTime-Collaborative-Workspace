import { useEffect, useState } from "react";
import "./FileExplorer.css";
import { getFiles, createFile } from "../Api/editorApi";
import CreateFileModal from "./createFileModel";
import { renameFile } from "../Api/editorApi";
import { deleteFile } from "../Api/editorApi";
import { copyFile } from "../Api/editorApi";
import { moveFile } from "../Api/editorApi";
import { downloadWorkspaceFile, downloadWorkspaceZip } from "../Api/editorApi";
import { connectFileSocket, disconnectFileSocket } from "../stomp/FileSocket";

export default function FileExplorer({ roomCode, selectedFile, onFileSelect }) {
  const [files, setFiles] = useState([]);
  const [collapsed, setCollapsed] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [editingFile, setEditingFile] = useState(null);
  const [newFileName, setNewFileName] = useState("");

  const [deleteModal, setDeleteModal] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [clipboard, setClipboard] = useState(null);
  const [explorerMenu, setExplorerMenu] = useState({
    visible: false,
    x: 0,
    y: 0,
  });

  /*
clipboard = {
    type: "copy",
    file: "Main.java"
}
*/

  const handleRename = async () => {
    if (!editingFile) return;

    const trimmed = newFileName.trim();

    if (!trimmed) {
      setEditingFile(null);
      return;
    }

    if (trimmed === editingFile) {
      setEditingFile(null);
      setNewFileName("");
      return;
    }

    try {
      await renameFile(roomCode, editingFile, trimmed);

      await loadFiles();

      if (selectedFile === editingFile) {
        onFileSelect(trimmed);
      }

      setEditingFile(null);
      setNewFileName("");
    } catch (error) {
      console.error(error);
    }
  };

  //delete file
  const handleDelete = async () => {
    try {
      await deleteFile(roomCode, deleteTarget);

      await loadFiles();

      if (selectedFile === deleteTarget) {
        onFileSelect(null);
      }

      setDeleteModal(false);
      setDeleteTarget(null);
    } catch (error) {
      console.error(error);
    }
  };
  //paste handler
  const handlePaste = async () => {
    if (!clipboard) return;

    try {
      let response;

      if (clipboard.type === "copy") {
        response = await copyFile(roomCode, clipboard.file, "");
      } else {
        response = await moveFile(roomCode, clipboard.file, "");
      }

      await loadFiles();

      onFileSelect(response.data.fileName);

      setClipboard(null);
    } catch (error) {
      console.error(error);
    }
  };

  //copy -->
  const handleCopy = () => {
    if (!contextMenu.file) return;

    setClipboard({
      type: "copy",
      file: contextMenu.file,
    });

    setContextMenu((prev) => ({
      ...prev,
      visible: false,
    }));
  };

  const [contextMenu, setContextMenu] = useState({
    visible: false,

    x: 0,
    y: 0,
    file: null,
  });

  useEffect(() => {
    loadFiles();
  }, [roomCode]);

  useEffect(() => {
    const closeMenu = () => {
      setContextMenu((prev) => ({
        ...prev,
        visible: false,
      }));

      setExplorerMenu((prev) => ({
        ...prev,
        visible: false,
      }));
    };

    window.addEventListener("click", closeMenu);

    return () => {
      window.removeEventListener("click", closeMenu);
    };
  }, []);

  useEffect(() => {
    connectFileSocket(roomCode, (event) => {
      console.log("FILE EVENT", event);

      switch (event.event) {
        case "CREATE":
          setFiles((prev) => {
            if (prev.includes(event.fileName)) return prev;
            return [...prev, event.fileName];
          });
          break;

        case "DELETE":
          setFiles((prev) => prev.filter((file) => file !== event.fileName));

          if (selectedFile === event.fileName) {
            onFileSelect(null);
            //change
            sendPresence({
              roomCode,
              sender: user,
              status: "EDITOR",
              fileName: "",
            });
          }
          break;

        case "RENAME":
          setFiles((prev) =>
            prev.map((file) =>
              file === event.oldFileName ? event.fileName : file,
            ),
          );

          if (selectedFile === event.oldFileName) {
            onFileSelect(event.fileName);
          }
          break;

        case "COPY":
          setFiles((prev) => {
            if (prev.includes(event.fileName)) return prev;
            return [...prev, event.fileName];
          });
          break;

        case "MOVE":
          setFiles((prev) =>
            prev.map((file) =>
              file === event.oldFileName ? event.fileName : file,
            ),
          );

          if (selectedFile === event.oldFileName) {
            onFileSelect(event.fileName);
          }
          break;

        default:
          loadFiles();
      }
    });
  }, [roomCode]);

  const loadFiles = async () => {
    try {
      const response = await getFiles(roomCode);
      setFiles(response.data);
    } catch (error) {
      console.error(error);
    }
  };

  const triggerBrowserDownload = (blob, fileName) => {
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  };

  const handleDownloadFile = async (fileName) => {
    try {
      const response = await downloadWorkspaceFile(roomCode, fileName);
      triggerBrowserDownload(response.data, fileName);
      setContextMenu((prev) => ({ ...prev, visible: false }));
    } catch (error) {
      console.error("Failed to download workspace file", error);
      window.alert(`Could not download ${fileName}. Please try again.`);
    }
  };

  const handleDownloadZip = async () => {
    try {
      const response = await downloadWorkspaceZip(roomCode);
      triggerBrowserDownload(response.data, `workspace-${roomCode}.zip`);
    } catch (error) {
      console.error("Failed to download workspace ZIP", error);
      window.alert("Could not download the workspace ZIP. Please try again.");
    }
  };

  const handleCreateFile = async (fileName) => {
    console.log("STEP 1");

    const response = await createFile(roomCode, fileName);

    console.log("STEP 2", response);

    await loadFiles();

    console.log("STEP 3");

    onFileSelect(fileName);

    console.log("STEP 4");

    setShowModal(false);

    console.log("STEP 5");
  };

  //cut-->
  const handleCut = () => {
    if (!contextMenu.file) return;

    setClipboard({
      type: "cut",
      file: contextMenu.file,
    });

    setContextMenu((prev) => ({
      ...prev,
      visible: false,
    }));
  };

  return (
    <>
      <div className="explorer">
        <div className="explorer-title">
          <span onClick={() => setCollapsed(!collapsed)}>
            {collapsed ? "▶ Files" : "▼ Files"}
          </span>

          <div className="explorer-actions">
            <button className="new-file-btn" title="Create file" onClick={() => setShowModal(true)}>
              +
            </button>

            <button
              className="download-all-btn"
              title="Download all workspace files as ZIP"
              onClick={(e) => {
                e.stopPropagation();
                handleDownloadZip();
              }}
              disabled={files.length === 0}
            >
              ZIP
            </button>

            <button
              className="refresh-btn"
              onClick={(e) => {
                e.stopPropagation();
                loadFiles();
              }}
            >
              ⟳
            </button>
          </div>
        </div>

        {!collapsed && (
          <div
            className="explorer-files"
            onClick={() => {
              setExplorerMenu({
                visible: false,
                x: 0,
                y: 0,
              });
            }}
            onContextMenu={(e) => {
              if (e.target !== e.currentTarget) return;

              e.preventDefault();
              setContextMenu({
                visible: false,
                x: 0,
                y: 0,
                file: null,
              });
              setExplorerMenu({
                visible: true,
                x: e.pageX,
                y: e.pageY,
              });

              setExplorerMenu({
                visible: true,
                x: e.pageX,
                y: e.pageY,
              });
            }}
          >
            {files.length === 0 ? (
              <div className="empty-folder">
                📂
                <br />
                No files available
              </div>
            ) : (
              files.map((fileName) => (
                <div
                  key={fileName}
                  className={
                    selectedFile === fileName
                      ? "explorer-file active"
                      : "explorer-file"
                  }
                  onClick={() => {
                    setContextMenu({
                      visible: false,
                      x: 0,
                      y: 0,
                      file: null,
                    });

                    if (editingFile !== fileName) {
                      onFileSelect(fileName);
                    }
                  }}
                  onContextMenu={(e) => {
                    e.preventDefault();

                    setExplorerMenu({
                      visible: false,
                      x: 0,
                      y: 0,
                    });

                    setContextMenu({
                      visible: true,
                      x: e.pageX,
                      y: e.pageY,
                      file: fileName,
                    });
                  }}
                >
                  <span className="file-icon">📄</span>

                  {editingFile === fileName ? (
                    <input
                      className="rename-input"
                      autoFocus
                      value={newFileName}
                      onClick={(e) => e.stopPropagation()}
                      onChange={(e) => setNewFileName(e.target.value)}
                      onBlur={handleRename}
                      onKeyDown={(e) => {
                        if (e.key === "Enter") {
                          handleRename();
                        }

                        if (e.key === "Escape") {
                          setEditingFile(null);
                          setNewFileName("");
                        }
                      }}
                    />
                  ) : (
                    <span className="file-name">{fileName}</span>
                  )}

                  {editingFile !== fileName && (
                    <button
                      type="button"
                      className="file-download-btn"
                      title={`Download ${fileName}`}
                      aria-label={`Download ${fileName}`}
                      onClick={(e) => {
                        e.stopPropagation();
                        handleDownloadFile(fileName);
                      }}
                    >
                      ↓
                    </button>
                  )}
                </div>
              ))
            )}
          </div>
        )}
      </div>

      {contextMenu.visible && (
        <div
          className="context-menu"
          style={{
            left: contextMenu.x,
            top: contextMenu.y,
          }}
        >
          <div
            className="context-item"
            onClick={() => {
              console.log("Rename", contextMenu.file);
              setEditingFile(contextMenu.file);

              setNewFileName(contextMenu.file);

              setContextMenu({
                ...contextMenu,
                visible: false,
              });
            }}
          >
            ✏ Rename
          </div>

          <div className="context-item" onClick={handleCopy}>
            📋 Copy
          </div>

          <div className="context-item" onClick={handleCut}>
            ✂ Cut
          </div>

          <div
            className="context-item"
            onClick={() => handleDownloadFile(contextMenu.file)}
          >
            ⬇ Download
          </div>

          <div
            className="context-item delete"
            onClick={() => {
              setDeleteTarget(contextMenu.file);

              setDeleteModal(true);

              setContextMenu((prev) => ({
                ...prev,
                visible: false,
              }));
            }}
          >
            🗑 Delete
          </div>
        </div>
      )}
      {explorerMenu.visible && (
        <div
          className="context-menu"
          style={{
            left: explorerMenu.x,
            top: explorerMenu.y,
          }}
        >
          <div
            className="context-item"
            onClick={() => {
              setShowModal(true);

              setExplorerMenu({
                visible: false,
                x: 0,
                y: 0,
              });
            }}
          >
            📄 New File
          </div>

          {clipboard && (
            <div
              className="context-item"
              onClick={async () => {
                await handlePaste();

                setExplorerMenu({
                  visible: false,
                  x: 0,
                  y: 0,
                });
              }}
            >
              📥 Paste
            </div>
          )}

          <div className="context-divider"></div>

          <div
            className="context-item"
            onClick={() => {
              loadFiles();

              setExplorerMenu({
                visible: false,
                x: 0,
                y: 0,
              });
            }}
          >
            🔄 Refresh
          </div>
        </div>
      )}

      {deleteModal && (
        <div className="modal-overlay">
          <div className="delete-modal">
            <h3>Delete File</h3>

            <p>
              Are you sure you want to delete
              <br />
              <b>{deleteTarget}</b> ?
            </p>

            <div className="delete-actions">
              <button
                className="cancel-btn"
                onClick={() => {
                  setDeleteModal(false);
                  setDeleteTarget(null);
                }}
              >
                Cancel
              </button>

              <button className="delete-btn" onClick={handleDelete}>
                Delete
              </button>
            </div>
          </div>
        </div>
      )}

      <CreateFileModal
        open={showModal}
        onClose={() => setShowModal(false)}
        onCreate={handleCreateFile}
      />
    </>
  );
}
