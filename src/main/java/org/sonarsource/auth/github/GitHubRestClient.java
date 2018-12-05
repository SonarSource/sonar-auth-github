/*
 * GitHub Authentication for SonarQube
 * Copyright (C) 2016-2019 SonarSource SA
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

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import org.sonar.api.server.ServerSide;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import static java.lang.String.format;

@ServerSide
public class GitHubRestClient {

  private static final Logger LOGGER = Loggers.get(GitHubRestClient.class);

  private static final Pattern NEXT_LINK_PATTERN = Pattern.compile(".*<(.*)>; rel=\"next\"");

  private final GitHubSettings settings;

  public GitHubRestClient(GitHubSettings settings) {
    this.settings = settings;
  }

  GsonUser getUser(OAuth20Service scribe, OAuth2AccessToken accessToken) throws IOException, ExecutionException, InterruptedException {
    String responseBody = executeRequest(settings.apiURL() + "user", scribe, accessToken).getBody();
    LOGGER.trace("User response received : {}", responseBody);
    return GsonUser.parse(responseBody);
  }

  String getEmail(OAuth20Service scribe, OAuth2AccessToken accessToken) throws IOException, ExecutionException, InterruptedException {
    String responseBody = executeRequest(settings.apiURL() + "user/emails", scribe, accessToken).getBody();
    LOGGER.trace("Emails response received : {}", responseBody);
    List<GsonEmails.GsonEmail> emails = GsonEmails.parse(responseBody);
    return emails.stream()
      .filter(email -> email.isPrimary() && email.isVerified())
      .findFirst()
      .map(GsonEmails.GsonEmail::getEmail)
      .orElse(null);
  }

  List<GsonTeams.GsonTeam> getTeams(OAuth20Service scribe, OAuth2AccessToken accessToken) throws IOException, ExecutionException, InterruptedException {
    Response response = executeRequest(settings.apiURL() + "user/teams?per_page=100", scribe, accessToken);
    LOGGER.trace("Teams response received : {}", response.getBody());
    List<GsonTeams.GsonTeam> teams = GsonTeams.parse(response.getBody());
    getNextTeams(teams, response, scribe, accessToken);
    return teams;
  }

  /**
   * Check to see that login is a member of organization.
   *
   * A 204 response code indicates organization membership.  302 and 404 codes are not treated as exceptional,
   * they indicate various ways in which a login is not a member of the organization.
   *
   * @see <a href="https://developer.github.com/v3/orgs/members/#response-if-requester-is-an-organization-member-and-user-is-a-member">GitHub members API</a>
   */
  boolean isOrganizationMember(OAuth20Service scribe, OAuth2AccessToken accessToken, String organization, String login)
    throws IOException, ExecutionException, InterruptedException {
    String requestUrl = settings.apiURL() + format("orgs/%s/members/%s", organization, login);
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

  private static void getNextTeams(List<GsonTeams.GsonTeam> teams, Response response, OAuth20Service scribe, OAuth2AccessToken accessToken)
    throws InterruptedException, ExecutionException, IOException {
    String nextEndPoint = readNextEndPoint(response);
    if (nextEndPoint == null) {
      return;
    }
    Response nextResponse = executeRequest(nextEndPoint, scribe, accessToken);
    LOGGER.trace("Teams response received : {}", nextResponse.getBody());
    teams.addAll(GsonTeams.parse(nextResponse.getBody()));
    getNextTeams(teams, nextResponse, scribe, accessToken);
  }

  private static Response executeRequest(String requestUrl, OAuth20Service scribe, OAuth2AccessToken accessToken) throws IOException, ExecutionException, InterruptedException {
    OAuthRequest request = new OAuthRequest(Verb.GET, requestUrl);
    scribe.signRequest(accessToken, request);
    Response response = scribe.execute(request);
    if (!response.isSuccessful()) {
      throw unexpectedResponseCode(requestUrl, response);
    }
    return response;
  }

  @CheckForNull
  private static String readNextEndPoint(Response response) {
    String link = response.getHeader("Link");
    if (link == null || link.isEmpty() || !link.contains("rel=\"next\"")) {
      return null;
    }
    Matcher nextLinkMatcher = NEXT_LINK_PATTERN.matcher(link);
    if (!nextLinkMatcher.find()) {
      return null;
    }
    return nextLinkMatcher.group(1);
  }

  private static IllegalStateException unexpectedResponseCode(String requestUrl, Response response) throws IOException {
    return new IllegalStateException(format("Fail to execute request '%s'. HTTP code: %s, response: %s", requestUrl, response.getCode(), response.getBody()));
  }
}
