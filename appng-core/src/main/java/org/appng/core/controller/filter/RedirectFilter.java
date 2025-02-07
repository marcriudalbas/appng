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
package org.appng.core.controller.filter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.appng.api.Environment;
import org.appng.api.Platform;
import org.appng.api.Scope;
import org.appng.api.SiteProperties;
import org.appng.api.model.Properties;
import org.appng.api.model.Site;
import org.appng.api.support.environment.DefaultEnvironment;
import org.appng.xml.BuilderFactory;
import org.tuckey.web.filters.urlrewrite.Conf;
import org.tuckey.web.filters.urlrewrite.ConfHandler;
import org.tuckey.web.filters.urlrewrite.NormalRule;
import org.tuckey.web.filters.urlrewrite.Rule;
import org.tuckey.web.filters.urlrewrite.UrlRewriteFilter;
import org.tuckey.web.filters.urlrewrite.UrlRewriter;
import org.tuckey.web.filters.urlrewrite.substitution.FunctionReplacer;
import org.tuckey.web.filters.urlrewrite.utils.Log;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import lombok.extern.slf4j.Slf4j;

/**
 * A {@link Filter} extending <a href="http://www.tuckey.org/urlrewrite/">UrlRewriteFilter</a> that supports
 * configuration per {@link Site}.
 * 
 * @author Matthias Müller
 */
@Slf4j
public class RedirectFilter extends UrlRewriteFilter {

	private static ConcurrentMap<String, CachedUrlRewriter> REWRITERS = new ConcurrentHashMap<>();
	private int reloadIntervall;

	@Override
	public void init(final FilterConfig filterConfig) throws ServletException {
		this.reloadIntervall = Integer.parseInt(filterConfig.getInitParameter("confReloadCheckInterval"));
		Log.setLevel("slf4j");
		super.init(filterConfig);
	}

	class RedirectRule {
		private final Pattern pattern;
		private final String target;

		RedirectRule(NormalRule rule) {
			if (rule.getFrom().startsWith("^") && rule.getFrom().endsWith("$")) {
				this.pattern = Pattern.compile(rule.getFrom().substring(1, rule.getFrom().length() - 1));
			} else {
				this.pattern = Pattern.compile(rule.getFrom());
			}
			this.target = rule.getTo();
		}

		String apply(String content) {
			return pattern.matcher(content).replaceAll(target);
		}

		String getPattern() {
			return pattern.pattern();
		}

		String getTarget() {
			return target;
		}

	}

	class CachedUrlRewriter extends UrlRewriter {
		private final Long created;
		private final List<RedirectRule> redirectRules;

		public CachedUrlRewriter(UrlRewriteConfig conf, String domain, String jspType) {
			super(conf);
			created = System.currentTimeMillis();
			redirectRules = new ArrayList<>();
			for (Rule rule : conf.getRules()) {
				if (rule instanceof NormalRule) {
					NormalRule normalRule = (NormalRule) rule;
					if (normalRule.getToType().contains("redirect") && normalRule.getFrom().contains(jspType)
							&& !FunctionReplacer.containsFunction(normalRule.getTo())) {
						redirectRules.add(new RedirectRule(normalRule));
					}
				}
			}
		}

		public List<RedirectRule> getRedirectRules() {
			return redirectRules;
		}

		@Override
		public UrlRewriteConfig getConf() {
			return (UrlRewriteConfig) super.getConf();
		}

	}

	public static class UrlRewriteConfig extends Conf {

		public UrlRewriteConfig(InputStream is, String fileName, URL systemId)
				throws IOException, SAXException, ParserConfigurationException {
			super(null, is, fileName, systemId.toString(), false);
			processConfDoc(parseConfig(systemId));
			initialise();
			getLoadedDate().setTime(System.currentTimeMillis());
		}

		@Override
		/* Since org.tuckey.web.filters.urlrewrite.Conf is not designed for extension, we need a little hack here. */
		protected synchronized void loadDom(InputStream inputStream) {
			// do nothing, work is done in constructor
		}

		@Override
		@SuppressWarnings("unchecked")
		public List<Rule> getRules() {
			return super.getRules();
		}

	}

