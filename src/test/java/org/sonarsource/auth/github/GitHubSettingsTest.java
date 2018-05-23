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

import org.junit.Test;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.config.internal.MapSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.auth.github.GitHubSettings.LOGIN_STRATEGY_DEFAULT_VALUE;
import static org.sonarsource.auth.github.GitHubSettings.LOGIN_STRATEGY_PROVIDER_ID;

public class GitHubSettingsTest {

  Settings settings = new MapSettings(new PropertyDefinitions(GitHubSettings.definitions()));

  GitHubSettings underTest = new GitHubSettings(settings);

  @Test
  public void is_enabled() {
    settings.setProperty("sonar.auth.github.clientId.secured", "id");
    settings.setProperty("sonar.auth.github.clientSecret.secured", "secret");
    settings.setProperty("sonar.auth.github.loginStrategy", LOGIN_STRATEGY_DEFAULT_VALUE);

    settings.setProperty("sonar.auth.github.enabled", true);
    assertThat(underTest.isEnabled()).isTrue();

    settings.setProperty("sonar.auth.github.enabled", false);
    assertThat(underTest.isEnabled()).isFalse();
  }

  @Test
  public void is_enabled_always_return_false_when_client_id_is_null() {
    settings.setProperty("sonar.auth.github.enabled", true);
    settings.setProperty("sonar.auth.github.clientId.secured", (String) null);
    settings.setProperty("sonar.auth.github.clientSecret.secured", "secret");
    settings.setProperty("sonar.auth.github.loginStrategy", LOGIN_STRATEGY_DEFAULT_VALUE);

    assertThat(underTest.isEnabled()).isFalse();
  }

  @Test
  public void is_enabled_always_return_false_when_client_secret_is_null() {
    settings.setProperty("sonar.auth.github.enabled", true);
    settings.setProperty("sonar.auth.github.clientId.secured", "id");
    settings.setProperty("sonar.auth.github.clientSecret.secured", (String) null);
    settings.setProperty("sonar.auth.github.loginStrategy", LOGIN_STRATEGY_DEFAULT_VALUE);

    assertThat(underTest.isEnabled()).isFalse();
  }

  @Test
  public void return_client_id() {
    settings.setProperty("sonar.auth.github.clientId.secured", "id");
    assertThat(underTest.clientId()).isEqualTo("id");
  }

  @Test
  public void return_client_secret() {
    settings.setProperty("sonar.auth.github.clientSecret.secured", "secret");
    assertThat(underTest.clientSecret()).isEqualTo("secret");
  }

  @Test
  public void return_login_strategy() {
    settings.setProperty("sonar.auth.github.loginStrategy", LOGIN_STRATEGY_PROVIDER_ID);
    assertThat(underTest.loginStrategy()).isEqualTo(LOGIN_STRATEGY_PROVIDER_ID);
  }

  @Test
  public void allow_users_to_sign_up() {
    settings.setProperty("sonar.auth.github.allowUsersToSignUp", "true");
    assertThat(underTest.allowUsersToSignUp()).isTrue();

    settings.setProperty("sonar.auth.github.allowUsersToSignUp", "false");
    assertThat(underTest.allowUsersToSignUp()).isFalse();

    // default value
    settings.setProperty("sonar.auth.github.allowUsersToSignUp", (String) null);
    assertThat(underTest.allowUsersToSignUp()).isTrue();
  }

  @Test
  public void sync_groups() {
    settings.setProperty("sonar.auth.github.groupsSync", "true");
    assertThat(underTest.syncGroups()).isTrue();

    settings.setProperty("sonar.auth.github.groupsSync", "false");
    assertThat(underTest.syncGroups()).isFalse();

    // default value
    settings.setProperty("sonar.auth.github.groupsSync", (String) null);
    assertThat(underTest.syncGroups()).isFalse();
  }

  @Test
  public void apiUrl_must_have_ending_slash() {
    settings.setProperty("sonar.auth.github.apiUrl", "https://github.com");
    assertThat(underTest.apiURL()).isEqualTo("https://github.com/");

    settings.setProperty("sonar.auth.github.apiUrl", "https://github.com/");
    assertThat(underTest.apiURL()).isEqualTo("https://github.com/");
  }

  @Test
  public void webUrl_must_have_ending_slash() {
    settings.setProperty("sonar.auth.github.webUrl", "https://github.com");
    assertThat(underTest.webURL()).isEqualTo("https://github.com/");

    settings.setProperty("sonar.auth.github.webUrl", "https://github.com/");
    assertThat(underTest.webURL()).isEqualTo("https://github.com/");
  }

  @Test
  public void return_organizations_single() {
    String setting = "example";
    settings.setProperty("sonar.auth.github.organizations", setting);
    String[] expected = new String[]{"example"};
    String[] actual = underTest.organizations();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void return_organizations_multiple() {
    String setting = "example0,example1";
    settings.setProperty("sonar.auth.github.organizations", setting);
    String[] expected = new String[]{"example0", "example1"};
    String[] actual = underTest.organizations();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void return_organizations_empty_list() {
    String[] setting = null;
    settings.setProperty("sonar.auth.github.organizations", setting);
    String[] expected = new String[]{};
    String[] actual = underTest.organizations();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void definitions() {
    assertThat(GitHubSettings.definitions()).hasSize(9);
  }
}
