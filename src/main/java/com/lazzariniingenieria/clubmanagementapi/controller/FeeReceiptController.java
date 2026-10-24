package com.lazzariniingenieria.clubmanagementapi.controller;

import com.lazzariniingenieria.clubmanagementapi.dto.FeeReceiptReportRequest;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.service.FeeReceiptService;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/receipts")
@RequiredArgsConstructor
public class FeeReceiptController {

    private final FeeReceiptService feeReceiptService;

    @GetMapping
    public ResponseEntity<byte[]> generate(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                           @Valid @ModelAttribute FeeReceiptReportRequest request) {
        Optional<byte[]> pdf = feeReceiptService.generateReceiptsPdf(currentUser.clubId(), request.paidFrom(), request.paidTo());

        if (pdf.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(attachmentFor(request));

        return new ResponseEntity<>(pdf.get(), headers, HttpStatus.OK);
    }

    private ContentDisposition attachmentFor(FeeReceiptReportRequest request) {
        String fileName = "cuotas-cobradas-" + request.paidFrom() + "-al-" + request.paidTo() + ".pdf";

        return ContentDisposition
                .attachment()
                .filename(fileName)
                .build();
    }
}
