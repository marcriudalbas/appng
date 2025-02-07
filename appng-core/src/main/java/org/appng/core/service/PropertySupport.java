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
package org.appng.core.service;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.appng.api.Platform;
import org.appng.api.SiteProperties;
import org.appng.api.VHostMode;
import org.appng.api.auth.AuthTools;
import org.appng.api.auth.PasswordPolicy;
import org.appng.api.model.Application;
import org.appng.api.model.Properties;
import org.appng.api.model.Property;
import org.appng.api.model.Property.Type;
import org.appng.api.model.Site;
import org.appng.api.support.PropertyHolder;
import org.appng.core.controller.HttpHeaders;
import org.appng.core.controller.messaging.HazelcastReceiver;
import org.appng.core.domain.SiteImpl;
import org.appng.core.repository.config.DataSourceFactory;
import org.appng.core.repository.config.HikariCPConfigurer;
import org.appng.core.security.ConfigurablePasswordPolicy;
import org.appng.core.security.DefaultPasswordPolicy;

import lombok.extern.slf4j.Slf4j;

/**
 * A service offering methods for initializing and retrieving the configuration {@link Properties} of the platform, a
 * {@link Site} or an {@link Application}.
 * 
 * @author Matthias Müller
 * 
 * @see Properties
 * @see PropertyHolder
 */
@Slf4j
public class PropertySupport {

	private static final String PREFIX_EMPTY = "";
	public static final String PREFIX_PLATFORM = "platform.";
	static final String PREFIX_SITE = "site.";
	private static final String PREFIX_APPLICATION = "application.";
	private static final String DOT = ".";
	static final String PREFIX_NODE = "platform.node.";

	public static final String PROP_PATTERN = "[a-zA-Z0-9\\-_]+";

	private PropertyHolder propertyHolder;
	private ResourceBundle bundle;

	/**
	 * Creates a new {@link PropertySupport} using the given {@link PropertyHolder}.
	 * 
	 * @param propertyHolder
	 *                       the {@link PropertyHolder} to use
	 */
	public PropertySupport(PropertyHolder propertyHolder) {
		this.propertyHolder = propertyHolder;
	}

	/**
	 * Aggregates the {@link Properties} of the platform, the given {@link Site} and given {@link Application} to a
	 * single {@link java.util.Properties} object, using a prefix for determining the origin of a certain property.The
	 * prefix for a site-property is {@code site.}, for a platform-property it's {@value #PREFIX_PLATFORM}. For an
	 * {@link Application} property no prefix is used.
	 * 
	 * @param platFormConfig
	 *                         the platform configuration, only needed if {@code addPlatformScope} is {@code true}.
	 * @param site
	 *                         the {@link Site} to retrieve {@link Properties} from (may be null)
	 * @param application
	 *                         the {@link Application} to retrieve {@link Properties} from (may be null)
	 * @param addPlatformScope
	 *                         set to {@code true} to add the platform properties
	 * 
	 * @return the aggregated {@link java.util.Properties} with prefixed entries
	 * 
	 * @see Properties#getPlainProperties()
	 */
	public static java.util.Properties getProperties(Properties platFormConfig, Site site, Application application,
			boolean addPlatformScope) {
		java.util.Properties props = new java.util.Properties();
		if (null != application) {
			addProperties(props, PREFIX_EMPTY, application.getProperties().getPlainProperties());
		}
		if (null != site) {
			addProperties(props, PREFIX_SITE, site.getProperties().getPlainProperties());
		}
		if (addPlatformScope) {
			addProperties(props, PREFIX_PLATFORM, platFormConfig.getPlainProperties());
		}
		return props;
	}

