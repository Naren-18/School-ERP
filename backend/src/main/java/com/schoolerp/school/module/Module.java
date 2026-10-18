package com.schoolerp.school.module;

import com.schoolerp.school.entity.SchoolSettings;

/** Optional modules a school can switch off in settings. */
public enum Module {

	ATTENDANCE, FEES, EXAMS, CERTIFICATES;

	public boolean isEnabled(SchoolSettings settings) {
		SchoolSettings.Modules m = settings.modules();
		return switch (this) {
			case ATTENDANCE -> m.attendance();
			case FEES -> m.fees();
			case EXAMS -> m.exams();
			case CERTIFICATES -> m.certificates();
		};
	}

}
