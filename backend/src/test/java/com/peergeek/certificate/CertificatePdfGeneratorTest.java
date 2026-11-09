package com.peergeek.certificate;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Iterator;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CertificatePdfGeneratorTest {

	private final CertificatePdfGenerator pdfGenerator = new CertificatePdfGenerator();

	@Test
	@DisplayName("Generates landscape A4 PDF with valid dimensions, text, and decodable QR code")
	void generatesLandscapeA4Pdf() throws Exception {
		CertificateEntity cert = new CertificateEntity(
				"PG-TESTCERT01",
				"verification-token-12345",
				1L,
				10L,
				100L,
				"Devansh Tyagi",
				"Advanced Java Spring Boot & Cloud Architecture Mastery Course",
				"Jane Doe",
				Instant.parse("2026-11-03T10:00:00Z")
		);

		String verificationUrl = "https://peergeek.com/verify/certificate/verification-token-12345";
		byte[] pdfBytes = pdfGenerator.generate(cert, verificationUrl);

		assertThat(pdfBytes).isNotEmpty();

		try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
			assertThat(doc.getNumberOfPages()).isEqualTo(1);
			PDPage page = doc.getPage(0);

			// Landscape A4 dimensions: width (~841.89) > height (~595.28)
			float width = page.getMediaBox().getWidth();
			float height = page.getMediaBox().getHeight();
			assertThat(width).isGreaterThan(height);
			assertThat(width).isBetween(840.0f, 843.0f);
			assertThat(height).isBetween(594.0f, 597.0f);
			assertThat(page.getRotation()).isEqualTo(0);

			// Text verification
			PDFTextStripper stripper = new PDFTextStripper();
			String text = stripper.getText(doc);
			assertThat(text).contains("PEERGEEK");
			assertThat(text).contains("CERTIFICATE OF COMPLETION");
			assertThat(text).contains("Devansh Tyagi");
			assertThat(text).contains("PG-TESTCERT01");

			// QR Code image decoding from PDF resources
			PDResources resources = page.getResources();
			BufferedImage qrImage = null;
			for (org.apache.pdfbox.cos.COSName xObjectName : resources.getXObjectNames()) {
				PDXObject xObject = resources.getXObject(xObjectName);
				if (xObject instanceof PDImageXObject imageXObject) {
					qrImage = imageXObject.getImage();
					break;
				}
			}

			assertThat(qrImage).isNotNull();
			BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(qrImage)));
			Result qrResult = new MultiFormatReader().decode(bitmap);
			assertThat(qrResult.getText()).isEqualTo(verificationUrl);
		}
	}

	@Test
	@DisplayName("Handles unicode and long text gracefully without throwing exceptions")
	void handlesUnicodeAndLongText() throws Exception {
		CertificateEntity cert = new CertificateEntity(
				"PG-UNICODE01",
				"verification-unicode-abc",
				2L,
				20L,
				200L,
				"Devansh Tyagi / आदित्य शर्मा / José Müller",
				"Extremely Long Course Title Covering Comprehensive Full-Stack Software Engineering, DevOps, Microservices Architecture, and Enterprise Security Systems in Deep Detail",
				"Dr. Instructor",
				Instant.now()
		);

		String verificationUrl = "https://peergeek.com/verify/certificate/verification-unicode-abc";
		byte[] pdfBytes = pdfGenerator.generate(cert, verificationUrl);
		assertThat(pdfBytes).isNotEmpty();

		try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
			assertThat(doc.getNumberOfPages()).isEqualTo(1);
			PDFTextStripper stripper = new PDFTextStripper();
			String text = stripper.getText(doc);
			assertThat(text).contains("PG-UNICODE01");
		}
	}
}

