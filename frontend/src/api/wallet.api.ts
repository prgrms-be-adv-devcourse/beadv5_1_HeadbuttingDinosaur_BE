import { apiClient, ApiResponse } from './client';
import type {
  WalletChargeStartRequest, WalletChargeStartResponse,
  WalletChargeConfirmRequest, WalletChargeConfirmResponse,
  WalletBalanceResponse,
  WalletTransactionListRequest, WalletTransactionListResponse,
  WalletWithdrawRequest, WalletWithdrawResponse,
} from './types';

export const startWalletCharge = (body: WalletChargeStartRequest) =>
  apiClient.post<ApiResponse<WalletChargeStartResponse>>('/wallet/charge', body);

export const confirmWalletCharge = (body: WalletChargeConfirmRequest) =>
  apiClient.post<ApiResponse<WalletChargeConfirmResponse>>('/wallet/charge/confirm', body);

export const getWalletBalance = () =>
  apiClient.get<ApiResponse<WalletBalanceResponse>>('/wallet');

export const getWalletTransactions = (params?: WalletTransactionListRequest) =>
  apiClient.get<ApiResponse<WalletTransactionListResponse>>('/wallet/transactions', { params });

export const withdrawWallet = (body: WalletWithdrawRequest) =>
  apiClient.post<ApiResponse<WalletWithdrawResponse>>('/wallet/withdraw', body);
