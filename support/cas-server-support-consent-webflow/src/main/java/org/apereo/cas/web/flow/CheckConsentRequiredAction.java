package org.apereo.cas.web.flow;

import org.apereo.cas.authentication.Authentication;
import org.apereo.cas.authentication.AuthenticationServiceSelectionPlan;
import org.apereo.cas.authentication.attribute.AttributeDefinitionStore;
import org.apereo.cas.authentication.principal.Service;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.consent.ConsentActivationStrategy;
import org.apereo.cas.consent.ConsentEngine;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.web.support.WebUtils;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;

/**
 * This is {@link CheckConsentRequiredAction}.
 *
 * @author Misagh Moayyed
 * @since 5.1.0
 */
public class CheckConsentRequiredAction extends AbstractConsentAction {
    /**
     * Indicates that webflow should proceed with consent.
     */
    public static final String EVENT_ID_CONSENT_REQUIRED = "consentRequired";

    private final ConsentActivationStrategy consentActivationStrategy;

    public CheckConsentRequiredAction(final ServicesManager servicesManager,
                                      final AuthenticationServiceSelectionPlan strategies,
                                      final ConsentEngine consentEngine,
                                      final CasConfigurationProperties casProperties,
                                      final AttributeDefinitionStore attributeDefinitionStore,
                                      final ConsentActivationStrategy consentActivationStrategy) {
        super(casProperties, servicesManager, strategies,
            consentEngine, attributeDefinitionStore);
        this.consentActivationStrategy = consentActivationStrategy;
    }

    @Override
    protected Event doExecuteInternal(final RequestContext requestContext) throws Throwable {
        val consentEvent = determineConsentEvent(requestContext);
        if (StringUtils.isBlank(consentEvent)) {
            return null;
        }
        prepareConsentForRequestContext(requestContext);
        return eventFactory.event(this, consentEvent);
    }

    protected String determineConsentEvent(final RequestContext requestContext) throws Throwable {
        val webService = WebUtils.getService(requestContext);
        val service = this.authenticationRequestServiceSelectionStrategies.resolveService(webService);
        if (service == null) {
            return null;
        }

        val registeredService = getRegisteredServiceForConsent(requestContext, service);
        val authentication = WebUtils.getAuthentication(requestContext);
        if (authentication == null) {
            return null;
        }

        return isConsentRequired(service, registeredService, authentication, requestContext);
    }

    protected String isConsentRequired(final Service service,
                                       final RegisteredService registeredService,
                                       final Authentication authentication,
                                       final RequestContext requestContext) throws Throwable {
        val request = WebUtils.getHttpServletRequestFromExternalWebflowContext(requestContext);
        val required = consentActivationStrategy.isConsentRequired(service,
            registeredService, authentication, request);
        return required ? EVENT_ID_CONSENT_REQUIRED : null;
    }
}
