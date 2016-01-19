/*
 * Github Authentication for SonarQube
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

import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Token;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.model.Verifier;
import com.github.scribejava.core.oauth.OAuthService;
import javax.servlet.http.HttpServletRequest;
import org.sonar.api.server.ServerSide;
import org.sonar.api.server.authentication.OAuth2IdentityProvider;
import org.sonar.api.server.authentication.UserIdentity;

@ServerSide
public class GithubIdentityProvider implements OAuth2IdentityProvider {

  private static final Token EMPTY_TOKEN = null;

  private final GithubService service;

  public GithubIdentityProvider(GithubService service) {
    this.service = service;
  }

  @Override
  public String getKey() {
    return "github";
  }

  @Override
  public String getName() {
    return "Github";
  }

  @Override
  public String getIconPath() {
    return "/static/authgithub/github.svg";
  }

  @Override
  public boolean isEnabled() {
    return service.isEnabled();
  }

  @Override
  public void init(InitContext context) {
    String state = context.generateCsrfState();
    OAuthService scribe = service.prepareScribe(context)
      .scope("user")
      .state(state)
      .build();
    String url = scribe.getAuthorizationUrl(EMPTY_TOKEN);
    context.redirectTo(url);
  }

  @Override
  public void callback(CallbackContext context) {
    context.verifyCsrfState();

    HttpServletRequest request = context.getRequest();
    OAuthService scribe = service.prepareScribe(context).build();
    String oAuthVerifier = request.getParameter("code");
    Token accessToken = scribe.getAccessToken(EMPTY_TOKEN, new Verifier(oAuthVerifier));

    OAuthRequest userRequest = new OAuthRequest(Verb.GET, "https://api.github.com/user", scribe);
    scribe.signRequest(accessToken, userRequest);

    com.github.scribejava.core.model.Response userResponse = userRequest.send();
    // TODO test if successful
    GsonUser gsonUser = GsonUser.parse(userResponse.getBody());

    UserIdentity userIdentity = UserIdentity.builder()
      .setId(gsonUser.getLogin())
      .setName(gsonUser.getName())
      .setEmail(gsonUser.getEmail())
      .build();
    context.authenticate(userIdentity);
    context.redirectToRequestedPage();
  }
}
