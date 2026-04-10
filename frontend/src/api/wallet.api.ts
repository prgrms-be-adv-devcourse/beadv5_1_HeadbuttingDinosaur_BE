import { apiClient, ApiResponse,idempotencyConfig } from './client';
import type {
  WalletChargeStartRequest, WalletChargeStartResponse,
  WalletChargeConfirmRequest, WalletChargeConfirmResponse,
  WalletBalanceResponse,
  WalletTransactionListRequest, WalletTransactionListResponse,
  WalletWithdrawRequest, WalletWithdrawResponse,
} from './types';

export const startWalletCharge = (body: WalletChargeStartRequest) =>
  apiClient.post<WalletChargeStartResponse>('/wallet/charge', body, idempotencyConfig());

export const confirmWalletCharge = (body: WalletChargeConfirmRequest) =>
  apiClient.post<WalletChargeConfirmResponse>('/wallet/charge/confirm', body, idempotencyConfig());

export const getWalletBalance = () =>
  apiClient.get<ApiResponse<WalletBalanceResponse>>('/wallet');

export const getWalletTransactions = (params?: WalletTransactionListRequest) =>
  apiClient.get<ApiResponse<WalletTransactionListResponse>>('/wallet/transactions', { params });

export const withdrawWallet = (body: WalletWithdrawRequest) =>
  apiClient.post<ApiResponse<WalletWithdrawResponse>>('/wallet/withdraw', body, idempotencyConfig());
