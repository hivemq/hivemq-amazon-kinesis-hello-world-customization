/*
 * Copyright 2023-present HiveMQ GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hivemq.extensions.amazon.kinesis.customizations.helloworld;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.annotations.Nullable;
import com.hivemq.extension.sdk.api.packets.general.Qos;
import com.hivemq.extension.sdk.api.services.builder.PublishBuilder;
import com.hivemq.extensions.amazon.kinesis.api.model.InboundKinesisRecord;
import com.hivemq.extensions.amazon.kinesis.api.transformers.KinesisToMqttInitInput;
import com.hivemq.extensions.amazon.kinesis.api.transformers.KinesisToMqttInput;
import com.hivemq.extensions.amazon.kinesis.api.transformers.KinesisToMqttOutput;
import com.hivemq.extensions.amazon.kinesis.api.transformers.KinesisToMqttTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * This example {@link KinesisToMqttTransformer} accepts an Amazon Kinesis record and creates a new MQTT PUBLISH from
 * it.
 * <p>
 * The example performs the following computational steps:
 * <ol>
 *     <li> Increment a specific metric for encrypted/unencrypted Amazon Kinesis records. </li>
 *     <li> Create a new MQTT publish message that contains the following information: </li>
 *         <ul>
 *             <li> The topic from the source stream name with prefix "from-kinesis/". Example: "from-kinesis/my-source-stream" </li>
 *             <li> The payload from the source data </li>
 *             <li> The qos from the custom settings configuration </li>
 *         </ul>
 *      <li> Provide the MQTT publish message to the extension for publication. </li>
 * </ol>
 * <p>
 * An example `amazon-kinesis-configuration.xml` file that enables this transformer is provided in `{@code src/main/resources}`.
 *
 * @author Mario Schwede
 * @since 4.14.0
 */
public class KinesisToMqttHelloWorldTransformer implements KinesisToMqttTransformer {
    private static final @NotNull Logger LOG = LoggerFactory.getLogger(KinesisToMqttHelloWorldTransformer.class);

    public static final @NotNull String KMS_ENCRYPTED_DATA_COUNTER_NAME =
            "com.hivemq.extensions.amazon-kinesis.customizations.kinesis-to-mqtt.transformer.kms-encrypted-data.count";
    public static final @NotNull String NOT_ENCRYPTED_DATA_COUNTER_NAME =
            "com.hivemq.extensions.amazon-kinesis.customizations.kinesis-to-mqtt.transformer.not-encrypted-data.count";

    private @Nullable Qos qos;

    @SuppressWarnings("NotNullFieldNotInitialized")
    private @NotNull Counter notEncryptedCounter;

    @SuppressWarnings("NotNullFieldNotInitialized")
    private @NotNull Counter kmsEncryptedCounter;

    @Override
    public void init(final @NotNull KinesisToMqttInitInput kinesisToMqttInitInput) {
        final MetricRegistry metricRegistry = kinesisToMqttInitInput.getMetricRegistry();
        notEncryptedCounter = metricRegistry.counter(NOT_ENCRYPTED_DATA_COUNTER_NAME);
        kmsEncryptedCounter = metricRegistry.counter(KMS_ENCRYPTED_DATA_COUNTER_NAME);

        try {
            qos = kinesisToMqttInitInput.getCustomSettings()
                    .getFirst("qos")
                    .map(Integer::parseInt)
                    .map(Qos::valueOf)
                    .orElse(null);
        } catch (final RuntimeException e) {
            LOG.debug("Could not parse the QoS from custom settings. Using QoS 0. ", e);
            qos = Qos.AT_MOST_ONCE;

        }
        LOG.info("{} initialized with QoS {}.",
                KinesisToMqttHelloWorldTransformer.class.getSimpleName(),
                qos != null ? "'" + qos + "'" : "not configured");
    }

    @Override
    public void transformKinesisToMqtt(
            final @NotNull KinesisToMqttInput kinesisToMqttInput,
            final @NotNull KinesisToMqttOutput kinesisToMqttOutput) {
        try {
            final InboundKinesisRecord inboundKinesisRecord = kinesisToMqttInput.getInboundKinesisRecord();

            if ("KMS".equals(inboundKinesisRecord.getEncryptionType())) {
                kmsEncryptedCounter.inc();
            } else {
                notEncryptedCounter.inc();
            }

            final PublishBuilder publishBuilder = kinesisToMqttOutput.newPublishBuilder() //
                    .topic("from-kinesis/" + inboundKinesisRecord.getStreamName()) //
                    .payload(inboundKinesisRecord.getData());

            if (qos != null) {
                publishBuilder.qos(qos);
            }

            kinesisToMqttOutput.setPublishes(List.of(publishBuilder.build()));
        } catch (final Exception e) {
            LOG.error("Amazon Kinesis to MQTT transformation failed: ", e);
        }
    }
}
