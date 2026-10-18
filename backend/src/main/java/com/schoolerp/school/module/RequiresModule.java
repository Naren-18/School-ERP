package com.schoolerp.school.module;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Put on a controller (or handler method) that belongs to an optional module.
 * When the school has the module switched off, the endpoint answers 404.
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresModule {

	Module value();

}
