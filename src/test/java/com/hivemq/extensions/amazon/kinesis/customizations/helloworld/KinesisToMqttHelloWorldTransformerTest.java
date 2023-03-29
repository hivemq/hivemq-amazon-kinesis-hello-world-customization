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

import com.codahale.metrics.MetricRegistry;
import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.packets.general.Qos;
import com.hivemq.extension.sdk.api.services.builder.PublishBuilder;
import com.hivemq.extension.sdk.api.services.publish.Publish;
import com.hivemq.extensions.amazon.kinesis.api.model.CustomSettings;
import com.hivemq.extensions.amazon.kinesis.api.model.InboundKinesisRecord;
import com.hivemq.extensions.amazon.kinesis.api.transformers.KinesisToMqttInitInput;
import com.hivemq.extensions.amazon.kinesis.api.transformers.KinesisToMqttInput;
import com.hivemq.extensions.amazon.kinesis.api.transformers.KinesisToMqttOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static com.hivemq.extensions.amazon.kinesis.customizations.helloworld.KinesisToMqttHelloWorldTransformer.KMS_ENCRYPTED_DATA_COUNTER_NAME;
import static com.hivemq.extensions.amazon.kinesis.customizations.helloworld.KinesisToMqttHelloWorldTransformer.NOT_ENCRYPTED_DATA_COUNTER_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Mario Schwede
 * @since 4.14.0
 */
class KinesisToMqttHelloWorldTransformerTest {
    private final @NotNull KinesisToMqttHelloWorldTransformer transformer = new KinesisToMqttHelloWorldTransformer();

    private final @NotNull MetricRegistry metricRegistry = new MetricRegistry();
    private final @NotNull CustomSettings customSettings = mock(CustomSettings.class);
    private final @NotNull KinesisToMqttInitInput initInput = mock(KinesisToMqttInitInput.class);
    private final @NotNull InboundKinesisRecord inboundKinesisRecord = mock(InboundKinesisRecord.class);
    private final @NotNull KinesisToMqttInput transformInput = mock(KinesisToMqttInput.class);
    private final @NotNull KinesisToMqttOutput transformerOutput = mock(KinesisToMqttOutput.class);
    private final @NotNull PublishBuilder builder = mock(PublishBuilder.class);
    private final @NotNull Publish publish = mock(Publish.class);
    private final @NotNull ByteBuffer data = StandardCharsets.UTF_8.encode("test-data");
    private final @NotNull String streamName = "test-stream";

    @BeforeEach
    void beforeEach() {
        when(initInput.getMetricRegistry()).thenReturn(metricRegistry);
        when(initInput.getCustomSettings()).thenReturn(customSettings);

        when(inboundKinesisRecord.getStreamName()).thenReturn(streamName);
        when(inboundKinesisRecord.getData()).thenReturn(data);

        when(transformInput.getMetricRegistry()).thenReturn(metricRegistry);
        when(transformInput.getCustomSettings()).thenReturn(customSettings);
        when(transformInput.getInboundKinesisRecord()).thenReturn(inboundKinesisRecord);

        when(builder.topic(any())).thenReturn(builder);
        when(builder.qos(any())).thenReturn(builder);
        when(builder.payload(any())).thenReturn(builder);
        when(builder.build()).thenReturn(publish);

        when(transformerOutput.newPublishBuilder()).thenReturn(builder);
    }

    @AfterEach
    void afterEach() {
        verifyNoMoreInteractions( //
                customSettings, initInput, inboundKinesisRecord, transformInput, transformerOutput, builder, publish);
    }

    @Test
    void transform() {
        transformer.init(initInput);

        assertThat(metricRegistry.getCounters()).containsOnlyKeys( //
                NOT_ENCRYPTED_DATA_COUNTER_NAME, KMS_ENCRYPTED_DATA_COUNTER_NAME);
        assertThat(metricRegistry.getCounters().get(NOT_ENCRYPTED_DATA_COUNTER_NAME).getCount()).isZero();
        assertThat(metricRegistry.getCounters().get(KMS_ENCRYPTED_DATA_COUNTER_NAME).getCount()).isZero();

        verify(initInput).getMetricRegistry();
        verify(initInput).getCustomSettings();

        verify(customSettings).getFirst("qos");

        transformer.transformKinesisToMqtt(transformInput, transformerOutput);

        verify(transformInput).getInboundKinesisRecord();

        verify(inboundKinesisRecord).getStreamName();
        verify(inboundKinesisRecord).getData();
        verify(inboundKinesisRecord).getEncryptionType();

        verify(builder).payload(data);
        verify(builder).topic("from-kinesis/" + streamName);
        verify(builder, never()).qos(any());
        verify(builder).build();

        verify(transformerOutput).newPublishBuilder();
        verify(transformerOutput).setPublishes(List.of(publish));

        assertThat(metricRegistry.getCounters().get(NOT_ENCRYPTED_DATA_COUNTER_NAME).getCount()).isOne();
        assertThat(metricRegistry.getCounters().get(KMS_ENCRYPTED_DATA_COUNTER_NAME).getCount()).isZero();
    }

