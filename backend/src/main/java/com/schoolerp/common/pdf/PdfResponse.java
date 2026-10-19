package com.schoolerp.common.pdf;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** A PDF shown in the browser (inline), with a file name for saving. */
public final class PdfResponse {

	private PdfResponse() {
	}

	public static ResponseEntity<byte[]> inline(String fileName, byte[] pdf) {
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(fileName).build().toString())
			.body(pdf);
	}

}
