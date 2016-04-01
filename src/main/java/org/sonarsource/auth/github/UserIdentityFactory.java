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

import com.google.common.base.Function;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.sonar.api.server.ServerSide;
import org.sonar.api.server.authentication.UserIdentity;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.FluentIterable.from;
import static java.lang.String.format;
import static org.sonarsource.auth.github.GitHubSettings.LOGIN_STRATEGY_PROVIDER_ID;
import static org.sonarsource.auth.github.GitHubSettings.LOGIN_STRATEGY_UNIQUE;

/**
 * Converts GitHub JSON response to {@link UserIdentity}
 */
@ServerSide
public class UserIdentityFactory {

  private final GitHubSettings settings;

  public UserIdentityFactory(GitHubSettings settings) {
    this.settings = settings;
  }

  public UserIdentity create(GsonUser gson, @Nullable List<GsonTeams.GsonTeam> teams) {
    UserIdentity.Builder builder = UserIdentity.builder()
      .setProviderLogin(gson.getLogin())
      .setLogin(generateLogin(gson))
      .setName(generateName(gson))
      .setEmail(gson.getEmail());
    if (teams != null) {
      builder.setGroups(from(teams).transform(TeamToGroup.INSTANCE).toSet());
    }
    return builder.build();
  }

  private String generateLogin(GsonUser gsonUser) {
    switch (settings.loginStrategy()) {
      case LOGIN_STRATEGY_PROVIDER_ID:
        return gsonUser.getLogin();
      case LOGIN_STRATEGY_UNIQUE:
        return generateUniqueLogin(gsonUser);
      default:
        throw new IllegalStateException(format("Login strategy not supported : %s", settings.loginStrategy()));
    }
  }

  private static String generateName(GsonUser gson) {
    String name = gson.getName();
    return isNullOrEmpty(name) ? gson.getLogin() : name;
  }

  private static String generateUniqueLogin(GsonUser gsonUser) {
    return format("%s@%s", gsonUser.getLogin(), GitHubIdentityProvider.KEY);
  }

  private enum TeamToGroup implements Function<GsonTeams.GsonTeam, String> {
    INSTANCE;

    @Override
    public String apply(@Nonnull GsonTeams.GsonTeam gsonTeam) {
      return gsonTeam.getOrganizationId() + "/" + gsonTeam.getId();
    }
  }
}
