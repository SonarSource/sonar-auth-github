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

import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.extractors.OAuth2AccessTokenExtractor;
import com.github.scribejava.core.extractors.TokenExtractor;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthConfig;
import com.github.scribejava.core.model.Verb;
import java.util.Map;
import org.sonar.api.server.ServerSide;

import static java.util.Objects.requireNonNull;

@ServerSide
public class ScribeGitHubApi extends DefaultApi20 {
  private final GitHubSettings settings;

  public ScribeGitHubApi(GitHubSettings settings) {
    this.settings = settings;
  }

  @Override
  public Verb getAccessTokenVerb() {
    return Verb.POST;
  }

  @Override
  public String getAccessTokenEndpoint() {
    return settings.webURL() + "login/oauth/access_token";
  }

  @Override
  protected String getAuthorizationBaseUrl() {
    return settings.webURL() + "login/oauth/authorize";
  }

  @Override
  public TokenExtractor<OAuth2AccessToken> getAccessTokenExtractor() {
    return OAuth2AccessTokenExtractor.instance();
  }

  @Override
  public String getAuthorizationUrl(OAuthConfig config, Map<String, String> additionalParams) {
    requireNonNull(config.getCallback(), "URL for callback should not be null.");
    requireNonNull(config.getApiKey(), "ApiKey should not be null.");
    requireNonNull(config.getScope(), "Scope should not be null.");
    requireNonNull(config.getState(), "State should not be null.");
    return super.getAuthorizationUrl(config, additionalParams);
  }

}
