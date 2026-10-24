package com.lazzariniingenieria.clubmanagementapi.report;

import com.lazzariniingenieria.clubmanagementapi.report.FeeReceiptCanvas.Box;
import com.lazzariniingenieria.clubmanagementapi.report.FeeReceiptCanvas.CellStyle;
import com.lazzariniingenieria.clubmanagementapi.report.FeeReceiptCanvas.Fonts;
import com.lazzariniingenieria.clubmanagementapi.report.FeeReceiptCanvas.Point;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

class FeeReceiptDocument implements Closeable {

    private static final Locale SPANISH_ARGENTINA = Locale.of("es", "AR");
    private static final PDRectangle PAGE_SIZE = PDRectangle.A4;
    private static final float PAGE_MARGIN = 28f;
    private static final float COPY_GAP = 20f;
    private static final float RECEIPT_GAP = 14f;
    private static final float ROW_HEIGHT = 16f;
    private static final int ROWS_BESIDES_MEMBERS = 3;
    private static final float COPY_WIDTH = (PAGE_SIZE.getWidth() - 2 * PAGE_MARGIN - COPY_GAP) / 2;
    private static final float LABEL_WIDTH = 56f;
    private static final float AMOUNT_WIDTH = 60f;
    private static final float NAME_WIDTH = COPY_WIDTH - LABEL_WIDTH - AMOUNT_WIDTH;
    private static final float LEFT_COPY_X = PAGE_MARGIN;
    private static final float RIGHT_COPY_X = PAGE_MARGIN + COPY_WIDTH + COPY_GAP;
    private static final float VERTICAL_CUT_LINE_X = PAGE_MARGIN + COPY_WIDTH + COPY_GAP / 2;
    private static final String MEMBERS_LABEL = "SOCIO/S";
    private static final String PERIOD_LABEL = "MES DE";
    private static final String TOTAL_LABEL = "TOTAL $";
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("LLLL yyyy", SPANISH_ARGENTINA);
    private static final DecimalFormatSymbols AMOUNT_SYMBOLS = DecimalFormatSymbols.getInstance(SPANISH_ARGENTINA);

    private final PDDocument pdfDocument = new PDDocument();
    private final Fonts fonts = Fonts.helvetica();
    private final String clubTitle;
    private FeeReceiptCanvas canvas;
    private float cursorY;

    FeeReceiptDocument(String clubName) {
        this.clubTitle = clubName.toUpperCase(SPANISH_ARGENTINA);
    }

    void addReceipt(FeeReceipt receipt) throws IOException {
        float receiptHeight = ROW_HEIGHT * (receipt.lines().size() + ROWS_BESIDES_MEMBERS);
        boolean fitsOnCurrentPage = canvas != null && cursorY - receiptHeight >= PAGE_MARGIN;

        if (fitsOnCurrentPage) {
            drawHorizontalCutLine(cursorY + RECEIPT_GAP / 2);
        } else {
            startNewPage();
        }

        drawCopy(receipt, LEFT_COPY_X);
        drawCopy(receipt, RIGHT_COPY_X);
        drawVerticalCutLine(receiptHeight);
        cursorY -= receiptHeight + RECEIPT_GAP;
    }

    byte[] toByteArray() throws IOException {
        closeCurrentPage();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        pdfDocument.save(output);

        return output.toByteArray();
    }

    @Override
    public void close() throws IOException {
        closeCurrentPage();
        pdfDocument.close();
    }

    private void startNewPage() throws IOException {
        closeCurrentPage();
        PDPage page = new PDPage(PAGE_SIZE);
        pdfDocument.addPage(page);
        PDPageContentStream content = new PDPageContentStream(pdfDocument, page);
        canvas = new FeeReceiptCanvas(content, fonts);
        cursorY = PAGE_SIZE.getHeight() - PAGE_MARGIN;
    }

