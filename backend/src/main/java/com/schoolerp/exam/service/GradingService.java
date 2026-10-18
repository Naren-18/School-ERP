package com.schoolerp.exam.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.common.error.BadRequestException;
import com.schoolerp.common.error.ConflictException;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.exam.dto.ExamDto;
import com.schoolerp.exam.entity.GradingBand;
import com.schoolerp.exam.entity.GradingScale;
import com.schoolerp.exam.repository.ExamRepository;
import com.schoolerp.exam.repository.GradingBandRepository;
import com.schoolerp.exam.repository.GradingScaleRepository;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/** Grading scales and their bands; two presets can be added in one click. */
@Service
@RequiredArgsConstructor
public class GradingService {

	private final GradingScaleRepository scales;

	private final GradingBandRepository bands;

	private final ExamRepository exams;

	@Transactional(readOnly = true)
	public List<ExamDto.Scale> list() {
		Long schoolId = TenantContext.require();
		List<GradingScale> list = scales.findBySchoolIdOrderByNameAsc(schoolId);
		Map<Long, List<GradingBand>> bandsByScale = bandsOf(list.stream().map(GradingScale::getId).toList());
		return list.stream()
			.map(s -> view(s, bandsByScale.getOrDefault(s.getId(), List.of()),
					exams.existsBySchoolIdAndGradingScaleId(schoolId, s.getId())))
			.toList();
	}

	@Transactional
	public ExamDto.Scale create(ExamDto.ScaleRequest request) {
		String name = request.name().trim();
		if (scales.existsBySchoolIdAndNameIgnoreCase(TenantContext.require(), name)) {
			throw new ConflictException("A grading scale named " + name + " already exists");
		}
		validate(request.bands());
		GradingScale scale = new GradingScale();
		scale.setName(name);
		scales.save(scale);
		return view(scale, saveBands(scale.getId(), request.bands()), false);
	}

	/** Replaces the name and all bands. Results are computed on the fly, so they follow the new bands. */
	@Transactional
	public ExamDto.Scale update(Long id, ExamDto.ScaleRequest request) {
		GradingScale scale = require(id);
		String name = request.name().trim();
		if (scales.existsBySchoolIdAndNameIgnoreCaseAndIdNot(TenantContext.require(), name, id)) {
			throw new ConflictException("A grading scale named " + name + " already exists");
		}
		validate(request.bands());
		scale.setName(name);
		bands.deleteByGradingScaleId(scale.getId());
		bands.flush();
		return view(scale, saveBands(scale.getId(), request.bands()),
				exams.existsBySchoolIdAndGradingScaleId(TenantContext.require(), id));
	}

	@Transactional
	public void delete(Long id) {
		GradingScale scale = require(id);
		if (exams.existsBySchoolIdAndGradingScaleId(TenantContext.require(), id)) {
			throw new ConflictException(scale.getName() + " is used by an exam");
		}
		bands.deleteByGradingScaleId(id);
		scales.delete(scale);
	}

	@Transactional
	public ExamDto.Scale addPreset(GradingPresets.Preset preset) {
		List<ExamDto.BandInput> presetBands = GradingPresets.presetBands(preset)
			.stream()
			.map(b -> new ExamDto.BandInput(b.min(), b.max(), b.grade(), b.gradePoint(), b.remark()))
			.toList();
		return create(new ExamDto.ScaleRequest(preset.displayName(), presetBands));
	}

	/** Calculator bands for a scale of this school. */
	@Transactional(readOnly = true)
	public List<ResultCalculator.Band> calculatorBands(Long scaleId) {
		require(scaleId);
		return bandsOf(List.of(scaleId)).getOrDefault(scaleId, List.of())
			.stream()
			.map(b -> new ResultCalculator.Band(b.getMinPercent(), b.getGrade(), b.getGradePoint(), b.getRemark()))
			.toList();
	}

	public GradingScale require(Long id) {
		return scales.findByIdAndSchoolId(id, TenantContext.require())
			.orElseThrow(() -> new NotFoundException("Grading scale"));
	}

	/**
	 * Every percentage from 0 must get a grade: one band starts at 0, no two
	 * bands share a minimum or a grade, and each band's min ≤ max.
	 */
	private static void validate(List<ExamDto.BandInput> list) {
		Set<BigDecimal> mins = new HashSet<>();
		Set<String> grades = new HashSet<>();
		for (ExamDto.BandInput b : list) {
			if (b.minPercent().compareTo(b.maxPercent()) > 0) {
				throw new BadRequestException("Grade " + b.grade() + ": the minimum is above the maximum");
			}
			if (!mins.add(b.minPercent().stripTrailingZeros())) {
				throw new BadRequestException("Two grades start at " + b.minPercent() + "%");
			}
			if (!grades.add(b.grade().trim().toUpperCase(Locale.ROOT))) {
				throw new BadRequestException("Grade " + b.grade() + " appears twice");
			}
		}
		if (mins.stream().noneMatch(m -> m.signum() == 0)) {
			throw new BadRequestException("One grade must start at 0% so every mark gets a grade");
		}
	}

	private List<GradingBand> saveBands(Long scaleId, List<ExamDto.BandInput> inputs) {
		List<GradingBand> saved = inputs.stream().map(in -> {
			GradingBand b = new GradingBand();
			b.setGradingScaleId(scaleId);
			b.setMinPercent(in.minPercent());
			b.setMaxPercent(in.maxPercent());
			b.setGrade(in.grade().trim());
			b.setGradePoint(in.gradePoint());
			b.setRemark(in.remark() == null || in.remark().isBlank() ? null : in.remark().trim());
			return b;
		}).toList();
		return bands.saveAll(saved).stream().sorted((x, y) -> y.getMinPercent().compareTo(x.getMinPercent())).toList();
	}

	private Map<Long, List<GradingBand>> bandsOf(List<Long> scaleIds) {
		if (scaleIds.isEmpty()) {
			return Map.of();
		}
		return bands.findByGradingScaleIdInOrderByMinPercentDesc(scaleIds)
			.stream()
			.collect(Collectors.groupingBy(GradingBand::getGradingScaleId));
	}

	private static ExamDto.Scale view(GradingScale s, List<GradingBand> list, boolean inUse) {
		return new ExamDto.Scale(s.getId(), s.getName(), list.stream()
			.map(b -> new ExamDto.Band(b.getId(), b.getMinPercent(), b.getMaxPercent(), b.getGrade(), b.getGradePoint(),
					b.getRemark()))
			.toList(), inUse);
	}

}
