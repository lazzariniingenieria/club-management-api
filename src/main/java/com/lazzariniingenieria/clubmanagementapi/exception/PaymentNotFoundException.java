package com.lazzariniingenieria.clubmanagementapi.exception;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(Long paymentId) {
        super("Payment " + paymentId + " not found");
    }
}
