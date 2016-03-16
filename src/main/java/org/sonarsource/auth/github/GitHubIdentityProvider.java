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

import com.github.scribejava.apis.GitHubApi;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Token;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.model.Verifier;
import com.github.scribejava.core.oauth.OAuthService;
import com.google.common.base.Function;
import java.util.List;
import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import org.sonar.api.server.ServerSide;
import org.sonar.api.server.authentication.Display;
import org.sonar.api.server.authentication.OAuth2IdentityProvider;
import org.sonar.api.server.authentication.UserIdentity;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.FluentIterable.from;
import static java.lang.String.format;
import static org.sonarsource.auth.github.GitHubSettings.LOGIN_STRATEGY_PROVIDER_ID;
import static org.sonarsource.auth.github.GitHubSettings.LOGIN_STRATEGY_UNIQUE;

@ServerSide
public class GitHubIdentityProvider implements OAuth2IdentityProvider {

  private static final Logger LOGGER = Loggers.get(GitHubIdentityProvider.class);

  private static final Token EMPTY_TOKEN = null;

  private final GitHubSettings settings;

  public GitHubIdentityProvider(GitHubSettings settings) {
    this.settings = settings;
  }

  @Override
  public String getKey() {
    return "github";
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

    GsonUser gsonUser = getUser(scribe, accessToken);

    UserIdentity.Builder builder = UserIdentity.builder()
      .setProviderLogin(gsonUser.getLogin())
      .setLogin(getLogin(gsonUser))
      .setName(getName(gsonUser))
      .setEmail(gsonUser.getEmail());

    if (settings.syncGroups()) {
      List<GsonTeams.GsonTeam> teams = getTeams(scribe, accessToken);
      builder.setGroups(from(teams).transform(TeamToGroup.INSTANCE).toSet());
    }

    context.authenticate(builder.build());
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
      throw new IllegalStateException("GitHub Authentication is disabled");
    }
    return new ServiceBuilder()
      .provider(GitHubApi.class)
      .apiKey(settings.clientId())
      .apiSecret(settings.clientSecret())
      .callback(context.getCallbackUrl());
  }

  private String getLogin(GsonUser gsonUser) {
    String loginStrategy = settings.loginStrategy();
    if (LOGIN_STRATEGY_UNIQUE.equals(loginStrategy)) {
      return generateUniqueLogin(gsonUser);
    } else if (LOGIN_STRATEGY_PROVIDER_ID.equals(loginStrategy)) {
      return gsonUser.getLogin();
    } else {
      throw new IllegalStateException(format("Login strategy not found : %s", loginStrategy));
    }
  }

  private static String getName(GsonUser gsonUser) {
    String name = gsonUser.getName();
    return isNullOrEmpty(name) ? gsonUser.getLogin() : name;
  }

  private String generateUniqueLogin(GsonUser gsonUser) {
    return getKey() + "@" + gsonUser.getLogin();
  }

  private enum TeamToGroup implements Function<GsonTeams.GsonTeam, String> {
    INSTANCE;

    @Override
    public String apply(@Nonnull GsonTeams.GsonTeam gsonTeam) {
      return gsonTeam.getOrganizationId() + "/" + gsonTeam.getId();
    }
  }

}
