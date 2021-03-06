/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pulsar.client.api;

import lombok.Cleanup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ConsumerBatchReceiveTest extends ProducerConsumerBase {

    private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();

    @BeforeClass
    @Override
    protected void setup() throws Exception {
        super.internalSetup();
        super.producerBaseSetup();
    }

    @AfterClass
    @Override
    protected void cleanup() throws Exception {
        super.internalCleanup();
    }

    @DataProvider(name = "batchReceivePolicy")
    public Object[][] batchReceivePolicyProvider() {
        return new Object[][] {

                // Default batch receive policy.
                { BatchReceivePolicy.DEFAULT_POLICY, true },
                // Only receive timeout limitation.
                { BatchReceivePolicy.builder()
                        .timeout(50, TimeUnit.MILLISECONDS)
                        .build(), true
                },
                // Only number of messages in a single batch receive limitation.
                { BatchReceivePolicy.builder()
                        .maxNumMessages(10)
                        .build(), true
                },
                // Number of messages and timeout limitation
                { BatchReceivePolicy.builder()
                        .maxNumMessages(13)
                        .timeout(50, TimeUnit.MILLISECONDS)
                        .build(), true
                },
                // Size of messages and timeout limitation
                { BatchReceivePolicy.builder()
                        .maxNumBytes(64)
                        .timeout(50, TimeUnit.MILLISECONDS)
                        .build(), true
                },
                // Default batch receive policy.
                { BatchReceivePolicy.DEFAULT_POLICY, false },
                // Only receive timeout limitation.
                { BatchReceivePolicy.builder()
                        .timeout(50, TimeUnit.MILLISECONDS)
                        .build(), false
                },
                // Only number of messages in a single batch receive limitation.
                { BatchReceivePolicy.builder()
                        .maxNumMessages(10)
                        .build(), false
                },
                // Number of messages and timeout limitation
                { BatchReceivePolicy.builder()
                        .maxNumMessages(13)
                        .timeout(50, TimeUnit.MILLISECONDS)
                        .build(), false
                },
                // Size of messages and timeout limitation
                { BatchReceivePolicy.builder()
                        .maxNumBytes(64)
                        .timeout(50, TimeUnit.MILLISECONDS)
                        .build(), false
                }
        };
    }

    @Test(dataProvider = "batchReceivePolicy")
    public void testBatchReceiveNonPartitionedTopic(BatchReceivePolicy batchReceivePolicy, boolean batchProduce) throws Exception {
        final String topic = "persistent://my-property/my-ns/batch-receive-non-partition-" + UUID.randomUUID();
        testBatchReceive(topic, batchReceivePolicy, batchProduce);
    }

    @Test(dataProvider = "batchReceivePolicy")
    public void testBatchReceivePartitionedTopic(BatchReceivePolicy batchReceivePolicy, boolean batchProduce) throws Exception {
        final String topic = "persistent://my-property/my-ns/batch-receive-" + UUID.randomUUID();
        admin.topics().createPartitionedTopic(topic, 3);
        testBatchReceive(topic, batchReceivePolicy, batchProduce);
    }

    @Test(dataProvider = "batchReceivePolicy")
    public void testAsyncBatchReceiveNonPartitionedTopic(BatchReceivePolicy batchReceivePolicy, boolean batchProduce) throws Exception {
        final String topic = "persistent://my-property/my-ns/batch-receive-non-partition-async-" + UUID.randomUUID();
        testBatchReceiveAsync(topic, batchReceivePolicy, batchProduce);
    }

    @Test(dataProvider = "batchReceivePolicy")
    public void testAsyncBatchReceivePartitionedTopic(BatchReceivePolicy batchReceivePolicy, boolean batchProduce) throws Exception {
        final String topic = "persistent://my-property/my-ns/batch-receive-async-" + UUID.randomUUID();
        admin.topics().createPartitionedTopic(topic, 3);
        testBatchReceiveAsync(topic, batchReceivePolicy, batchProduce);
    }

    @Test(dataProvider = "batchReceivePolicy")
    public void testBatchReceiveAndRedeliveryNonPartitionedTopic(BatchReceivePolicy batchReceivePolicy, boolean batchProduce) throws Exception {
        final String topic = "persistent://my-property/my-ns/batch-receive-and-redelivery-non-partition-" + UUID.randomUUID();
        testBatchReceiveAndRedelivery(topic, batchReceivePolicy, batchProduce);
    }

    @Test(dataProvider = "batchReceivePolicy")
    public void testBatchReceiveAndRedeliveryPartitionedTopic(BatchReceivePolicy batchReceivePolicy, boolean batchProduce) throws Exception {
        final String topic = "persistent://my-property/my-ns/batch-receive-and-redelivery-" + UUID.randomUUID();
        admin.topics().createPartitionedTopic(topic, 3);
        testBatchReceiveAndRedelivery(topic, batchReceivePolicy, batchProduce);
    }

    private void testBatchReceive(String topic, BatchReceivePolicy batchReceivePolicy, boolean batchProduce) throws Exception {
        ProducerBuilder<String> producerBuilder = pulsarClient.newProducer(Schema.STRING).topic(topic);
        if (!batchProduce) {
            producerBuilder.enableBatching(false);
        }
        @Cleanup
        Producer<String> producer = producerBuilder.create();
        @Cleanup
        Consumer<String> consumer = pulsarClient.newConsumer(Schema.STRING)
                .topic(topic)
                .subscriptionName("s1")
                .batchReceivePolicy(batchReceivePolicy)
                .subscribe();
        sendMessagesAsyncAndWait(producer, 100);
        batchReceiveAndCheck(consumer, 100);
    }

    private void testBatchReceiveAsync(String topic, BatchReceivePolicy batchReceivePolicy, boolean batchProduce) throws Exception {

        if (batchReceivePolicy.getTimeoutMs() <= 0) {
            return;
        }

        ProducerBuilder<String> producerBuilder = pulsarClient.newProducer(Schema.STRING).topic(topic);
        if (!batchProduce) {
            producerBuilder.enableBatching(false);
        }

        @Cleanup
        Producer<String> producer = producerBuilder.create();
        @Cleanup
        Consumer<String> consumer = pulsarClient.newConsumer(Schema.STRING)
                .topic(topic)
                .subscriptionName("s1")
                .batchReceivePolicy(batchReceivePolicy)
                .subscribe();

        sendMessagesAsyncAndWait(producer, 100);
        CountDownLatch latch = new CountDownLatch(100);
        receiveAsync(consumer, 100, latch);
        latch.await();
    }

    private void testBatchReceiveAndRedelivery(String topic, BatchReceivePolicy batchReceivePolicy, boolean batchProduce) throws Exception {
        ProducerBuilder<String> producerBuilder = pulsarClient.newProducer(Schema.STRING).topic(topic);
        if (!batchProduce) {
            producerBuilder.enableBatching(false);
        }
        @Cleanup
        Producer<String> producer = producerBuilder.create();
        @Cleanup
        Consumer<String> consumer = pulsarClient.newConsumer(Schema.STRING)
                .topic(topic)
                .subscriptionName("s1")
                .batchReceivePolicy(batchReceivePolicy)
                .ackTimeout(1, TimeUnit.SECONDS)
                .subscribe();
        sendMessagesAsyncAndWait(producer, 100);
        batchReceiveAndRedelivery(consumer, 100);
    }

    private void receiveAsync(Consumer<String> consumer, int expected, CountDownLatch latch) {
        consumer.batchReceiveAsync().thenAccept(messages -> {
            if (messages != null) {
                log.info("Received {} messages in a single batch receive.", messages.size());
                for (Message<String> message : messages) {
                    Assert.assertNotNull(message.getValue());
                    log.info("Get message {} from batch", message.getValue());
                    latch.countDown();
                }
                try {
                    consumer.acknowledge(messages);
                } catch (PulsarClientException e) {
                    log.error("Ack message error", e);
                }
                if (messages.size() < expected) {
                    EXECUTOR.execute(() -> receiveAsync(consumer, expected - messages.size(), latch));
                } else {
                    Assert.assertEquals(expected, 0);
                }
            }
        });
    }

    private void sendMessagesAsyncAndWait(Producer<String> producer, int messages) throws Exception {
        CountDownLatch latch = new CountDownLatch(messages);
        for (int i = 0; i < messages; i++) {
            String message = "my-message-" + i;
            producer.sendAsync(message).thenAccept(messageId -> {
                log.info("Message {} published {}", message, messageId);
                if (messageId != null) {
                    latch.countDown();
                }
            });
        }
        latch.await();
    }

    private void batchReceiveAndCheck(Consumer<String> consumer, int expected) throws Exception {
        Messages<String> messages;
        int messageReceived = 0;
        do {
            messages = consumer.batchReceive();
            if (messages != null) {
                messageReceived += messages.size();
                log.info("Received {} messages in a single batch receive.", messages.size());
                for (Message<String> message : messages) {
                    Assert.assertNotNull(message.getValue());
                    log.info("Get message {} from batch", message.getValue());
                }
                consumer.acknowledge(messages);
            }
        } while (messageReceived < expected);
        Assert.assertEquals(expected, messageReceived);
    }

    private void batchReceiveAndRedelivery(Consumer<String> consumer, int expected) throws Exception {
        Messages<String> messages;
        int messageReceived = 0;
        do {
            messages = consumer.batchReceive();
            if (messages != null) {
                messageReceived += messages.size();
                log.info("Received {} messages in a single batch receive.", messages.size());
                for (Message<String> message : messages) {
                    Assert.assertNotNull(message.getValue());
                    log.info("Get message {} from batch", message.getValue());
                    // don't ack, test message redelivery
                }
            }
        } while (messageReceived < expected);
        Assert.assertEquals(expected, messageReceived);

        do {
            messages = consumer.batchReceive();
            if (messages != null) {
                messageReceived += messages.size();
                log.info("Received {} messages in a single batch receive.", messages.size());
                for (Message<String> message : messages) {
                    Assert.assertNotNull(message.getValue());
                    log.info("Get message {} from batch", message.getValue());
                }
            }
            consumer.acknowledge(messages);
        } while (messageReceived < expected * 2);
        Assert.assertEquals(expected * 2, messageReceived);
    }

    private static final Logger log = LoggerFactory.getLogger(ConsumerBatchReceiveTest.class);
}
