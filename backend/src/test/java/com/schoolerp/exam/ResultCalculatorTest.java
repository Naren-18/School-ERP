package com.schoolerp.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.schoolerp.exam.service.GradingPresets;
import com.schoolerp.exam.service.ResultCalculator;
import com.schoolerp.exam.service.ResultCalculator.Band;
import com.schoolerp.exam.service.ResultCalculator.Entry;
import com.schoolerp.exam.service.ResultCalculator.ExamResult;
import com.schoolerp.exam.service.ResultCalculator.Paper;
import com.schoolerp.exam.service.ResultCalculator.WeightedExam;

class ResultCalculatorTest {

	private static final List<Band> CBSE = GradingPresets.bands(GradingPresets.Preset.CBSE_9_POINT);

	private static BigDecimal d(String v) {
		return new BigDecimal(v);
	}

	private static final Paper MATHS = new Paper(1L, 10L, "Maths", d("50"), d("17.5"));

	private static final Paper SCIENCE = new Paper(2L, 11L, "Science", d("50"), d("17.5"));

	@Test
	void gradeBoundariesAndGaps() {
		assertThat(ResultCalculator.grade(CBSE, d("100"))).isEqualTo("A1");
		assertThat(ResultCalculator.grade(CBSE, d("91.00"))).isEqualTo("A1");
		assertThat(ResultCalculator.grade(CBSE, d("90.99"))).isEqualTo("A2");
		// 90.5 sits between the printed bands 81–90 and 91–100: it is A2, never "no grade".
		assertThat(ResultCalculator.grade(CBSE, d("90.50"))).isEqualTo("A2");
		assertThat(ResultCalculator.grade(CBSE, d("33"))).isEqualTo("D");
		assertThat(ResultCalculator.grade(CBSE, d("32.99"))).isEqualTo("E1");
		assertThat(ResultCalculator.grade(CBSE, d("0"))).isEqualTo("E2");
		assertThat(ResultCalculator.grade(CBSE, null)).isNull();
		assertThat(ResultCalculator.grade(GradingPresets.bands(GradingPresets.Preset.TS_CCE), d("34.99"))).isEqualTo("E");
	}

	@Test
	void examTotalsPassAndAbsent() {
		ExamResult r = ResultCalculator.exam(List.of(MATHS, SCIENCE),
				Map.of(1L, new Entry(d("45.5"), false), 2L, new Entry(d("17.5"), false)), CBSE);
		assertThat(r.total()).isEqualByComparingTo("63");
		assertThat(r.percent()).isEqualByComparingTo("63.00");
		assertThat(r.grade()).isEqualTo("B2");
		assertThat(r.passed()).isTrue();
		assertThat(r.subjects().get(0).percent()).isEqualByComparingTo("91.00");
		assertThat(r.subjects().get(0).grade()).isEqualTo("A1");

		ExamResult absent = ResultCalculator.exam(List.of(MATHS, SCIENCE),
				Map.of(1L, new Entry(d("50"), false), 2L, new Entry(null, true)), CBSE);
		assertThat(absent.percent()).isEqualByComparingTo("50.00");
		assertThat(absent.subjects().get(1).absent()).isTrue();
		assertThat(absent.subjects().get(1).passed()).isFalse();
		assertThat(absent.passed()).isFalse();

		ExamResult belowPass = ResultCalculator.exam(List.of(MATHS),
				Map.of(1L, new Entry(d("17.49"), false)), CBSE);
		assertThat(belowPass.passed()).isFalse();
	}

	@Test
	void missingMarksMakeTheResultIncompleteNotZero() {
		ExamResult r = ResultCalculator.exam(List.of(MATHS, SCIENCE), Map.of(1L, new Entry(d("40"), false)), CBSE);
		assertThat(r.complete()).isFalse();
		assertThat(r.passed()).isNull();
		assertThat(r.percent()).isEqualByComparingTo("80.00");
		assertThat(r.subjects().get(1).entered()).isFalse();
	}

	@Test
	void weightedFinalResult() {
		ExamResult fa = ResultCalculator.exam(List.of(MATHS), Map.of(1L, new Entry(d("40"), false)), CBSE);
		ExamResult sa = ResultCalculator.exam(List.of(MATHS), Map.of(1L, new Entry(d("30"), false)), CBSE);
		ExamResult unweighted = ResultCalculator.exam(List.of(MATHS), Map.of(1L, new Entry(d("0"), false)), CBSE);
		var fin = ResultCalculator.finalResult(List.of(new WeightedExam(d("20"), fa), new WeightedExam(d("80"), sa),
				new WeightedExam(null, unweighted)), CBSE);
		// 0.2 × 80 + 0.8 × 60 = 64
		assertThat(fin.percent()).isEqualByComparingTo("64.00");
		assertThat(fin.grade()).isEqualTo("B2");
		assertThat(fin.passed()).isTrue();
		assertThat(fin.subjectPercent().get(10L)).isEqualByComparingTo("64.00");

		ExamResult failed = ResultCalculator.exam(List.of(MATHS), Map.of(1L, new Entry(null, true)), CBSE);
		assertThat(ResultCalculator.finalResult(List.of(new WeightedExam(d("20"), fa), new WeightedExam(d("80"), failed)),
				CBSE).passed()).isFalse();
		assertThat(ResultCalculator.finalResult(List.of(new WeightedExam(null, fa)), CBSE).percent()).isNull();
	}

	@Test
	void competitionRanking() {
		record S(String name, BigDecimal pct) {
		}
		S a = new S("a", d("90")), b = new S("b", d("80")), c = new S("c", d("80")), e = new S("e", d("70")),
				none = new S("x", null);
		Map<S, Integer> ranks = ResultCalculator.rank(List.of(e, b, a, none, c), S::pct);
		assertThat(ranks).containsEntry(a, 1).containsEntry(b, 2).containsEntry(c, 2).containsEntry(e, 4)
			.doesNotContainKey(none);
	}

}
