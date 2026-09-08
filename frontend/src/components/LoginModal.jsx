import { useState } from "react";
import "./Model.css";

function LoginModal({ onClose }) {
  const [username, setUsername] = useState("");

  const [password, setPassword] = useState("");

  const [message, setMessage] = useState("");

  const login = async (e) => {
    e.preventDefault();

    console.log("LOGIN FUNCTION CALLED");

    try {
      const response = await fetch("http://localhost:8080/login", {
        method: "POST",
        credentials: "include",

        headers: {
          "Content-Type": "application/json",
        },

        body: JSON.stringify({
          username,
          password,
        }),
      });

      if (response.ok) {
        // setMessage("username not match")
        // alert("logged in success");
        onClose();
        window.location.reload();
      } else {
        setMessage("username not matched");
      }
    } catch (error) {
      console.error(error);

      setMessage("Server Error");
    }
  };

  return (
    <div className="modal">
      <div className="modal-content">
        <h2>Login</h2>

        <form onSubmit={login}>
          <input
            type="text"
            placeholder="Username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />

          <input
            type="password"
            placeholder="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />

          <button type="submit" className="modal-btn">
            Login
          </button>
        </form>

        <button className="close-btn" onClick={onClose}>
          Close
        </button>

        <p className="error-message">{message}</p>
      </div>
    </div>
  );
}

export default LoginModal;
