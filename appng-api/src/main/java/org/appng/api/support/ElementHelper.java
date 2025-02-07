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

import static org.appng.api.Scope.REQUEST;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.namespace.QName;

import org.apache.commons.lang3.StringUtils;
import org.appng.api.ApplicationConfigProvider;
import org.appng.api.DataContainer;
import org.appng.api.Environment;
import org.appng.api.FieldProcessor;
import org.appng.api.MessageParam;
import org.appng.api.Options;
import org.appng.api.ParameterSupport;
import org.appng.api.Path;
import org.appng.api.PermissionOwner;
import org.appng.api.PermissionProcessor;
import org.appng.api.Platform;
import org.appng.api.ProcessingException;
import org.appng.api.Scope;
import org.appng.api.Session;
import org.appng.api.SiteProperties;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.api.support.environment.EnvironmentKeys;
import org.appng.api.support.environment.ScopedEnvironment;
import org.appng.el.ExpressionEvaluator;
import org.appng.xml.platform.BeanOption;
import org.appng.xml.platform.Condition;
import org.appng.xml.platform.Config;
import org.appng.xml.platform.Data;
import org.appng.xml.platform.DataConfig;
import org.appng.xml.platform.FieldDef;
import org.appng.xml.platform.Link;
import org.appng.xml.platform.Linkable;
import org.appng.xml.platform.Linkmode;
import org.appng.xml.platform.Linkpanel;
import org.appng.xml.platform.Messages;
import org.appng.xml.platform.MetaData;
import org.appng.xml.platform.Navigation;
import org.appng.xml.platform.NavigationItem;
import org.appng.xml.platform.OpenapiAction;
import org.appng.xml.platform.OptionGroup;
import org.appng.xml.platform.PageConfig;
import org.appng.xml.platform.Param;
import org.appng.xml.platform.Params;
import org.appng.xml.platform.Result;
import org.appng.xml.platform.Resultset;
import org.appng.xml.platform.Selection;
import org.appng.xml.platform.SelectionGroup;
import org.appng.xml.platform.Template;
import org.appng.xml.platform.ValidationGroups;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.util.ClassUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class offering methods for proper initialization of {@link Linkpanel}s, {@link Link}s, {@link Navigation}/
 * {@link NavigationItem}s, {@link BeanOption}s, {@link Param}s etc. <br/>
 * Also wraps an {@link Environment} and an {@link ExpressionEvaluator} to evaluate expressions.
 * 
 * @author Matthias Müller
 */
@Slf4j
public class ElementHelper {

	private static final String APP = "APP";
	private static final String SITE = "SITE";
	private static final String SESSION = "SESSION";

	private static final String SLASH = "/";

	public static final String INTERNAL_ERROR = "internal.error";

	private Application application;
	private Environment environment;
	private Site site;

	private ExpressionEvaluator expressionEvaluator;

	public ElementHelper(Site site, Application application) {
		this.application = application;
		this.site = site;
	}

	public ElementHelper(Environment environment, Site site, Application application,
			ExpressionEvaluator expressionEvaluator) {
		this.site = site;
		this.application = application;
		this.environment = environment;
		this.expressionEvaluator = initExpressionEvaluator(expressionEvaluator);
	}

	public ExpressionEvaluator initExpressionEvaluator(ExpressionEvaluator other) {
		if (null != other) {
			if (null != site) {
				other.setVariable(SITE, site.getProperties().getPlainProperties());
			}
			if (null != application) {
				other.setVariable(APP, application.getProperties().getPlainProperties());
			}
			if (null != environment) {
				ScopedEnvironment sessionEnv = environment.getEnvironment(Scope.SESSION);
				if (null != sessionEnv) {
					other.setVariable(SESSION, sessionEnv.getContainer());
				}
			}
		}
		return other;
	}

