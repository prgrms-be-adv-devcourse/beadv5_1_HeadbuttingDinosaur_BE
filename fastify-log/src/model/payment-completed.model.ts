export interface PaymentCompletedOrderItem {
  eventId: string;
  quantity: number;
}

export interface PaymentCompletedEvent {
  orderId: string;
  userId: string;
  paymentId?: string;
  paymentMethod?: string;
  orderItems: PaymentCompletedOrderItem[];
  totalAmount: number;
  timestamp: string;
}
