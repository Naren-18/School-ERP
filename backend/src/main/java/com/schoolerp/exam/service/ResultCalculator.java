package com.schoolerp.exam.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Pure result arithmetic, no database. Rules:
 * <ul>
 * <li>Subject % = marks ÷ max × 100 (2 decimals). Absent counts as 0 and fails the subject.</li>
 * <li>A subject passes when marks ≥ pass marks (no pass marks set: any entered marks pass).</li>
 * <li>Not-entered subjects are left out of totals and make the result incomplete
 * ({@code passed} is null), so a missing mark is never silently a zero.</li>
 * <li>Grade = the band with the highest minimum that the percentage reaches, so
 * gaps such as 90.5 between bands 81–90 and 91–100 fall into the lower band.</li>
 * <li>Final = Σ(weight × exam %) ÷ Σ(weight) over weighted exams that have results;
 * it passes when every one of those exams passed.</li>
 * </ul>
 */
public final class ResultCalculator {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private ResultCalculator() {
	}

	public record Band(BigDecimal minPercent, String grade, BigDecimal gradePoint, String remark) {
	}

	/** One subject paper of an exam. */
	public record Paper(Long examSubjectId, Long subjectId, String subjectName, BigDecimal maxMarks,
			BigDecimal passMarks) {
	}

	/** What was entered for a student in a paper; no Entry at all means "not entered yet". */
	public record Entry(BigDecimal marks, boolean absent) {
	}

	public record SubjectResult(Long subjectId, String subjectName, BigDecimal maxMarks, BigDecimal marks,
			boolean absent, boolean entered, BigDecimal percent, String grade, Boolean passed) {
	}

	public record ExamResult(List<SubjectResult> subjects, BigDecimal total, BigDecimal maxTotal, BigDecimal percent,
			String grade, boolean complete, Boolean passed) {
	}

	/** An exam's overall result with its weight, for the final result. */
	public record WeightedExam(BigDecimal weight, ExamResult result) {
	}

	public record FinalResult(BigDecimal percent, String grade, Boolean passed, Map<Long, BigDecimal> subjectPercent,
			Map<Long, String> subjectGrade) {
	}

	/** The band with the highest minimum not above the percentage; null when there is no percentage. */
	public static Band band(List<Band> bands, BigDecimal percent) {
		if (percent == null) {
			return null;
		}
		return bands.stream()
			.filter(b -> b.minPercent().compareTo(percent) <= 0)
			.max(Comparator.comparing(Band::minPercent))
			.orElse(null);
	}

	public static String grade(List<Band> bands, BigDecimal percent) {
		Band b = band(bands, percent);
		return b == null ? null : b.grade();
	}

	public static ExamResult exam(List<Paper> papers, Map<Long, Entry> entries, List<Band> bands) {
		List<SubjectResult> subjects = new ArrayList<>();
		BigDecimal total = BigDecimal.ZERO;
		BigDecimal maxTotal = BigDecimal.ZERO;
		boolean complete = true;
		boolean allPassed = true;
		for (Paper p : papers) {
			Entry e = entries.get(p.examSubjectId());
			if (e == null) {
				complete = false;
				subjects.add(new SubjectResult(p.subjectId(), p.subjectName(), p.maxMarks(), null, false, false, null, null,
						null));
				continue;
			}
			BigDecimal marks = e.absent() ? BigDecimal.ZERO : e.marks();
			BigDecimal pct = percent(marks, p.maxMarks());
			boolean passed = !e.absent() && (p.passMarks() == null || marks.compareTo(p.passMarks()) >= 0);
			allPassed &= passed;
			total = total.add(marks);
			maxTotal = maxTotal.add(p.maxMarks());
			subjects.add(new SubjectResult(p.subjectId(), p.subjectName(), p.maxMarks(), e.absent() ? null : marks,
					e.absent(), true, pct, grade(bands, pct), passed));
		}
		BigDecimal pct = maxTotal.signum() == 0 ? null : percent(total, maxTotal);
		return new ExamResult(subjects, total, maxTotal, pct, grade(bands, pct), complete,
				complete && !papers.isEmpty() ? allPassed : null);
	}

	public static FinalResult finalResult(List<WeightedExam> exams, List<Band> bands) {
		BigDecimal weighted = BigDecimal.ZERO;
		BigDecimal weights = BigDecimal.ZERO;
		Boolean passed = Boolean.TRUE;
		Map<Long, BigDecimal[]> bySubject = new LinkedHashMap<>();
		for (WeightedExam we : exams) {
			if (we.weight() == null || we.weight().signum() <= 0 || we.result().percent() == null) {
				continue;
			}
			weighted = weighted.add(we.weight().multiply(we.result().percent()));
			weights = weights.add(we.weight());
			if (we.result().passed() == null) {
				passed = null;
			}
			else if (passed != null && !we.result().passed()) {
				passed = Boolean.FALSE;
			}
			for (SubjectResult s : we.result().subjects()) {
				if (s.percent() != null) {
					BigDecimal[] acc = bySubject.computeIfAbsent(s.subjectId(), k -> new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
					acc[0] = acc[0].add(we.weight().multiply(s.percent()));
					acc[1] = acc[1].add(we.weight());
				}
			}
		}
		if (weights.signum() == 0) {
			return new FinalResult(null, null, null, Map.of(), Map.of());
		}
		BigDecimal pct = weighted.divide(weights, 2, RoundingMode.HALF_UP);
		Map<Long, BigDecimal> subjectPercent = new LinkedHashMap<>();
		Map<Long, String> subjectGrade = new LinkedHashMap<>();
		bySubject.forEach((id, acc) -> {
			BigDecimal p = acc[0].divide(acc[1], 2, RoundingMode.HALF_UP);
			subjectPercent.put(id, p);
			subjectGrade.put(id, grade(bands, p));
		});
		return new FinalResult(pct, grade(bands, pct), passed, subjectPercent, subjectGrade);
	}

	/** Standard competition ranking (1, 2, 2, 4) by percentage, highest first; no percentage = no rank. */
	public static <T> Map<T, Integer> rank(List<T> items, Function<T, BigDecimal> percent) {
		List<T> ranked = items.stream()
			.filter(i -> percent.apply(i) != null)
			.sorted(Comparator.comparing(percent, Comparator.reverseOrder()))
			.toList();
		Map<T, Integer> ranks = new LinkedHashMap<>();
		for (int i = 0; i < ranked.size(); i++) {
			boolean tie = i > 0 && percent.apply(ranked.get(i)).compareTo(percent.apply(ranked.get(i - 1))) == 0;
			ranks.put(ranked.get(i), tie ? ranks.get(ranked.get(i - 1)) : i + 1);
		}
		return ranks;
	}

	static BigDecimal percent(BigDecimal marks, BigDecimal max) {
		return marks.multiply(HUNDRED).divide(max, 2, RoundingMode.HALF_UP);
	}

}