	void initLinkpanel(ApplicationRequest applicationRequest, Path pathInfo, DataConfig dsConfig,
			ParameterSupport parameterSupport) {
		List<Linkpanel> linkpanel = dsConfig.getLinkpanel();
		if (null != linkpanel) {
			List<Linkpanel> out = null;
			if (null != linkpanel) {
				out = new ArrayList<>();
				for (Linkpanel panel : linkpanel) {
					Linkpanel outPanel = initLinkpanel(applicationRequest, pathInfo, panel, parameterSupport);
					if (null != outPanel) {
						out.add(outPanel);
					}
				}
				linkpanel.clear();
				linkpanel.addAll(out);
			}
		}
	}

	private Linkpanel initLinkpanel(ApplicationRequest request, Path pathInfo, Linkpanel panel,
			ParameterSupport parameterSupport) {
		Linkpanel outPanel = null;
		PermissionProcessor permissionProcessor = request.getPermissionProcessor();
		if (null != panel && permissionProcessor.hasPermissions(new PermissionOwner(panel))) {
			outPanel = new Linkpanel();
			String panelId = panel.getId();
			outPanel.setId(panelId);
			outPanel.setLocation(panel.getLocation());
			List<Linkable> links = panel.getLinks();
			int linkCount = 1;
			String servicePath = pathInfo.getServicePath();
			String guiPath = pathInfo.getGuiPath();
			for (Linkable linkable : links) {
				boolean hasPermission = request.getPermissionProcessor().hasPermissions(new PermissionOwner(linkable));
				if (hasPermission) {
					Condition condition = linkable.getCondition();
					ExpressionEvaluator linkExpressionEvaluator = initExpressionEvaluator(
							parameterSupport.getExpressionEvaluator());
					boolean doInclude = expressionMatchesOrContainsCurrent(condition, linkExpressionEvaluator);
					boolean showDisabled = Boolean.TRUE.equals(linkable.isShowDisabled());

					if (doInclude || showDisabled) {
						linkable.setCondition(condition);
						request.setLabel(linkable.getLabel());
						request.setLabel(linkable.getConfirmation());
						outPanel.getLinks().add(linkable);

						if (linkable instanceof Link) {
							Link link = (Link) linkable;
							if (link.getId() == null) {
								link.setId(panelId + "[" + linkCount + "]");
							}
							String currentTarget = link.getTarget();
							String newTarget = parameterSupport.replaceParameters(currentTarget);
							if (Linkmode.WEBSERVICE.equals(link.getMode())) {
								newTarget = servicePath + SLASH + site.getName() + SLASH + application.getName() + SLASH
										+ Platform.SERVICE_TYPE_WEBSERVICE + SLASH + newTarget;
							}
							if (Linkmode.REST.equals(link.getMode())) {
								newTarget = servicePath + SLASH + site.getName() + SLASH + application.getName() + SLASH
										+ Platform.SERVICE_TYPE_REST + SLASH + newTarget;
							}
							StringBuilder proposedPath = new StringBuilder();
							proposedPath.append(guiPath).append(pathInfo.getOutputPrefix()).append(SLASH);
							proposedPath.append(site.getName()).append(SLASH).append(application.getName());
							proposedPath.append(newTarget);
							if (pathInfo.isPathSelected(proposedPath.toString())) {
								linkable.setActive(Boolean.TRUE.toString());
							}
							link.setTarget(newTarget);
						} else if (linkable instanceof OpenapiAction) {
							OpenapiAction actionLink = (OpenapiAction) linkable;
							StringBuilder target = new StringBuilder();
							target.append(site.getProperties().getString(SiteProperties.SERVICE_PATH));
							target.append("/");
							target.append(site.getName());
							target.append("/");
							target.append(application.getName());
							target.append("/rest/openapi/action/");
							target.append(actionLink.getEventId());
							target.append("/");
							target.append(actionLink.getId());
							actionLink.setTarget(target.toString());
						}

					}
				}
				linkCount++;
			}
		}
		return outPanel;
	}

	private boolean expressionMatchesOrContainsCurrent(Condition condition,
			ExpressionEvaluator conditionExpressionEvaluator) {
		if (null != condition) {
			String expression = condition.getExpression();
			if (StringUtils.isNotBlank(expression) && !expression.contains(AdapterBase.CURRENT)) {
				return conditionMatches(conditionExpressionEvaluator, condition);
			}
		}
		return true;
	}

