import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../Api/axiosInstance";
import "./MyRooms.css";

function MyRooms() {
  const [rooms, setRooms] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [deletingCode, setDeletingCode] = useState("");
  const navigate = useNavigate();

  const loadRooms = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const response = await api.get("/my-rooms");
      setRooms(Array.isArray(response.data) ? response.data : []);
    } catch (err) {
      setError(
        err.response?.data?.message ||
          "Could not load your rooms. Please try again.",
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadRooms();
  }, [loadRooms]);

  const deleteRoom = async (room) => {
    const confirmed = window.confirm(
      `Permanently delete "${room.roomName}" (${room.roomCode})? This deletes the room's messages, activity history, uploaded-file records, and workspace files. This cannot be undone.`,
    );
    if (!confirmed) return;

    setDeletingCode(room.roomCode);
    setError("");
    try {
      await api.delete(`/room/${encodeURIComponent(room.roomCode)}`);
      setRooms((current) =>
        current.filter((item) => item.roomCode !== room.roomCode),
      );
    } catch (err) {
      setError(
        err.response?.data?.message ||
          err.response?.data ||
          "Room deletion failed.",
      );
    } finally {
      setDeletingCode("");
    }
  };

  return (
    <main className="my-rooms-page">
      <section className="my-rooms-header">
        <div>
          <p className="my-rooms-eyebrow">WORKSPACE MANAGEMENT</p>
          <h1>My Rooms</h1>
          <p className="my-rooms-subtitle">
            Rooms you created, with their room codes.
          </p>
        </div>
        <button
          className="my-rooms-create"
          onClick={() => navigate("/dashboard")}
        >
          + Create Room
        </button>
      </section>

      {error && (
        <div className="my-rooms-error" role="alert">
          {String(error)}
        </div>
      )}

      {loading ? (
        <div className="my-rooms-empty">Loading your rooms…</div>
      ) : rooms.length === 0 ? (
        <div className="my-rooms-empty">
          <h2>No rooms created yet</h2>
          <p>Create a room to see its name and code here.</p>
          <button
            className="my-rooms-create"
            onClick={() => navigate("/dashboard")}
          >
            Create your first room
          </button>
        </div>
      ) : (
        <div className="my-rooms-list">
          {rooms.map((room) => (
            <article className="my-room-card" key={room.roomCode}>
              <div className="my-room-icon" aria-hidden="true">
                ⌘
              </div>
              <div className="my-room-details">
                <h2>{room.roomName || "Untitled room"}</h2>
                <p>
                  Room code <code>{room.roomCode}</code>
                </p>
                {room.createdAt && (
                  <span className="my-room-date">Created {room.createdAt}</span>
                )}
              </div>
              <div className="my-room-actions">
                <button
                  className="my-room-open"
                  onClick={() =>
                    navigate(`/room/${encodeURIComponent(room.roomCode)}`)
                  }
                >
                  Open room
                </button>
                <button
                  className="my-room-delete"
                  disabled={deletingCode === room.roomCode}
                  onClick={() => deleteRoom(room)}
                >
                  {deletingCode === room.roomCode ? "Deleting…" : "Delete"}
                </button>
              </div>
            </article>
          ))}
        </div>
      )}

      <button className="my-rooms-back" onClick={() => navigate("/")}>
        ← Back to home
      </button>
    </main>
  );
}

export default MyRooms;
