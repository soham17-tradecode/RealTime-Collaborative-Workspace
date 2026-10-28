import { useState, useEffect } from "react";
import { Routes, Route } from "react-router-dom";

import Home from "./pages/Home";
import Dashboard from "./pages/Dashboard";
import MyRooms from "./pages/MyRooms";
import Room from "./stomp/Room";
import useInternetConnection from "./hooks/useInternetConnection";
import api from "./Api/axiosInstance";

function App() {
  const [user, setUser] = useState(null);
  const [checkingUser, setCheckingUser] = useState(true);

  const isOnline = useInternetConnection();

  useEffect(() => {
    if (!isOnline) {
      setCheckingUser(false);
      setUser(null);
      return;
    }

    let cancelled = false;

    api
      .post("/me")
      .then((response) => {
        if (!cancelled) {
          setUser(response.data.username);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setUser(null);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setCheckingUser(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [isOnline]);

  if (!isOnline) {
    return (
      <main
        style={{
          minHeight: "100vh",
          display: "flex",
          flexDirection: "column",
          justifyContent: "center",
          alignItems: "center",
          padding: "24px",
          textAlign: "center",
          fontFamily: "Arial, sans-serif",
          backgroundColor: "#ffffff",
          color: "#222222",
        }}
      >
        <h2>Internet connection required</h2>
        <p>Connect to the internet to use this application.</p>
        <button onClick={() => window.location.reload()}>
          Retry connection
        </button>
      </main>
    );
  }

  if (checkingUser) {
    return (
      <main
        style={{
          minHeight: "100vh",
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
          fontFamily: "Arial, sans-serif",
        }}
      >
        Checking your session...
      </main>
    );
  }

  return (
    <Routes>
      <Route path="/" element={<Home user={user} />} />
      <Route path="/dashboard" element={<Dashboard />} />
      <Route path="/my-rooms" element={<MyRooms />} />
      <Route path="/room/:roomCode" element={<Room />} />
    </Routes>
  );
}

export default App;
