package net.tbmcv.tbmmovel;
/*
JsonRestClient.java
Copyright (C) 2016  Daniel Getz

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;

public interface JsonRestClient {
    RequestBuilder buildRequest();

    interface RequestBuilder {
        JSONObject fetch() throws IOException, JSONException;

        RequestBuilder auth(String username, String password);

        RequestBuilder method(String method);

        RequestBuilder body(JSONObject body);

        RequestBuilder toUri(URI uri);

        RequestBuilder toUri(String uri);

        RequestBuilder connectTimeout(int timeout);

        RequestBuilder readTimeout(int timeout);
    }
}
