package pet.jen.mbdev.api.auth;

import feign.FeignException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import pet.jen.mbdev.api.TokenProvider;
import pet.jen.mbdev.api.auth.client.AuthorizationApi;
import pet.jen.mbdev.api.auth.client.LoginApi;
import pet.jen.mbdev.api.auth.domain.ConsentInformation;
import pet.jen.mbdev.api.auth.domain.OAuthConfig;
import pet.jen.mbdev.api.auth.domain.SessionInformation;
import pet.jen.mbdev.api.auth.exception.*;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;

@RunWith(MockitoJUnitRunner.class)
public class AuthorizationFlowHandlerTest extends BaseAuthorizationTest {

    @Mock
    private AuthorizationApi authorizationApi;

    @Mock
    private LoginApi loginApi;

    private AuthorizationFlowHandler authorizationFlowHandler;
    private OAuthConfig defaultConfig;

    @Before
    public void setup() {
        defaultConfig = createDefaultConfig();
        authorizationFlowHandler = new AuthorizationFlowHandler(authorizationApi, loginApi, defaultConfig);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInstantiate_whenOAuthConfigIsNotValid_shouldThrowException() {
        OAuthConfig config = OAuthConfig.builder().build();
        AuthorizationFlowHandler.setup(config);
    }

    @Test
    public void testRetrieveSession_whenClientCallSucceeded_shouldReturnSessionInformation() throws Exception {
        Mockito.when(authorizationApi.authorize(
                eq("client-id"),
                eq("http://localhost"),
                eq("scope1 scope2")
                )).thenReturn(resourceAsString("__files/login/redirected_login_form.html"));
        SessionInformation sessionInfo = authorizationFlowHandler.retrieveSession(
                defaultConfig.getClientId(),
                defaultConfig.getRedirectUri(),
                defaultConfig.getScopes());
        assertThat(sessionInfo.getAppId()).isEqualTo("ONEAPI.PROD");
        assertThat(sessionInfo.getSessionID()).isEqualTo("070027d2-5bd3-4038-8903-e2007a2e14b1");
        assertThat(sessionInfo.getSessionData()).isEqualTo("eyJ0eXAiOiJKV1QiLCJhbGci...KfQ.zoINDytohLmYv1bTKABqnIu8wLS4pETEakcdnnkLF40");
    }

    @Test(expected = SessionRetrievalException.class)
    public void testRetrieveSession_whenSessionInformationIsInvalid_shouldThrowSessionRetrievalException() {
        Mockito.when(authorizationApi.authorize(
                eq("client-id"),
                eq("http://localhost"),
                eq("scope1 scope2")
        )).thenReturn("<html/>");

        authorizationFlowHandler.retrieveSession(
                defaultConfig.getClientId(),
                defaultConfig.getRedirectUri(),
                defaultConfig.getScopes());
    }

    @Test(expected = SessionRetrievalException.class)
    public void testRetrieveSession_whenClientCallFailed_shouldThrowSessionRetrievalException() {
        Mockito.doThrow(Mockito.mock(FeignException.class))
                .when(authorizationApi).authorize(
                    eq("client-id"),
                    eq("http://localhost"),
                    eq("scope1 scope2"));

        authorizationFlowHandler.retrieveSession(
                defaultConfig.getClientId(),
                defaultConfig.getRedirectUri(),
                defaultConfig.getScopes());
    }

    @Test(expected = UserLoginFailedException.class)
    public void testSubmitUserLogin_whenConsentInformationIsInvalid_shouldThrowException() {
        Mockito.when(loginApi.login(
                eq("app-id"),
                eq("sessionId"),
                eq("sessionData"),
                eq("username"),
                eq("password"))).thenReturn("<html/>");
        authorizationFlowHandler.submitUserLogin(createSessionInfo(), "username", "password");
    }

    @Test(expected = UserLoginFailedException.class)
    public void testSubmitUserLogin_whenClientCallFails_shouldThrowException() {
        Mockito.when(loginApi.login(
                eq("app-id"),
                eq("sessionId"),
                eq("sessionData"),
                eq("username"),
                eq("password"))).thenThrow(FeignException.class);
        authorizationFlowHandler.submitUserLogin(createSessionInfo(), "username", "password");
    }

    @Test
    public void testSubmitUserConsent_whenDecoderReturnedAuthCodeException_shouldReturnCorrectAuthCode() {
        Mockito.doThrow(new AuthCodeException("authCode"))
                .when(authorizationApi).postConsent(
                        eq("Grant"),
                        eq("sessionId"),
                        eq("sessionData")
                );
        assertThat(authorizationFlowHandler.submitUserConsent(createConsentInfo(createSessionInfo()))).isEqualTo("authCode");
    }

    @Test(expected = UserConsentException.class)
    public void testSubmitUserConsent_whenDecoderReturnedNullAuthCode_shouldThrowUserConsentException() {
        Mockito.doThrow(new AuthCodeException(null))
                .when(authorizationApi).postConsent(
                eq("Grant"),
                eq("sessionId"),
                eq("sessionData")
        );
        authorizationFlowHandler.submitUserConsent(createConsentInfo(createSessionInfo()));
    }

    @Test(expected = UserConsentException.class)
    public void testSubmitUserConsent_whenClientCallFails_shouldThrowUserConsentException() throws Exception {
        Mockito.doThrow(Mockito.mock(FeignException.class))
                .when(authorizationApi).postConsent(
                eq("Grant"),
                eq("sessionId"),
                eq("sessionData")
        );
        authorizationFlowHandler.submitUserConsent(createConsentInfo(createSessionInfo()));
    }


    @Test
    public void testLogin_whenClientFlowSucceeds_shouldReturnAuthorizationProvider() throws Exception {
        Mockito.when(authorizationApi.authorize(eq("client-id"), eq("http://localhost"), eq("scope1 scope2")))
                .thenReturn(resourceAsString("__files/login/redirected_login_form.html"));
        Mockito.when(loginApi.login(
                eq("ONEAPI.PROD"),
                eq("070027d2-5bd3-4038-8903-e2007a2e14b1"),
                eq("eyJ0eXAiOiJKV1QiLCJhbGci...KfQ.zoINDytohLmYv1bTKABqnIu8wLS4pETEakcdnnkLF40"),
                eq("username"),
                eq("password"))).thenReturn(resourceAsString("__files/login/consent_form.html"));
        Mockito.doThrow(new AuthCodeException("authorization-token"))
                .when(authorizationApi)
                .postConsent(
                        eq("Grant"),
                        eq("849cc52b-9ca4-4ec1-b1c6-c26ae9e398ed"),
                        eq("eyJjdHkiO..._t7wrqQ"));
        TokenProvider tokenProvider = authorizationFlowHandler.authorize("username", "password");
        assertThat(tokenProvider).isNotNull();
    }

    @Test(expected = AuthorizationInitializationException.class)
    public void testInit_whenSingleStepFails_shouldThrowAuthorizationInitializationException() {
        authorizationFlowHandler.authorize("username", "password");
    }

    private SessionInformation createSessionInfo() {
        return new SessionInformation("sessionId", "sessionData", "app-id");
    }

    private ConsentInformation createConsentInfo(SessionInformation sessionInfo) {
        ConsentInformation consentInfo = new ConsentInformation();
        consentInfo.setAction("Grant");
        consentInfo.setSessionID(sessionInfo.getSessionID());
        consentInfo.setSessionData(sessionInfo.getSessionData());
        return consentInfo;
    }
}