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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonar.api.server.ServerSide;
import org.sonar.api.server.authentication.UserIdentity;

import static java.lang.String.format;
import static org.sonarsource.auth.github.GitHubSettings.LOGIN_STRATEGY_PROVIDER_ID;
import static org.sonarsource.auth.github.GitHubSettings.LOGIN_STRATEGY_UNIQUE;
import static org.sonarsource.auth.github.GsonEmails.GsonEmail;

/**
 * Converts GitHub JSON response to {@link UserIdentity}
 */
@ServerSide
public class UserIdentityFactory {

  private final GitHubSettings settings;

  public UserIdentityFactory(GitHubSettings settings) {
    this.settings = settings;
  }

  public UserIdentity create(GsonUser user, List<GsonEmail> emails, @Nullable List<GsonTeams.GsonTeam> teams) {
    Set<String> secondaryEmails = emails.stream()
      .filter(GsonEmail::isVerified)
      .filter(email -> !email.isPrimary())
      .map(GsonEmail::getEmail)
      .collect(Collectors.toSet());
    String userEmail = user.getEmail();
    // if the user has not specified a public email address in their profile
    // TODO add unit tests
    String primaryEmail = userEmail != null ? userEmail
      : emails.stream()
        .filter(GsonEmail::isPrimary)
        .filter(GsonEmail::isVerified)
        .findFirst()
        .map(GsonEmail::getEmail)
        .orElse(null);
    UserIdentity.Builder builder = UserIdentity.builder()
      .setProviderLogin(user.getLogin())
      .setLogin(generateLogin(user))
      .setName(generateName(user))
      .setEmail(primaryEmail)
      .setSecondaryEmails(secondaryEmails);
    if (teams != null) {
      builder.setGroups(teams.stream()
        .map(team -> team.getOrganizationId() + "/" + team.getId())
        .collect(Collectors.toSet()));
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
    return name == null || name.isEmpty() ? gson.getLogin() : name;
  }

  private static String generateUniqueLogin(GsonUser gsonUser) {
    return format("%s@%s", gsonUser.getLogin(), GitHubIdentityProvider.KEY);
  }

}
