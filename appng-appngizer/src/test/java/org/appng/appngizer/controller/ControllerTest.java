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
package org.appng.appngizer.controller;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.appng.api.Platform;
import org.appng.api.Scope;
import org.appng.api.VHostMode;
import org.appng.api.model.Property;
import org.appng.api.model.SimpleProperty;
import org.appng.api.support.PropertyHolder;
import org.appng.api.support.environment.DefaultEnvironment;
import org.appng.appngizer.model.xml.PackageType;
import org.appng.appngizer.model.xml.Repository;
import org.appng.appngizer.model.xml.RepositoryMode;
import org.appng.appngizer.model.xml.RepositoryType;
import org.appng.core.controller.PlatformStartup;
import org.appng.core.model.RepositoryCacheFactory;
import org.appng.core.service.CoreService;
import org.appng.core.service.PropertySupport;
import org.appng.testsupport.validation.WritingXmlValidator;
import org.appng.testsupport.validation.XPathDifferenceHandler;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.xml.transform.StringResult;
import org.xml.sax.SAXException;

@RunWith(SpringRunner.class)
@ContextConfiguration(locations = { "classpath:test-context.xml" }, initializers = ControllerTest.Initializer.class)
@DirtiesContext
@WebAppConfiguration
public abstract class ControllerTest {

	static class Initializer implements ApplicationContextInitializer<AbstractApplicationContext> {
		public void initialize(AbstractApplicationContext applicationContext) {
			applicationContext.setDisplayName(PlatformStartup.APPNG_CONTEXT);
		}
	}

	@Autowired
	protected WebApplicationContext wac;

	@Autowired
	protected Jaxb2Marshaller marshaller;

	protected MockMvc mockMvc;

	protected XPathDifferenceHandler differenceListener;

	static boolean platformInitialized = false;

	void installApplication() throws Exception {
		Repository repo = new Repository();
		repo.setName("local");
		repo.setEnabled(true);
		repo.setStrict(false);
		repo.setPublished(false);
		repo.setMode(RepositoryMode.ALL);
		repo.setType(RepositoryType.LOCAL);
		repo.setUri(getUri());

		postAndVerify("/repository", null, repo, HttpStatus.CREATED);

		org.appng.appngizer.model.xml.Package install = new org.appng.appngizer.model.xml.Package();
		install.setName("demo-application");
		install.setVersion("1.5.3");
		install.setTimestamp("2013-01-13-1303");
		install.setDisplayName("Demo Application");
		install.setType(PackageType.APPLICATION);

		putAndVerify("/repository/local/install", null, install, HttpStatus.OK);
	}

	protected String getUri() throws IOException {
		File path = new File(new File("").getAbsolutePath(), "../appng-core/src/test/resources/zip");
		File repoFolder = new File("target/repo");
		FileUtils.copyDirectory(path, repoFolder);
		return repoFolder.toURI().toString();
	}

	@Before
	public void setup() {
		Locale.setDefault(Locale.ENGLISH);
		this.differenceListener = new XPathDifferenceHandler();
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
		if (!platformInitialized) {
			Map<Object, Object> platformEnv = new ConcurrentHashMap<>();
			platformEnv.put(Platform.Environment.APPNG_VERSION, "1.21.x");
			List<? extends Property> properties = Arrays.asList(
					new SimpleProperty(PropertySupport.PREFIX_PLATFORM + Platform.Property.SHARED_SECRET, "4711"),
					new SimpleProperty(PropertySupport.PREFIX_PLATFORM + Platform.Property.VHOST_MODE,
							VHostMode.NAME_BASED.name()));
			platformEnv.put(Platform.Environment.PLATFORM_CONFIG,
					new PropertyHolder(PropertySupport.PREFIX_PLATFORM, properties));
			this.wac.getServletContext().setAttribute(Scope.PLATFORM.name(), platformEnv);
			RepositoryCacheFactory.init(null, null, null, null, false);
			Properties defaultOverrides = new Properties();
			defaultOverrides.put(PropertySupport.PREFIX_PLATFORM + Platform.Property.MESSAGING_ENABLED, "false");
			wac.getBean(CoreService.class).initPlatformConfig(defaultOverrides, "target/webapps/ROOT", false, true,
					false);
			DefaultEnvironment.initGlobal(this.wac.getServletContext());
			platformInitialized = true;
		}
	}