	/**
	 * Returns the dot-separated full name for a given property, depending on whether a {@link Site} and/or an
	 * {@link Application} are given.
	 * 
	 * @param site
	 *                    the {@link Site}, may be {@code null}
	 * @param application
	 *                    the {@link Application}, may be {@code null}
	 * @param name
	 *                    the raw name of the property, without dot-notation
	 * 
	 * @return the full name of the property.
	 */
	public static String getPropertyName(Site site, Application application, String name) {
		return getPropertyPrefix(site, application) + name;
	}

	/**
	 * Returns the dot-separated property-prefix, depending on whether a {@link Site} and/or an {@link Application} are
	 * given.
	 * 
	 * @param site
	 *                    the {@link Site}, may be {@code null}
	 * @param application
	 *                    the {@link Application}, may be {@code null}
	 */
	public static String getPropertyPrefix(Site site, Application application) {
		String prefix = PREFIX_PLATFORM;
		if (null != site) {
			prefix += PREFIX_SITE + site.getName() + DOT;
		}
		if (null != application) {
			prefix += PREFIX_APPLICATION + application.getName() + DOT;
		}
		return prefix;
	}

	/**
	 * Returns the dot-separated property-prefix for a site-property.
	 * 
	 * @param site
	 *             the {@link Site}
	 * 
	 * @return the dot-separated property-prefix
	 */
	public static String getSitePrefix(Site site) {
		return getPropertyPrefix(site, null);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static void addProperties(Map props, String prefix, java.util.Properties propsToAdd) {
		for (Object property : propsToAdd.keySet()) {
			props.put(prefix + property, propsToAdd.getProperty((String) property));
		}
	}

	private String addPlatformProperty(java.util.Properties defaultOverrides, String name, Object defaultValue) {
		return addPlatformProperty(defaultOverrides, name, defaultValue, Type.forObject(defaultValue));
	}

	private String addPlatformProperty(java.util.Properties defaultOverrides, String name, Object defaultValue,
			Type type) {
		if (defaultOverrides.containsKey(PREFIX_PLATFORM + name)) {
			defaultValue = defaultOverrides.get(PREFIX_PLATFORM + name);
			defaultOverrides.remove(PREFIX_PLATFORM + name);
		}
		return addProperty(name, defaultValue, PREFIX_PLATFORM, type);
	}

	private String addSiteProperty(String name, Object defaultValue) {
		return addSiteProperty(name, defaultValue, Type.forObject(defaultValue));
	}

	private String addSiteProperty(String name, Object defaultValue, Type type) {
		return addProperty(name, defaultValue, PREFIX_SITE, type);
	}

	private String addProperty(String name, Object defaultValue, String prefix, Type type) {
		String description = bundle.getString(prefix + name);
		boolean multiline = Type.MULTILINE.equals(type);
		Property added = propertyHolder.addProperty(name, defaultValue, description, type);
		String value = multiline ? added.getClob() : added.getDefaultString();
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("added property {}{} = {}", prefix, name, value);
		}
		return value;
	}

