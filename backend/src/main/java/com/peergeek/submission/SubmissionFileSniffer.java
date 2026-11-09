package com.peergeek.submission;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class SubmissionFileSniffer {

	private SubmissionFileSniffer() {
	}

	public static boolean isValidContent(String filename, String mimeType, byte[] sample) {
		if (sample == null || sample.length == 0) {
			return false;
		}

		String extension = getExtension(filename).toLowerCase(Locale.ROOT);

		return switch (extension) {
			case "pdf" -> startsWith(sample, new byte[]{'%', 'P', 'D', 'F', '-'});
			case "png" -> startsWith(sample, new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
			case "jpeg", "jpg" -> startsWith(sample, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
			case "zip", "docx", "pptx", "xlsx" -> startsWith(sample, new byte[]{'P', 'K', 0x03, 0x04});
			case "txt", "csv" -> containsNoNullBytes(sample) && isValidUtf8(sample);
			default -> false;
		};
	}

	public static String getExtension(String filename) {
		if (filename == null) {
			return "";
		}
		int lastDot = filename.lastIndexOf('.');
		return (lastDot >= 0 && lastDot < filename.length() - 1) ? filename.substring(lastDot + 1) : "";
	}

	private static boolean startsWith(byte[] data, byte[] prefix) {
		if (data.length < prefix.length) {
			return false;
		}
		for (int i = 0; i < prefix.length; i++) {
			if (data[i] != prefix[i]) {
				return false;
			}
		}
		return true;
	}

	private static boolean containsNoNullBytes(byte[] data) {
		for (byte b : data) {
			if (b == 0) {
				return false;
			}
		}
		return true;
	}

	private static boolean isValidUtf8(byte[] data) {
		try {
			var decoder = StandardCharsets.UTF_8.newDecoder();
			decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT);
			decoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
			decoder.decode(java.nio.ByteBuffer.wrap(data));
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}
