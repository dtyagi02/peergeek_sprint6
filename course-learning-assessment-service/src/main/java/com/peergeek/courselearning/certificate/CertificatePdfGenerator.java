package com.peergeek.courselearning.certificate;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class CertificatePdfGenerator {

    private static final Logger log = LoggerFactory.getLogger(CertificatePdfGenerator.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM yyyy").withZone(ZoneOffset.UTC);

    private static final Color PRIMARY_BURGUNDY = new Color(163, 28, 27);
    private static final Color GOLD_ACCENT = new Color(201, 160, 80);
    private static final Color TEXT_DARK = new Color(26, 32, 44);
    private static final Color TEXT_MUTED = new Color(100, 116, 139);
    private static final Color BG_LIGHT = new Color(253, 253, 254);

    public byte[] generate(CertificateEntity certificate, String verificationUrl) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDRectangle landscapeA4 = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
            PDPage page = new PDPage(landscapeA4);
            document.addPage(page);

            float width = landscapeA4.getWidth();
            float height = landscapeA4.getHeight();

            FontHolder fonts = loadFonts(document);

            try (PDPageContentStream canvas = new PDPageContentStream(document, page)) {
                // Background
                canvas.setNonStrokingColor(BG_LIGHT);
                canvas.addRect(0, 0, width, height);
                canvas.fill();

                // Outer decorative frame
                canvas.setStrokingColor(PRIMARY_BURGUNDY);
                canvas.setLineWidth(3.5f);
                canvas.addRect(24, 24, width - 48, height - 48);
                canvas.stroke();

                // Inner gold accent frame
                canvas.setStrokingColor(GOLD_ACCENT);
                canvas.setLineWidth(1.0f);
                canvas.addRect(32, 32, width - 64, height - 64);
                canvas.stroke();

                // Header branding
                drawCenteredText(canvas, "PEERGEEK", fonts.bold(), 16, width / 2, height - 75, PRIMARY_BURGUNDY, width - 100);
                drawCenteredText(canvas, "CERTIFICATE OF COMPLETION", fonts.bold(), 26, width / 2, height - 120, TEXT_DARK, width - 100);

                // Subtitle
                drawCenteredText(canvas, "This is proudly presented to", fonts.regular(), 13, width / 2, height - 165, TEXT_MUTED, width - 120);

                // Student Name
                String studentName = certificate.getStudentName() != null ? certificate.getStudentName().trim() : "Student";
                float studentFontSize = calculateFittedFontSize(fonts.bold(), studentName, 30, 18, width - 160);
                drawCenteredText(canvas, studentName, fonts.bold(), studentFontSize, width / 2, height - 215, TEXT_DARK, width - 140);

                // Gold separator line under name
                float lineHalfWidth = 140;
                canvas.setStrokingColor(GOLD_ACCENT);
                canvas.setLineWidth(1.5f);
                canvas.moveTo(width / 2 - lineHalfWidth, height - 232);
                canvas.lineTo(width / 2 + lineHalfWidth, height - 232);
                canvas.stroke();

                // Completion phrase
                drawCenteredText(canvas, "for successfully completing the course", fonts.regular(), 13, width / 2, height - 265, TEXT_MUTED, width - 120);

                // Course Title
                String courseTitle = certificate.getCourseTitle() != null ? certificate.getCourseTitle().trim() : "Course";
                List<String> titleLines = wrapText(fonts.bold(), 20, courseTitle, width - 200);
                float currentY = height - 305;
                for (String line : titleLines) {
                    drawCenteredText(canvas, line, fonts.bold(), 20, width / 2, currentY, PRIMARY_BURGUNDY, width - 160);
                    currentY -= 26;
                }

                // Instructor Name
                String instructorText = "Instructor: " + (certificate.getInstructorName() != null ? certificate.getInstructorName() : "PeerGeek Faculty");
                drawCenteredText(canvas, instructorText, fonts.regular(), 12, width / 2, currentY - 10, TEXT_MUTED, width - 160);

                // Footer metadata (left side)
                float footerY = 90;
                drawLeftText(canvas, "Certificate ID: " + certificate.getCertificateNumber(), fonts.bold(), 10, 60, footerY, TEXT_DARK);
                drawLeftText(canvas, "Issued: " + DATE_FORMATTER.format(certificate.getIssuedAt()), fonts.regular(), 9.5f, 60, footerY - 16, TEXT_MUTED);
                drawLeftText(canvas, "Verify: " + verificationUrl, fonts.regular(), 8.5f, 60, footerY - 32, TEXT_MUTED);

                // QR Code (bottom-right side)
                try {
                    BufferedImage qrImage = generateQrCode(verificationUrl, 100, 100);
                    PDImageXObject pdImage = LosslessFactory.createFromImage(document, qrImage);
                    float qrSize = 85;
                    float qrX = width - 60 - qrSize;
                    float qrY = 48;
                    canvas.drawImage(pdImage, qrX, qrY, qrSize, qrSize);

                    drawCenteredText(canvas, "Scan to verify", fonts.regular(), 8, qrX + qrSize / 2, qrY + qrSize + 4, TEXT_MUTED, qrSize + 20);
                } catch (Exception e) {
                    log.warn("Failed to attach QR code to certificate PDF: {}", e.getMessage());
                }
            }

            document.save(output);
            return output.toByteArray();
        } catch (Exception ex) {
            log.error("Failed to generate certificate PDF for certNumber={}: {}", certificate.getCertificateNumber(), ex.getMessage(), ex);
            throw new IllegalStateException("Unable to generate certificate PDF", ex);
        }
    }

    private BufferedImage generateQrCode(String text, int width, int height) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 1);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, width, height, hints);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    private FontHolder loadFonts(PDDocument document) {
        try {
            InputStream regularStream = getClass().getResourceAsStream("/fonts/FreeSans.ttf");
            InputStream boldStream = getClass().getResourceAsStream("/fonts/FreeSans-Bold.ttf");
            if (regularStream != null && boldStream != null) {
                PDFont reg = PDType0Font.load(document, regularStream);
                PDFont bold = PDType0Font.load(document, boldStream);
                return new FontHolder(reg, bold);
            }
        } catch (Exception ex) {
            log.warn("Could not load TrueType fonts from resources, falling back to standard fonts: {}", ex.getMessage());
        }
        return new FontHolder(
                new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
        );
    }

    private float calculateFittedFontSize(PDFont font, String text, float maxFontSize, float minFontSize, float maxWidth) {
        try {
            float stringWidth = font.getStringWidth(text) / 1000f;
            float idealSize = (maxWidth / stringWidth);
            if (idealSize < maxFontSize) {
                return Math.max(minFontSize, idealSize);
            }
        } catch (Exception ignored) {}
        return maxFontSize;
    }

    private List<String> wrapText(PDFont font, float fontSize, String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }

        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
            try {
                float width = font.getStringWidth(candidate) / 1000f * fontSize;
                if (width <= maxWidth) {
                    currentLine = new StringBuilder(candidate);
                } else {
                    if (!currentLine.isEmpty()) {
                        lines.add(currentLine.toString());
                    }
                    currentLine = new StringBuilder(word);
                }
            } catch (Exception e) {
                currentLine.append(" ").append(word);
            }
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    private void drawLeftText(PDPageContentStream canvas, String text, PDFont font, float size, float x, float y, Color color) {
        try {
            canvas.beginText();
            canvas.setFont(font, size);
            canvas.setNonStrokingColor(color);
            canvas.newLineAtOffset(x, y);
            canvas.showText(sanitize(text, font));
            canvas.endText();
        } catch (Exception e) {
            log.warn("Error drawing text '{}': {}", text, e.getMessage());
        }
    }

    private void drawCenteredText(PDPageContentStream canvas, String text, PDFont font, float size, float centerX, float y, Color color, float maxWidth) {
        try {
            String safeText = sanitize(text, font);
            float stringWidth = font.getStringWidth(safeText) / 1000f * size;
            float startX = centerX - (stringWidth / 2f);
            canvas.beginText();
            canvas.setFont(font, size);
            canvas.setNonStrokingColor(color);
            canvas.newLineAtOffset(startX, y);
            canvas.showText(safeText);
            canvas.endText();
        } catch (Exception e) {
            log.warn("Error drawing centered text '{}': {}", text, e.getMessage());
        }
    }

    private String sanitize(String input, PDFont font) {
        if (input == null) return "";
        if (font instanceof PDType0Font) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < input.length(); ) {
                int codePoint = input.codePointAt(i);
                String charStr = new String(Character.toChars(codePoint));
                try {
                    font.encode(charStr);
                    sb.append(charStr);
                } catch (Exception e) {
                    sb.append(' ');
                }
                i += Character.charCount(codePoint);
            }
            return sb.toString();
        }
        return input.replaceAll("[^\\x20-\\x7E]", " ");
    }

    private record FontHolder(PDFont regular, PDFont bold) {}
}

