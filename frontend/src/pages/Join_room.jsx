import { useState } from "react";
import "./Dashboard.css";
import { useNavigate } from "react-router-dom";
import api from "../Api/axiosInstance";

function Join_room({ onClose }) {
  const [roomCode, setRoomCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const navigate = useNavigate();

  const handleJoinRoom = async (e) => {
    e.preventDefault();
    const normalizedCode = roomCode.trim().toUpperCase();

    if (!normalizedCode) {
      setMessage("Room code is required");
      return;
    }

    try {
      setLoading(true);
      setMessage("");

      // Backend returns 200 for both a first join and an existing member.
      const response = await api.post("/joinRoom", {
        roomCode: normalizedCode,
      });

      if (response.status === 200) {
        navigate(`/room/${normalizedCode}`);
        if (onClose) onClose();
      }
    } catch (error) {
      console.error("Join room failed:", error);
      const data = error.response?.data;
      setMessage(
        typeof data === "string"
          ? data
          : data?.message || "Could not join room. Check the room code and backend logs."
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="dashboard-container">
      <div className="dashboard-card">
        <h2>Join Room</h2>
        <form onSubmit={handleJoinRoom}>
          <input
            type="text"
            placeholder="Enter room code"
            value={roomCode}
            onChange={(e) => setRoomCode(e.target.value)}
            className="room-input"
            autoComplete="off"
          />
          <button type="submit" disabled={loading} className="create-btn">
            {loading ? "Joining..." : "Join Room"}
          </button>
        </form>
        <button type="button" className="close-btn" onClick={onClose}>
          Close
        </button>
        {message && <p className="message" role="alert">{message}</p>}
      </div>
    </div>
  );
}

export default Join_room;
