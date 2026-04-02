//package com.devticket.payment.mock;
//
//import com.devticket.payment.payment.application.service.PaymentService;
//import com.devticket.payment.payment.presentation.dto.PaymentConfirmRequest;
//import com.devticket.payment.payment.presentation.dto.PaymentConfirmResponse;
//import com.devticket.payment.payment.presentation.dto.PaymentFailRequest;
//import com.devticket.payment.payment.presentation.dto.PaymentFailResponse;
//import com.devticket.payment.payment.presentation.dto.PaymentReadyRequest;
//import com.devticket.payment.payment.presentation.dto.PaymentReadyResponse;
//import java.util.UUID;
//import lombok.RequiredArgsConstructor;
//import org.springframework.context.annotation.Profile;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//@RestController
//@RequestMapping("/test/payments")
//@RequiredArgsConstructor
//@Profile("local")
//public class PaymentTestController {
//
//    private final PaymentService paymentService;
//
//    private static final UUID TEST_USER_ID =
//        UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
//
//    @PostMapping("/ready")
//    public ResponseEntity<PaymentReadyResponse> readyPayment(@RequestBody PaymentReadyRequest request) {
//        PaymentReadyResponse response = paymentService.readyPayment(TEST_USER_ID, request);
//        return ResponseEntity.status(HttpStatus.CREATED).body(response);
//    }
//
//    @PostMapping("/confirm")
//    public PaymentConfirmResponse confirmPayment(@RequestBody PaymentConfirmRequest request) {
//        return paymentService.confirmPgPayment(TEST_USER_ID, request);
//    }
//
//    @PostMapping("/fail")
//    public PaymentFailResponse failPayment(@RequestBody PaymentFailRequest request) {
//        return paymentService.failPgPayment(TEST_USER_ID, request);
//    }
//
//}
