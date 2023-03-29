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

import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.packets.publish.PublishPacket;
import com.hivemq.extensions.amazon.kinesis.api.builders.OutboundKinesisRecordBuilder;
import com.hivemq.extensions.amazon.kinesis.api.model.OutboundKinesisRecord;
import com.hivemq.extensions.amazon.kinesis.api.transformers.MqttToKinesisInitInput;
import com.hivemq.extensions.amazon.kinesis.api.transformers.MqttToKinesisInput;
import com.hivemq.extensions.amazon.kinesis.api.transformers.MqttToKinesisOutput;
import com.hivemq.extensions.amazon.kinesis.api.transformers.MqttToKinesisTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * This example {@link MqttToKinesisTransformer} accepts an MQTT PUBLISH and creates a new Amazon Kinesis record from
 * it.
 * <p>
 * The example performs the following computational steps:
 * <ol>
 *     <li> Read the Amazon Kinesis stream names from the `destination` custom setting. </li>
 *     <li> Create a new Amazon Kinesis record for each destination Amazon Kinesis stream that contains the following information: </li>
 *         <ul>
 *             <li> The destination as stream name. </li>
 *             <li> The payload as data. </li>
 *             <li> The MQTT topic as partition key. </li>
 *             <li> A random explicit hash key. </li>
 *         </ul>
 *      <li> Provide the records to the extension for sending. </li>
 * </ol>
 * <p>
 * An example `amazon-kinesis-configuration.xml` file that enables this transformer is provided in `{@code src/main/resources}`.
 *
 * @author Mario Schwede
 * @since 4.14.0
 */
public class MqttToKinesisHelloWorldTransformer implements MqttToKinesisTransformer {
    private static final @NotNull Logger LOG = LoggerFactory.getLogger(MqttToKinesisHelloWorldTransformer.class);

    private @NotNull List<String> destinationStreamNames = List.of();

    @Override
    public void init(final @NotNull MqttToKinesisInitInput mqttToKinesisInitInput) {
        destinationStreamNames = mqttToKinesisInitInput.getCustomSettings().getAllForName("destination");
        LOG.info("{} initialized. Destinations: {}.",
                MqttToKinesisHelloWorldTransformer.class.getSimpleName(),
                String.join(", ", destinationStreamNames));
    }

    @Override
    public void transformMqttToKinesis(
            final @NotNull MqttToKinesisInput mqttToKinesisInput,
            final @NotNull MqttToKinesisOutput mqttToKinesisOutput) {
        try {
            final PublishPacket publishPacket = mqttToKinesisInput.getPublishPacket();
            final String mqttTopic = publishPacket.getTopic();
            final ByteBuffer payload = publishPacket.getPayload().orElseGet(() -> ByteBuffer.allocate(0));

            final List<OutboundKinesisRecord> outboundKinesisRecords = new ArrayList<>(destinationStreamNames.size());
            for (final String destinationStreamName : destinationStreamNames) {
                try {
                    final OutboundKinesisRecordBuilder builder = mqttToKinesisOutput.newOutboundKinesisRecordBuilder()
                            .streamName(destinationStreamName)
                            .data(payload)
                            .partitionKey(mqttTopic)
                            .randomExplicitHashKey();
                    outboundKinesisRecords.add(builder.build());
                } catch (final Exception e) {
                    LOG.error("Could not create an Amazon Kinesis record from MQTT publish with topic '{}' because",
                            mqttTopic,
                            e);
                }
            }
            mqttToKinesisOutput.setOutboundKinesisRecords(outboundKinesisRecords);
        } catch (final Exception e) {
            LOG.error("MQTT to Amazon Kinesis transformation failed: ", e);
        }
    }
}
