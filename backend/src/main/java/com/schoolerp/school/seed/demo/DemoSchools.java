package com.schoolerp.school.seed.demo;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.MonthDay;
import java.util.List;

import com.schoolerp.academic.entity.Subject;
import com.schoolerp.exam.service.GradingPresets;
import com.schoolerp.school.entity.School;

/**
 * The three fictional demo schools. Dates are month-days placed in the current
 * academic year when seeding, so the data always looks current.
 */
final class DemoSchools {

	private DemoSchools() {
	}

	/** Nursery, LKG, UKG, Class 1 … Class 10: levels 0–12. */
	static final List<String> CLASSES = List.of("Nursery", "LKG", "UKG", "Class 1", "Class 2", "Class 3", "Class 4",
			"Class 5", "Class 6", "Class 7", "Class 8", "Class 9", "Class 10");

	static final int CLASS_1 = 3;

	static final int CLASS_6 = 8;

	/** Amount band of a level: pre-primary, primary (1–5), high school (6–10). */
	static int band(int level) {
		return level < CLASS_1 ? 0 : level < CLASS_6 ? 1 : 2;
	}

	record SubjectSpec(String name, String code, Subject.Type type, int fromLevel, int toLevel) {

		boolean taughtIn(int level) {
			return level >= fromLevel && level <= toLevel;
		}

	}

	/** One installment of a fee head, with its amount for each band. */
	record FeeSpec(String head, String installment, MonthDay due, int prePrimary, int primary, int high) {

		BigDecimal amount(int level) {
			return BigDecimal.valueOf(switch (band(level)) {
				case 0 -> prePrimary;
				case 1 -> primary;
				default -> high;
			});
		}

	}

	record ExamSpec(String name, MonthDay start, MonthDay end, int weightage, int maxMarks, String passMarks) {
	}

	record HolidaySpec(MonthDay date, String description) {
	}

	record Staff(String principal, String admin, String accountant, String teacher1, String teacher2) {
	}

	/**
	 * @param transportHead fee head that only some students use (others get a full concession), or null
	 */
	record Spec(String code, String name, School.Board board, String medium, String address, String phone,
			String email, String color, String logo, String prefix, int yearStartMonth, int yearMonths,
			List<DayOfWeek> weekOff, GradingPresets.Preset grading, boolean showRank, List<ExamSpec> exams,
			List<SubjectSpec> subjects, List<FeeSpec> fees, String transportHead, List<HolidaySpec> holidays,
			List<String> coScholastic, Staff staff, String motherTongue, String town) {
	}

	private static SubjectSpec subject(String name, String code, Subject.Type type, int from, int to) {
		return new SubjectSpec(name, code, type, from, to);
	}

	private static final int ALL_FROM = 0;

	private static final int LAST = 12;

