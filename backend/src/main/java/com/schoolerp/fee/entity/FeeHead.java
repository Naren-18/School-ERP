package com.schoolerp.fee.entity;

import com.schoolerp.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A kind of fee: Tuition, Transport, Exam, Books. Inactive heads stay on old plans. */
@Getter
@Setter
@Entity
@Table(name = "fee_head")
public class FeeHead extends TenantEntity {

	@Column(nullable = false, length = 60)
	private String name;

	@Column(nullable = false)
	private boolean active = true;

}
