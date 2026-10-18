package com.schoolerp.school.module;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.school.repository.SchoolRepository;
import com.schoolerp.tenant.TenantContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/** Rejects requests to {@link RequiresModule} endpoints when the school has that module off. */
@Configuration
@RequiredArgsConstructor
public class ModuleGuard implements WebMvcConfigurer, HandlerInterceptor {

	private final SchoolRepository schools;

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(this).addPathPatterns("/api/**");
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!(handler instanceof HandlerMethod method)) {
			return true;
		}
		RequiresModule onMethod = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(),
				RequiresModule.class);
		RequiresModule required = onMethod != null ? onMethod
				: AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequiresModule.class);
		if (required == null) {
			return true;
		}
		Long schoolId = TenantContext.get();
		Module module = required.value();
		boolean enabled = schoolId != null && schools.findById(schoolId)
			.map(school -> module.isEnabled(school.getSettings()))
			.orElse(false);
		if (!enabled) {
			throw new NotFoundException("Page");
		}
		return true;
	}

}
