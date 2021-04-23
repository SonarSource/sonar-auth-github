/*
 * GitHub Authentication for SonarQube
 * Copyright (C) 2016-2021 SonarSource SA
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
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonar.api.SonarRuntime;
import org.sonar.api.server.authentication.UserIdentity;
import org.sonar.api.utils.Version;

import static org.sonarsource.auth.github.UserIdentityGenerator.generateLogin;
import static org.sonarsource.auth.github.UserIdentityGenerator.generateName;

public class UserIdentityFactoryImpl implements UserIdentityFactory {

  private final GitHubSettings settings;
  private final SonarRuntime sonarRuntime;

  public UserIdentityFactoryImpl(GitHubSettings settings, SonarRuntime sonarRuntime) {
    this.settings = settings;
    this.sonarRuntime = sonarRuntime;
  }

  @Override
  public UserIdentity create(GsonUser user, @Nullable String email, @Nullable List<GsonTeams.GsonTeam> teams) {
    UserIdentity.Builder builder = UserIdentity.builder()
      .setProviderLogin(user.getLogin())
      .setLogin(generateLogin(user, settings.loginStrategy()))
      .setName(generateName(user))
      .setEmail(email);
    if (teams != null) {
      builder.setGroups(teams.stream()
        .map(team -> team.getOrganizationId() + "/" + team.getId())
        .collect(Collectors.toSet()));
    }
    // provider id is not set as this method was added in SonarQube 7.2
    if (sonarRuntime.getApiVersion().isGreaterThanOrEqual(Version.create(7, 2))) {
      builder.setProviderId(user.getId());
    }
    return builder.build();
  }

}
