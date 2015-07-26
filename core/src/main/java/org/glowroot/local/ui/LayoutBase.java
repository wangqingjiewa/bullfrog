/*
 * Copyright 2014-2015 the original author or authors.
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
package org.glowroot.local.ui;

import com.google.common.collect.ImmutableList;
import org.immutables.value.Value;

import org.glowroot.config.AnonymousAccess;
import org.glowroot.config.RollupConfig;
import org.glowroot.config.Versions;

@Value.Immutable
abstract class LayoutBase {

    abstract boolean jvmHeapDump();
    abstract String footerMessage();
    abstract boolean adminPasswordEnabled();
    abstract boolean readOnlyPasswordEnabled();
    abstract AnonymousAccess anonymousAccess();
    abstract ImmutableList<String> transactionTypes();
    abstract String defaultTransactionType();
    abstract ImmutableList<Double> defaultPercentiles();
    abstract ImmutableList<String> transactionCustomAttributes();
    abstract ImmutableList<RollupConfig> rollupConfigs();
    abstract long gaugeCollectionIntervalMillis();

    @Value.Derived
    public String version() {
        return Versions.getVersion(this);
    }
}
