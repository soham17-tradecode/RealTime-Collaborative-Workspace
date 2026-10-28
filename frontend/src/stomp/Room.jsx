import { useEffect, useState, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
import SidebarPanel from "../Room/SidebarPanel";
import FloatingPanel from "../components/FloatingPanel";
import api from "../Api/axiosInstance";
import { uploadFile, downloadFile } from "../Api/fileApi";
import MonacoEditorPanel from "../Editor/MonacoEditorPanel";
import FileExplorer from "../FileExplorer/FileExplorer";
import ChatPanel from "../Room/ChatPanel";
import ToolPanel from "../Room/ToolPanel";
import Sidebar from "../Room/Sidebar";
import MembersPanel from "../Room/MembersPanel";
import EditorPanel from "../Room/EditorPanel";
import { saveWorkspace, loadWorkspace } from "../Api/workspaceApi";
import {
  connectWebSocket,
  disconnectWebSocket,
  sendMessage,
} from "../stomp/ChatMessage";
import ActivityFeed from "../components/ActivityFeed";
// import AIPanel from "../AI/AIPanel";

import "./Room.css";
import { sendPresence } from "../stomp/PresenceSocket";
import { connectPresence, disconnectPresence } from "../stomp/PresenceSocket";

function Room() {
  const [selectedFile, setSelectedFile] = useState(null);
  const [openTabs, setOpenTabs] = useState([]);
  const [fileContents, setFileContents] = useState({});
  const [leftPanel, setLeftPanel] = useState(null);
  const [dirtyFiles, setDirtyFiles] = useState({});
  const [mode, setMode] = useState("editor");
  // const [leftPanel, setLeftPanel] = useState("files");

  // const [showAI, setShowAI] = useState(false);
  const [showActivity, setShowActivity] = useState(false);
  // const [showExplorer, setShowExplorer] = useState(false);
  const [presence, setPresence] = useState({});
  // const [mode, setMode] = useState("editor");
  // const [showActivity, setShowActivity] = useState(false);

  const { roomCode } = useParams();

  const [user, setUser] = useState("");

  const navigate = useNavigate();
  const [cursor, setCursor] = useState({
    line: 1,
    column: 1,
  });

  const [members, setMembers] = useState([]);

  const [messages, setMessages] = useState([]);

  const [messageText, setMessageText] = useState("");

  const [validated, setValidated] = useState(false);
  const [workspaceLoaded, setWorkspaceLoaded] = useState(false);

  useEffect(() => {
    if (!workspaceLoaded) return;

    saveWorkspace(roomCode, selectedFile, openTabs).catch(console.error);
  }, [selectedFile, openTabs, workspaceLoaded]);
  useEffect(() => {
    if (!validated) return;

    loadWorkspace(roomCode)
      .then((res) => {
        setSelectedFile(res.data.selectedFile);

        setOpenTabs(res.data.openTabs || []);

        setWorkspaceLoaded(true);
      })
      .catch(console.error);
  }, [validated]);

  const handleEditorFileSelect = (fileName) => {
    setSelectedFile(fileName);

    setOpenTabs((prev) => {
      if (!fileName) return [];

      if (prev.includes(fileName)) return prev;

      return [...prev, fileName];
    });

    if (fileName) {
      sendPresence({
        roomCode,
        sender: user,
        status: "dsrgfsr",
        fileName,
      });
    }
  };

  useEffect(() => {
    if (!validated) return;

    api.get(`/presence/${roomCode}`).then((res) => {
      setPresence(res.data);
    });
  }, [roomCode, validated]);

  useEffect(() => {
    if (!validated || !user) return;

    if (mode === "chat") {
      sendPresence({
        roomCode,
        sender: user,
        status: "CHAT",
        fileName: "",
      });
    }
  }, [mode, roomCode, user, validated]);

  useEffect(() => {
    if (!validated) return;

    connectPresence(roomCode, (message) => {
      setPresence((prev) => ({
        ...prev,
        [message.sender]: message,
      }));
    });

    return () => {
      disconnectPresence();
    };
  }, [roomCode, validated]);

  useEffect(() => {
    let cancelled = false;

    const validateRoom = async () => {
      try {
        console.log("Validating room:", roomCode);

        const response = await api.get(`/roomCode/${roomCode}/validated`);

        console.log(
          "Room validation response:",
          response.status,
          response.data,
        );

        if (!cancelled) {
          setValidated(true);
        }
      } catch (error) {
        console.error("Room validation failed:", {
          roomCode,
          status: error.response?.status,
          data: error.response?.data,
          url: error.config?.url,
          baseURL: error.config?.baseURL,
        });

        if (cancelled) return;

        if (error.response?.status === 404) {
          navigate("/room-not-found", { replace: true });
        } else if (error.response?.status === 403) {
          navigate("/access-denied", { replace: true });
        }
      }
    };

    if (roomCode) {
      validateRoom();
    }

    return () => {
      cancelled = true;
    };
  }, [roomCode, navigate]);

  useEffect(() => {
    if (!validated) return;

    connectWebSocket(
      roomCode,

      (message) => {
        console.log("WS RECEIVED:", message);

        setMessages((prev) => [...prev, message]);
      },
    );

    return () => {
      disconnectWebSocket();
    };
  }, [roomCode, validated]);

  useEffect(() => {
    fetch(`http://localhost:8080/${roomCode}/members`, {
      credentials: "include",
    })
      .then((res) => res.json())
      .then((data) => {
        setMembers(data);
      });
  }, [roomCode]);

  const handleSend = () => {
    if (!messageText.trim()) return;

    sendPresence({
      roomCode,
      sender: user,
      status: "CHAT",
      fileName: "",
    });

    sendMessage(user, roomCode, messageText);

    setMessageText("");
  };

  const leaveRoom = async () => {
    try {
      await api.post("/leave", {
        roomCode,
      });

      navigate("/");
    } catch (error) {
      console.error(error);
    }
  };

  useEffect(() => {
    api
      .get(`/roomCode/${roomCode}/messages`)
      .then((response) => {
        setMessages(response.data);
      })
      .catch((error) => {
        console.log(error);
      });
  }, [roomCode]);

  useEffect(() => {
    api
      .post("/me")
      .then((response) => {
        setUser(response.data.username);
      })
      .catch(console.error);
  }, []);

  const chatRef = useRef(null);
  useEffect(() => {
    if (chatRef.current) {
      chatRef.current.scrollTop = chatRef.current.scrollHeight;
    }
  }, [messages]);

  const handleFileSelect = async (e) => {
    const file = e.target.files[0];
    console.log("Selected file:", file);
    if (!file) return;

    try {
      await uploadFile(file, roomCode);
    } catch (error) {
      console.error(error);
    }
  };

  return (
    <div className="room-container">
      <Sidebar
        mode={mode}
        setMode={setMode}
        leftPanel={leftPanel}
        setLeftPanel={setLeftPanel}
      />
      <ToolPanel
        leftPanel={leftPanel}
        roomCode={roomCode}
        selectedFile={selectedFile}
        onFileSelect={handleEditorFileSelect}
        fileContents={fileContents}
      />

      {mode === "chat" && (
        <ChatPanel
          roomCode={roomCode}
          user={user}
          members={members}
          presence={presence}
          leaveRoom={leaveRoom}
          messages={messages}
          messageText={messageText}
          setMessageText={setMessageText}
          handleSend={handleSend}
          handleFileSelect={handleFileSelect}
          downloadFile={downloadFile}
          chatRef={chatRef}
        />
      )}

      {mode === "editor" && (
        <EditorPanel
          roomCode={roomCode}
          selectedFile={selectedFile}
          setSelectedFile={setSelectedFile}
          openTabs={openTabs}
          setOpenTabs={setOpenTabs}
          fileContents={fileContents}
          setFileContents={setFileContents}
          dirtyFiles={dirtyFiles}
          setDirtyFiles={setDirtyFiles}
          members={members}
          presence={presence}
          cursor={cursor}
          setCursor={setCursor}
          user={user}
          leaveRoom={leaveRoom}
        />
      )}

      {/* {showActivity && (
        <FloatingPanel
          title="Workspace Activity"
          onClose={() => setShowActivity(false)}
        >
          <ActivityFeed roomCode={roomCode} />
        </FloatingPanel>
      )}

      {showExplorer && (
        <FloatingPanel
          title="File Explorer"
          onClose={() => setShowExplorer(false)}
        >
          <FileExplorer
            roomCode={roomCode}
            selectedFile={selectedFile}
            onFileSelect={setSelectedFile}
          />
        </FloatingPanel>
      )}
      {showAI && (
        <FloatingPanel title="AI Assistant" onClose={() => setShowAI(false)}>
          <AIPanel />
        </FloatingPanel>
      )} */}
    </div>
  );
}

export default Room;
