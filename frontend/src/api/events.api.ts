import { apiClient, ApiResponse } from "./client";
import type {
  EventListRequest,
  EventListResponse,
  EventDetailResponse,
  EventSearchRequest,
  EventSearchResponse,
  EventFilterRequest,
  EventFilterResponse,
  SellerEventCreateRequest,
  SellerEventCreateResponse,
  SellerEventListRequest,
  SellerEventListResponse,
  SellerEventDetailResponse,
  SellerEventUpdateRequest,
  SellerEventUpdateResponse,
  SellerEventStopResponse,
  SellerEventSummaryResponse,
  SellerEventParticipantListRequest,
  SellerEventParticipantListResponse,
  SellerEventRefundListRequest,
  SellerEventRefundListResponse,
} from "./types";

// ── 공개 이벤트 ────────────────────────────────────────────────────────────────
export const getEvents = (params?: EventListRequest) =>
  apiClient.get<ApiResponse<EventListResponse>>("/events", { params });

export const getEventDetail = (eventId: string) =>
  apiClient.get<ApiResponse<EventDetailResponse>>(`/events/${eventId}`);

export const searchEvents = (params: EventSearchRequest) =>
  apiClient.get<ApiResponse<EventSearchResponse>>("/events", { params });

export const filterEvents = (params: EventFilterRequest) =>
  apiClient.get<ApiResponse<EventFilterResponse>>("/events", { params });

// ── 판매자 이벤트 ──────────────────────────────────────────────────────────────
export const createSellerEvent = (body: SellerEventCreateRequest) =>
  apiClient.post<ApiResponse<SellerEventCreateResponse>>('/seller/events', body);

export const getSellerEvents = (params?: SellerEventListRequest) =>
  apiClient.get<ApiResponse<SellerEventListResponse>>('/seller/events', { params });

export const getSellerEventDetail = (eventId: string) =>
  apiClient.get<ApiResponse<SellerEventDetailResponse>>(
    `/seller/events/${eventId}`,
  );

export const updateSellerEvent = (
  eventId: string,
  body: SellerEventUpdateRequest,
) =>
  apiClient.patch<ApiResponse<SellerEventUpdateResponse>>(
    `/seller/events/${eventId}`,
    body,
  );

export const stopSellerEvent = (eventId: string) =>
  apiClient.patch<ApiResponse<SellerEventStopResponse>>(
    `/seller/events/${eventId}/cancel`,
  );

export const getSellerEventSummary = (eventId: string) =>
  apiClient.get<ApiResponse<SellerEventSummaryResponse>>(
    `/seller/events/${eventId}/statistics`,
  );

export const getSellerEventParticipants = (
  eventId: string,
  params?: SellerEventParticipantListRequest,
) =>
  apiClient.get<ApiResponse<SellerEventParticipantListResponse>>(
    `/seller/events/${eventId}/participants`,
    { params },
  );

export const getSellerEventRefunds = (
  eventId: string,
  params?: SellerEventRefundListRequest,
) =>
  apiClient.get<ApiResponse<SellerEventRefundListResponse>>(
    `/seller/events/${eventId}/refunds`,
    { params },
  );

