import { apiClient, ApiResponse } from './client';
import type {
  CartItemRequest, 
  CartResponse,
  CartItemQuantityRequest, CartItemQuantityResponse,
  CartItemDeleteResponse,
  CartClearResponse,
  AddCartItemResponse
} from './types';

export const addCartItem = (body: CartItemRequest) =>
  apiClient.post<AddCartItemResponse>('/cart/items', body);

export const getCart = () =>
  apiClient.get<CartResponse>('/cart');

export const updateCartItemQuantity = (cartItemId: string, body: CartItemQuantityRequest) =>
  apiClient.patch<CartItemQuantityResponse>(`/cart/items/${cartItemId}`, body);

export const deleteCartItem = (cartItemId: string) =>
  apiClient.delete<CartItemDeleteResponse>(`/cart/items/${cartItemId}`);

export const clearCart = () =>
  apiClient.delete<CartClearResponse>('/cart');
