/*
 * GitHub Authentication for SonarQube
 * Copyright (C) 2016-2019 SonarSource SA
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
import javax.annotation.Nullable;
import org.sonar.api.server.ServerSide;
import org.sonar.api.server.authentication.UserIdentity;

/**
 * Converts GitHub JSON response to {@link UserIdentity}
 */
@ServerSide
public interface UserIdentityFactory {

  UserIdentity create(GsonUser user, @Nullable String email, @Nullable List<GsonTeams.GsonTeam> teams);
}