	/**
	 * Initializes the {@link Site} configuration with the default values. The properties are added to the
	 * {@link PropertyHolder} this {@link PropertySupport} was created with.
	 * 
	 * @param site
	 *                       the {@link Site} to initialize the {@link Properties} for
	 * @param platformConfig
	 *                       the platform configuration
	 * 
	 * @see #PropertySupport(PropertyHolder)
	 * @see SiteProperties
	 */
	public void initSiteProperties(SiteImpl site, Properties platformConfig) {
		bundle = ResourceBundle.getBundle("org/appng/core/site-config");

		String appNGData = platformConfig.getString(Platform.Property.APPNG_DATA);
		String repositoryPath = platformConfig.getString(Platform.Property.REPOSITORY_PATH);
		addSiteProperty(SiteProperties.SITE_ROOT_DIR, normalizePath(appNGData, repositoryPath, site.getName()));

		String passwordPolicyClass = platformConfig.getString(Platform.Property.PASSWORD_POLICY,
				ConfigurablePasswordPolicy.class.getName());
		PasswordPolicy passwordPolicy = null;
		try {
			passwordPolicy = (PasswordPolicy) getClass().getClassLoader().loadClass(passwordPolicyClass).newInstance();
		} catch (ReflectiveOperationException e) {
			LOGGER.error("error while instantiating " + passwordPolicyClass, e);
			passwordPolicy = new ConfigurablePasswordPolicy();
		}

		LOGGER.debug("Using {} for site {}", passwordPolicy.getClass().getName(), site.getName());
		passwordPolicy.configure(platformConfig);
		site.setPasswordPolicy(passwordPolicy);

		addSiteProperty(SiteProperties.NAME, site.getName());
		addSiteProperty(SiteProperties.HOST, site.getHost());
		addSiteProperty(SiteProperties.WWW_DIR, "/www");
		String managerPath = addSiteProperty(SiteProperties.MANAGER_PATH, "/manager");
		addSiteProperty(SiteProperties.SERVICE_OUTPUT_FORMAT, "html");
		addSiteProperty(SiteProperties.SERVICE_OUTPUT_TYPE, "service");
		addSiteProperty(SiteProperties.SERVICE_PATH, "/service");
		addSiteProperty(SiteProperties.SESSION_TRACKING_ENABLED, true);
		addSiteProperty(SiteProperties.SUPPORTED_LANGUAGES, "en, de");
		addSiteProperty(SiteProperties.CACHE_CLEAR_ON_SHUTDOWN, true);
		addSiteProperty(SiteProperties.CACHE_ENABLED, false);
		addSiteProperty(SiteProperties.CACHE_EXCEPTIONS, managerPath + "\r\n/health", Type.MULTILINE);
		addSiteProperty(SiteProperties.CACHE_TIME_TO_LIVE, 1800);
		addSiteProperty(SiteProperties.CACHE_TIMEOUTS, StringUtils.EMPTY, Type.MULTILINE);
		addSiteProperty(SiteProperties.CACHE_TIMEOUTS_ANT_STYLE, true);
		addSiteProperty(SiteProperties.CACHE_STATISTICS, false);
		addSiteProperty(SiteProperties.ERROR_PAGE, "error");
		addSiteProperty(SiteProperties.ERROR_PAGES, "/de=fehler|/en=error");
		addSiteProperty(SiteProperties.INDEX_DIR, "/index");
		addSiteProperty(SiteProperties.INDEX_TIMEOUT, 5000);
		addSiteProperty(SiteProperties.INDEX_QUEUE_SIZE, 1000);
		addSiteProperty(SiteProperties.JDBC_CONNECTION_TIMEOUT, DataSourceFactory.DEFAULT_TIMEOUT);
		addSiteProperty(SiteProperties.JDBC_LOG_PERFORMANCE, false);
		addSiteProperty(SiteProperties.JDBC_MAX_LIFETIME, DataSourceFactory.DEFAULT_LIFE_TIME);
		addSiteProperty(SiteProperties.JDBC_VALIDATION_TIMEOUT, DataSourceFactory.DEFAULT_TIMEOUT);
		addSiteProperty(SiteProperties.SEARCH_CHUNK_SIZE, 20);
		addSiteProperty(SiteProperties.SEARCH_MAX_HITS, 100);
		addSiteProperty(Platform.Property.MAIL_HOST, "localhost");
		addSiteProperty(Platform.Property.MAIL_PORT, 25);
		addSiteProperty(Platform.Property.MAIL_DISABLED, true);
		addSiteProperty(SiteProperties.INDEX_CONFIG, "/de;de;GermanAnalyzer|/assets;de;GermanAnalyzer");
		addSiteProperty(SiteProperties.INDEX_FILETYPES, "jsp,pdf,doc");
		addSiteProperty(SiteProperties.INDEX_FILE_SYSTEM_QUEUE_SIZE, 2500);
		addSiteProperty(SiteProperties.DEFAULT_PAGE, "index");
		addSiteProperty(SiteProperties.DEFAULT_PAGE_SIZE, 25);
		addSiteProperty(SiteProperties.APPEND_TAB_ID, false);
		addSiteProperty(SiteProperties.ALLOW_SKIP_RENDER, false);
		addSiteProperty(Platform.Property.ENCODING, HttpHeaders.CHARSET_UTF8);
		addSiteProperty(SiteProperties.ASSETS_DIR, "/assets");
		addSiteProperty(SiteProperties.DOCUMENT_DIR, "/de");
		addSiteProperty(SiteProperties.ENFORCE_PRIMARY_DOMAIN, false);
		addSiteProperty(SiteProperties.DEFAULT_APPLICATION, "appng-manager");
		addSiteProperty(Platform.Property.LOCALE, Locale.getDefault().getLanguage());
		addSiteProperty(Platform.Property.TIME_ZONE, TimeZone.getDefault().getID());
		addSiteProperty(SiteProperties.TEMPLATE, "appng");
		addSiteProperty(SiteProperties.DATASOURCE_CONFIGURER, HikariCPConfigurer.class.getName());
		addSiteProperty(SiteProperties.TAG_PREFIX, "appNG");
		addSiteProperty(SiteProperties.REWRITE_CONFIG, "/meta/conf/urlrewrite.xml");
		addSiteProperty(SiteProperties.SET_DEBUG_HEADERS, false);
		addSiteProperty(SiteProperties.SUPPORT_RELOAD_FILE,
				!platformConfig.getBoolean(Platform.Property.MESSAGING_ENABLED));

		addSiteProperty(SiteProperties.AUTH_APPLICATION, "appng-authentication");
		addSiteProperty(SiteProperties.AUTH_LOGIN_PAGE, "webform");
		addSiteProperty(SiteProperties.AUTH_LOGIN_REF, "webform");
		addSiteProperty(SiteProperties.AUTH_LOGOUT_ACTION_NAME, "action");
		addSiteProperty(SiteProperties.AUTH_LOGOUT_ACTION_VALUE, "logout");
		addSiteProperty(SiteProperties.AUTH_LOGOUT_PAGE, "webform");
		addSiteProperty(SiteProperties.AUTH_LOGOUT_REF, "webform/logout");
		addSiteProperty(SiteProperties.CSRF_PROTECTION_ENABLED, "false");
		addSiteProperty(SiteProperties.CSRF_PROTECTED_METHODS, "POST,PUT");
		addSiteProperty(SiteProperties.CSRF_PROTECTED_PATHS, "/manager");

		StringBuilder xssExceptions = new StringBuilder();
		xssExceptions.append("# template" + StringUtils.LF);
		xssExceptions.append(platformConfig.getString(Platform.Property.TEMPLATE_PREFIX) + StringUtils.LF);
		xssExceptions.append("# appng-manager" + StringUtils.LF);
		xssExceptions.append(managerPath + "/" + site.getName() + "/appng-manager" + StringUtils.LF);
		addSiteProperty(SiteProperties.XSS_EXCEPTIONS, xssExceptions.toString(), Type.MULTILINE);

		addSiteProperty(LdapService.LDAP_DISABLED, false);
		addSiteProperty(LdapService.LDAP_HOST, "ldap://localhost:389");
		addSiteProperty(LdapService.LDAP_USER_BASE_DN, "OU=Users,DC=example,DC=com");
		addSiteProperty(LdapService.LDAP_GROUP_BASE_DN, "OU=Groups,DC=example,DC=com");
		addSiteProperty(LdapService.LDAP_USER, "serviceUser");
		addSiteProperty(LdapService.LDAP_PASSWORD, "secret", Type.PASSWORD);
		addSiteProperty(LdapService.LDAP_DOMAIN, "EXAMPLE");
		addSiteProperty(LdapService.LDAP_ID_ATTRIBUTE, "sAMAccountName");
		addSiteProperty(LdapService.LDAP_PRINCIPAL_SCHEME, "SAM");
		addSiteProperty(LdapService.LDAP_START_TLS, false);

		propertyHolder.setFinal();
	}

