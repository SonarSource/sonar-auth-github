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

import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.model.OAuthConfig;
import com.github.scribejava.core.utils.OAuthEncoder;
import com.github.scribejava.core.utils.Preconditions;

class GithubWithConfigurableURL extends DefaultApi20 {
    private final GitHubSettings settings;

    public GithubWithConfigurableURL(GitHubSettings settings) {
        this.settings = settings;
    }

    @Override
    public String getAccessTokenEndpoint() {
        return settings.webURL() + "login/oauth/access_token";
    }

    @Override
    public String getAuthorizationUrl(OAuthConfig config) {
        //originally from https://github.com/scribejava/scribejava
        Preconditions.checkValidUrl(config.getCallback(), "Must provide a valid url as callback. GitHub does not support OOB");
        StringBuilder sb = new StringBuilder(String.format(settings.webURL() + "login/oauth/authorize?client_id=%s&redirect_uri=%s", new Object[]{config.getApiKey(), OAuthEncoder.encode(config.getCallback())}));
        if (config.hasScope()) {
            sb.append('&').append("scope").append('=').append(OAuthEncoder.encode(config.getScope()));
        }

        String state = config.getState();
        if (state != null) {
            sb.append('&').append("state").append('=').append(OAuthEncoder.encode(state));
        }

        return sb.toString();
    }
}
