/*
 * Copyright 2011-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.appng.api.support;

import java.util.Arrays;
import java.util.List;

import org.appng.api.Environment;
import org.appng.api.RequestSupport;
import org.appng.api.ResultService;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.api.support.field.FieldConversionFactory;
import org.appng.el.ExpressionEvaluator;
import org.appng.xml.platform.FieldType;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.MessageSource;
import org.springframework.core.convert.ConversionService;

import lombok.Getter;
import lombok.Setter;

/**
 * Baseclass for {@link ResultService} and {@link RequestSupport} implementations
 * 
 * @author Matthias Müller
 */
@Getter
@Setter
abstract class AdapterBase implements InitializingBean {

	protected static final String CURRENT = "current";

	protected final List<FieldType> LIST_TYPES = Arrays.asList(FieldType.LIST_CHECKBOX, FieldType.LIST_RADIO,
			FieldType.LIST_SELECT, FieldType.LIST_TEXT);

	protected final List<FieldType> FILE_TYPES = Arrays.asList(FieldType.FILE, FieldType.FILE_MULTIPLE);

	protected ConversionService conversionService;
	protected MessageSource messageSource;
	protected Environment environment;
	protected ExpressionEvaluator expressionEvaluator;
	protected FieldConversionFactory fieldConverter;
	protected Application application;
	protected Site site;

	public final boolean isFile(FieldType type) {
		return FILE_TYPES.contains(type);
	}

	public final boolean isListType(FieldType type) {
		return LIST_TYPES.contains(type);
	}

	public void afterPropertiesSet() {
		fieldConverter = new FieldConversionFactory(expressionEvaluator);
		fieldConverter.setConversionService(conversionService);
		fieldConverter.setEnvironment(environment);
		fieldConverter.setMessageSource(messageSource);
		fieldConverter.afterPropertiesSet();
	}
}