	public static Document parseConfig(URL resource) throws ParserConfigurationException, SAXException, IOException {
		String confSystemId = resource.toString();
		ConfHandler handler = new ConfHandler(confSystemId);

		DocumentBuilderFactory factory = BuilderFactory.documentBuilderFactory();
		factory.setValidating(true);
		factory.setNamespaceAware(true);
		factory.setXIncludeAware(true);
		factory.setIgnoringComments(true);
		factory.setIgnoringElementContentWhitespace(true);

		DocumentBuilder parser = factory.newDocumentBuilder();
		parser.setErrorHandler(handler);
		parser.setEntityResolver(handler);
		Document doc = parser.parse(resource.openStream(), confSystemId);

		Element rootElement = doc.getDocumentElement();
		NodeList children = rootElement.getChildNodes();
		List<Node> deprecatedNodes = new ArrayList<>();
		for (int i = 0; i < children.getLength(); i++) {
			Node node = children.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE && !"rule".equals(node.getNodeName())) {
				NodeList rulesNode = node.getChildNodes();
				for (int j = 0; j < rulesNode.getLength(); j++) {
					Node ruleNode = rulesNode.item(j);
					if (ruleNode.getNodeType() == Node.ELEMENT_NODE && "rule".equals(ruleNode.getNodeName())) {
						rootElement.appendChild(ruleNode);
					}
				}
				deprecatedNodes.add(node);
			}
		}
		for (Node node : deprecatedNodes) {
			rootElement.removeChild(node);
		}
		return doc;
	}

	@Override
	protected UrlRewriter getUrlRewriter(ServletRequest request, ServletResponse response, FilterChain chain) {
		DefaultEnvironment env = EnvironmentFilter.environment();
		Site site = env.getSite();
		String jspType = getJspType(env);

		if (null != site) {
			boolean reload = true;
			String siteName = site.getName();
			if (REWRITERS.containsKey(siteName)) {
				CachedUrlRewriter cachedUrlRewriter = REWRITERS.get(siteName);
				long ageMillis = (System.currentTimeMillis() - cachedUrlRewriter.created);
				reload = ageMillis > reloadIntervall;
				LOGGER.trace("found existing CachedUrlRewriter for site {}, age: {}ms, reload required: {}", siteName,
						ageMillis, reload);
			}

			if (reload) {
				File confFile = getConfFile(site);
				if (confFile.exists()) {
					if (confFile.canRead()) {
						try (FileInputStream is = new FileInputStream(confFile)) {
							UrlRewriteConfig conf = new UrlRewriteConfig(is, confFile.getName(),
									confFile.toURI().toURL());
							checkConf(conf);
							if (conf.isOk()) {
								CachedUrlRewriter cachedUrlRewriter = new CachedUrlRewriter(conf, site.getDomain(),
										jspType);
								REWRITERS.put(siteName, cachedUrlRewriter);
								LOGGER.debug("reloaded config for site {} from {}, {} rules found", siteName, confFile,
										conf.getRules().size());
							} else {
								LOGGER.warn("invalid config-file for site '{}': {}", siteName, confFile);
							}
						} catch (IOException | SAXException | ParserConfigurationException e) {
							LOGGER.error("error processing {}", confFile);
						}
					} else {
						LOGGER.warn("Can not read {}, please check file permissions!", confFile.getAbsolutePath());
					}
				} else {
					LOGGER.debug("Configuration file does not exist: {}", confFile);
				}
			}
			return REWRITERS.get(siteName);
		}
		return null;
	}

	public static List<RedirectRule> getRedirectRules(String siteName) {
		return REWRITERS.containsKey(siteName) ? REWRITERS.get(siteName).getRedirectRules() : null;
	}

	private static String getJspType(Environment env) {
		Properties platformProperties = env.getAttribute(Scope.PLATFORM, Platform.Environment.PLATFORM_CONFIG);
		return platformProperties.getString(Platform.Property.JSP_FILE_TYPE);
	}

	private static File getConfFile(Site site) {
		String rootPath = site.getProperties().getString(SiteProperties.SITE_ROOT_DIR);
		String rewriteConfig = site.getProperties().getString(SiteProperties.REWRITE_CONFIG);
		return Paths.get(rootPath, rewriteConfig).toAbsolutePath().toFile();
	}
}