	static final List<Spec> ALL = List.of(
			new Spec("vjhs", "Vidya Jyothi High School", School.Board.STATE, "TELUGU",
					"2-7-145, Vidya Nagar, Hanamkonda, Telangana", "0870-2400100", "office@vjhs.example", "#9a3412",
					"/demo/vjhs-logo.png", "VJHS", 6, 11, List.of(DayOfWeek.SUNDAY), GradingPresets.Preset.TS_CCE, true,
					List.of(new ExamSpec("FA1", MonthDay.of(7, 21), MonthDay.of(7, 23), 10, 50, "17.5"),
							new ExamSpec("FA2", MonthDay.of(9, 8), MonthDay.of(9, 10), 10, 50, "17.5"),
							new ExamSpec("SA1", MonthDay.of(11, 3), MonthDay.of(11, 8), 30, 100, "35"),
							new ExamSpec("FA3", MonthDay.of(12, 15), MonthDay.of(12, 17), 10, 50, "17.5"),
							new ExamSpec("FA4", MonthDay.of(2, 2), MonthDay.of(2, 4), 10, 50, "17.5"),
							new ExamSpec("SA2", MonthDay.of(3, 20), MonthDay.of(3, 28), 30, 100, "35")),
					List.of(subject("Telugu", "TEL", Subject.Type.LANGUAGE, ALL_FROM, LAST),
							subject("English", "ENG", Subject.Type.LANGUAGE, ALL_FROM, LAST),
							subject("Hindi", "HIN", Subject.Type.LANGUAGE, 5, LAST),
							subject("Mathematics", "MAT", Subject.Type.CORE, ALL_FROM, LAST),
							subject("EVS", "EVS", Subject.Type.CORE, CLASS_1, CLASS_6 - 1),
							subject("Science", "SCI", Subject.Type.CORE, CLASS_6, LAST),
							subject("Social Studies", "SOC", Subject.Type.CORE, CLASS_6, LAST),
							subject("Drawing", "DRW", Subject.Type.CO_CURRICULAR, ALL_FROM, CLASS_1 - 1)),
					List.of(new FeeSpec("Tuition", "Term 1", MonthDay.of(6, 15), 2000, 3000, 4000),
							new FeeSpec("Tuition", "Term 2", MonthDay.of(9, 15), 2000, 3000, 4000),
							new FeeSpec("Tuition", "Term 3", MonthDay.of(12, 15), 2000, 3000, 4000),
							new FeeSpec("Books & Uniform", "Annual", MonthDay.of(6, 15), 2000, 2500, 3000)),
					null,
					List.of(new HolidaySpec(MonthDay.of(8, 15), "Independence Day"),
							new HolidaySpec(MonthDay.of(9, 30), "Bathukamma"),
							new HolidaySpec(MonthDay.of(10, 1), "Dussehra holidays"),
							new HolidaySpec(MonthDay.of(10, 2), "Gandhi Jayanti / Dussehra"),
							new HolidaySpec(MonthDay.of(10, 3), "Dussehra holidays"),
							new HolidaySpec(MonthDay.of(1, 14), "Sankranti"),
							new HolidaySpec(MonthDay.of(1, 26), "Republic Day")),
					List.of("Art Education", "Health & Physical Education", "Work Education", "Discipline"),
					new Staff("M. Padmavathi", "K. Ramesh", "B. Sridevi", "G. Srinivas", "P. Anitha"), "Telugu",
					"Hanamkonda"),
			new Spec("lvps", "Lotus Valley Public School", School.Board.CBSE, "ENGLISH",
					"Plot 12, Nizampet Road, Hyderabad, Telangana", "040-29880011", "info@lvps.example", "#0f766e",
					"/demo/lvps-logo.png", "LVPS", 4, 12, List.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
					GradingPresets.Preset.CBSE_9_POINT, false,
					List.of(new ExamSpec("Term 1", MonthDay.of(9, 22), MonthDay.of(9, 30), 50, 100, "33"),
							new ExamSpec("Term 2", MonthDay.of(3, 2), MonthDay.of(3, 12), 50, 100, "33")),
					List.of(subject("English", "ENG", Subject.Type.LANGUAGE, ALL_FROM, LAST),
							subject("Hindi", "HIN", Subject.Type.LANGUAGE, CLASS_1, LAST),
							subject("Telugu", "TEL", Subject.Type.LANGUAGE, CLASS_1 + 2, LAST),
							subject("Mathematics", "MAT", Subject.Type.CORE, ALL_FROM, LAST),
							subject("EVS", "EVS", Subject.Type.CORE, CLASS_1, CLASS_6 - 1),
							subject("Science", "SCI", Subject.Type.CORE, CLASS_6, LAST),
							subject("Social Science", "SST", Subject.Type.CORE, CLASS_6, LAST),
							subject("Art & Craft", "ART", Subject.Type.CO_CURRICULAR, ALL_FROM, CLASS_1 - 1)),
					List.of(new FeeSpec("Tuition", "Quarter 1", MonthDay.of(4, 10), 9000, 11000, 14000),
							new FeeSpec("Tuition", "Quarter 2", MonthDay.of(7, 10), 9000, 11000, 14000),
							new FeeSpec("Tuition", "Quarter 3", MonthDay.of(10, 10), 9000, 11000, 14000),
							new FeeSpec("Tuition", "Quarter 4", MonthDay.of(1, 10), 9000, 11000, 14000),
							new FeeSpec("Annual Charges", "Annual", MonthDay.of(4, 10), 6000, 7000, 8000)),
					null,
					List.of(new HolidaySpec(MonthDay.of(8, 15), "Independence Day"),
							new HolidaySpec(MonthDay.of(10, 2), "Gandhi Jayanti"),
							new HolidaySpec(MonthDay.of(10, 20), "Diwali"),
							new HolidaySpec(MonthDay.of(12, 25), "Christmas"),
							new HolidaySpec(MonthDay.of(1, 26), "Republic Day")),
					List.of("Work Education", "Art Education", "Health & Physical Education", "Discipline"),
					new Staff("Dr. Anjali Mehta", "Rohit Kapoor", "Swathi Iyer", "Neha Sharma", "Arun Menon"), "English",
					"Hyderabad"),
			new Spec("sems", "Sunrise English Medium School", School.Board.STATE, "ENGLISH",
					"8-3-22, Gandhi Chowk, Khammam, Telangana", "08742-230456", "contact@sems.example", "#6d28d9",
					"/demo/sems-logo.png", "SEMS", 6, 11, List.of(DayOfWeek.SUNDAY), GradingPresets.Preset.TS_CCE, true,
					List.of(new ExamSpec("FA1", MonthDay.of(7, 22), MonthDay.of(7, 24), 10, 50, "17.5"),
							new ExamSpec("FA2", MonthDay.of(9, 9), MonthDay.of(9, 11), 10, 50, "17.5"),
							new ExamSpec("SA1", MonthDay.of(11, 4), MonthDay.of(11, 10), 30, 100, "35"),
							new ExamSpec("SA2", MonthDay.of(3, 18), MonthDay.of(3, 26), 50, 100, "35")),
					List.of(subject("English", "ENG", Subject.Type.LANGUAGE, ALL_FROM, LAST),
							subject("Telugu", "TEL", Subject.Type.LANGUAGE, ALL_FROM, LAST),
							subject("Hindi", "HIN", Subject.Type.LANGUAGE, CLASS_1 + 2, LAST),
							subject("Mathematics", "MAT", Subject.Type.CORE, ALL_FROM, LAST),
							subject("EVS", "EVS", Subject.Type.CORE, CLASS_1, CLASS_6 - 1),
							subject("Science", "SCI", Subject.Type.CORE, CLASS_6, LAST),
							subject("Social Studies", "SOC", Subject.Type.CORE, CLASS_6, LAST),
							subject("Rhymes & Activities", "RHY", Subject.Type.CO_CURRICULAR, ALL_FROM, CLASS_1 - 1)),
					List.of(new FeeSpec("Tuition", "Term 1", MonthDay.of(6, 15), 7000, 9000, 12000),
							new FeeSpec("Tuition", "Term 2", MonthDay.of(11, 15), 7000, 9000, 12000),
							new FeeSpec("Transport", "Term 1", MonthDay.of(6, 15), 5000, 5000, 5000),
							new FeeSpec("Transport", "Term 2", MonthDay.of(11, 15), 5000, 5000, 5000)),
					"Transport",
					List.of(new HolidaySpec(MonthDay.of(8, 15), "Independence Day"),
							new HolidaySpec(MonthDay.of(10, 2), "Gandhi Jayanti"),
							new HolidaySpec(MonthDay.of(10, 1), "Dussehra"),
							new HolidaySpec(MonthDay.of(12, 25), "Christmas"),
							new HolidaySpec(MonthDay.of(1, 14), "Sankranti"),
							new HolidaySpec(MonthDay.of(1, 26), "Republic Day")),
					List.of("Art Education", "Health & Physical Education", "Discipline"),
					new Staff("S. Raghavendra Rao", "N. Kavitha", "T. Mahesh", "R. Swapna", "D. Kiran Kumar"), "Telugu",
					"Khammam"));

}