    private void closeCurrentPage() throws IOException {
        if (canvas == null) {
            return;
        }

        canvas.close();
        canvas = null;
    }

    private void drawCopy(FeeReceipt receipt, float left) throws IOException {
        Box headerBox = new Box(left, cursorY - ROW_HEIGHT, COPY_WIDTH, ROW_HEIGHT);
        canvas.drawCell(headerBox, clubTitle, CellStyle.HEADER);
        float membersBottom = drawMemberRows(receipt, left);
        Box periodLabelBox = new Box(left, membersBottom - ROW_HEIGHT, LABEL_WIDTH, ROW_HEIGHT);
        canvas.drawCell(periodLabelBox, PERIOD_LABEL, CellStyle.BOLD_CENTERED);
        canvas.drawCell(valueBoxBeside(periodLabelBox), formatPeriod(receipt), CellStyle.BOLD_CENTERED);
        Box totalLabelBox = new Box(left, membersBottom - 2 * ROW_HEIGHT, LABEL_WIDTH, ROW_HEIGHT);
        canvas.drawCell(totalLabelBox, TOTAL_LABEL, CellStyle.BOLD_CENTERED);
        canvas.drawCell(valueBoxBeside(totalLabelBox), formatAmount(receipt.totalAmount()), CellStyle.BOLD_RIGHT);
    }

    private float drawMemberRows(FeeReceipt receipt, float left) throws IOException {
        float membersTop = cursorY - ROW_HEIGHT;
        float membersHeight = ROW_HEIGHT * receipt.lines().size();
        float membersBottom = membersTop - membersHeight;
        canvas.drawCell(new Box(left, membersBottom, LABEL_WIDTH, membersHeight), MEMBERS_LABEL, CellStyle.BOLD_CENTERED);
        float rowBottom = membersTop - ROW_HEIGHT;

        for (FeeReceiptLine line : receipt.lines()) {
            Box nameBox = new Box(left + LABEL_WIDTH, rowBottom, NAME_WIDTH, ROW_HEIGHT);
            Box amountBox = new Box(left + LABEL_WIDTH + NAME_WIDTH, rowBottom, AMOUNT_WIDTH, ROW_HEIGHT);
            canvas.drawCell(nameBox, line.memberName(), CellStyle.PLAIN_LEFT);
            canvas.drawCell(amountBox, formatAmount(line.amount()), CellStyle.PLAIN_RIGHT);
            rowBottom -= ROW_HEIGHT;
        }

        return membersBottom;
    }

    private Box valueBoxBeside(Box labelBox) {
        return new Box(labelBox.x() + LABEL_WIDTH, labelBox.y(), NAME_WIDTH + AMOUNT_WIDTH, ROW_HEIGHT);
    }

    private void drawHorizontalCutLine(float y) throws IOException {
        Point from = new Point(PAGE_MARGIN / 2, y);
        Point to = new Point(PAGE_SIZE.getWidth() - PAGE_MARGIN / 2, y);
        canvas.drawCutLine(from, to);
    }

    private void drawVerticalCutLine(float receiptHeight) throws IOException {
        Point from = new Point(VERTICAL_CUT_LINE_X, cursorY + RECEIPT_GAP / 2);
        Point to = new Point(VERTICAL_CUT_LINE_X, cursorY - receiptHeight - RECEIPT_GAP / 2);
        canvas.drawCutLine(from, to);
    }

    private String formatPeriod(FeeReceipt receipt) {
        String period = PERIOD_FORMAT.format(receipt.periodCovered());

        return period.toUpperCase(SPANISH_ARGENTINA);
    }

    private String formatAmount(BigDecimal amount) {
        BigDecimal significantAmount = amount.stripTrailingZeros();
        boolean hasCents = significantAmount.scale() > 0;
        DecimalFormat amountFormat = new DecimalFormat(hasCents ? "#,##0.00" : "#,##0", AMOUNT_SYMBOLS);

        return amountFormat.format(amount);
    }
}
