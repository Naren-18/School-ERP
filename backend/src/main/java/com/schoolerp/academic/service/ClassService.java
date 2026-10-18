package com.schoolerp.academic.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.academic.dto.ClassDto;
import com.schoolerp.academic.entity.SchoolClass;
import com.schoolerp.academic.repository.ClassSubjectRepository;
import com.schoolerp.academic.repository.SchoolClassRepository;
import com.schoolerp.academic.repository.SectionRepository;
import com.schoolerp.common.error.ConflictException;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClassService {

	private final SchoolClassRepository classes;

	private final SectionRepository sections;

	private final ClassSubjectRepository classSubjects;

	@Transactional(readOnly = true)
	public List<ClassDto.Response> list() {
		return classes.findBySchoolIdOrderByDisplayOrderAscNameAsc(TenantContext.require())
			.stream()
			.map(ClassDto.Response::of)
			.toList();
	}

	@Transactional
	public ClassDto.Response create(ClassDto.Request request) {
		String name = request.name().trim();
		if (classes.existsBySchoolIdAndNameIgnoreCase(TenantContext.require(), name)) {
			throw new ConflictException("A class named " + name + " already exists");
		}
		SchoolClass c = new SchoolClass();
		c.setName(name);
		c.setDisplayOrder(request.displayOrder());
		return ClassDto.Response.of(classes.save(c));
	}

	@Transactional
	public ClassDto.Response update(Long id, ClassDto.Request request) {
		SchoolClass c = require(id);
		String name = request.name().trim();
		if (classes.existsBySchoolIdAndNameIgnoreCaseAndIdNot(TenantContext.require(), name, id)) {
			throw new ConflictException("A class named " + name + " already exists");
		}
		c.setName(name);
		c.setDisplayOrder(request.displayOrder());
		return ClassDto.Response.of(c);
	}

	@Transactional
	public void delete(Long id) {
		Long schoolId = TenantContext.require();
		SchoolClass c = require(id);
		if (sections.existsBySchoolIdAndClassId(schoolId, id) || classSubjects.existsBySchoolIdAndClassId(schoolId, id)) {
			throw new ConflictException("This class has sections or subjects. Remove them first.");
		}
		classes.delete(c);
		classes.flush();
	}

	public SchoolClass require(Long id) {
		return classes.findByIdAndSchoolId(id, TenantContext.require())
			.orElseThrow(() -> new NotFoundException("Class"));
	}

}
