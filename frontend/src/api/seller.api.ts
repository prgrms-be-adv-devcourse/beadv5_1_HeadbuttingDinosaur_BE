import { apiClient, ApiResponse } from './client';
import type {
  SettlementResponse,
  SellerSettlementDetailResponse,
} from './types';

export const getSellerSettlements = () =>
  apiClient.get<ApiResponse<SettlementResponse>>('/seller/settlements');

export const getSellerSettlementDetail = (settlementId: string) =>
  apiClient.get<ApiResponse<SellerSettlementDetailResponse>>(`/seller/settlements/${settlementId}`);
