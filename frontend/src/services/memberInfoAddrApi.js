import fetchAPI from "./api.js";

const BASE_URL = "/member-info-addr";

const getAuthHeaders = () => {
  const token = localStorage.getItem("accessToken");
  const headers = {
    "Content-Type": "application/json",
  };
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  return headers;
};

export const createMemberInfoAddr = async (data) => {
  const response = await fetchAPI(`${BASE_URL}`, {
    method: "POST",
    headers: getAuthHeaders(),
    body: JSON.stringify(data),
  });
  return response;
};

export const updateMemberInfoAddr = async (id, data) => {
  const response = await fetchAPI(`${BASE_URL}/${id}`, {
    method: "PUT",
    headers: getAuthHeaders(),
    body: JSON.stringify(data),
  });
  return response;
};

export const setDefaultMemberInfoAddr = async (id) => {
  const response = await fetchAPI(`${BASE_URL}/${id}/default`, {
    method: "PUT",
    headers: getAuthHeaders(),
  });
  return response;
};

export const getMemberInfoAddrList = async (memberId) => {
  const response = await fetchAPI(`${BASE_URL}/member/${memberId}`, {
    method: "GET",
    headers: getAuthHeaders(),
  });
  return response || [];
};

export const deleteMemberInfoAddr = async (id) => {
  const response = await fetchAPI(`${BASE_URL}/${id}`, {
    method: "DELETE",
    headers: getAuthHeaders(),
  });
  return response;
};

