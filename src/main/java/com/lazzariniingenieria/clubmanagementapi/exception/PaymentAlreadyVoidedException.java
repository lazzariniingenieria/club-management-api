package com.lazzariniingenieria.clubmanagementapi.exception;

public class PaymentAlreadyVoidedException extends RuntimeException {

    public PaymentAlreadyVoidedException(Long paymentId) {
        super("Payment " + paymentId + " is already voided");
    }
}
