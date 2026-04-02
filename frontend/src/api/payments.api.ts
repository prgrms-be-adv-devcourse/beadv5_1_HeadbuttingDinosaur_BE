import { apiClient, ApiResponse } from './client';
import type {
  PaymentRequest, PaymentResponse,
  PaymentConfirmRequest, PaymentConfirmResponse,
} from './types';

export const readyPayment = (body: PaymentRequest) =>
  apiClient.post<ApiResponse<PaymentResponse>>('/payments/ready', body);

export const confirmPayment = (body: PaymentConfirmRequest) =>
  apiClient.post<ApiResponse<PaymentConfirmResponse>>('/payments/confirm', body);