	public void initNavigation(ApplicationRequest applicationRequest, Path pathInfo, PageConfig pageConfig) {
		ParameterSupport parameterSupport = applicationRequest.getParameterSupportDollar();
		Linkpanel pageLinks = initLinkpanel(applicationRequest, pathInfo, pageConfig.getLinkpanel(), parameterSupport);

		Linkpanel navigation = applicationRequest.getApplicationConfig().getApplicationRootConfig().getNavigation();
		if (null != navigation) {
			navigation = initLinkpanel(applicationRequest, pathInfo, navigation, parameterSupport);
			if (!(null == pageLinks || null == navigation)) {
				for (Linkable link : navigation.getLinks()) {
					pageLinks.getLinks().add(link);
				}
				pageConfig.setLinkpanel(pageLinks);
			} else {
				pageConfig.setLinkpanel(navigation);
			}
		}
	}

	/**
	 * Builds {@link Options} from the given list of {@link BeanOption}s, without evaluation of parameter placeholders.
	 * 
	 * @param  beanOptions
	 *                     some {@link BeanOption}s
	 * 
	 * @return             the {@link Options}
	 * 
	 * @see                #initOptions(List)
	 */
	Options getOptions(List<BeanOption> beanOptions) {
		OptionsImpl options = new OptionsImpl();
		if (null != beanOptions) {
			for (BeanOption beanOption : beanOptions) {
				OptionImpl opt = new OptionImpl(beanOption.getName());
				Map<QName, String> attributes = beanOption.getOtherAttributes();
				for (Entry<QName, String> entry : attributes.entrySet()) {
					String optionName = entry.getKey().getLocalPart();
					opt.addAttribute(optionName, entry.getValue());
				}
				options.addOption(opt);
			}
		}
		return options;
	}

	/**
	 * Performs parameter-replacement for the given {@link BeanOption}s
	 * 
	 * @param beanOptions
	 *                    some {@link BeanOption}s
	 */
	void initOptions(List<BeanOption> beanOptions) {
		if (null != beanOptions) {
			for (BeanOption beanOption : beanOptions) {
				Map<QName, String> attributes = beanOption.getOtherAttributes();
				for (Entry<QName, String> entry : attributes.entrySet()) {
					String value = expressionEvaluator.evaluate(entry.getValue(), String.class);
					entry.setValue(value);
				}
			}
		}
	}

	void setSelectionTitles(Data data, ApplicationRequest applicationRequest) {
		setSelectionTitles(data.getSelections(), applicationRequest);
		for (SelectionGroup group : data.getSelectionGroups()) {
			setSelectionTitles(group.getSelections(), applicationRequest);
		}
	}

	private void setSelectionTitles(List<Selection> selections, ApplicationRequest applicationRequest) {
		for (Selection selection : selections) {
			applicationRequest.setLabel(selection.getTitle());
			applicationRequest.setLabel(selection.getTooltip());
			for (OptionGroup optionGroup : selection.getOptionGroups()) {
				applicationRequest.setLabel(optionGroup.getLabel());
			}
		}
	}

	void processConfig(ApplicationConfigProvider applicationConfigProvider, ApplicationRequest applicationRequest,
			DataConfig config, Map<String, String> parameters) {
		MetaData metaData = getFilteredMetaData(applicationRequest, config.getMetaData(), false);
		config.setMetaData(metaData);
		// DO NOT evaluate hidden and readOnly here!!
		Path path = applicationRequest.getEnvironment().getAttribute(REQUEST, EnvironmentKeys.PATH_INFO);
		initLinkpanel(applicationRequest, path, config, new DollarParameterSupport(parameters));
		addTemplates(applicationConfigProvider, config);
	}

