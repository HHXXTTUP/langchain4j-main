export const API_BASE_URL = __API_BASE_URL__.replace(/\/$/, '');
export const CLOUD_ENV_ID = __CLOUD_ENV_ID__;
export const CLOUD_SERVICE_NAME = __CLOUD_SERVICE_NAME__;
export const USE_CLOUD_CONTAINER = __USE_CLOUD_CONTAINER__;

export function apiUrl(path: string): string {
  if (/^https?:\/\//.test(path)) {
    return path;
  }
  return `${API_BASE_URL}${path.startsWith('/') ? path : `/${path}`}`;
}
