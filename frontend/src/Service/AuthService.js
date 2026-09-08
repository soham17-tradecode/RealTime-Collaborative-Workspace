// import api from "../Api/axiosInstance";\
import axios from "axios";

export const refreshAccessToken = async () => {
  const response = await axios.post(
    "http://localhost:8080/refresh",
    {},
    {
      withCredentials: true,
    },
  );
  return response.data;
};
