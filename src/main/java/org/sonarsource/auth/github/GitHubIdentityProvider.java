/*
 * GitHub Authentication for SonarQube
 * Copyright (C) 2016-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.auth.github;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Token;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.model.Verifier;
import com.github.scribejava.core.oauth.OAuthService;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.sonar.api.server.ServerSide;
import org.sonar.api.server.authentication.Display;
import org.sonar.api.server.authentication.OAuth2IdentityProvider;
import org.sonar.api.server.authentication.UserIdentity;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import static java.lang.String.format;

@ServerSide
public class GitHubIdentityProvider implements OAuth2IdentityProvider {

  public static final String KEY = "github";
  private static final Logger LOGGER = Loggers.get(GitHubIdentityProvider.class);
  private static final Token EMPTY_TOKEN = null;

  private final GitHubSettings settings;
  private final UserIdentityFactory userIdentityFactory;

  public GitHubIdentityProvider(GitHubSettings settings, UserIdentityFactory userIdentityFactory) {
    this.settings = settings;
    this.userIdentityFactory = userIdentityFactory;
  }

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public String getName() {
    return "GitHub";
  }

  @Override
  public Display getDisplay() {
    return Display.builder()
      // URL of src/main/resources/static/github.svg at runtime
      .setIconPath("/static/authgithub/github.svg")
      .setBackgroundColor("#444444")
      .build();
  }

  @Override
  public boolean isEnabled() {
    return settings.isEnabled();
  }

  @Override
  public boolean allowsUsersToSignUp() {
    return settings.allowUsersToSignUp();
  }

  @Override
  public void init(InitContext context) {
    String state = context.generateCsrfState();
    OAuthService scribe = prepareScribe(context)
      .scope(settings.syncGroups() ? "user:email,read:org" : "user:email")
      .state(state)
      .build();
    String url = scribe.getAuthorizationUrl(EMPTY_TOKEN);
    context.redirectTo(url);
  }

  @Override
  public void callback(CallbackContext context) {
    context.verifyCsrfState();

    HttpServletRequest request = context.getRequest();
    OAuthService scribe = prepareScribe(context).build();
    String oAuthVerifier = request.getParameter("code");
    Token accessToken = scribe.getAccessToken(EMPTY_TOKEN, new Verifier(oAuthVerifier));

    OAuthRequest userRequest = new OAuthRequest(Verb.GET, settings.apiURL() + "user", scribe);
    scribe.signRequest(accessToken, userRequest);
    GsonUser gsonUser = getUser(scribe, accessToken);

    com.github.scribejava.core.model.Response userResponse = userRequest.send();
    if (!userResponse.isSuccessful()) {
      throw new IllegalStateException(format("Can not get GitHub user profile. HTTP code: %s, response: %s",
        userResponse.getCode(), userResponse.getBody()));
    }
    String userResponseBody = userResponse.getBody();
    LOGGER.trace("User response received : %s", userResponseBody);

    UserIdentity userIdentity = userIdentityFactory.create(gsonUser,
      settings.syncGroups() ? getTeams(scribe, accessToken) : null);
    context.authenticate(userIdentity);
    context.redirectToRequestedPage();
  }

  private static GsonUser getUser(OAuthService scribe, Token accessToken) {
    String responseBody = executeRequest("https://api.github.com/user", scribe, accessToken);
    LOGGER.trace("User response received : {}", responseBody);
    return GsonUser.parse(responseBody);
  }

  private static List<GsonTeams.GsonTeam> getTeams(OAuthService scribe, Token accessToken) {
    String responseBody = executeRequest("https://api.github.com/user/teams", scribe, accessToken);
    LOGGER.trace("Teams response received : {}", responseBody);
    return GsonTeams.parse(responseBody);
  }

  private static String executeRequest(String requestUrl, OAuthService scribe, Token accessToken) {
    OAuthRequest request = new OAuthRequest(Verb.GET, requestUrl, scribe);
    scribe.signRequest(accessToken, request);

    com.github.scribejava.core.model.Response response = request.send();
    if (!response.isSuccessful()) {
      throw new IllegalStateException(format("Fail to execute request '%s'. Error code is %s, Body of the response is %s",
        requestUrl, response.getCode(), response.getBody()));
    }
    return response.getBody();
  }

  private ServiceBuilder prepareScribe(OAuth2IdentityProvider.OAuth2Context context) {
    if (!isEnabled()) {
      throw new IllegalStateException("GitHub authentication is disabled");
    }
    return new ServiceBuilder()
      .provider(new GithubWithConfigurableURL(settings))
      .apiKey(settings.clientId())
      .apiSecret(settings.clientSecret())
      .callback(context.getCallbackUrl());
  }

}