	MetaData getFilteredMetaData(ApplicationRequest request, MetaData metaData, boolean write) {
		MetaData result = new MetaData();
		if (null != metaData) {
			result.setBinding(metaData.getBinding());
			result.setResultSelector(metaData.getResultSelector());
			result.setBindClass(metaData.getBindClass());
			result.setValidation(metaData.getValidation());
			List<FieldDef> fieldDefinitions = metaData.getFields();
			List<FieldDef> fields = filterFieldDefinitions(request, fieldDefinitions, write);
			result.getFields().addAll(fields);
		}
		return result;
	}

	/**
	 * removes those {@link FieldDef}s from the given fieldDefinitions for whom the user has no permissions and returns
	 * a new list containing only the allowed {@link FieldDef}s
	 */
	private List<FieldDef> filterFieldDefinitions(ApplicationRequest request, List<FieldDef> fieldDefinitions,
			boolean write) {
		List<FieldDef> fields = new ArrayList<>();
		PermissionProcessor permissionProcessor = request.getPermissionProcessor();
		if (null != fieldDefinitions) {
			for (FieldDef fieldDef : fieldDefinitions) {
				boolean hasPermission = false;
				if (write) {
					hasPermission = permissionProcessor.hasWritePermission(fieldDef);
				} else {
					hasPermission = permissionProcessor.hasReadPermission(fieldDef);
				}
				if (hasPermission) {
					if (!write) {
						request.setLabel(fieldDef.getLabel());
						request.setLabel(fieldDef.getTooltip());
					}
					Condition condition = fieldDef.getCondition();
					boolean isValid = expressionMatchesOrContainsCurrent(condition, expressionEvaluator);
					if (isValid) {
						String format = fieldDef.getFormat();
						if (null != format) {
							format = expressionEvaluator.evaluate(format, String.class);
							fieldDef.setFormat(format);
						}
						filterFieldDefinitions(request, fieldDef.getFields(), write);
						fields.add(fieldDef);
					}
				}
			}
		}
		return fields;
	}

	void addTemplates(ApplicationConfigProvider applicationConfigProvider, Config config) {
		List<Template> templates = config.getTemplates();
		if (null != templates) {
			applicationConfigProvider.getApplicationRootConfig().getConfig().getTemplates().addAll(templates);
		}
	}

	Map<String, String> initializeParameters(String reference, ApplicationRequest applicationRequest,
			ParameterSupport parameterSupport, Params referenceParams, Params executionParams)
			throws ProcessingException {
		Map<String, String> executionParameters = new HashMap<>();
		Map<String, String> referenceParameters = new HashMap<>();
		if (null != referenceParams) {
			for (Param p : referenceParams.getParam()) {
				String newValue = parameterSupport.replaceParameters(p.getValue());
				if (StringUtils.isEmpty(newValue) && StringUtils.isNotEmpty(p.getDefault())) {
					newValue = p.getDefault();
				}
				p.setValue(newValue);
				if (null != newValue) {
					referenceParameters.put(p.getName(), newValue);
				}
			}

			if (null != executionParams) {
				for (Param p : executionParams.getParam()) {
					String value = p.getValue();
					if (StringUtils.isEmpty(value)) {
						value = referenceParameters.get(p.getName());
						if (StringUtils.isEmpty(value) && StringUtils.isNotEmpty(p.getDefault())) {
							value = p.getDefault();
						}
					}
					p.setValue(value);
					if (null != value) {
						executionParameters.put(p.getName(), value);
					}
				}
			}
		}
		if (applicationRequest.isPost()) {
			Map<String, List<String>> parametersList = applicationRequest.getParametersList();
			for (String param : parametersList.keySet()) {
				String postParam = StringUtils.join(parametersList.get(param), "|");
				String existingValue = executionParameters.get(param);
				if (null == existingValue) {
					executionParameters.put(param, postParam);
				} else {
					if (!existingValue.equals(postParam)) {
						String message = String.format(
								"the parameter '%s' is ambiguous, since it's a execution parameter for '%s' (value: '%s') "
										+ "and also POST-parameter (value: '%s''). Avoid such overlapping parameters!",
								param, reference, existingValue, postParam);
						LOGGER.warn(message);
						// TODO APPNG-442
						// throwing ProcessingException may be too aggressive here
						// throw new ProcessingException(message, null);
					}
				}
			}
		}
		this.expressionEvaluator = initExpressionEvaluator(new ExpressionEvaluator(executionParameters));
		expressionEvaluator.setVariable(ApplicationRequest.I18N_VAR, new I18n(applicationRequest));

		return executionParameters;
	}

