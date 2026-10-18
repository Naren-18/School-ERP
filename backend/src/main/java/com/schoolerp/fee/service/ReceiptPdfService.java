package com.schoolerp.fee.service;

import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.common.pdf.HtmlPdf;
import com.schoolerp.fee.dto.FeeDto;
import com.schoolerp.school.entity.School;
import com.schoolerp.school.service.SchoolService;

import lombok.RequiredArgsConstructor;

/** Receipt PDF: school header with logo, student and payment details, lines, amount in words. */
@Service
@RequiredArgsConstructor
public class ReceiptPdfService {

	private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	private final FeeCollectionService collection;

	private final SchoolService schoolService;

	@Transactional(readOnly = true)
	public byte[] receiptPdf(Long receiptId) {
		FeeDto.Receipt r = collection.receipt(receiptId);
		School school = schoolService.requireCurrent();
		String logo = schoolService.currentLogo().map(l -> HtmlPdf.img(l.content(), l.contentType(), "logo")).orElse("");
		return HtmlPdf.render(html(r, school, logo));
	}

	private static String html(FeeDto.Receipt r, School school, String logo) {
		StringBuilder rows = new StringBuilder();
		int n = 1;
		for (FeeDto.ReceiptLine line : r.lines()) {
			rows.append("<tr><td>").append(n++).append("</td><td>").append(HtmlPdf.esc(line.feeHeadName())).append(" – ")
				.append(HtmlPdf.esc(line.installmentName())).append("</td><td class=\"num\">").append(HtmlPdf.inr(line.amount()))
				.append("</td></tr>");
		}
		String contact = contactLine(school);
		String reference = r.referenceNo() == null ? "" : " · Ref. " + HtmlPdf.esc(r.referenceNo());
		String cancelled = !r.cancelled() ? ""
				: "<div class=\"cancelled\">CANCELLED</div><p class=\"cancel-note\">Cancelled on "
						+ HtmlPdf.esc(r.cancelledAt().format(DMY)) + " by " + HtmlPdf.esc(r.cancelledByName()) + ": "
						+ HtmlPdf.esc(r.cancelReason()) + "</p>";
		return """
				<!DOCTYPE html>
				<html><head><meta charset="UTF-8"/><style>
				@page { size: A5 landscape; margin: 12mm; }
				body { font-family: 'DejaVu Sans'; font-size: 10pt; color: #111; }
				.head { border-bottom: 2px solid %s; padding-bottom: 6px; margin-bottom: 8px; }
				.logo { height: 44px; float: left; margin-right: 10px; }
				h1 { font-size: 15pt; margin: 0; }
				.muted { color: #555; font-size: 8.5pt; }
				.title { text-align: center; font-weight: 700; letter-spacing: 2px; margin: 6px 0; }
				table { width: 100%%; border-collapse: collapse; }
				.meta td { padding: 2px 0; }
				.lines th, .lines td { border: 1px solid #bbb; padding: 4px 6px; }
				.lines th { background: #f3f4f6; text-align: left; }
				.num { text-align: right; }
				.total td { font-weight: 700; }
				.words { margin-top: 6px; font-style: italic; }
				.sign { margin-top: 26px; text-align: right; }
				.cancelled { position: absolute; top: 40mm; left: 30mm; font-size: 48pt; color: #fca5a5;
				  transform: rotate(-20deg); font-weight: 700; }
				.cancel-note { color: #b91c1c; }
				</style></head><body>
				<div class="head">%s<h1>%s</h1><div class="muted">%s</div><div style="clear: both"></div></div>
				<div class="title">FEE RECEIPT</div>
				%s
				<table class="meta"><tr>
				  <td>Receipt no. <b>%s</b></td><td class="num">Date <b>%s</b></td></tr>
				  <tr><td>Student <b>%s</b> (Adm. %s)</td><td class="num">Class <b>%s</b> · %s</td></tr>
				</table>
				<table class="lines" style="margin-top: 6px">
				  <tr><th style="width: 8%%">#</th><th>Particulars</th><th class="num" style="width: 26%%">Amount</th></tr>
				  %s
				  <tr class="total"><td></td><td>Total</td><td class="num">%s</td></tr>
				</table>
				<div class="words">%s</div>
				<div>Paid by <b>%s</b>%s</div>
				<div class="sign">Received by %s<br/><span class="muted">Computer-generated receipt</span></div>
				</body></html>
				""".formatted(HtmlPdf.esc(school.getPrimaryColor()), logo, HtmlPdf.esc(school.getName()), contact, cancelled,
				HtmlPdf.esc(r.receiptNo()), r.paidOn().format(DMY), HtmlPdf.esc(r.studentName()), HtmlPdf.esc(r.admissionNo()),
				HtmlPdf.esc(r.classLabel()), HtmlPdf.esc(r.academicYearName()), rows, HtmlPdf.inr(r.totalAmount()), HtmlPdf.esc(r.amountInWords()),
				HtmlPdf.esc(r.paymentMode().label()), reference, HtmlPdf.esc(r.collectedByName()));
	}

	/** Address · phone · email, skipping blanks. */
	private static String contactLine(School school) {
		return String.join(" · ", Stream.of(school.getAddress(), school.getPhone(), school.getEmail())
			.filter(s -> s != null && !s.isBlank())
			.map(HtmlPdf::esc)
			.toList());
	}


}
