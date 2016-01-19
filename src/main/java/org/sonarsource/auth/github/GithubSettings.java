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

import java.util.Arrays;
import java.util.List;
import javax.annotation.CheckForNull;
import org.sonar.api.PropertyType;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.Settings;
import org.sonar.api.server.ServerSide;

@ServerSide
public class GithubSettings {

  public static final String CLIENT_ID = "sonar.auth.github.clientId";
  public static final String CLIENT_SECRET = "sonar.auth.github.clientSecret";
  public static final String ENABLED = "sonar.auth.github.enabled";
  public static final String ALLOW_USERS_TO_SIGN_UP = "sonar.auth.github.allowUsersToSignUp";
  // TODO fix category
  public static final String CATEGORY = "Github";

  private final Settings settings;

  public GithubSettings(Settings settings) {
    this.settings = settings;
  }

  @CheckForNull
  public String clientId() {
    return settings.getString(CLIENT_ID);
  }

  @CheckForNull
  public String clientSecret() {
    return settings.getString(CLIENT_SECRET);
  }

  public boolean isEnabled() {
    return settings.getBoolean(ENABLED) && clientId() != null && clientSecret() != null;
  }

  public boolean allowUsersToSignUp() {
    return settings.getBoolean(ALLOW_USERS_TO_SIGN_UP);
  }

  public static List<PropertyDefinition> definitions() {
    return Arrays.asList(
      PropertyDefinition.builder(ENABLED)
        .name("Enabled")
        .description("Enable Github users to login. Value is ignored if client ID and secret are not defined.")
        .category(CATEGORY)
        .type(PropertyType.BOOLEAN)
        .defaultValue(String.valueOf(false))
        .index(1)
        .build(),
      PropertyDefinition.builder(ENABLED)
        .name("Allow users to sign-up")
        .description("TODO")
        .category(CATEGORY)
        .type(PropertyType.BOOLEAN)
        .defaultValue(String.valueOf(true))
        .index(2)
        .build(),
      PropertyDefinition.builder(CLIENT_ID)
        .name("Client ID")
        .description("TODO")
        .category(CATEGORY)
        .index(3)
        .build(),
      PropertyDefinition.builder(CLIENT_SECRET)
        .name("Client Secret")
        .description("TODO")
        .category(CATEGORY)
        .index(4)
        .build()
      );
  }
}
