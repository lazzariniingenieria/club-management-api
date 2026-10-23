package com.lazzariniingenieria.clubmanagementapi.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class FeeReceiptPdfRendererTest {

    private static final String CLUB_NAME = "Club Atlético Jorge Newbery";
    private static final LocalDate JULY = LocalDate.parse("2026-07-01");
    private static final BigDecimal FEE = new BigDecimal("2000.00");

    private final FeeReceiptPdfRenderer renderer = new FeeReceiptPdfRenderer();

    @Test
    void shouldPrintEveryReceiptTwiceSideBySide() throws IOException {
        FeeReceipt receipt = receipt(JULY, line("Lazzarini Bautista", FEE));

        String text = extractText(renderer.render(CLUB_NAME, List.of(receipt)));

        assertThat(countOccurrences(text, "Lazzarini Bautista")).isEqualTo(2);
    }

    @Test
    void shouldPrintTheClubNameInUppercaseAsTheHeader() throws IOException {
        FeeReceipt receipt = receipt(JULY, line("Lazzarini Bautista", FEE));

        String text = extractText(renderer.render(CLUB_NAME, List.of(receipt)));

        assertThat(text).contains("CLUB ATLÉTICO JORGE NEWBERY");
    }

    @Test
    void shouldPrintThePeriodCoveredAsUppercaseSpanishMonthAndYear() throws IOException {
        FeeReceipt receipt = receipt(LocalDate.parse("2026-08-01"), line("Lazzarini Bautista", FEE));

        String text = extractText(renderer.render(CLUB_NAME, List.of(receipt)));

        assertThat(text).contains("AGOSTO 2026");
    }

    @Test
    void shouldPrintEachMemberAmountAndTheTotalWithArgentineThousandsSeparator() throws IOException {
        FeeReceipt receipt = receipt(JULY, line("Lazzarini Andres", FEE), line("Lazzarini Bautista", new BigDecimal("1500.00")));

        String text = extractText(renderer.render(CLUB_NAME, List.of(receipt)));

        assertThat(text).contains("2.000", "1.500", "3.500");
    }

    @Test
    void shouldPrintCentsAfterADecimalCommaWhenTheAmountHasThem() throws IOException {
        FeeReceipt receipt = receipt(JULY, line("Lazzarini Bautista", new BigDecimal("1500.50")));

        String text = extractText(renderer.render(CLUB_NAME, List.of(receipt)));

        assertThat(text).contains("1.500,50");
    }

    @Test
    void shouldOmitCentsWhenTheAmountIsWhole() throws IOException {
        FeeReceipt receipt = receipt(JULY, line("Lazzarini Bautista", FEE));

        String text = extractText(renderer.render(CLUB_NAME, List.of(receipt)));

        assertThat(text).doesNotContain("2.000,00");
    }

    @Test
    void shouldStartANewPageInsteadOfSplittingAReceiptAcrossPages() throws IOException {
        List<FeeReceipt> receipts = new ArrayList<>();

        for (int receiptNumber = 1; receiptNumber <= 6; receiptNumber++) {
            receipts.add(fiveMemberReceipt("Familia" + receiptNumber));
        }

        byte[] pdf = renderer.render(CLUB_NAME, receipts);

        assertThat(pageCount(pdf)).isEqualTo(2);
        assertThat(extractPageText(pdf, 1)).contains("Familia5").doesNotContain("Familia6");
        assertThat(extractPageText(pdf, 2)).contains("Familia6").doesNotContain("Familia5");
    }

    @Test
    void shouldReplaceCharactersTheFontCannotPrintInsteadOfFailing() throws IOException {
        FeeReceipt receipt = receipt(JULY, line("Gomez Ana 😀", FEE));

        String text = extractText(renderer.render(CLUB_NAME, List.of(receipt)));

        assertThat(text).contains("Gomez Ana ?");
    }

    @Test
    void shouldPrintAccentsOfDecomposedNamesInsteadOfReplacingThem() throws IOException {
        String decomposedName = Normalizer.normalize("Gómez Ana", Normalizer.Form.NFD);
        FeeReceipt receipt = receipt(JULY, line(decomposedName, FEE));

        String text = extractText(renderer.render(CLUB_NAME, List.of(receipt)));

        assertThat(text).contains("Gómez Ana").doesNotContain("?");
    }

    @Test
    void shouldShrinkALongNameToFitItsCellWithoutCuttingIt() throws IOException {
        String longName = "Maria del Carmen de Berga Rodriguez Paz";
        FeeReceipt receipt = receipt(JULY, line(longName, FEE));

        String text = extractText(renderer.render(CLUB_NAME, List.of(receipt)));

        assertThat(text).contains(longName);
    }

    @Test
    void shouldTruncateWithAnEllipsisANameTooLongToFitEvenAtTheMinimumFontSize() throws IOException {
        String tooLongName = "Prieto Nacitichi " + "Emanuel ".repeat(20);
        FeeReceipt receipt = receipt(JULY, line(tooLongName, FEE));

        String text = extractText(renderer.render(CLUB_NAME, List.of(receipt)));

        assertThat(text).contains("Prieto Nacitichi Emanuel", "…").doesNotContain(tooLongName.strip());
    }

    private FeeReceipt fiveMemberReceipt(String lastName) {
        List<FeeReceiptLine> lines = new ArrayList<>();

        for (int memberNumber = 1; memberNumber <= 5; memberNumber++) {
            lines.add(line(lastName + " Socio" + memberNumber, FEE));
        }

        return new FeeReceipt(JULY, lines, new BigDecimal("10000.00"));
    }

    private FeeReceipt receipt(LocalDate periodCovered, FeeReceiptLine... lines) {
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (FeeReceiptLine line : lines) {
            totalAmount = totalAmount.add(line.amount());
        }

        return new FeeReceipt(periodCovered, List.of(lines), totalAmount);
    }

    private FeeReceiptLine line(String memberName, BigDecimal amount) {
        return new FeeReceiptLine(memberName, amount);
    }

    private int countOccurrences(String text, String expected) {
        Matcher matcher = Pattern.compile(Pattern.quote(expected)).matcher(text);
        int occurrences = 0;

        while (matcher.find()) {
            occurrences++;
        }

        return occurrences;
    }

    private int pageCount(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    private String extractText(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();

            return stripper.getText(document);
        }
    }

    private String extractPageText(byte[] pdf, int pageNumber) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(pageNumber);
            stripper.setEndPage(pageNumber);

            return stripper.getText(document);
        }
    }
}
