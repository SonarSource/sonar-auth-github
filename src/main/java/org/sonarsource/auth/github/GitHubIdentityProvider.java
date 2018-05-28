/*
 * GitHub Authentication for SonarQube
 * Copyright (C) 2016-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
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
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.sonar.api.server.ServerSide;
import org.sonar.api.server.authentication.Display;
import org.sonar.api.server.authentication.OAuth2IdentityProvider;
import org.sonar.api.server.authentication.UnauthorizedException;
import org.sonar.api.server.authentication.UserIdentity;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import static java.lang.String.format;

@ServerSide
public class GitHubIdentityProvider implements OAuth2IdentityProvider {

  static final String KEY = "github";

  private static final Logger LOGGER = Loggers.get(GitHubIdentityProvider.class);

  private final GitHubSettings settings;
  private final UserIdentityFactory userIdentityFactory;
  private final ScribeGitHubApi scribeApi;

  public GitHubIdentityProvider(GitHubSettings settings, UserIdentityFactory userIdentityFactory, ScribeGitHubApi scribeApi) {
    this.settings = settings;
    this.userIdentityFactory = userIdentityFactory;
    this.scribeApi = scribeApi;
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
    OAuth20Service scribe = newScribeBuilder(context)
      .scope(getScope())
      .state(state)
      .build(scribeApi);
    String url = scribe.getAuthorizationUrl(/* additionalParams */ );
    context.redirectTo(url);
  }

  String getScope() {
    return (settings.syncGroups() || isOrganizationMembershipRequired()) ? "user:email,read:org" : "user:email";
  }

  @Override
  public void callback(CallbackContext context) {
    try {
      onCallback(context);
    } catch (IOException | ExecutionException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private void onCallback(CallbackContext context) throws InterruptedException, ExecutionException, IOException {
    context.verifyCsrfState();

    HttpServletRequest request = context.getRequest();
    OAuth20Service scribe = newScribeBuilder(context).build(scribeApi);
    String code = request.getParameter("code");
    OAuth2AccessToken accessToken = scribe.getAccessToken(code);

    GsonUser user = getUser(scribe, accessToken);
    if (isUnauthorized(accessToken, user.getLogin())) {
      throw new UnauthorizedException(format("'%s' must be a member of at least one organization: '%s'",
        user.getLogin(), Arrays.stream(settings.organizations()).collect(Collectors.joining("', '"))));
    }

    final String email;
    if (user.getEmail() == null) {
      // if the user has not specified a public email address in their profile
      email = getEmail(scribe, accessToken);
    } else {
      email = user.getEmail();
    }

    UserIdentity userIdentity = userIdentityFactory.create(user, email,
      settings.syncGroups() ? getTeams(scribe, accessToken) : null);
    context.authenticate(userIdentity);
    context.redirectToRequestedPage();
  }

  private GsonUser getUser(OAuth20Service scribe, OAuth2AccessToken accessToken) throws IOException, ExecutionException, InterruptedException {
    String responseBody = executeRequest(settings.apiURL() + "user", scribe, accessToken);
    LOGGER.trace("User response received : {}", responseBody);
    return GsonUser.parse(responseBody);
  }

  private String getEmail(OAuth20Service scribe, OAuth2AccessToken accessToken) throws IOException, ExecutionException, InterruptedException {
    String responseBody = executeRequest(settings.apiURL() + "user/emails", scribe, accessToken);
    LOGGER.trace("Emails response received : {}", responseBody);
    List<GsonEmails.GsonEmail> emails = GsonEmails.parse(responseBody);
    return emails.stream()
      .filter(email -> email.isPrimary() && email.isVerified())
      .findFirst()
      .map(GsonEmails.GsonEmail::getEmail)
      .orElse(null);
  }

  private List<GsonTeams.GsonTeam> getTeams(OAuth20Service scribe, OAuth2AccessToken accessToken) throws IOException, ExecutionException, InterruptedException {
    String responseBody = executeRequest(settings.apiURL() + "user/teams", scribe, accessToken);
    LOGGER.trace("Teams response received : {}", responseBody);
    return GsonTeams.parse(responseBody);
  }

  boolean isOrganizationMembershipRequired() {
    return settings.organizations().length > 0;
  }

  private boolean isOrganizationsMember(OAuth2AccessToken accessToken, String login) throws IOException, ExecutionException, InterruptedException {
    for (String organization : settings.organizations()) {
      if (isOrganizationMember(accessToken, organization, login)) {
        return true;
      }
    }
    return false;
  }

  private boolean isUnauthorized(OAuth2AccessToken accessToken, String login) throws IOException, ExecutionException, InterruptedException {
    return isOrganizationMembershipRequired() && !isOrganizationsMember(accessToken, login);
  }

  /**
   * Check to see that login is a member of organization.
   *
   * A 204 response code indicates organization membership.  302 and 404 codes are not treated as exceptional,
   * they indicate various ways in which a login is not a member of the organization.
   *
   * @see <a href="https://developer.github.com/v3/orgs/members/#response-if-requester-is-an-organization-member-and-user-is-a-member">GitHub members API</a>
   */
  private boolean isOrganizationMember(OAuth2AccessToken accessToken, String organization, String login) throws IOException, ExecutionException, InterruptedException {
    String requestUrl = settings.apiURL() + format("orgs/%s/members/%s", organization, login);
    OAuth20Service scribe = new ServiceBuilder(settings.clientId())
      .apiSecret(settings.clientSecret())
      .build(scribeApi);
    OAuthRequest request = new OAuthRequest(Verb.GET, requestUrl);
    scribe.signRequest(accessToken, request);

    Response response = scribe.execute(request);
    int code = response.getCode();
    switch (code) {
      case HttpURLConnection.HTTP_MOVED_TEMP:
      case HttpURLConnection.HTTP_NOT_FOUND:
      case HttpURLConnection.HTTP_NO_CONTENT:
        LOGGER.trace("Orgs response received : {}", code);
        return code == HttpURLConnection.HTTP_NO_CONTENT;
      default:
        throw unexpectedResponseCode(requestUrl, response);
    }
  }

  private static String executeRequest(String requestUrl, OAuth20Service scribe, OAuth2AccessToken accessToken) throws IOException, ExecutionException, InterruptedException {
    OAuthRequest request = new OAuthRequest(Verb.GET, requestUrl);
    scribe.signRequest(accessToken, request);
    Response response = scribe.execute(request);
    if (!response.isSuccessful()) {
      throw unexpectedResponseCode(requestUrl, response);
    }
    return response.getBody();
  }

  private static IllegalStateException unexpectedResponseCode(String requestUrl, Response response) throws IOException {
    return new IllegalStateException(format("Fail to execute request '%s'. HTTP code: %s, response: %s", requestUrl, response.getCode(), response.getBody()));
  }

  private ServiceBuilder newScribeBuilder(OAuth2IdentityProvider.OAuth2Context context) {
    if (!isEnabled()) {
      throw new IllegalStateException("GitHub authentication is disabled");
    }
    return new ServiceBuilder(settings.clientId())
      .apiSecret(settings.clientSecret())
      .callback(context.getCallbackUrl());
  }
}