    @Test
    void transform_kms_encryption() {
        when(inboundKinesisRecord.getEncryptionType()).thenReturn("KMS");

        transformer.init(initInput);

        assertThat(metricRegistry.getCounters()).containsOnlyKeys( //
                NOT_ENCRYPTED_DATA_COUNTER_NAME, KMS_ENCRYPTED_DATA_COUNTER_NAME);
        assertThat(metricRegistry.getCounters().get(NOT_ENCRYPTED_DATA_COUNTER_NAME).getCount()).isZero();
        assertThat(metricRegistry.getCounters().get(KMS_ENCRYPTED_DATA_COUNTER_NAME).getCount()).isZero();

        verify(initInput).getMetricRegistry();
        verify(initInput).getCustomSettings();

        verify(customSettings).getFirst("qos");

        transformer.transformKinesisToMqtt(transformInput, transformerOutput);

        verify(transformInput).getInboundKinesisRecord();

        verify(inboundKinesisRecord).getStreamName();
        verify(inboundKinesisRecord).getData();
        verify(inboundKinesisRecord).getEncryptionType();

        verify(builder).payload(data);
        verify(builder).topic("from-kinesis/" + streamName);
        verify(builder, never()).qos(any());
        verify(builder).build();

        verify(transformerOutput).newPublishBuilder();
        verify(transformerOutput).setPublishes(List.of(publish));

        assertThat(metricRegistry.getCounters().get(NOT_ENCRYPTED_DATA_COUNTER_NAME).getCount()).isZero();
        assertThat(metricRegistry.getCounters().get(KMS_ENCRYPTED_DATA_COUNTER_NAME).getCount()).isOne();
    }

    @Test
    void transform_valid_qos_setting() {
        when(customSettings.getFirst("qos")).thenReturn(Optional.of("2"));

        transformer.init(initInput);

        assertThat(metricRegistry.getCounters()).containsOnlyKeys( //
                NOT_ENCRYPTED_DATA_COUNTER_NAME, KMS_ENCRYPTED_DATA_COUNTER_NAME);
        assertThat(metricRegistry.getCounters().get(NOT_ENCRYPTED_DATA_COUNTER_NAME).getCount()).isZero();
        assertThat(metricRegistry.getCounters().get(KMS_ENCRYPTED_DATA_COUNTER_NAME).getCount()).isZero();

        verify(initInput).getMetricRegistry();
        verify(initInput).getCustomSettings();

        verify(customSettings).getFirst("qos");

        transformer.transformKinesisToMqtt(transformInput, transformerOutput);

        verify(transformInput).getInboundKinesisRecord();

        verify(inboundKinesisRecord).getStreamName();
        verify(inboundKinesisRecord).getData();
        verify(inboundKinesisRecord).getEncryptionType();

        verify(builder).payload(data);
        verify(builder).topic("from-kinesis/" + streamName);
        verify(builder).qos(Qos.EXACTLY_ONCE);
        verify(builder).build();

        verify(transformerOutput).newPublishBuilder();
        verify(transformerOutput).setPublishes(List.of(publish));

        assertThat(metricRegistry.getCounters().get(NOT_ENCRYPTED_DATA_COUNTER_NAME).getCount()).isOne();
        assertThat(metricRegistry.getCounters().get(KMS_ENCRYPTED_DATA_COUNTER_NAME).getCount()).isZero();
    }

    @Test
    void transform_invalid_qos_setting() {
        when(customSettings.getFirst("qos")).thenReturn(Optional.of("invalid"));

        transformer.init(initInput);

        assertThat(metricRegistry.getCounters()).containsOnlyKeys( //
                NOT_ENCRYPTED_DATA_COUNTER_NAME, KMS_ENCRYPTED_DATA_COUNTER_NAME);
        assertThat(metricRegistry.getCounters().get(NOT_ENCRYPTED_DATA_COUNTER_NAME).getCount()).isZero();
        assertThat(metricRegistry.getCounters().get(KMS_ENCRYPTED_DATA_COUNTER_NAME).getCount()).isZero();

        verify(initInput).getMetricRegistry();
        verify(initInput).getCustomSettings();

        verify(customSettings).getFirst("qos");

        transformer.transformKinesisToMqtt(transformInput, transformerOutput);

        verify(transformInput).getInboundKinesisRecord();

        verify(inboundKinesisRecord).getStreamName();
        verify(inboundKinesisRecord).getData();
        verify(inboundKinesisRecord).getEncryptionType();

        verify(builder).payload(data);
        verify(builder).topic("from-kinesis/" + streamName);
        verify(builder).qos(Qos.AT_MOST_ONCE);
        verify(builder).build();

        verify(transformerOutput).newPublishBuilder();
        verify(transformerOutput).setPublishes(List.of(publish));

        assertThat(metricRegistry.getCounters().get(NOT_ENCRYPTED_DATA_COUNTER_NAME).getCount()).isOne();
        assertThat(metricRegistry.getCounters().get(KMS_ENCRYPTED_DATA_COUNTER_NAME).getCount()).isZero();
    }

    @Test
    void transform_throws_exception() {
        when(transformInput.getInboundKinesisRecord()).thenThrow(new RuntimeException());

        transformer.init(initInput);

        assertThat(metricRegistry.getCounters()).containsOnlyKeys( //
                NOT_ENCRYPTED_DATA_COUNTER_NAME, KMS_ENCRYPTED_DATA_COUNTER_NAME);
        assertThat(metricRegistry.getCounters().get(NOT_ENCRYPTED_DATA_COUNTER_NAME).getCount()).isZero();
        assertThat(metricRegistry.getCounters().get(KMS_ENCRYPTED_DATA_COUNTER_NAME).getCount()).isZero();

        verify(initInput).getMetricRegistry();
        verify(initInput).getCustomSettings();

        verify(customSettings).getFirst("qos");

        transformer.transformKinesisToMqtt(transformInput, transformerOutput);

        verify(transformInput).getInboundKinesisRecord();

        assertThat(metricRegistry.getCounters().get(NOT_ENCRYPTED_DATA_COUNTER_NAME).getCount()).isZero();
        assertThat(metricRegistry.getCounters().get(KMS_ENCRYPTED_DATA_COUNTER_NAME).getCount()).isZero();
    }
}
