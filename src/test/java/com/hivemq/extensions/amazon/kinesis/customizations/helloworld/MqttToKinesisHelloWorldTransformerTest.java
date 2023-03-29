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
import com.hivemq.extensions.amazon.kinesis.api.model.CustomSettings;
import com.hivemq.extensions.amazon.kinesis.api.model.OutboundKinesisRecord;
import com.hivemq.extensions.amazon.kinesis.api.transformers.MqttToKinesisInitInput;
import com.hivemq.extensions.amazon.kinesis.api.transformers.MqttToKinesisInput;
import com.hivemq.extensions.amazon.kinesis.api.transformers.MqttToKinesisOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Mario Schwede
 * @since 4.14.0
 */
class MqttToKinesisHelloWorldTransformerTest {
    private final @NotNull MqttToKinesisHelloWorldTransformer transformer = new MqttToKinesisHelloWorldTransformer();

    private final @NotNull CustomSettings customSettings = mock(CustomSettings.class);
    private final @NotNull MqttToKinesisInitInput initInput = mock(MqttToKinesisInitInput.class);
    private final @NotNull PublishPacket publishPacket = mock(PublishPacket.class);
    private final @NotNull MqttToKinesisInput transformInput = mock(MqttToKinesisInput.class);
    private final @NotNull MqttToKinesisOutput transformerOutput = mock(MqttToKinesisOutput.class);
    private final @NotNull OutboundKinesisRecordBuilder builder = mock(OutboundKinesisRecordBuilder.class);
    private final @NotNull OutboundKinesisRecord outboundKinesisRecord = mock(OutboundKinesisRecord.class);
    private final @NotNull ByteBuffer data = StandardCharsets.UTF_8.encode("test-data");
    private final @NotNull String mqttTopic = "test-topic";

    @BeforeEach
    void beforeEach() {
        when(initInput.getCustomSettings()).thenReturn(customSettings);

        when(publishPacket.getPayload()).thenReturn(Optional.of(data));
        when(publishPacket.getTopic()).thenReturn(mqttTopic);

        when(transformInput.getCustomSettings()).thenReturn(customSettings);
        when(transformInput.getPublishPacket()).thenReturn(publishPacket);

        when(builder.streamName(any())).thenReturn(builder);
        when(builder.data(any(ByteBuffer.class))).thenReturn(builder);
        when(builder.partitionKey(any())).thenReturn(builder);
        when(builder.randomExplicitHashKey()).thenReturn(builder);
        when(builder.build()).thenReturn(outboundKinesisRecord);

        when(transformerOutput.newOutboundKinesisRecordBuilder()).thenReturn(builder);
    }

    @AfterEach
    void afterEach() {
        verifyNoMoreInteractions( //
                customSettings,
                initInput,
                publishPacket,
                transformInput,
                transformerOutput,
                builder,
                outboundKinesisRecord);
    }

    @Test
    void transform_no_destination() {
        transformer.init(initInput);

        verify(initInput).getCustomSettings();
        verify(customSettings).getAllForName("destination");

        transformer.transformMqttToKinesis(transformInput, transformerOutput);

        verify(transformInput).getPublishPacket();
        verify(publishPacket).getTopic();
        verify(publishPacket).getPayload();

        verify(transformerOutput).setOutboundKinesisRecords(List.of());
    }

    @Test
    void transform_one_destination() {
        when(customSettings.getAllForName("destination")).thenReturn(List.of("destination-01"));

        transformer.init(initInput);

        verify(initInput).getCustomSettings();
        verify(customSettings).getAllForName("destination");

        transformer.transformMqttToKinesis(transformInput, transformerOutput);

        verify(transformInput).getPublishPacket();
        verify(publishPacket).getTopic();
        verify(publishPacket).getPayload();

        verify(transformerOutput).newOutboundKinesisRecordBuilder();
        verify(builder).streamName("destination-01");
        verify(builder).data(data);
        verify(builder).partitionKey(mqttTopic);
        verify(builder).randomExplicitHashKey();
        verify(builder).build();

        verify(transformerOutput).setOutboundKinesisRecords(List.of(outboundKinesisRecord));
    }

    @Test
    void transform_multiple_destinations() {
        when(customSettings.getAllForName("destination")).thenReturn(List.of("destination-01",
                "destination-02",
                "destination-03"));

        transformer.init(initInput);

        verify(initInput).getCustomSettings();
        verify(customSettings).getAllForName("destination");

        transformer.transformMqttToKinesis(transformInput, transformerOutput);

        verify(transformInput).getPublishPacket();
        verify(publishPacket).getTopic();
        verify(publishPacket).getPayload();

        verify(transformerOutput, times(3)).newOutboundKinesisRecordBuilder();
        verify(builder).streamName("destination-01");
        verify(builder).streamName("destination-02");
        verify(builder).streamName("destination-03");
        verify(builder, times(3)).data(data);
        verify(builder, times(3)).partitionKey(mqttTopic);
        verify(builder, times(3)).randomExplicitHashKey();
        verify(builder, times(3)).build();

        verify(transformerOutput).setOutboundKinesisRecords( //
                List.of(outboundKinesisRecord, outboundKinesisRecord, outboundKinesisRecord));
    }

    @Test
    void transform_throw_exception() {
        when(transformInput.getPublishPacket()).thenThrow(new RuntimeException());

        transformer.init(initInput);

        verify(initInput).getCustomSettings();
        verify(customSettings).getAllForName("destination");

        transformer.transformMqttToKinesis(transformInput, transformerOutput);

        verify(transformInput).getPublishPacket();
    }

    @Test
    void transform_multiple_destinations_one_throws_exception() {
        when(customSettings.getAllForName("destination")).thenReturn(List.of("destination-01",
                "destination-02",
                "destination-03"));
        when(builder.streamName("destination-02")).thenThrow(new RuntimeException());

        transformer.init(initInput);

        verify(initInput).getCustomSettings();
        verify(customSettings).getAllForName("destination");

        transformer.transformMqttToKinesis(transformInput, transformerOutput);

        verify(transformInput).getPublishPacket();
        verify(publishPacket).getTopic();
        verify(publishPacket).getPayload();

        verify(transformerOutput, times(3)).newOutboundKinesisRecordBuilder();
        verify(builder).streamName("destination-01");
        verify(builder).streamName("destination-02");
        verify(builder).streamName("destination-03");
        verify(builder, times(2)).data(data);
        verify(builder, times(2)).partitionKey(mqttTopic);
        verify(builder, times(2)).randomExplicitHashKey();
        verify(builder, times(2)).build();

        verify(transformerOutput).setOutboundKinesisRecords( //
                List.of(outboundKinesisRecord, outboundKinesisRecord));
    }
}
