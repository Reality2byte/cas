package org.apereo.cas.ticket.refreshtoken;

import org.apereo.cas.authentication.Authentication;
import org.apereo.cas.authentication.principal.Service;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.support.oauth.OAuth20GrantTypes;
import org.apereo.cas.support.oauth.OAuth20ResponseTypes;
import org.apereo.cas.support.oauth.services.OAuthRegisteredService;
import org.apereo.cas.support.oauth.services.RegisteredServiceOAuthRefreshTokenExpirationPolicy;
import org.apereo.cas.support.oauth.util.OAuth20Utils;
import org.apereo.cas.ticket.ExpirationPolicy;
import org.apereo.cas.ticket.ExpirationPolicyBuilder;
import org.apereo.cas.ticket.Ticket;
import org.apereo.cas.ticket.UniqueTicketIdGenerator;
import org.apereo.cas.ticket.tracking.TicketTrackingPolicy;
import org.apereo.cas.util.DefaultUniqueTicketIdGenerator;
import org.apereo.cas.util.function.FunctionUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.val;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Default OAuth refresh token factory.
 *
 * @author Jerome Leleu
 * @since 5.0.0
 */
@RequiredArgsConstructor
public class OAuth20DefaultRefreshTokenFactory implements OAuth20RefreshTokenFactory {

    protected final UniqueTicketIdGenerator refreshTokenIdGenerator;

    @Getter
    protected final ExpirationPolicyBuilder<OAuth20RefreshToken> expirationPolicyBuilder;

    protected final ServicesManager servicesManager;

    protected final TicketTrackingPolicy descendantTicketsTrackingPolicy;

    public OAuth20DefaultRefreshTokenFactory(final ExpirationPolicyBuilder<OAuth20RefreshToken> expirationPolicyBuilder,
                                             final ServicesManager servicesManager,
                                             final TicketTrackingPolicy descendantTicketsTrackingPolicy) {
        this(new DefaultUniqueTicketIdGenerator(), expirationPolicyBuilder,
            servicesManager, descendantTicketsTrackingPolicy);
    }

    @Override
    public OAuth20RefreshToken create(final Service service,
                                      final Authentication authentication,
                                      final Ticket ticketGrantingTicket,
                                      final Collection<String> scopes,
                                      final String clientId,
                                      final String accessToken,
                                      final Map<String, Map<String, Object>> requestClaims,
                                      final OAuth20ResponseTypes responseType,
                                      final OAuth20GrantTypes grantType) throws Throwable {
        val registeredService = OAuth20Utils.getRegisteredOAuthServiceByClientId(servicesManager, clientId);
        
        val limitReached = Optional.ofNullable(registeredService)
            .map(OAuthRegisteredService::getRefreshTokenExpirationPolicy)
            .map(RegisteredServiceOAuthRefreshTokenExpirationPolicy::getMaxActiveTokens)
            .filter(count -> count > 0 && ticketGrantingTicket != null)
            .stream()
            .anyMatch(count -> count <= descendantTicketsTrackingPolicy.countTicketsFor(ticketGrantingTicket, service));
        FunctionUtils.throwIf(limitReached, () -> new IllegalArgumentException("Access token limit for %s is reached".formatted(service.getId())));
        
        val codeId = refreshTokenIdGenerator.getNewTicketId(OAuth20RefreshToken.PREFIX);
        val expirationPolicyToUse = determineExpirationPolicyForService(registeredService);
        val refreshToken = new OAuth20DefaultRefreshToken(codeId, service, authentication,
            expirationPolicyToUse, ticketGrantingTicket,
            scopes, clientId, accessToken, requestClaims, responseType, grantType);
        descendantTicketsTrackingPolicy.trackTicket(ticketGrantingTicket, refreshToken);
        return refreshToken;
    }

    protected ExpirationPolicy determineExpirationPolicyForService(final RegisteredService registeredService) {
        return expirationPolicyBuilder.buildTicketExpirationPolicyFor(registeredService);
    }

    @Override
    public Class<? extends Ticket> getTicketType() {
        return OAuth20RefreshToken.class;
    }
}
