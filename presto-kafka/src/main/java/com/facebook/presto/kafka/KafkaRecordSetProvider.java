/*
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
package com.facebook.presto.kafka;

import com.facebook.presto.decoder.DispatchingRowDecoderFactory;
import com.facebook.presto.decoder.RowDecoder;
import com.facebook.presto.spi.ColumnHandle;
import com.facebook.presto.spi.ConnectorSession;
import com.facebook.presto.spi.ConnectorSplit;
import com.facebook.presto.spi.RecordSet;
import com.facebook.presto.spi.connector.ConnectorRecordSetProvider;
import com.facebook.presto.spi.connector.ConnectorTransactionHandle;
import com.google.common.collect.ImmutableList;

import javax.inject.Inject;

import java.util.List;

import static com.facebook.presto.kafka.KafkaHandleResolver.convertSplit;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;

/**
 * Factory for Kafka specific {@link RecordSet} instances.
 */
public class KafkaRecordSetProvider
        implements ConnectorRecordSetProvider
{
    private DispatchingRowDecoderFactory decoderFactory;
    private final KafkaSimpleConsumerManager consumerManager;

    @Inject
    public KafkaRecordSetProvider(DispatchingRowDecoderFactory decoderFactory, KafkaSimpleConsumerManager consumerManager)
    {
        this.decoderFactory = requireNonNull(decoderFactory, "decoderFactory is null");
        this.consumerManager = requireNonNull(consumerManager, "consumerManager is null");
    }

    @Override
    public RecordSet getRecordSet(ConnectorTransactionHandle transaction, ConnectorSession session, ConnectorSplit split, List<? extends ColumnHandle> columns)
    {
        KafkaSplit kafkaSplit = convertSplit(split);

        List<KafkaColumnHandle> kafkaColumns = columns.stream()
                .map(KafkaHandleResolver::convertColumnHandle)
                .collect(ImmutableList.toImmutableList());

        RowDecoder keyDecoder = decoderFactory.create(
                kafkaSplit.getKeyDataFormat(),
                emptyMap(),
                kafkaColumns.stream()
                        .filter(col -> !col.isInternal())
                        .filter(KafkaColumnHandle::isKeyDecoder)
                        .collect(toImmutableSet()));

        RowDecoder messageDecoder = decoderFactory.create(
                kafkaSplit.getMessageDataFormat(),
                emptyMap(),
                kafkaColumns.stream()
                        .filter(col -> !col.isInternal())
                        .filter(col -> !col.isKeyDecoder())
                        .collect(toImmutableSet()));

        return new KafkaRecordSet(kafkaSplit, consumerManager, kafkaColumns, keyDecoder, messageDecoder);
    }
}
