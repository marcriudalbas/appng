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
package org.appng.core.repository.config;

import javax.sql.DataSource;

import org.appng.core.domain.DatabaseConnection;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * A {@link FactoryBean} for {@link DataSource}s, using a {@link DatasourceConfigurer}.
 * 
 * @author Matthias Müller
 */
@Slf4j
public class DataSourceFactory implements FactoryBean<DataSource>, DisposableBean, DatasourceConfigurer {

	private DatasourceConfigurer configurer;

	private @Setter String configurerClass;
	private @Setter boolean autoCommit = false;
	private @Setter boolean logPerformance = false;
	private @Setter long connectionTimeout = DEFAULT_TIMEOUT;
	private @Setter long validationTimeout = DEFAULT_TIMEOUT;
	private @Setter long maxLifetime = DEFAULT_LIFE_TIME;

	public DataSourceFactory() {

	}

	public DataSourceFactory(DatasourceConfigurer configurer) {
		this.configurer = configurer;
	}

	public Class<?> getObjectType() {
		return DataSource.class;
	}

	public boolean isSingleton() {
		return true;
	}

	private DatasourceConfigurer initConfigurer() {
		try {
			Class<?> loadClass = Thread.currentThread().getContextClassLoader().loadClass(configurerClass);
			this.configurer = (DatasourceConfigurer) loadClass.newInstance();
			this.configurer.setLogPerformance(logPerformance);
			this.configurer.setConnectionTimeout(connectionTimeout);
			this.configurer.setValidationTimeout(validationTimeout);
			this.configurer.setMaxLifetime(maxLifetime);
			this.configurer.setAutoCommit(autoCommit);
		} catch (Exception e) {
			LOGGER.error(String.format("error creating instance of '%s'", configurerClass), e);
		}
		return configurer;
	}

	public DataSource getObject() throws Exception {
		return getDataSource();
	}

	public void configure(DatabaseConnection connection) {
		if (null != connection) {
			initConfigurer();
			configurer.configure(connection);
		}
	}

	public void destroy() {
		if (null != configurer) {
			configurer.destroy();
		}
	}

	public DataSource getDataSource() {
		return configurer.getDataSource();
	}

}
