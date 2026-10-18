package com.schoolerp.exam.service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import com.schoolerp.exam.service.ResultCalculator.Band;

/**
 * Ready-made grading scales a school can add with one click (and then edit).
 * Each band starts at its minimum; the maximum is informational.
 */
public final class GradingPresets {

	public enum Preset {

		CBSE_9_POINT("CBSE 9-point"), TS_CCE("TS state CCE");

		private final String displayName;

		Preset(String displayName) {
			this.displayName = displayName;
		}

		public String displayName() {
			return displayName;
		}

	}

	/** min, max, grade, grade point, remark */
	private static final Object[][] CBSE = {
			{ "91", "100", "A1", "10", "Outstanding" }, { "81", "90.99", "A2", "9", "Excellent" },
			{ "71", "80.99", "B1", "8", "Very good" }, { "61", "70.99", "B2", "7", "Good" },
			{ "51", "60.99", "C1", "6", "Above average" }, { "41", "50.99", "C2", "5", "Average" },
			{ "33", "40.99", "D", "4", "Fair" }, { "21", "32.99", "E1", null, "Needs improvement" },
			{ "0", "20.99", "E2", null, "Needs improvement" } };

	private static final Object[][] TS = {
			{ "91", "100", "A1", "10", "Excellent" }, { "81", "90.99", "A2", "9", "Very good" },
			{ "71", "80.99", "B1", "8", "Good" }, { "61", "70.99", "B2", "7", "Above average" },
			{ "51", "60.99", "C1", "6", "Average" }, { "41", "50.99", "C2", "5", "Below average" },
			{ "35", "40.99", "D", "4", "Pass" }, { "0", "34.99", "E", null, "Needs improvement" } };

	private GradingPresets() {
	}

	public record PresetBand(BigDecimal min, BigDecimal max, String grade, BigDecimal gradePoint, String remark) {
	}

	public static List<PresetBand> presetBands(Preset preset) {
		Object[][] rows = preset == Preset.CBSE_9_POINT ? CBSE : TS;
		return Arrays.stream(rows)
			.map(r -> new PresetBand(new BigDecimal((String) r[0]), new BigDecimal((String) r[1]), (String) r[2],
					r[3] == null ? null : new BigDecimal((String) r[3]), (String) r[4]))
			.toList();
	}

	/** The preset as calculator bands (used by tests and as a reference). */
	public static List<Band> bands(Preset preset) {
		return presetBands(preset).stream().map(b -> new Band(b.min(), b.grade(), b.gradePoint(), b.remark())).toList();
	}

}