	@AfterClass
	public static void shutDown() {
		platformInitialized = false;
	}

	protected MockHttpServletResponse postAndVerify(String uri, String controlSource, Object content, HttpStatus status)
			throws Exception {
		MockHttpServletRequestBuilder post = MockMvcRequestBuilders.post(new URI(uri));
		return sendAndVerify(post, content, status, controlSource);
	}

	protected MockHttpServletResponse putAndVerify(String uri, String controlSource, Object content, HttpStatus status)
			throws Exception {
		MockHttpServletRequestBuilder post = MockMvcRequestBuilders.put(new URI(uri));
		return sendAndVerify(post, content, status, controlSource);
	}

	protected MockHttpServletResponse getAndVerify(String uri, String controlSource, HttpStatus status)
			throws Exception {
		MockHttpServletRequestBuilder get = MockMvcRequestBuilders.get(new URI(uri));
		return verify(get, status, controlSource);
	}

	protected MockHttpServletResponse deleteAndVerify(String uri, String controlSource, HttpStatus status)
			throws Exception {
		return deleteAndVerify(uri, controlSource, null, status);
	}

	protected MockHttpServletResponse deleteAndVerify(String uri, String controlSource, Object content,
			HttpStatus status) throws Exception {
		MockHttpServletRequestBuilder delete = MockMvcRequestBuilders.delete(new URI(uri));
		return sendAndVerify(delete, content, status, controlSource);
	}

	protected MockHttpServletResponse sendAndVerify(MockHttpServletRequestBuilder builder, Object content,
			HttpStatus status, String controlSource)
			throws Exception, UnsupportedEncodingException, SAXException, IOException {
		if (null != content) {
			builder.contentType(MediaType.TEXT_XML_VALUE);
			StringResult result = new StringResult();
			marshaller.marshal(content, result);
			builder.content(result.toString());
		}
		return verify(builder, status, controlSource);
	}

	protected MockHttpServletResponse verify(MockHttpServletRequestBuilder builder, HttpStatus status,
			String controlSource) throws Exception, UnsupportedEncodingException, SAXException, IOException {
		builder.header(HttpHeaders.AUTHORIZATION, "Bearer 4711");
		MvcResult mvcResult = mockMvc.perform(builder).andReturn();
		MockHttpServletResponse response = mvcResult.getResponse();
		Assert.assertEquals("HTTP status does not match	", status.value(), response.getStatus());
		if (HttpStatus.OK.equals(status)) {
			Assert.assertEquals("Content type does not match ", MediaType.TEXT_XML_VALUE, response.getContentType());
		}
		validate(response.getContentAsString(), controlSource);
		return response;
	}

	protected List<Property> getPlatformProperties(String prefix) {
		List<Property> platformProperties = new ArrayList<>();
		platformProperties.add(new SimpleProperty(prefix + Platform.Property.VHOST_MODE, VHostMode.NAME_BASED.name()));
		platformProperties.add(new SimpleProperty(prefix + Platform.Property.LOCALE, "en"));
		platformProperties.add(new SimpleProperty(prefix + Platform.Property.TIME_ZONE, "Europe/Berlin"));
		platformProperties.add(new SimpleProperty(prefix + Platform.Property.PLATFORM_ROOT_PATH, "target/ROOT"));
		platformProperties.add(new SimpleProperty(prefix + Platform.Property.CACHE_FOLDER, "cache"));
		platformProperties.add(new SimpleProperty(prefix + Platform.Property.APPLICATION_CACHE_FOLDER, "application"));
		platformProperties.add(new SimpleProperty(prefix + Platform.Property.PLATFORM_CACHE_FOLDER, "platform"));
		platformProperties.add(new SimpleProperty(prefix + Platform.Property.MESSAGING_RECEIVER, "dummy"));
		platformProperties.add(new SimpleProperty(prefix + Platform.Property.PLATFORM_ROOT_PATH, ""));
		return platformProperties;
	}

	protected <T> void validate(String response, String controlSource) throws SAXException, IOException {
		if (StringUtils.isNoneBlank(response, controlSource)) {
			WritingXmlValidator.validateXml(response, controlSource, differenceListener);
		}
	}

	protected void assertLocation(String location, HttpServletResponse response) {
		Assert.assertEquals(location, response.getHeader(HttpHeaders.LOCATION));
	}

}