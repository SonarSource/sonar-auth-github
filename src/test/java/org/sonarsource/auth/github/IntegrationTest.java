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

import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.server.authentication.OAuth2IdentityProvider;
import org.sonar.api.server.authentication.UserIdentity;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IntegrationTest {

  private static final String CALLBACK_URL = "http://localhost/oauth/callback/github";

  @Rule
  public MockWebServer github = new MockWebServer();

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  // load settings with default values
  Settings settings = new Settings(new PropertyDefinitions(GitHubSettings.definitions()));
  GitHubSettings gitHubSettings = new GitHubSettings(settings);
  UserIdentityFactory userIdentityFactory = new UserIdentityFactory(gitHubSettings);
  ScribeGitHubApi scribeApi = new ScribeGitHubApi(gitHubSettings);
  GitHubIdentityProvider underTest = new GitHubIdentityProvider(gitHubSettings, userIdentityFactory, scribeApi);

  @Before
  public void enable() {
    settings.setProperty("sonar.auth.github.clientId.secured", "the_id");
    settings.setProperty("sonar.auth.github.clientSecret.secured", "the_secret");
    settings.setProperty("sonar.auth.github.enabled", true);
    settings.setProperty("sonar.auth.github.apiUrl", format("http://%s:%d", github.getHostName(), github.getPort()));
    settings.setProperty("sonar.auth.github.webUrl", format("http://%s:%d", github.getHostName(), github.getPort()));
  }

  /**
   * First phase: SonarQube redirects browser to GitHub authentication form, requesting the
   * minimal access rights ("scope") to get user profile (login, name, email and others).
   */
  @Test
  public void redirect_browser_to_github_authentication_form() throws Exception {
    DumbInitContext context = new DumbInitContext("the-csrf-state");
    underTest.init(context);
    assertThat(context.redirectedTo)
      .startsWith(github.url("login/oauth/authorize").toString())
      .contains("scope=" + URLEncoder.encode("user:email", StandardCharsets.UTF_8.name()));
  }

  /**
   * Second phase: GitHub redirects browser to SonarQube at /oauth/callback/github?code={the verifier code}.
   * This SonarQube web service sends two requests to GitHub:
   * <ul>
   *   <li>get an access token</li>
   *   <li>get the profile of the authenticated user</li>
   * </ul>
   */
  @Test
  public void callback_on_successful_authentication() throws IOException, InterruptedException {
    github.enqueue(newSuccessfulAccessTokenResponse());
    // response of api.github.com/user
    github.enqueue(new MockResponse().setBody("{\"login\":\"octocat\", \"name\":\"monalisa octocat\",\"email\":\"octocat@github.com\"}"));

    HttpServletRequest request = newRequest("the-verifier-code");
    DumbCallbackContext callbackContext = new DumbCallbackContext(request);
    underTest.callback(callbackContext);

    assertThat(callbackContext.csrfStateVerified.get()).isTrue();
    assertThat(callbackContext.userIdentity.getLogin()).isEqualTo("octocat@github");
    assertThat(callbackContext.userIdentity.getName()).isEqualTo("monalisa octocat");
    assertThat(callbackContext.userIdentity.getEmail()).isEqualTo("octocat@github.com");
    assertThat(callbackContext.redirectedToRequestedPage.get()).isTrue();

    // Verify the requests sent to GitHub
    RecordedRequest accessTokenGitHubRequest = github.takeRequest();
    assertThat(accessTokenGitHubRequest.getPath())
      .startsWith("/login/oauth/access_token")
      .contains("code=the-verifier-code");
    RecordedRequest profileGitHubRequest = github.takeRequest();
    assertThat(profileGitHubRequest.getPath()).startsWith("/user");
  }

  @Test
  public void redirect_browser_to_github_authentication_form_with_group_sync() throws Exception {
    settings.setProperty("sonar.auth.github.groupsSync", true);
    DumbInitContext context = new DumbInitContext("the-csrf-state");
    underTest.init(context);
    assertThat(context.redirectedTo)
      .startsWith(github.url("login/oauth/authorize").toString())
      .contains("scope=" + URLEncoder.encode("user:email,read:org", StandardCharsets.UTF_8.name()));
  }

  @Test
  public void callback_on_successful_authentication_with_group_sync() throws IOException, InterruptedException {
    settings.setProperty("sonar.auth.github.groupsSync", true);

    github.enqueue(newSuccessfulAccessTokenResponse());
    // response of api.github.com/user
    github.enqueue(new MockResponse().setBody("{\"login\":\"octocat\", \"name\":\"monalisa octocat\",\"email\":\"octocat@github.com\"}"));
    // response of api.github.com/user/teams
    github.enqueue(new MockResponse().setBody("[\n" +
      "  {\n" +
      "    \"slug\": \"developers\",\n" +
      "    \"organization\": {\n" +
      "      \"login\": \"SonarSource\"\n" +
      "    }\n" +
      "  }\n" +
      "]"));

    HttpServletRequest request = newRequest("the-verifier-code");
    DumbCallbackContext callbackContext = new DumbCallbackContext(request);
    underTest.callback(callbackContext);

    assertThat(callbackContext.userIdentity.getGroups()).containsOnly("SonarSource/developers");
  }

  @Test
  public void redirect_browser_to_github_authentication_form_with_organizations() throws Exception {
    settings.setProperty("sonar.auth.github.organizations", "example0, example1");
    DumbInitContext context = new DumbInitContext("the-csrf-state");
    underTest.init(context);
    assertThat(context.redirectedTo)
            .startsWith(github.url("login/oauth/authorize").toString())
            .contains("scope=" + URLEncoder.encode("user:email,read:org", StandardCharsets.UTF_8.name()));
  }

  @Test
  public void callback_on_successful_authentication_with_organizations_with_membership() throws IOException, InterruptedException {
    settings.setProperty("sonar.auth.github.organizations", "example0, example1");

    github.enqueue(newSuccessfulAccessTokenResponse());
    // response of api.github.com/user
    github.enqueue(new MockResponse().setBody("{\"login\":\"octocat\", \"name\":\"monalisa octocat\",\"email\":\"octocat@github.com\"}"));
    // response of api.github.com/orgs/example0/members/user
    github.enqueue(new MockResponse().setResponseCode(204));

    HttpServletRequest request = newRequest("the-verifier-code");
    DumbCallbackContext callbackContext = new DumbCallbackContext(request);
    underTest.callback(callbackContext);

    assertThat(callbackContext.csrfStateVerified.get()).isTrue();
    assertThat(callbackContext.userIdentity).isNotNull();
    assertThat(callbackContext.redirectedToRequestedPage.get()).isTrue();
  }

  @Test
  public void callback_on_successful_authentication_with_organizations_without_membership() throws IOException, InterruptedException {
    settings.setProperty("sonar.auth.github.organizations", "example");
    settings.setProperty("sonar.auth.github.loginStrategy", GitHubSettings.LOGIN_STRATEGY_PROVIDER_ID);

    github.enqueue(newSuccessfulAccessTokenResponse());
    // response of api.github.com/user
    github.enqueue(new MockResponse().setBody("{\"login\":\"octocat\", \"name\":\"monalisa octocat\",\"email\":\"octocat@github.com\"}"));
    // response of api.github.com/orgs/example0/members/user
    github.enqueue(new MockResponse().setResponseCode(404).setBody("{}"));

    HttpServletRequest request = newRequest("the-verifier-code");
    DumbCallbackContext callbackContext = new DumbCallbackContext(request);
    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage("'octocat' must be a member of at least one organization: 'example'");
    underTest.callback(callbackContext);
  }

  @Test
  public void callback_throws_ISE_if_error_when_requesting_user_profile() throws IOException, InterruptedException {
    github.enqueue(newSuccessfulAccessTokenResponse());
    // api.github.com/user crashes
    github.enqueue(new MockResponse().setResponseCode(500).setBody("{error}"));

    DumbCallbackContext callbackContext = new DumbCallbackContext(newRequest("the-verifier-code"));
    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage("Fail to execute request");
    expectedException.expectMessage("HTTP code: 500, response: {error}");
    underTest.callback(callbackContext);

    assertThat(callbackContext.csrfStateVerified.get()).isTrue();
    assertThat(callbackContext.userIdentity).isNull();
    assertThat(callbackContext.redirectedToRequestedPage.get()).isFalse();
  }

  /**
   * Response sent by GitHub to SonarQube when generating an access token
   */
  private static MockResponse newSuccessfulAccessTokenResponse() {
    // github does not return the standard JSON format but plain-text
    // see https://developer.github.com/v3/oauth/
    return new MockResponse().setBody("access_token=e72e16c7e42f292c6912e7710c838347ae178b4a&scope=user%2Cgist&token_type=bearer");
  }

  private static HttpServletRequest newRequest(String verifierCode) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameter("code")).thenReturn(verifierCode);
    return request;
  }

  private static class DumbCallbackContext implements OAuth2IdentityProvider.CallbackContext {
    final HttpServletRequest request;
    final AtomicBoolean csrfStateVerified = new AtomicBoolean(false);
    final AtomicBoolean redirectedToRequestedPage = new AtomicBoolean(false);
    UserIdentity userIdentity = null;

    public DumbCallbackContext(HttpServletRequest request) {
      this.request = request;
    }

    @Override
    public void verifyCsrfState() {
      this.csrfStateVerified.set(true);
    }

    @Override
    public void redirectToRequestedPage() {
      redirectedToRequestedPage.set(true);
    }

    @Override
    public void authenticate(UserIdentity userIdentity) {
      this.userIdentity = userIdentity;
    }

    @Override
    public String getCallbackUrl() {
      return CALLBACK_URL;
    }

    @Override
    public HttpServletRequest getRequest() {
      return request;
    }

    @Override
    public HttpServletResponse getResponse() {
      throw new UnsupportedOperationException("not used");
    }
  }

  private static class DumbInitContext implements OAuth2IdentityProvider.InitContext {
    String redirectedTo = null;
    private final String generatedCsrfState;

    public DumbInitContext(String generatedCsrfState) {
      this.generatedCsrfState = generatedCsrfState;
    }

    @Override
    public String generateCsrfState() {
      return generatedCsrfState;
    }

    @Override
    public void redirectTo(String url) {
      this.redirectedTo = url;
    }

    @Override
    public String getCallbackUrl() {
      return CALLBACK_URL;
    }

    @Override
    public HttpServletRequest getRequest() {
      return null;
    }

    @Override
    public HttpServletResponse getResponse() {
      return null;
    }
  }
}
