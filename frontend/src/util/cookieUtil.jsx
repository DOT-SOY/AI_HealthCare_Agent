import { Cookies } from "react-cookie";

let cookies = null;
try {
  cookies = new Cookies();
} catch (_) {
  // CookiesProvider 미마운트 시 new Cookies() 실패 가능 → document.cookie 폴백으로 앱 크래시 방지
}

function getCookieFallback(name) {
  if (typeof document === "undefined" || !document.cookie) return undefined;
  const match = document.cookie.match(new RegExp("(?:^|; )" + name.replace(/([.$?*|{}()[\]\\/+^])/g, "\\$1") + "=([^;]*)"));
  return match ? decodeURIComponent(match[1]) : undefined;
}

function setCookieFallback(name, value, days) {
  if (typeof document === "undefined") return;
  const expires = new Date();
  expires.setUTCDate(expires.getUTCDate() + days);
  document.cookie = name + "=" + encodeURIComponent(typeof value === "string" ? value : JSON.stringify(value)) + "; path=/; expires=" + expires.toUTCString();
}

function removeCookieFallback(name, path = "/") {
  if (typeof document === "undefined") return;
  document.cookie = name + "=; path=" + path + "; expires=Thu, 01 Jan 1970 00:00:00 GMT";
}

export const setCookie = (name, value, days) => {
  const expires = new Date();
  expires.setUTCDate(expires.getUTCDate() + days);
  if (cookies) return cookies.set(name, value, { path: "/", expires });
  setCookieFallback(name, value, days);
};

export const getCookie = (name) => {
  if (cookies) return cookies.get(name);
  const raw = getCookieFallback(name);
  if (raw === undefined) return undefined;
  try {
    return JSON.parse(raw);
  } catch (_) {
    return raw;
  }
};

export const removeCookie = (name, path = "/") => {
  if (cookies) return cookies.remove(name, { path });
  removeCookieFallback(name, path);
};