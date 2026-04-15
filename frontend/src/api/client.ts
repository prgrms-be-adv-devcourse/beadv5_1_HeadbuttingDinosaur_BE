import axios, { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse, AxiosError } from 'axios';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

/**
 * 백엔드가 프로필 미완성 사용자에게 반환하는 403 에러 코드.
 * 백엔드 ErrorCode enum 값과 일치해야 함.
 */
const PROFILE_INCOMPLETE_CODE = 'PROFILE_NOT_COMPLETED';

/** 403 응답이 "프로필 미완성" 에러인지 판별 */
function isProfileIncomplete(error: AxiosError): boolean {
  const data = error.response?.data as { code?: string } | undefined;
  return data?.code === PROFILE_INCOMPLETE_CODE;
}

export const apiClient: AxiosInstance = axios.create({
  baseURL: "/api",
  timeout: 10_000,
  headers: { 'Content-Type': 'application/json' },
  paramsSerializer: (params) => {
    const searchParams = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
      if (value === undefined || value === null) continue;
      if (Array.isArray(value)) {
        value.forEach((v) => searchParams.append(key, String(v)));
      } else {
        searchParams.append(key, String(value));
      }
    }
    return searchParams.toString();
  },
});

// ── Request: access token 주입 ──────────────────────────────────────────────
apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('accessToken');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// ── Response: 401 → 토큰 재발급 후 원본 요청 재시도 ──────────────────────────
let isRefreshing = false;
let failedQueue: Array<{ resolve: (v: string) => void; reject: (e: unknown) => void }> = [];

const processQueue = (error: unknown, token: string | null) => {
  failedQueue.forEach(({ resolve, reject }) => (error ? reject(error) : resolve(token!)));
  failedQueue = [];
};

apiClient.interceptors.response.use(
  (response: AxiosResponse) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

    // 403 + PROFILE_NOT_COMPLETED: 프로필 미완성 → 프로필 설정 페이지로 리다이렉트
    // 다른 이유의 403(권한 부족, 비즈니스 규칙 등)은 여기서 처리하지 않음
    if (error.response?.status === 403 && isProfileIncomplete(error)) {
      const currentPath = window.location.pathname;
      if (currentPath !== '/social/profile-setup' && currentPath !== '/oauth/callback') {
        window.location.href = '/social/profile-setup';
      }
      return Promise.reject(error);
    }

    if (error.response?.status !== 401 || originalRequest._retry) {
      return Promise.reject(error);
    }

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        failedQueue.push({ resolve, reject });
      }).then((token) => {
        originalRequest.headers.Authorization = `Bearer ${token}`;
        return apiClient(originalRequest);
      });
    }

    originalRequest._retry = true;
    isRefreshing = true;

    try {
      const refreshToken = localStorage.getItem('refreshToken');
      const { data } = await axios.post(`${BASE_URL}/auth/reissue`, { refreshToken });
      const newAccessToken: string = data.data.accessToken;

      localStorage.setItem('accessToken', newAccessToken);
      apiClient.defaults.headers.common.Authorization = `Bearer ${newAccessToken}`;
      processQueue(null, newAccessToken);
      originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;

      return apiClient(originalRequest);
    } catch (refreshError) {
      processQueue(refreshError, null);
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      window.location.href = '/login';
      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  },
);

/** 공통 응답 래퍼 – 서버가 { code, message, data } 구조를 반환한다고 가정 */
export interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
}

/** 멱등성 키 헤더를 포함한 axios config를 반환합니다. */
export function idempotencyConfig() {
  return {
    headers: { 'Idempotency-Key': crypto.randomUUID() },
  };
}

/** 페이지네이션 공통 타입 */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
