import { apiClient, ApiResponse } from './client';
import type {
  AdminDashboardResponse,
  AdminEventSearchRequest, AdminEventListResponse,
  EventCancelResponse,
  UserSearchCondition, UserListResponse,
  AdminUserDetailResponse,
  UserStatusRequest, UserStatusResponse,
  UserRoleRequest, UserRoleResponse,
  SellerApplicationListResponse,
  SettlementResponse,
} from './types';

// ── 대시보드 ───────────────────────────────────────────────────────────────────
export const getAdminDashboard = () =>
  apiClient.get<ApiResponse<AdminDashboardResponse>>('/admin/dashboard');

// ── 이벤트 관리 ────────────────────────────────────────────────────────────────
export const getAdminEvents = (params?: AdminEventSearchRequest) =>
  apiClient.get<ApiResponse<AdminEventListResponse>>('/admin/events', { params });

export const forcecancelEvent = (eventId: string) =>
  apiClient.patch<ApiResponse<EventCancelResponse>>(`/admin/events/${eventId}/force-cancel`);

// ── 회원 관리 ──────────────────────────────────────────────────────────────────
export const getAdminUsers = (params?: UserSearchCondition) =>
  apiClient.get<ApiResponse<UserListResponse>>('/admin/users', { params });

export const getAdminUserDetail = (userId: string) =>
  apiClient.get<ApiResponse<AdminUserDetailResponse>>(`/admin/users/${userId}`);

export const updateUserStatus = (userId: string, body: UserStatusRequest) =>
  apiClient.patch<ApiResponse<UserStatusResponse>>(`/admin/users/${userId}/status`, body);

export const updateUserRole = (userId: string, body: UserRoleRequest) =>
  apiClient.patch<ApiResponse<UserRoleResponse>>(`/admin/users/${userId}/role`, body);

// ── 판매자 신청 ────────────────────────────────────────────────────────────────
export const getSellerApplications = () =>
    apiClient.get<SellerApplicationListResponse[]>('/admin/seller-applications');

export const processSellerApplication = (applicationId: string, decision: string) =>
    apiClient.patch(`/admin/seller-applications/${applicationId}`, { decision });

// ── 정산 ───────────────────────────────────────────────────────────────────────
export const runSettlementProcess = () =>
  apiClient.post('/admin/settlements/run');

export const getAdminSettlements = () =>
  apiClient.get<ApiResponse<SettlementResponse>>('/admin/settlements');

// ── 수수료 정책 ────────────────────────────────────────────────────────────────
export const getFeePolicies = () =>
  apiClient.get('/admin/fee-policies');

export const createFeePolicy = (body: unknown) =>
  apiClient.post('/admin/fee-policies', body);

export const updateFeePolicy = (policyId: string, body: unknown) =>
  apiClient.patch(`/admin/fee-policies/${policyId}`, body);
