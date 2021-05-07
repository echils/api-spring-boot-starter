package com.github.api;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * EnableApiReporterDelegate
 * Decorate the startup class with this annotation to enable automatic hosting of API documents
 *
 * @author echils
 * @since 2020-12-01 16:06:25
 */
@Target(value = ElementType.TYPE)
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Import(ApiDocumentConfiguration.class)
public @interface EnableApiReporterDelegate {

}
