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

import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class GsonEmailsTest {

  @Test
  public void parse() throws Exception {
    List<GsonEmails.GsonEmail> underTest = GsonEmails.parse(
      "[\n" +
        "  {\n" +
        "    \"email\": \"octocat@github.com\",\n" +
        "    \"verified\": true,\n" +
        "    \"primary\": true\n" +
        "  },\n" +
        "  {\n" +
        "    \"email\": \"support@github.com\",\n" +
        "    \"verified\": false,\n" +
        "    \"primary\": false\n" +
        "  }\n" +
        "]");
    assertThat(underTest).hasSize(2);

    assertThat(underTest.get(0).getEmail()).isEqualTo("octocat@github.com");
    assertThat(underTest.get(0).isVerified()).isTrue();
    assertThat(underTest.get(0).isPrimary()).isTrue();

    assertThat(underTest.get(1).getEmail()).isEqualTo("support@github.com");
    assertThat(underTest.get(1).isVerified()).isFalse();
    assertThat(underTest.get(1).isPrimary()).isFalse();
  }

}