	/**
	 * Returns the messages for the current session.
	 * 
	 * @param  environment
	 *                     the current {@link Environment}
	 * 
	 * @return             the messages for the current session, if any
	 */
	public static Messages addMessages(Environment environment, Messages messages) {
		Messages messagesFromSession = environment.getAttribute(Scope.SESSION, Session.Environment.MESSAGES);
		if (messages.getMessageList().size() > 0) {
			if (null == messagesFromSession) {
				messagesFromSession = new Messages();
			}
			messagesFromSession.getMessageList().addAll(messages.getMessageList());
			environment.setAttribute(Scope.SESSION, Session.Environment.MESSAGES, messagesFromSession);
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("messages : {}", messagesFromSession.getMessageList());
			}
		}
		return messagesFromSession;
	}

	/**
	 * Returns the messages for the current session.
	 * 
	 * @return the messages for the current session, if any
	 */
	public Messages removeMessages() {
		return removeMessagesInternal(environment);
	}

	public Messages removeMessages(Environment environment) {
		return removeMessagesInternal(environment);
	}

	private Messages removeMessagesInternal(Environment other) {
		Messages messages = other.removeAttribute(Scope.SESSION, Session.Environment.MESSAGES);
		if (LOGGER.isDebugEnabled() && null != messages) {
			LOGGER.debug("removed messages : {}", messages.getMessageList());
		}
		return messages;
	}

	/**
	 * @deprecated use {@link #hasMessages()}, since an {@link Environment} is wrapped here
	 */
	@Deprecated
	public boolean hasMessages(Environment environment) {
		return hasMessages();
	}

	/**
	 * Returns the messages for the current session.
	 * 
	 * @return the messages for the current session, if any
	 */
	public Messages getMessages() {
		return getMessagesInternal(environment);
	}

