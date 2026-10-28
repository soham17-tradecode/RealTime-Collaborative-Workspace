import { useEffect, useState } from "react";

const INTERNET_CHECK_URL = "https://www.google.com/generate_204";

const CHECK_INTERVAL_MS = 30000;
const REQUEST_TIMEOUT_MS = 5000;

export default function useInternetConnection() {
  const [isOnline, setIsOnline] = useState(false);

  useEffect(() => {
    let mounted = true;
    let checking = false;
    let controller = null;
    let timeoutId = null;

    const checkInternet = async () => {
      if (!navigator.onLine) {
        setIsOnline(false);
        return;
      }

      if (checking) return;

      checking = true;
      controller = new AbortController();

      timeoutId = setTimeout(() => {
        controller?.abort();
      }, REQUEST_TIMEOUT_MS);

      try {
        await fetch(INTERNET_CHECK_URL, {
          method: "GET",
          mode: "no-cors",
          cache: "no-store",
          signal: controller.signal,
        });

        if (mounted) {
          setIsOnline(true);
        }
      } catch {
        if (mounted) {
          setIsOnline(false);
        }
      } finally {
        clearTimeout(timeoutId);
        timeoutId = null;
        controller = null;
        checking = false;
      }
    };

    const handleOnline = () => {
      checkInternet();
    };

    const handleOffline = () => {
      controller?.abort();
      setIsOnline(false);
    };

    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);

    checkInternet();

    const intervalId = setInterval(checkInternet, CHECK_INTERVAL_MS);

    return () => {
      mounted = false;
      clearInterval(intervalId);
      clearTimeout(timeoutId);
      controller?.abort();

      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
    };
  }, []);

  return isOnline;
}
