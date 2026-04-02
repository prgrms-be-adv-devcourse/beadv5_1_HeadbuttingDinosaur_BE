import { apiClient, ApiResponse } from './client';
import type {
  WalletRefundRequest, WalletRefundResponse,
  PgRefundRequest, PgRefundResponse,
  RefundListResponse,
  RefundDetailResponse,
} from './types';

export const refundByWallet = (body: WalletRefundRequest) =>
  apiClient.post<ApiResponse<WalletRefundResponse>>('/refunds/wallet', body);

export const refundByPg = (body: PgRefundRequest) =>
  apiClient.post<ApiResponse<PgRefundResponse>>('/refunds/pg', body);

export const getRefunds = () =>
  apiClient.get<ApiResponse<RefundListResponse>>('/refunds');

export const getRefundDetail = (refundId: string) =>
  apiClient.get<ApiResponse<RefundDetailResponse>>(`/refunds/${refundId}`);
