package com.lazzariniingenieria.clubmanagementapi.report;

import java.io.Closeable;
import java.io.IOException;
import java.text.Normalizer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

class FeeReceiptCanvas implements Closeable {

    private static final float FONT_SIZE = 9f;
    private static final float MIN_FONT_SIZE = 6f;
    private static final float CAP_HEIGHT_RATIO = 0.718f;
    private static final float CELL_PADDING = 4f;
    private static final float BORDER_WIDTH = 0.6f;
    private static final float BLACK = 0f;
    private static final float HEADER_BACKGROUND_GRAY = 0.88f;
    private static final float CUT_LINE_GRAY = 0.55f;
    private static final float[] CUT_LINE_DASH = {3f, 3f};
    private static final float[] SOLID_LINE = {};
    private static final String ELLIPSIS = "…";
    private static final int REPLACEMENT_CHARACTER = '?';

    private final PDPageContentStream content;
    private final Fonts fonts;

    FeeReceiptCanvas(PDPageContentStream content, Fonts fonts) throws IOException {
        this.content = content;
        this.fonts = fonts;
        content.setLineWidth(BORDER_WIDTH);
    }

    void drawCell(Box box, String text, CellStyle style) throws IOException {
        if (style.isFilled()) {
            fillBackground(box);
        }

        content.addRect(box.x(), box.y(), box.width(), box.height());
        content.stroke();
        drawText(box, text, style);
    }

    void drawCutLine(Point from, Point to) throws IOException {
        content.setStrokingColor(CUT_LINE_GRAY);
        content.setLineDashPattern(CUT_LINE_DASH, 0);
        content.moveTo(from.x(), from.y());
        content.lineTo(to.x(), to.y());
        content.stroke();
        content.setLineDashPattern(SOLID_LINE, 0);
        content.setStrokingColor(BLACK);
    }

    @Override
    public void close() throws IOException {
        content.close();
    }

    private void fillBackground(Box box) throws IOException {
        content.setNonStrokingColor(HEADER_BACKGROUND_GRAY);
        content.addRect(box.x(), box.y(), box.width(), box.height());
        content.fill();
        content.setNonStrokingColor(BLACK);
    }

    private void drawText(Box box, String text, CellStyle style) throws IOException {
        PDFont font = style.isBold() ? fonts.bold() : fonts.regular();
        float availableWidth = box.width() - 2 * CELL_PADDING;
        FittedText fittedText = fitToWidth(toEncodableText(text, font), font, availableWidth);
        float textWidth = textWidth(fittedText.text(), font, fittedText.fontSize());
        float textX = alignedX(box, textWidth, style.getAlignment());
        float textY = box.y() + (box.height() - fittedText.fontSize() * CAP_HEIGHT_RATIO) / 2;

        content.beginText();
        content.setFont(font, fittedText.fontSize());
        content.newLineAtOffset(textX, textY);
        content.showText(fittedText.text());
        content.endText();
    }

    // Standard 14 fonts only cover WinAnsi. The text is first composed (NFC) so a decomposed accent (e + U+0301) becomes
    // the single WinAnsi character é. Anything still outside WinAnsi (an emoji pasted into a name, a tab) would make
    // showText throw and fail the whole report, so it is printed as '?' instead.
    private String toEncodableText(String text, PDFont font) throws IOException {
        StringBuilder encodableText = new StringBuilder();
        String composedText = Normalizer.normalize(text, Normalizer.Form.NFC);

        for (int codePoint : composedText.codePoints().toArray()) {
            int printableCodePoint = canEncode(font, codePoint) ? codePoint : REPLACEMENT_CHARACTER;
            encodableText.appendCodePoint(printableCodePoint);
        }

        return encodableText.toString();
    }

    private boolean canEncode(PDFont font, int codePoint) throws IOException {
        try {
            font.encode(Character.toString(codePoint));

            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private FittedText fitToWidth(String text, PDFont font, float maxWidth) throws IOException {
        float naturalWidth = textWidth(text, font, FONT_SIZE);
        float scaledFontSize = FONT_SIZE * maxWidth / naturalWidth;
        float fontSize = Math.clamp(scaledFontSize, MIN_FONT_SIZE, FONT_SIZE);
        boolean fitsAtMinimumFontSize = scaledFontSize >= MIN_FONT_SIZE;
        String fittedText = fitsAtMinimumFontSize ? text : truncateToWidth(text, font, maxWidth);

        return new FittedText(fittedText, fontSize);
    }

    private String truncateToWidth(String text, PDFont font, float maxWidth) throws IOException {
        String truncatedText = text;

        while (!truncatedText.isEmpty() && textWidth(truncatedText + ELLIPSIS, font, MIN_FONT_SIZE) > maxWidth) {
            truncatedText = truncatedText.substring(0, truncatedText.length() - 1);
        }

        return truncatedText.strip() + ELLIPSIS;
    }

    private float textWidth(String text, PDFont font, float fontSize) throws IOException {
        return font.getStringWidth(text) / 1000 * fontSize;
    }

    private float alignedX(Box box, float textWidth, Alignment alignment) {
        return switch (alignment) {
            case LEFT -> box.x() + CELL_PADDING;
            case CENTER -> box.x() + (box.width() - textWidth) / 2;
            case RIGHT -> box.x() + box.width() - CELL_PADDING - textWidth;
        };
    }

    record Fonts(PDFont regular, PDFont bold) {

        static Fonts helvetica() {
            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            return new Fonts(regular, bold);
        }
    }

    record Box(float x, float y, float width, float height) {
    }

    record Point(float x, float y) {
    }

    enum Alignment {
        LEFT, CENTER, RIGHT
    }

    @Getter
    @RequiredArgsConstructor
    enum CellStyle {
        HEADER(true, Alignment.CENTER, true),
        BOLD_CENTERED(true, Alignment.CENTER, false),
        PLAIN_LEFT(false, Alignment.LEFT, false),
        PLAIN_RIGHT(false, Alignment.RIGHT, false),
        BOLD_RIGHT(true, Alignment.RIGHT, false);

        private final boolean bold;
        private final Alignment alignment;
        private final boolean filled;
    }

    private record FittedText(String text, float fontSize) {
    }
}
