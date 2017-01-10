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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GsonUserTest {

  @Test
  public void parse_json() throws Exception {
    try (InputStream json = getClass().getResourceAsStream("GsonUserTest/user.json")) {
      GsonUser user = GsonUser.parse(IOUtils.toString(json, StandardCharsets.UTF_8.name()));
      assertThat(user.getLogin()).isEqualTo("octocat");
      assertThat(user.getName()).isEqualTo("monalisa octocat");
      assertThat(user.getEmail()).isEqualTo("octocat@github.com");
    }
  }

  @Test
  public void name_can_be_null() {
    GsonUser underTest = GsonUser.parse("{login:octocat, email:octocat@github.com}");
    assertThat(underTest.getLogin()).isEqualTo("octocat");
    assertThat(underTest.getName()).isNull();
  }

  @Test
  public void email_can_be_null() {
    GsonUser underTest = GsonUser.parse("{login:octocat}");
    assertThat(underTest.getLogin()).isEqualTo("octocat");
    assertThat(underTest.getEmail()).isNull();
  }
}
