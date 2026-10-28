import { useState, useEffect } from "react";
import axios from "axios";
import "./Dashboard.css";
import { useNavigate } from "react-router-dom";
import api from "../Api/axiosInstance";

function Dashboard() {
  const [roomName, setRoomName] = useState("");
  const [roomCode, setRoomCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const navigate = useNavigate();
  const handleCreateRoom = async (e) => {
    e.preventDefault();
    if (!roomName.trim()) {
      setMessage("Room name is required");
      return;
    }
    try {
      setLoading(true);
      setMessage("");
      const response = await api.post(
        "/room",
        {
          roomName: roomName.trim(),
        },
        {
          // withCredentials: true
        },
      );
      const roomCode = response.data.roomCode;

      await api.post("/joinRoom", {
        roomCode: roomCode,
      });
      navigate(`/room/${roomCode}`);

      // setMessage(`Room created: ${roomCode}`);
      // navigate(`/room/${roomCode}`);

      //optional
      // setRoomName("");
    } catch (error) {
      console.error(error);
      if (error.response) {
        setMessage(error.response.data.message || "Failed to create room");
      } else {
        setMessage("Server not reachable");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="dashboard-container">
      <div className="dashboard-card">
        <h2>Create Room</h2>

        <form onSubmit={handleCreateRoom}>
          <input
            type="text"
            placeholder="Enter room name"
            value={roomName}
            onChange={(e) => setRoomName(e.target.value)}
            className="room-input"
          />

          <button
            type="submit"
            disabled={loading}
            className="create-btn"
            // onClick={join}
          >
            {loading ? "Creating..." : "Create Room"}
          </button>
        </form>

        <button className="close-btn" onClick={() => navigate("/")}>
          Back to Home
        </button>

        {message && <p className="message">{message}</p>}
      </div>
    </div>
  );
}
export default Dashboard;
