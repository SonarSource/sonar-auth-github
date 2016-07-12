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

import java.util.Arrays;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.Settings;
import org.sonar.api.server.ServerSide;

import static java.lang.String.format;
import static java.lang.String.valueOf;
import static org.sonar.api.PropertyType.BOOLEAN;
import static org.sonar.api.PropertyType.SINGLE_SELECT_LIST;
import static org.sonar.api.PropertyType.STRING;

@ServerSide
public class GitHubSettings {

  private static final String CLIENT_ID = "sonar.auth.github.clientId.secured";
  private static final String CLIENT_SECRET = "sonar.auth.github.clientSecret.secured";
  private static final String ENABLED = "sonar.auth.github.enabled";
  private static final String ALLOW_USERS_TO_SIGN_UP = "sonar.auth.github.allowUsersToSignUp";
  private static final String GROUPS_SYNC = "sonar.auth.github.groupsSync";
  private static final String API_URL = "sonar.auth.github.apiUrl";
  private static final String WEB_URL = "sonar.auth.github.webUrl";

  static final String LOGIN_STRATEGY = "sonar.auth.github.loginStrategy";
  static final String LOGIN_STRATEGY_UNIQUE = "Unique";
  static final String LOGIN_STRATEGY_PROVIDER_ID = "Same as GitHub login";
  static final String LOGIN_STRATEGY_DEFAULT_VALUE = LOGIN_STRATEGY_UNIQUE;

  private static final String ORGANIZATIONS = "sonar.auth.github.organizations";

  private static final String CATEGORY = "github";
  private static final String SUBCATEGORY = "authentication";

  private final Settings settings;

  public GitHubSettings(Settings settings) {
    this.settings = settings;
  }

  public String clientId() {
    return emptyIfNull(settings.getString(CLIENT_ID));
  }

  public String clientSecret() {
    return emptyIfNull(settings.getString(CLIENT_SECRET));
  }

  public boolean isEnabled() {
    return settings.getBoolean(ENABLED) && !clientId().isEmpty() && !clientSecret().isEmpty();
  }

  public boolean allowUsersToSignUp() {
    return settings.getBoolean(ALLOW_USERS_TO_SIGN_UP);
  }

  public String loginStrategy() {
    return emptyIfNull(settings.getString(LOGIN_STRATEGY));
  }

  public boolean syncGroups() {
    return settings.getBoolean(GROUPS_SYNC);
  }

  @CheckForNull
  public String webURL() {
    return urlWithEndingSlash(settings.getString(WEB_URL));
  }

  @CheckForNull
  public String apiURL() {
    return urlWithEndingSlash(settings.getString(API_URL));
  }

  public String[] organizations() {
    return settings.getStringArray(ORGANIZATIONS);
  }

  @CheckForNull
  private static String urlWithEndingSlash(@Nullable String url) {
    if (url != null && !url.endsWith("/")) {
      return url + "/";
    }
    return url;
  }

  private static String emptyIfNull(@Nullable  String s) {
    return s == null ? "" : s;
  }

  public static List<PropertyDefinition> definitions() {
    int index = 1;
    return Arrays.asList(
      PropertyDefinition.builder(ENABLED)
        .name("Enabled")
        .description("Enable GitHub users to login. Value is ignored if client ID and secret are not defined.")
        .category(CATEGORY)
        .subCategory(SUBCATEGORY)
        .type(BOOLEAN)
        .defaultValue(valueOf(false))
        .index(index++)
        .build(),
      PropertyDefinition.builder(CLIENT_ID)
        .name("Client ID")
        .description("Client ID provided by GitHub when registering the application.")
        .category(CATEGORY)
        .subCategory(SUBCATEGORY)
        .index(index++)
        .build(),
      PropertyDefinition.builder(CLIENT_SECRET)
        .name("Client Secret")
        .description("Client password provided by GitHub when registering the application.")
        .category(CATEGORY)
        .subCategory(SUBCATEGORY)
        .index(index++)
        .build(),
      PropertyDefinition.builder(ALLOW_USERS_TO_SIGN_UP)
        .name("Allow users to sign-up")
        .description("Allow new users to authenticate. When set to 'false', only existing users will be able to authenticate to the server.")
        .category(CATEGORY)
        .subCategory(SUBCATEGORY)
        .type(BOOLEAN)
        .defaultValue(valueOf(true))
        .index(index++)
        .build(),
      PropertyDefinition.builder(LOGIN_STRATEGY)
        .name("Login generation strategy")
        .description(format("When the login strategy is set to '%s', the user's login will be auto-generated the first time so that it is unique. " +
          "When the login strategy is set to '%s', the user's login will be the GitHub login.",
          LOGIN_STRATEGY_UNIQUE, LOGIN_STRATEGY_PROVIDER_ID))
        .category(CATEGORY)
        .subCategory(SUBCATEGORY)
        .type(SINGLE_SELECT_LIST)
        .defaultValue(LOGIN_STRATEGY_DEFAULT_VALUE)
        .options(LOGIN_STRATEGY_UNIQUE, LOGIN_STRATEGY_PROVIDER_ID)
        .index(index++)
        .build(),
      PropertyDefinition.builder(GROUPS_SYNC)
        .name("Synchronize teams as groups")
        .description("For each team he belongs to, the user will be associated to a group named 'Organisation/Team' (if it exists) in SonarQube.")
        .category(CATEGORY)
        .subCategory(SUBCATEGORY)
        .type(BOOLEAN)
        .defaultValue(valueOf(false))
        .index(index++)
        .build(),
      PropertyDefinition.builder(API_URL)
        .name("The API url for a GitHub instance.")
        .description("The API url for a GitHub instance. https://api.github.com/ for github.com, https://github.company.com/api/v3/ when using Github Enterprise")
        .category(CATEGORY)
        .subCategory(SUBCATEGORY)
        .type(STRING)
        .defaultValue(valueOf("https://api.github.com/"))
        .index(index++)
        .build(),
      PropertyDefinition.builder(WEB_URL)
        .name("The WEB url for a GitHub instance.")
        .description("The WEB url for a GitHub instance. " +
          "https://github.com/ for github.com, https://github.company.com/ when using GitHub Enterprise.")
        .category(CATEGORY)
        .subCategory(SUBCATEGORY)
        .type(STRING)
        .defaultValue(valueOf("https://github.com/"))
        .index(index++)
        .build(),
      PropertyDefinition.builder(ORGANIZATIONS)
        .name("Organizations")
        .description("Only members of these organizations will be able to authenticate to the server. " +
          "If a user is a member of any of the organizations listed they will be authenticated.")
        .multiValues(true)
        .category(CATEGORY)
        .subCategory(SUBCATEGORY)
        .index(index++)
        .build());
  }
}
