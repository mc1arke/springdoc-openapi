/*
 *
 *  *
 *  *  * Copyright 2019-2020 the original author or authors.
 *  *  *
 *  *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  *  * you may not use this file except in compliance with the License.
 *  *  * You may obtain a copy of the License at
 *  *  *
 *  *  *      https://www.apache.org/licenses/LICENSE-2.0
 *  *  *
 *  *  * Unless required by applicable law or agreed to in writing, software
 *  *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  * See the License for the specific language governing permissions and
 *  *  * limitations under the License.
 *  *
 *
 */

package org.springdoc.webmvc.api;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.core.util.PathUtils;
import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.api.AbstractOpenApiResource;
import org.springdoc.core.AbstractRequestService;
import org.springdoc.core.GenericResponseService;
import org.springdoc.core.OpenAPIService;
import org.springdoc.core.OperationService;
import org.springdoc.core.SpringDocConfigProperties;
import org.springdoc.core.SpringDocProviders;
import org.springdoc.core.customizers.OpenApiCustomiser;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.fn.RouterOperation;
import org.springdoc.core.providers.ActuatorProvider;
import org.springdoc.core.providers.RepositoryRestResourceProvider;
import org.springdoc.core.providers.SecurityOAuth2Provider;
import org.springdoc.core.providers.SpringWebProvider;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import static org.springdoc.core.Constants.DEFAULT_GROUP_NAME;
import static org.springdoc.core.providers.ActuatorProvider.getTag;

/**
 * The type Web mvc open api resource.
 * @author bnasslahsen, Azige
 */
public abstract class OpenApiResource extends AbstractOpenApiResource {

	/**
	 * Instantiates a new Open api resource.
	 *
	 * @param groupName the group name
	 * @param openAPIBuilderObjectFactory the open api builder object factory
	 * @param requestBuilder the request builder
	 * @param responseBuilder the response builder
	 * @param operationParser the operation parser
	 * @param operationCustomizers the operation customizers
	 * @param openApiCustomisers the open api customisers
	 * @param springDocConfigProperties the spring doc config properties
	 */
	public OpenApiResource(String groupName, ObjectFactory<OpenAPIService> openAPIBuilderObjectFactory, AbstractRequestService requestBuilder,
			GenericResponseService responseBuilder, OperationService operationParser,
			Optional<List<OperationCustomizer>> operationCustomizers,
			Optional<List<OpenApiCustomiser>> openApiCustomisers,
			SpringDocConfigProperties springDocConfigProperties,
			SpringDocProviders springDocProviders) {
		super(groupName, openAPIBuilderObjectFactory, requestBuilder, responseBuilder, operationParser, operationCustomizers, openApiCustomisers, springDocConfigProperties, springDocProviders);
	}

	/**
	 * Instantiates a new Open api resource.
	 *
	 * @param openAPIBuilderObjectFactory the open api builder object factory
	 * @param requestBuilder the request builder
	 * @param responseBuilder the response builder
	 * @param operationParser the operation parser
	 * @param operationCustomizers the operation customizers
	 * @param openApiCustomisers the open api customisers
	 */
	public OpenApiResource(ObjectFactory<OpenAPIService> openAPIBuilderObjectFactory, AbstractRequestService requestBuilder,
			GenericResponseService responseBuilder, OperationService operationParser,
			Optional<List<OperationCustomizer>> operationCustomizers,
			Optional<List<OpenApiCustomiser>> openApiCustomisers,
			SpringDocConfigProperties springDocConfigProperties,
			SpringDocProviders springDocProviders) {
		super(DEFAULT_GROUP_NAME, openAPIBuilderObjectFactory, requestBuilder, responseBuilder, operationParser, operationCustomizers, openApiCustomisers, springDocConfigProperties, springDocProviders);
	}

	/**
	 * Openapi json string.
	 *
	 * @param request the request
	 * @param apiDocsUrl the api docs url
	 * @param locale the locale
	 * @return the string
	 * @throws JsonProcessingException the json processing exception
	 */
	public String openapiJson(HttpServletRequest request,
			String apiDocsUrl, Locale locale)
			throws JsonProcessingException {
		calculateServerUrl(request, apiDocsUrl, locale);
		OpenAPI openAPI = this.getOpenApi(locale);
		return writeJsonValue(openAPI);
	}