//	public Messages getMessages(Environment environment) {
//		return getMessagesInternal(environment);
//	}

	public Messages getMessagesInternal(Environment other) {
		Messages messages = other.getAttribute(Scope.SESSION, Session.Environment.MESSAGES);
		if (LOGGER.isDebugEnabled() && null != messages) {
			LOGGER.debug("retrieved messages : {}", messages.getMessageList());
		}
		return messages;
	}

	public boolean hasMessages() {
		return null != getMessages();
	}

	ExpressionEvaluator getExpressionEvaluator() {
		return expressionEvaluator;
	}

	public boolean conditionMatches(Condition condition) {
		return conditionMatches(getExpressionEvaluator(), condition);
	}

	public static boolean conditionMatches(ExpressionEvaluator expressionEvaluator, Condition condition) {
		if (null == condition || StringUtils.isBlank(condition.getExpression())) {
			return true;
		}
		String expression = condition.getExpression();
		boolean evaluated = expressionEvaluator.evaluate(expression);
		LOGGER.debug("{} = {}", expression, evaluated);
		return evaluated;
	}

	public void processDataContainer(org.appng.api.Request applicationRequest, DataContainer container,
			String callerName) throws ClassNotFoundException, ProcessingException {

		Data data = container.getWrappedData();
		FieldProcessor fieldProcessor = container.getFieldProcessor();

		ResultServiceImpl resultService = new ResultServiceImpl(getExpressionEvaluator());
		resultService.setSite(site);
		resultService.setApplication(application);
		resultService.setConversionService(applicationRequest);
		resultService.setEnvironment(applicationRequest.getEnvironment());
		MessageSource messageSource = this.application.getBean(MessageSource.class);
		resultService.setMessageSource(messageSource);
		resultService.afterPropertiesSet();
		if (container.isSingleResult()) {
			Object item = container.getItem();
			verifyItemType(fieldProcessor.getMetaData(), item, callerName);
			Result result = resultService.getResult(fieldProcessor, item);
			evaluateActionFieldConditions(fieldProcessor.getFields());
			data.setResult(result);
		} else {
			Resultset resultset = null;
			Collection<?> items = null;
			if (null != container.getPage()) {
				Page<?> page = container.getPage();
				resultset = resultService.getResultset(fieldProcessor, page);
				items = page.getContent();
			} else if (null != container.getItems()) {
				resultset = resultService.getResultset(fieldProcessor, container.getItems());
				items = container.getItems();
			} else {
				throw new ProcessingException("DataContainer must either have a page or a Collection of items",
						fieldProcessor);
			}
			if (!items.isEmpty()) {
				verifyItemType(fieldProcessor.getMetaData(), items.iterator().next(), callerName);
			}
			data.setResultset(resultset);
		}
	}

	private void evaluateActionFieldConditions(List<FieldDef> fields) {
		// for actions, FE doesn't like ${..} in expressions, these must be 'true' or 'false'
		for (FieldDef fieldDef : fields) {
			Condition condition = fieldDef.getCondition();
			if (null != condition && StringUtils.isNotBlank(condition.getExpression())) {
				condition.setExpression(expressionEvaluator.getString(condition.getExpression()));
			}
			evaluateActionFieldConditions(fieldDef.getFields());
		}
	}

	private void verifyItemType(MetaData metaData, Object item, String dsId) throws ClassNotFoundException {
		if (null == item) {
			throw new IllegalArgumentException("datasource " + dsId + " did not return an item!");
		}
		Class<?> bindClass = ClassUtils.forName(metaData.getBindClass(), site.getSiteClassLoader());
		Class<? extends Object> itemClass = item.getClass();
		if (!bindClass.isAssignableFrom(itemClass)) {
			String message = "the object of type '" + itemClass.getName() + "' returned by '" + dsId
					+ "' is not of the desired type '" + metaData.getBindClass() + "' as defined in the meta-data!";
			throw new IllegalArgumentException(message);
		}
	}

	public boolean isMessageParam(Object o) {
		return null != o && (o instanceof MessageParam) && ((MessageParam) o).getMessageKey() != null;
	}

	public Class<?>[] getValidationGroups(MetaData metaData, Object bindObject) {
		List<Class<?>> groups = new ArrayList<>();
		ValidationGroups validationGroups = metaData.getValidation();
		if (null != validationGroups) {
			getExpressionEvaluator().setVariable(AdapterBase.CURRENT, bindObject);
			for (ValidationGroups.Group group : new ArrayList<ValidationGroups.Group>(validationGroups.getGroups())) {
				String expression = group.getCondition();
				Condition condition = new Condition();
				condition.setExpression(expression);
				if (StringUtils.isBlank(expression) || conditionMatches(condition)) {
					try {
						groups.add(site.getSiteClassLoader().loadClass(group.getClazz()));
					} catch (ClassNotFoundException e) {
						LOGGER.error("validation group {} not found!", group.getClazz());
					}
				} else {
					validationGroups.getGroups().remove(group);
				}
			}
		}
		return groups.toArray(new Class<?>[groups.size()]);
	}

	/**
	 * @deprecated use {@link #getOutputPrefix()}, since an {@link Environment} is wrapped here
	 */
	@Deprecated
	public String getOutputPrefix(Environment env) {
		return getOutputPrefix();
	}

	public String getOutputPrefix() {
		if (Boolean.TRUE.equals(environment.removeAttribute(REQUEST, EnvironmentKeys.EXPLICIT_FORMAT))) {
			Path pathInfo = environment.getAttribute(REQUEST, EnvironmentKeys.PATH_INFO);
			StringBuilder prefix = new StringBuilder().append(pathInfo.getGuiPath());
			prefix.append(pathInfo.getOutputPrefix()).append(Path.SEPARATOR).append(pathInfo.getSiteName());
			return prefix.append(Path.SEPARATOR).toString();
		}
		return null;
	}

}
