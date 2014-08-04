/*
* Copyright (C) 2014 SlimRoms Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.android.internal.util.ldroid;

import android.os.SystemProperties;

public class BuildInfo {

    private static final String BUILD_TYPE_PROP = "ro.ldroid.buildtype";
    private static final String LDROID_VERSION_PROP = "ro.ldroid.version";

    public static String getLDroidBuildType() {
        return SystemProperties.get(BUILD_TYPE_PROP, "");
    }

    public static String getLDroidVersion() {
        return SystemProperties.get(LDROID_VERSION_PROP, "");
    }
}