	/**
	 * Openapi yaml string.
	 *
	 * @param request the request
	 * @param apiDocsUrl the api docs url
	 * @param locale the locale
	 * @return the string
	 * @throws JsonProcessingException the json processing exception
	 */
	public String openapiYaml(HttpServletRequest request,
			String apiDocsUrl, Locale locale)
			throws JsonProcessingException {
		calculateServerUrl(request, apiDocsUrl, locale);
		OpenAPI openAPI = this.getOpenApi(locale);
		return writeYamlValue(openAPI);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected void getPaths(Map<String, Object> restControllers, Locale locale) {
		SpringWebProvider springWebProvider = springDocProviders.getSpringWebProvider();
		Map<RequestMappingInfo, HandlerMethod> map = springWebProvider.getHandlerMethods();

		Optional<RepositoryRestResourceProvider> repositoryRestResourceProviderOptional = springDocProviders.getRepositoryRestResourceProvider();
		repositoryRestResourceProviderOptional.ifPresent(restResourceProvider -> {
			List<RouterOperation> operationList = restResourceProvider.getRouterOperations(openAPIService.getCalculatedOpenAPI(), locale);
			calculatePath(operationList, locale);
			restResourceProvider.customize(openAPIService.getCalculatedOpenAPI());
			Map<RequestMappingInfo, HandlerMethod> mapDataRest = restResourceProvider.getHandlerMethods();
			Map<String, Object> requestMappingMap = restResourceProvider.getBasePathAwareControllerEndpoints();
			Class[] additionalRestClasses = requestMappingMap.values().stream().map(Object::getClass).toArray(Class[]::new);
			AbstractOpenApiResource.addRestControllers(additionalRestClasses);
			calculatePath(requestMappingMap, mapDataRest, locale);
		});

		calculatePath(restControllers, map, locale);
		Optional<ActuatorProvider> actuatorProviderOptional = springDocProviders.getActuatorProvider();
		if (actuatorProviderOptional.isPresent() && springDocConfigProperties.isShowActuator()) {
			map = actuatorProviderOptional.get().getMethods();
			this.openAPIService.addTag(new HashSet<>(map.values()), getTag());
			calculatePath(restControllers, map, locale);
		}



		Optional<SecurityOAuth2Provider> securityOAuth2ProviderOptional = springDocProviders.getSpringSecurityOAuth2Provider();
		if (securityOAuth2ProviderOptional.isPresent()) {
			SecurityOAuth2Provider securityOAuth2Provider = securityOAuth2ProviderOptional.get();
			Map<RequestMappingInfo, HandlerMethod> mapOauth = securityOAuth2Provider.getHandlerMethods();
			Map<String, Object> requestMappingMapSec = securityOAuth2Provider.getFrameworkEndpoints();
			Class[] additionalRestClasses = requestMappingMapSec.values().stream().map(Object::getClass).toArray(Class[]::new);
			AbstractOpenApiResource.addRestControllers(additionalRestClasses);
			calculatePath(requestMappingMapSec, mapOauth, locale);
		}

		springDocProviders.getRouterFunctionProvider().ifPresent(routerFunctions -> routerFunctions.getRouterFunctionPaths()
				.ifPresent(routerBeans -> routerBeans.forEach((beanName, routerFunctionVisitor) -> getRouterFunctionPaths(beanName, routerFunctionVisitor, locale))));

	}

	/**
	 * Calculate path.
	 *
	 * @param restControllers the rest controllers
	 * @param map the map
	 * @param locale the locale
	 */
	protected void calculatePath(Map<String, Object> restControllers, Map<RequestMappingInfo, HandlerMethod> map, Locale locale) {
		List<Map.Entry<RequestMappingInfo, HandlerMethod>> entries = new ArrayList<>(map.entrySet());
		entries.sort(byReversedRequestMappingInfos());
		SpringWebProvider springWebProvider = springDocProviders.getSpringWebProvider();
		for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : entries) {
			RequestMappingInfo requestMappingInfo = entry.getKey();
			HandlerMethod handlerMethod = entry.getValue();
			Set<String> patterns = springWebProvider.getActivePatterns(requestMappingInfo);
			if (!CollectionUtils.isEmpty(patterns)) {
				Map<String, String> regexMap = new LinkedHashMap<>();
				for (String pattern : patterns) {
					String operationPath = PathUtils.parsePath(pattern, regexMap);
					String[] produces = requestMappingInfo.getProducesCondition().getProducibleMediaTypes().stream().map(MimeType::toString).toArray(String[]::new);
					String[] consumes = requestMappingInfo.getConsumesCondition().getConsumableMediaTypes().stream().map(MimeType::toString).toArray(String[]::new);
					String[] headers = requestMappingInfo.getHeadersCondition().getExpressions().stream().map(Object::toString).toArray(String[]::new);
					if ((isRestController(restControllers, handlerMethod, operationPath) || isActuatorRestController(operationPath, handlerMethod))
							&& isFilterCondition(handlerMethod, operationPath, produces, consumes, headers)) {
						Set<RequestMethod> requestMethods = requestMappingInfo.getMethodsCondition().getMethods();
						// default allowed requestmethods
						if (requestMethods.isEmpty())
							requestMethods = this.getDefaultAllowedHttpMethods();
						calculatePath(handlerMethod, operationPath, requestMethods, locale);
					}
				}
			}
		}
	}


	/**
	 * By reversed request mapping infos comparator.
	 *
	 * @return the comparator
	 */
	private Comparator<Map.Entry<RequestMappingInfo, HandlerMethod>> byReversedRequestMappingInfos() {
		return Comparator.<Map.Entry<RequestMappingInfo, HandlerMethod>, String>
						comparing(a -> a.getKey().toString())
				.reversed();
	}

	/**
	 * Calculate server url.
	 *
	 * @param request the request
	 * @param apiDocsUrl the api docs url
	 */
	protected void calculateServerUrl(HttpServletRequest request, String apiDocsUrl, Locale locale) {
		super.initOpenAPIBuilder(locale);
		String calculatedUrl = getServerUrl(request, apiDocsUrl);
		openAPIService.setServerBaseUrl(calculatedUrl);
	}

	/**
	 * Gets server url.
	 *
	 * @param request the request
	 * @param apiDocsUrl the api docs url
	 * @return the server url
	 */
	protected abstract String getServerUrl(HttpServletRequest request, String apiDocsUrl);

}
