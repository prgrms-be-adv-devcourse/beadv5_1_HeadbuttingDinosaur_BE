import { apiClient, ApiResponse } from './client';
import type {
  SignUpRequest, SignUpResponse,
  LoginRequest, LoginResponse,
  SocialSignUpOrLoginRequest, SocialSignUpOrLoginResponse,
  LogoutResponse,
  TokenRefreshRequest, TokenRefreshResponse,
  SignUpProfileRequest, SignUpProfileResponse,
  GetProfileResponse,
  UpdateProfileRequest, UpdateProfileResponse,
  ChangePasswordRequest, ChangePasswordResponse,
  WithdrawResponse,
  SellerApplicationRequest,
  SellerApplicationStatusResponse,
  TechStackListResponse,
} from './types';

// ── 인증 ─────────────────────────────────────────────────────────────────────
export const signup = (body: SignUpRequest) =>
  apiClient.post<ApiResponse<SignUpResponse>>('/auth/signup', body);

export const login = (body: LoginRequest) =>
  apiClient.post<ApiResponse<LoginResponse>>('/auth/login', body);

export const socialLoginGoogle = (body: SocialSignUpOrLoginRequest) =>
  apiClient.post<ApiResponse<SocialSignUpOrLoginResponse>>('/auth/social/google', body);

export const logout = () =>
  apiClient.post<ApiResponse<LogoutResponse>>('/auth/logout', null, {
    headers: {
      'Refresh-Token': localStorage.getItem('refreshToken') ?? '',
    },
  });

export const reissueToken = (body: TokenRefreshRequest) =>
  apiClient.post<ApiResponse<TokenRefreshResponse>>('/auth/reissue', body);

// ── 유저 / 프로필 ──────────────────────────────────────────────────────────────
export const withdrawUser = () =>
  apiClient.delete<ApiResponse<WithdrawResponse>>('/users/me');

export const createProfile = (body: SignUpProfileRequest) =>
  apiClient.post<ApiResponse<SignUpProfileResponse>>('/users/profile', body);

export const getProfile = () =>
  apiClient.get<ApiResponse<GetProfileResponse>>('/users/me');

export const updateProfile = (body: UpdateProfileRequest) =>
  apiClient.patch<ApiResponse<UpdateProfileResponse>>('/users/me', body);

export const changePassword = (body: ChangePasswordRequest) =>
  apiClient.patch<ApiResponse<ChangePasswordResponse>>('/users/me/password', body);

// ── 판매자 전환 신청 ────────────────────────────────────────────────────────────
export const applyForSeller = (body: SellerApplicationRequest) =>
    apiClient.post<ApiResponse<SellerApplicationStatusResponse>>('/seller-applications', body);

export const getSellerApplicationStatus = () =>
  apiClient.get<ApiResponse<SellerApplicationStatusResponse>>('/seller-applications/me');

// ── 공통 ──────────────────────────────────────────────────────────────────────
export const getTechStacks = () =>
  apiClient.get<ApiResponse<TechStackListResponse>>('/tech-stacks');
