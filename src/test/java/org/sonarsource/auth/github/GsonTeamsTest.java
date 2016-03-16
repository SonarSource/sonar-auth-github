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

import java.util.List;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GsonTeamsTest {

  @Test
  public void parse_one_team() throws Exception {
    List<GsonTeams.GsonTeam> underTest = GsonTeams.parse(
      "[\n" +
        "  {\n" +
        "    \"name\": \"Developers\",\n" +
        "    \"slug\": \"developers\",\n" +
        "    \"organization\": {\n" +
        "      \"login\": \"SonarSource\"\n" +
        "    }\n" +
        "  }\n" +
        "]");
    assertThat(underTest).hasSize(1);

    assertThat(underTest.get(0).getId()).isEqualTo("developers");
    assertThat(underTest.get(0).getOrganizationId()).isEqualTo("SonarSource");
  }

  @Test
  public void parse_two_teams() throws Exception {
    List<GsonTeams.GsonTeam> underTest = GsonTeams.parse(
      "[\n" +
        "  {\n" +
        "    \"name\": \"Developers\",\n" +
        "    \"slug\": \"developers\",\n" +
        "    \"organization\": {\n" +
        "      \"login\": \"SonarSource\"\n" +
        "    }\n" +
        "  },\n" +
        "  {\n" +
        "    \"login\": \"SonarSource Developers\",\n" +
        "    \"organization\": {\n" +
        "      \"login\": \"SonarQubeCommunity\"\n" +
        "    }\n" +
        "  }\n" +
        "]");
    assertThat(underTest).hasSize(2);
  }

}
