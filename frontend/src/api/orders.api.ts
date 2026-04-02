import { apiClient, ApiResponse } from './client';
import type {
  OrderRequest, OrderResponse,
  OrderListRequest, OrderListResponse,
  OrderDetailResponse,
  OrderCancelResponse,
} from './types';

export const createOrder = (body: OrderRequest) =>
  apiClient.post<ApiResponse<OrderResponse>>('/orders', body);

export const getOrders = (params?: OrderListRequest) =>
  apiClient.get<ApiResponse<OrderListResponse>>('/orders', { params });

export const getOrderDetail = (orderId: string) =>
  apiClient.get<ApiResponse<OrderDetailResponse>>(`/orders/${orderId}`);

export const cancelOrder = (orderId: string) =>
  apiClient.patch<ApiResponse<OrderCancelResponse>>(`/orders/${orderId}/cancel`);