	public void initPlatformConfig(String rootPath, Boolean devMode) {
		initPlatformConfig(rootPath, devMode, new java.util.Properties(), true);
	}

	/**
	 * Initializes the platform configuration with the default values. The properties are added to the
	 * {@link PropertyHolder} this {@link PropertySupport} was created with.
	 * 
	 * @param rootPath
	 *                           the root path of the platform (see
	 *                           {@link org.appng.api.Platform.Property#PLATFORM_ROOT_PATH})
	 * @param devMode
	 *                           value for the {@link org.appng.api.Platform.Property#DEV_MODE} property to set
	 * @param immutableOverrides
	 *                           some {@link java.util.Properties} used to override the default values
	 * @param finalize
	 *                           whether or not to call {@link PropertyHolder#setFinal()}
	 * 
	 * @see #PropertySupport(PropertyHolder)
	 * @see org.appng.api.Platform.Property
	 */
	public void initPlatformConfig(String rootPath, Boolean devMode, java.util.Properties immutableOverrides,
			boolean finalize) {
		java.util.Properties defaultOverrides = new java.util.Properties();
		defaultOverrides.putAll(immutableOverrides);
		bundle = ResourceBundle.getBundle("org/appng/core/platform-config");
		if (null != rootPath) {
			String realRootPath = addPlatformProperty(defaultOverrides, Platform.Property.PLATFORM_ROOT_PATH,
					normalizePath(rootPath));
			String appngDataDir = System.getProperty(Platform.Property.APPNG_DATA);
			if (StringUtils.isBlank(appngDataDir)) {
				appngDataDir = realRootPath;
			} else {
				appngDataDir = normalizePath(appngDataDir);
			}
			addPlatformProperty(defaultOverrides, Platform.Property.APPNG_DATA, appngDataDir);
		}
		addPlatformProperty(defaultOverrides, Platform.Property.APPLICATION_CACHE_FOLDER, "application");
		addPlatformProperty(defaultOverrides, Platform.Property.CACHE_FOLDER, "cache");
		addPlatformProperty(defaultOverrides, Platform.Property.CLEAN_TEMP_FOLDER_ON_STARTUP, false);
		addPlatformProperty(defaultOverrides, Platform.Property.CSRF_FILTER_ENABLED, false);
		addPlatformProperty(defaultOverrides, Platform.Property.DATABASE_PREFIX, StringUtils.EMPTY);
		addPlatformProperty(defaultOverrides, Platform.Property.DATABASE_VALIDATION_PERIOD, 15);
		addPlatformProperty(defaultOverrides, Platform.Property.DEFAULT_TEMPLATE, "appng");
		addPlatformProperty(defaultOverrides, Platform.Property.DEV_MODE, devMode);
		addPlatformProperty(defaultOverrides, Platform.Property.CACHE_CONFIG, "WEB-INF/conf/hazelcast.xml");
		addPlatformProperty(defaultOverrides, Platform.Property.ENCODING, HttpHeaders.CHARSET_UTF8);
		addPlatformProperty(defaultOverrides, Platform.Property.FILEBASED_DEPLOYMENT, Boolean.TRUE);
		addPlatformProperty(defaultOverrides, Platform.Property.FORMAT_OUTPUT, false);
		addPlatformProperty(defaultOverrides, Platform.Property.IMAGE_CACHE_FOLDER, "image");
		addPlatformProperty(defaultOverrides, Platform.Property.IMAGEMAGICK_PATH, "/usr/bin");
		addPlatformProperty(defaultOverrides, Platform.Property.INACTIVE_LOCK_PERIOD, 0);
		addPlatformProperty(defaultOverrides, Platform.Property.JSP_FILE_TYPE, "jsp");
		addPlatformProperty(defaultOverrides, Platform.Property.LOCALE, "en");
		addPlatformProperty(defaultOverrides, Platform.Property.LOGFILE, "appNG.log");
		addPlatformProperty(defaultOverrides, Platform.Property.MAIL_DISABLED, true);
		addPlatformProperty(defaultOverrides, Platform.Property.MAIL_HOST, "localhost");
		addPlatformProperty(defaultOverrides, Platform.Property.MAIL_PORT, 25);
		addPlatformProperty(defaultOverrides, Platform.Property.MANAGE_DATABASES, Boolean.TRUE);
		addPlatformProperty(defaultOverrides, Platform.Property.MAX_UPLOAD_SIZE, 30 * 1024 * 1024);
		addPlatformProperty(defaultOverrides, Platform.Property.MAX_LOGIN_ATTEMPTS, 20);
		addPlatformProperty(defaultOverrides, Platform.Property.MDC_ENABLED, Boolean.TRUE);
		addPlatformProperty(defaultOverrides, Platform.Property.MESSAGING_ENABLED, Boolean.TRUE);
		addPlatformProperty(defaultOverrides, Platform.Property.MESSAGING_GROUP_ADDRESS, "224.2.2.4");
		addPlatformProperty(defaultOverrides, Platform.Property.MESSAGING_GROUP_PORT, 4000);
		addPlatformProperty(defaultOverrides, Platform.Property.MESSAGING_RECEIVER, HazelcastReceiver.class.getName());
		addPlatformProperty(defaultOverrides, Platform.Property.MONITOR_PERFORMANCE, false);
		addPlatformProperty(defaultOverrides, Platform.Property.MONITORING_PATH, "/health");
		addPlatformProperty(defaultOverrides, Platform.Property.PASSWORD_POLICY_ERROR_MSSG_KEY,
				DefaultPasswordPolicy.ERROR_MSSG_KEY);
		addPlatformProperty(defaultOverrides, Platform.Property.PASSWORD_POLICY_REGEX, DefaultPasswordPolicy.REGEX);
		addPlatformProperty(defaultOverrides, Platform.Property.PLATFORM_CACHE_FOLDER, "platform");
		addPlatformProperty(defaultOverrides, Platform.Property.APPLICATION_DIR, "/applications");
		addPlatformProperty(defaultOverrides, Platform.Property.REPOSITORY_PATH, "repository");
		addPlatformProperty(defaultOverrides, Platform.Property.REPOSITORY_DEFAULT_DIGEST, "");
		addPlatformProperty(defaultOverrides, Platform.Property.REPOSITORY_CERT, StringUtils.EMPTY, Type.MULTILINE);
		addPlatformProperty(defaultOverrides, Platform.Property.REPOSITORY_SIGNATURE, StringUtils.EMPTY,
				Type.MULTILINE);
		addPlatformProperty(defaultOverrides, Platform.Property.REPOSITORY_TRUSTSTORE, StringUtils.EMPTY);
		addPlatformProperty(defaultOverrides, Platform.Property.REPOSITORY_TRUST_STORE_PASSWORD, StringUtils.EMPTY,
				Type.PASSWORD);
		addPlatformProperty(defaultOverrides, Platform.Property.REPOSITORY_VERIFY_SIGNATURE, true);
		addPlatformProperty(defaultOverrides, Platform.Property.SESSION_TIMEOUT, 1800);
		addPlatformProperty(defaultOverrides, Platform.Property.SESSION_FILTER, StringUtils.EMPTY, Type.MULTILINE);

		String sharedSecretFullName = PREFIX_PLATFORM + Platform.Property.SHARED_SECRET;
		Property sharedSecret = propertyHolder.getProperty(sharedSecretFullName);
		if (null == sharedSecret || defaultOverrides.containsKey(sharedSecretFullName)) {
			String defaultSecret = AuthTools.getRandomSalt(32);
			addPlatformProperty(defaultOverrides, Platform.Property.SHARED_SECRET, defaultSecret, Type.PASSWORD);
		}

		addPlatformProperty(defaultOverrides, Platform.Property.TEMPLATE_FOLDER, "/templates");
		addPlatformProperty(defaultOverrides, Platform.Property.TEMPLATE_PREFIX, "/template");
		addPlatformProperty(defaultOverrides, Platform.Property.TIME_ZONE, TimeZone.getDefault().getID());
		addPlatformProperty(defaultOverrides, Platform.Property.UPLOAD_DIR, "/uploads");
		addPlatformProperty(defaultOverrides, Platform.Property.VHOST_MODE, VHostMode.NAME_BASED.name());
		addPlatformProperty(defaultOverrides, Platform.Property.WRITE_DEBUG_FILES, Boolean.FALSE);
		addPlatformProperty(defaultOverrides, Platform.Property.XSS_PROTECT, Boolean.FALSE);
		addPlatformProperty(defaultOverrides, Platform.Property.XSS_ALLOWED_TAGS, "a href class style|div align style");

		if (!defaultOverrides.isEmpty()) {
			for (Object additionalProp : defaultOverrides.keySet()) {
				String prefixedName = (String) additionalProp;
				if (prefixedName.startsWith(PREFIX_PLATFORM)) {
					String value = defaultOverrides.getProperty(prefixedName);
					String name = prefixedName.substring(PREFIX_PLATFORM.length());
					boolean isMultiline = value.contains(StringUtils.LF);
					propertyHolder.addProperty(name, value, null, isMultiline ? Type.MULTILINE : Type.forObject(value));
					if (LOGGER.isDebugEnabled()) {
						LOGGER.debug("added optional property {}{} = {}", PREFIX_PLATFORM, name, value);
					}
				}
			}
		}

		if (finalize) {
			propertyHolder.setFinal();
		}
	}

