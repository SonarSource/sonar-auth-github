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

import com.github.scribejava.apis.GitHubApi;
import com.github.scribejava.core.builder.ServiceBuilder;
import javax.annotation.CheckForNull;
import org.sonar.api.config.Settings;
import org.sonar.api.server.ServerSide;
import org.sonar.api.server.authentication.OAuth2IdentityProvider;

@ServerSide
public class GithubService {

  private final Settings settings;

  public GithubService(Settings settings) {
    this.settings = settings;
  }

  public ServiceBuilder prepareScribe(OAuth2IdentityProvider.OAuth2Context context) {
    if (!isEnabled()) {
      throw new IllegalStateException("Github Authentication is disabled");
    }
    return new ServiceBuilder()
      .provider(GitHubApi.class)
      .apiKey(clientId())
      .apiSecret(clientSecret())
      .callback(context.getCallbackUrl());
  }

  @CheckForNull
  private String clientId() {
    return settings.getString(GithubProperties.CLIENT_ID);
  }

  @CheckForNull
  private String clientSecret() {
    return settings.getString(GithubProperties.CLIENT_SECRET);
  }

  public boolean isEnabled() {
    return settings.getBoolean(GithubProperties.ENABLED) && clientId() != null && clientSecret() != null;
  }
}
