package com.lazzariniingenieria.clubmanagementapi.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FeeReceiptPdfRenderer {

    public byte[] render(String clubName, List<FeeReceipt> receipts) {
        try (FeeReceiptDocument document = new FeeReceiptDocument(clubName)) {
            for (FeeReceipt receipt : receipts) {
                document.addReceipt(receipt);
            }

            return document.toByteArray();
        } catch (IOException exception) {
            String message = "Failed to render " + receipts.size() + " fee receipt(s) for club " + clubName;

            throw new UncheckedIOException(message, exception);
        }
    }
}
