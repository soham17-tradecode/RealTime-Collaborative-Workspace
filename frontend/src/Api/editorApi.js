import api from "./axiosInstance";

export const createFile = (roomCode, fileName) => {
  return api.post(
    `/room/${roomCode}/files`,

    {
      fileName,
    },
  );
};

export const getFiles = (roomCode) => {
  return api.get(`/room/${roomCode}/allFiles`);
};

export const openFile = (roomCode, fileName) => {
  return api.get(`/room/${roomCode}/workspace/${fileName}`);
};

export const saveFile = (roomCode, fileName, content) => {
  return api.post(
    `/room/${roomCode}/workspace/${fileName}`,

    {
      content,
    },
  );
};

export const renameFile = (roomCode, oldFileName, newFileName) => {
  return api.put(
    `/room/${roomCode}/workspace/rename`,

    {
      oldFileName,
      newFileName,
    },
  );
};

export const deleteFile = (roomCode, fileName) => {
  return api.delete(`/room/${roomCode}/workspace/${fileName}`);
};

export const copyFile = (roomCode, sourceFile, targetFile = "") => {
  return api.post(`/room/${roomCode}/workspace/copy`, {
    sourceFile,
    targetFile,
  });
};

export const moveFile = (roomCode, sourceFile, targetFile) => {
  return api.put(`/room/${roomCode}/workspace/move`, {
    sourceFile,
    targetFile,
  });
};

// Download one workspace file using the browser's standard download flow.
export const downloadWorkspaceFile = (roomCode, fileName) => {
  return api.get(
    `/room/${encodeURIComponent(roomCode)}/workspace/${encodeURIComponent(fileName)}/download`,
    { responseType: "blob" },
  );
};

// Download all workspace files as a ZIP archive.
export const downloadWorkspaceZip = (roomCode) => {
  return api.get(
    `/room/${encodeURIComponent(roomCode)}/workspace/download-zip`,
    { responseType: "blob" },
  );
};