	private String normalizePath(String segment, String... pathelements) {
		if (StringUtils.isNotBlank(segment) && StringUtils.isNoneBlank(pathelements)) {
			return Paths.get(segment, pathelements).normalize().toString();
		}
		return StringUtils.EMPTY;
	}

	/**
	 * Returns the dot-separated property-prefix for a node-property.
	 * 
	 * @param nodeId
	 *             the node id as returned by {@link org.appng.api.messaging.Messaging#getNodeId(org.appng.api.Environment)}
	 * 
	 * @return the dot-separated property-prefix
	 */
	public static String getNodePrefix(String nodeId) {
		return PREFIX_NODE + nodeId.replace('.', '_') + DOT;
	}

	/**
	 * Initializes the node configuration with the default values. The properties are added to the
	 * {@link PropertyHolder} this {@link PropertySupport} was created with.
	 * 
	 * @param finalize
	 *                           whether or not to call {@link PropertyHolder#setFinal()}
	 * 
	 * @see #PropertySupport(PropertyHolder)
	 */
	public void initNodeConfig(boolean finalize) {
		if (finalize) {
			propertyHolder.setFinal();
		}
	}

	private String addNodeProperty(String name, Object defaultValue) {
		return addNodeProperty(name, defaultValue, Type.forObject(defaultValue));
	}

	private String addNodeProperty(String name, Object defaultValue, Type type) {
		return addProperty(name, defaultValue, PREFIX_NODE, type);
	}

	static List<String> getSiteRelevantPlatformProps() {
		return Arrays
				.asList(Platform.Property.APPNG_DATA, Platform.Property.REPOSITORY_PATH,
						Platform.Property.PASSWORD_POLICY_REGEX, Platform.Property.PASSWORD_POLICY_ERROR_MSSG_KEY,
						Platform.Property.TEMPLATE_PREFIX, Platform.Property.MESSAGING_ENABLED)
				.stream().map(p -> PREFIX_PLATFORM + p).collect(Collectors.toList());
	}
}
