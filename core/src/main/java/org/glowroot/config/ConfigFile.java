/*
 * Copyright 2013-2014 the original author or authors.
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
package org.glowroot.config;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import org.immutables.common.marshal.Marshaling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.common.JavaVersion;
import org.glowroot.markers.OnlyUsedByTests;

class ConfigFile {

    private static final Logger logger = LoggerFactory.getLogger(ConfigFile.class);

    private static final String NEWLINE;

    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        String newline = StandardSystemProperty.LINE_SEPARATOR.value();
        if (newline == null) {
            NEWLINE = "\n";
        } else {
            NEWLINE = newline;
        }
    }

    private final File file;
    private final ImmutableList<PluginDescriptor> pluginDescriptors;

    ConfigFile(File file, List<PluginDescriptor> pluginDescriptors) {
        this.file = file;
        // sorted by id for writing to config file
        this.pluginDescriptors =
                PluginDescriptor.orderingById.immutableSortedCopy(pluginDescriptors);
    }

    private Config readValue(String content) throws IOException {
        Config config = Marshaling.fromJson(content, Config.class);
        UserInterfaceConfig userInterfaceConfig = config.userInterfaceConfig();
        if (userInterfaceConfig.defaultTransactionType().isEmpty()) {
            userInterfaceConfig = ((ImmutableUserInterfaceConfig) userInterfaceConfig)
                    .withDefaultTransactionType(getDefaultTransactionType(config.capturePoints()));
            config = ((ImmutableConfig) config).withUserInterfaceConfig(userInterfaceConfig);
        }
        if (!mapper.readTree(content).has("gauges")) {
            List<Gauge> defaultGauges = getDefaultGauges();
            config = ((ImmutableConfig) config).withGauges(defaultGauges);
        }
        Map<String, PluginConfig> filePluginConfigs = Maps.newHashMap();
        for (PluginConfig pluginConfig : config.pluginConfigs()) {
            filePluginConfigs.put(pluginConfig.id(), pluginConfig);
        }
        List<PluginConfig> pluginConfigs = Lists.newArrayList();
        for (PluginDescriptor pluginDescriptor : pluginDescriptors) {
            PluginConfig filePluginConfig = filePluginConfigs.get(pluginDescriptor.id());
            ImmutablePluginConfig.Builder builder = ImmutablePluginConfig.builder()
                    .id(pluginDescriptor.id());
            if (filePluginConfig != null) {
                builder.enabled(filePluginConfig.enabled());
            }
            for (PropertyDescriptor propertyDescriptor : pluginDescriptor.properties()) {
                String propertyName = propertyDescriptor.name();
                PropertyValue filePropertyValue = null;
                if (filePluginConfig != null) {
                    filePropertyValue = filePluginConfig.getValidatedPropertyValue(propertyName,
                            propertyDescriptor.type());
                }
                if (filePropertyValue == null) {
                    builder.putProperties(propertyName,
                            propertyDescriptor.getValidatedNonNullDefaultValue());
                } else {
                    builder.putProperties(propertyName, filePropertyValue);
                }
            }
            pluginConfigs.add(builder.build());
        }
        return ((ImmutableConfig) config).withPluginConfigs(pluginConfigs);
    }

    Config loadConfig() throws IOException {
        if (!file.exists()) {
            Config config = getDefaultConfig();
            write(config);
            return config;
        }
        String content = Files.toString(file, Charsets.UTF_8);
        Config config;
        String warningMessage = null;
        try {
            config = readValue(content);
        } catch (Exception e) {
            // immutables json processing wraps IOExceptions inside RuntimeExceptions so can't rely
            // on just catching IOException here
            logger.warn("error processing config file: {}", file.getAbsolutePath(), e);
            File backupFile = new File(file.getParentFile(), file.getName() + ".invalid-orig");
            config = getDefaultConfig();
            try {
                Files.copy(file, backupFile);
                warningMessage = "due to an error in the config file, it has been backed up to"
                        + " extension '.invalid-orig' and overwritten with the default config";
            } catch (IOException f) {
                logger.warn("error making a copy of the invalid config file before overwriting it",
                        f);
                warningMessage = "due to an error in the config file, it has been overwritten with"
                        + " the default config";
            }
        }
        // it's nice to update config.json on startup if it is missing some/all config
        // properties so that the file contents can be reviewed/updated/copied if desired
        writeToFileIfNeeded(config, content);
        if (warningMessage != null) {
            logger.warn(warningMessage);
        }
        return config;
    }

    private String getDefaultTransactionType(List<CapturePoint> capturePoints) {
        for (PluginDescriptor descriptor : pluginDescriptors) {
            if (!descriptor.transactionTypes().isEmpty()) {
                return descriptor.transactionTypes().get(0);
            }
        }
        for (CapturePoint capturePoint : capturePoints) {
            if (!capturePoint.transactionType().isEmpty()) {
                return capturePoint.transactionType();
            }
        }
        return "";
    }

    private void writeToFileIfNeeded(Config config, String existingContent) throws IOException {
        String content = ConfigFile.writeValueAsString(config);
        if (content.equals(existingContent)) {
            // it's nice to preserve the correct modification stamp on the file to track when it was
            // last really changed
            return;
        }
        Files.write(content, file, Charsets.UTF_8);
    }

    Config getDefaultConfig() {
        return ImmutableConfig.builder()
                .addAllPluginConfigs(getDefaultPluginConfigs(pluginDescriptors))
                .addAllGauges(getDefaultGauges())
                .build();
    }

    void write(Config config) throws IOException {
        Files.write(writeValueAsString(config), file, Charsets.UTF_8);
    }

    private static List<Gauge> getDefaultGauges() {
        List<Gauge> defaultGauges = Lists.newArrayList();
        defaultGauges.add(ImmutableGauge.builder()
                .name("java.lang/Memory")
                .mbeanObjectName("java.lang:type=Memory")
                .addMbeanAttributeNames("HeapMemoryUsage.used")
                .build());
        defaultGauges.add(ImmutableGauge.builder()
                .name("java.lang/MemoryPool/PS Old Gen")
                .mbeanObjectName("java.lang:type=MemoryPool,name=PS Old Gen")
                .addMbeanAttributeNames("Usage.used")
                .build());
        defaultGauges.add(ImmutableGauge.builder()
                .name("java.lang/MemoryPool/PS Eden Space")
                .mbeanObjectName("java.lang:type=MemoryPool,name=PS Eden Space")
                .addMbeanAttributeNames("Usage.used")
                .build());
        ImmutableGauge.Builder operatingSystemMBean = ImmutableGauge.builder()
                .name("java.lang/OperatingSystem")
                .mbeanObjectName("java.lang:type=OperatingSystem")
                .addMbeanAttributeNames("FreePhysicalMemorySize");
        if (!JavaVersion.isJdk6()) {
            // these are only available since 1.7
            operatingSystemMBean.addMbeanAttributeNames("ProcessCpuLoad");
            operatingSystemMBean.addMbeanAttributeNames("SystemCpuLoad");
        }
        defaultGauges.add(operatingSystemMBean.build());
        return defaultGauges;
    }

    private static String writeValueAsString(Config config) throws IOException {
        CustomPrettyPrinter prettyPrinter = new CustomPrettyPrinter();
        prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        StringBuilder sb = new StringBuilder();
        JsonGenerator jg = mapper.getFactory().createGenerator(CharStreams.asWriter(sb))
                .setPrettyPrinter(prettyPrinter);
        ConfigMarshaler.marshal(jg, config);
        jg.close();
        // newline is not required, just a personal preference
        return sb.toString() + NEWLINE;
    }

    private static List<PluginConfig> getDefaultPluginConfigs(
            List<PluginDescriptor> pluginDescriptors) {
        List<PluginConfig> pluginConfigs = Lists.newArrayList();
        for (PluginDescriptor pluginDescriptor : pluginDescriptors) {
            ImmutablePluginConfig.Builder builder = ImmutablePluginConfig.builder()
                    .id(pluginDescriptor.id())
                    .enabled(true);
            for (PropertyDescriptor propertyDescriptor : pluginDescriptor.properties()) {
                PropertyValue defaultValue = propertyDescriptor.getValidatedNonNullDefaultValue();
                builder.putProperties(propertyDescriptor.name(), defaultValue);
            }
            pluginConfigs.add(builder.build());
        }
        return pluginConfigs;
    }

    @SuppressWarnings("serial")
    private static class CustomPrettyPrinter extends DefaultPrettyPrinter {

        @Override
        public void writeObjectFieldValueSeparator(JsonGenerator jg) throws IOException {
            jg.writeRaw(": ");
        }
    }

    @OnlyUsedByTests
    void delete() throws IOException {
        if (!file.delete()) {
            throw new IOException("Could not delete file: " + file.getCanonicalPath());
        }
    }
}