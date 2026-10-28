import { useState } from "react";
import "./Navbar.css";
import { useNavigate } from "react-router-dom";

import LoginModal from "./LoginModal";
import RegisterModal from "./RegisterModal";
import Dashboard from "../pages/Dashboard";
import Join_room from "../pages/Join_room";

function Navbar({ user }) {
  const [showLogin, setShowLogin] = useState(false);
  const [showRegister, setShowRegister] = useState(false);
  const [showDropdown, setShowDropdown] = useState(false);
  const [joinRoom, setjoinRoom] = useState(false);

  const navigate = useNavigate();

  const logout = async (e) => {
    if (e) e.preventDefault();
    try {
      await fetch("http://localhost:8080/logout", {
        method: "POST",
        credentials: "include",
      });
    } catch (error) {
      console.error("Logout failed:", error);
    }
    window.location.reload();
  };

  return (
    <>
      <nav className="navbar">
        <div className="logo" onClick={() => navigate("/")}>
          Workspace
        </div>

        <div className="auth-buttons">
          {user ? (
            <div className="user-section">
              <button
                className="create-room-btn"
                onClick={() => navigate("/dashboard")}
              >
                + Create Room
              </button>

              <button
                className="Join-room-btn"
                onClick={() => setjoinRoom(true)}
              >
                Join Room
              </button>

              <button className="notification" aria-label="Notifications">
                🔔
              </button>

              <div
                className="profile-container"
                onClick={() => setShowDropdown(!showDropdown)}
              >
                <div className="avatar">{user.charAt(0).toUpperCase()}</div>
                <span className="user-name">{user}</span>
                <span className="arrow">▼</span>

                {showDropdown && (
                  <div className="dropdown-menu">
                    <div className="dropdown-user">👤 {user}</div>
                    <div className="dropdown-divider"></div>
                    <button
                      className="dropdown-item"
                      onClick={() => { setShowDropdown(false); navigate("/my-rooms"); }}
                    >My Rooms</button>
                    <button
                      className="dropdown-item logout-item"
                      onClick={logout}
                    >
                      Logout
                    </button>
                  </div>
                )}
              </div>
            </div>
          ) : (
            <>
              <button className="login-btn" onClick={() => setShowLogin(true)}>
                Login
              </button>
              <button
                className="signup-btn"
                onClick={() => setShowRegister(true)}
              >
                Register
              </button>
            </>
          )}
        </div>
      </nav>

      {showLogin && <LoginModal onClose={() => setShowLogin(false)} />}
      {showRegister && <RegisterModal onClose={() => setShowRegister(false)} />}
      {joinRoom && <Join_room onClose={() => setjoinRoom(false)} />}
    </>
  );
}

export default Navbar;
