/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.amqp;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.DestinationUtil;
import org.apache.qpid.jms.JmsConnection;
import org.apache.qpid.jms.policy.JmsDefaultPrefetchPolicy;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class JMSMessageConsumerTest extends JMSClientTestSupport {

   protected static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   protected String getConfiguredProtocols() {
      return "AMQP,OPENWIRE,CORE";
   }

   @Test(timeout = 30000)
   public void testDeliveryModeAMQPProducerCoreConsumer() throws Exception {
      Connection connection = createConnection(); //AMQP
      Connection connection2 = createCoreConnection(); //CORE
      testDeliveryMode(connection, connection2);
   }

   @Test(timeout = 30000)
   public void testDeliveryModeAMQPProducerAMQPConsumer() throws Exception {
      Connection connection = createConnection(); //AMQP
      Connection connection2 = createConnection(); //AMQP
      testDeliveryMode(connection, connection2);
   }

   @Test(timeout = 30000)
   public void testDeliveryModeCoreProducerAMQPConsumer() throws Exception {
      Connection connection = createCoreConnection(); //CORE
      Connection connection2 = createConnection(); //AMQP
      testDeliveryMode(connection, connection2);
   }

   @Test(timeout = 30000)
   public void testDeliveryModeCoreProducerCoreConsumer() throws Exception {
      Connection connection = createCoreConnection(); //CORE
      Connection connection2 = createCoreConnection(); //CORE
      testDeliveryMode(connection, connection2);
   }

   private void testDeliveryMode(Connection connection1, Connection connection2) throws JMSException {
      try {
         Session session1 = connection1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         javax.jms.Queue queue1 = session1.createQueue(getQueueName());
         javax.jms.Queue queue2 = session2.createQueue(getQueueName());

         final MessageConsumer consumer2 = session2.createConsumer(queue2);

         MessageProducer producer = session1.createProducer(queue1);
         producer.setDeliveryMode(DeliveryMode.PERSISTENT);
         connection1.start();

         TextMessage message = session1.createTextMessage();
         message.setText("hello");
         producer.send(message);

         Message received = consumer2.receive(100);

         assertNotNull("Should have received a message by now.", received);
         assertTrue("Should be an instance of TextMessage", received instanceof TextMessage);
         assertEquals(DeliveryMode.PERSISTENT, received.getJMSDeliveryMode());
      } finally {
         connection1.close();
         connection2.close();
      }
   }

   @Test(timeout = 30000)
   public void testPriorityAMQPProducerCoreConsumer() throws Exception {
      Connection connection = createConnection(); //AMQP
      Connection connection2 = createCoreConnection(); //CORE
      testPriority(connection, connection2);
   }

   @Test(timeout = 30000)
   public void testPriorityAMQPProducerAMQPConsumer() throws Exception {
      Connection connection = createConnection(); //AMQP
      Connection connection2 = createConnection(); //AMQP
      testPriority(connection, connection2);
   }

   @Test(timeout = 30000)
   public void testPriorityModeCoreProducerAMQPConsumer() throws Exception {
      Connection connection = createCoreConnection(); //CORE
      Connection connection2 = createConnection(); //AMQP
      testPriority(connection, connection2);
   }

   @Test(timeout = 30000)
   public void testPriorityCoreProducerCoreConsumer() throws Exception {
      Connection connection = createCoreConnection(); //CORE
      Connection connection2 = createCoreConnection(); //CORE
      testPriority(connection, connection2);
   }

   private void testPriority(Connection connection1, Connection connection2) throws JMSException {
      try {
         Session session1 = connection1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         javax.jms.Queue queue1 = session1.createQueue(getQueueName());
         javax.jms.Queue queue2 = session2.createQueue(getQueueName());

         final MessageConsumer consumer2 = session2.createConsumer(queue2);

         MessageProducer producer = session1.createProducer(queue1);
         producer.setPriority(2);
         connection1.start();

         TextMessage message = session1.createTextMessage();
         message.setText("hello");
         producer.send(message);

         Message received = consumer2.receive(100);

         assertNotNull("Should have received a message by now.", received);
         assertTrue("Should be an instance of TextMessage", received instanceof TextMessage);
         assertEquals(2, received.getJMSPriority());
      } finally {
         connection1.close();
         connection2.close();
      }
   }

   @Test(timeout = 60000)
   public void testSelectorOnTopic() throws Exception {
      doTestSelector(true);
   }

   @Test(timeout = 60000)
   public void testSelectorOnQueue() throws Exception {
      doTestSelector(false);
   }

   private void doTestSelector(boolean topic) throws Exception {
      Connection connection = createConnection();

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Destination destination = null;
         if (topic) {
            destination = session.createTopic(getTopicName());
         } else {
            destination = session.createQueue(getQueueName());
         }

         MessageProducer producer = session.createProducer(destination);
         MessageConsumer messageConsumer = session.createConsumer(destination, "color = 'RED'");

         TextMessage message = session.createTextMessage();
         message.setText("msg:0");
         producer.send(message);
         message = session.createTextMessage();
         message.setText("msg:1");
         message.setStringProperty("color", "RED");
         producer.send(message);

         connection.start();

         TextMessage m = (TextMessage) messageConsumer.receive(5000);
         assertNotNull(m);
         assertEquals("msg:1", m.getText());
         assertEquals(m.getStringProperty("color"), "RED");
      } finally {
         connection.close();
      }
   }

   @Test(timeout = 60000)
   public void testDurableSubscriptionWithConfigurationManagedQueueWithCore() throws Exception {
      testDurableSubscriptionWithConfigurationManagedQueue(() -> createCoreConnection(false));

   }

   @Test(timeout = 60000)
   public void testDurableSubscriptionWithConfigurationManagedQueueWithOpenWire() throws Exception {
      testDurableSubscriptionWithConfigurationManagedQueue(() -> createOpenWireConnection(false));

   }

   @Test(timeout = 60000)
   public void testDurableSubscriptionWithConfigurationManagedQueueWithAMQP() throws Exception {
      testDurableSubscriptionWithConfigurationManagedQueue(() -> JMSMessageConsumerTest.super.createConnection(false));
   }

   private void testDurableSubscriptionWithConfigurationManagedQueue(ConnectionSupplier connectionSupplier) throws Exception {
      final String clientId = "bar";
      final String subName = "foo";
      final String queueName = DestinationUtil.createQueueNameForSubscription(true, clientId, subName).toString();
      server.stop();
      server.getConfiguration().addQueueConfiguration(new QueueConfiguration(queueName).setAddress("myTopic").setFilterString("color = 'BLUE'").setRoutingType(RoutingType.MULTICAST));
      server.getConfiguration().setAmqpUseCoreSubscriptionNaming(true);
      server.start();

      try (Connection connection = connectionSupplier.createConnection()) {
         connection.setClientID(clientId);
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic destination = session.createTopic("myTopic");

         MessageConsumer messageConsumer = session.createDurableSubscriber(destination, subName);
         messageConsumer.close();

         Queue queue = server.locateQueue(queueName);
         assertNotNull(queue);
         assertNotNull(queue.getFilter());
         assertEquals("color = 'BLUE'", queue.getFilter().getFilterString().toString());
      }
   }

   @Test(timeout = 30000)
   public void testSelectorsWithJMSTypeOnTopic() throws Exception {
      doTestSelectorsWithJMSType(true);
   }

   @Test(timeout = 30000)
   public void testSelectorsWithJMSTypeOnQueue() throws Exception {
      doTestSelectorsWithJMSType(false);
   }

   private void doTestSelectorsWithJMSType(boolean topic) throws Exception {
      final Connection connection = createConnection();
      final String type = "myJMSType";

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Destination destination = null;
         if (topic) {
            destination = session.createTopic(getTopicName());
         } else {
            destination = session.createQueue(getQueueName());
         }

         MessageProducer producer = session.createProducer(destination);
         MessageConsumer consumer = session.createConsumer(destination, "JMSType = '" + type + "'");

         TextMessage message1 = session.createTextMessage();
         message1.setText("text");
         producer.send(message1, DeliveryMode.NON_PERSISTENT, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

         TextMessage message2 = session.createTextMessage();
         message2.setJMSType(type);
         message2.setText("text + type");
         producer.send(message2, DeliveryMode.NON_PERSISTENT, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

         connection.start();

         Message msg = consumer.receive(2000);
         assertNotNull(msg);
         assertTrue(msg instanceof TextMessage);
         assertEquals("Unexpected JMSType value", type, msg.getJMSType());
         assertEquals("Unexpected message content", "text + type", ((TextMessage) msg).getText());
      } finally {
         connection.close();
      }
   }

   @Test(timeout = 30000)
   public void testSelectorsWithJMSCorrelationID() throws Exception {
      Connection connection = createConnection();

      final String correlationID = UUID.randomUUID().toString();

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue = session.createQueue(getQueueName());
         MessageProducer producer = session.createProducer(queue);

         TextMessage message1 = session.createTextMessage();
         message1.setText("text");
         producer.send(message1);

         TextMessage message2 = session.createTextMessage();
         message2.setJMSCorrelationID(correlationID);
         message2.setText("JMSCorrelationID");
         producer.send(message2);

         QueueBrowser browser = session.createBrowser(queue);
         Enumeration<?> enumeration = browser.getEnumeration();
         int count = 0;
         while (enumeration.hasMoreElements()) {
            Message m = (Message) enumeration.nextElement();
            assertTrue(m instanceof TextMessage);
            count++;
         }

         assertEquals(2, count);

         MessageConsumer consumer = session.createConsumer(queue, "JMSCorrelationID = '" + correlationID + "'");
         Message msg = consumer.receive(2000);
         assertNotNull(msg);
         assertTrue(msg instanceof TextMessage);
         assertEquals("Unexpected JMSCorrelationID value", correlationID, msg.getJMSCorrelationID());
         assertEquals("Unexpected message content", "JMSCorrelationID", ((TextMessage) msg).getText());
      } finally {
         connection.close();
      }
   }

   @Test(timeout = 30000)
   public void testSelectorsWithJMSPriority() throws Exception {
      Connection connection = createConnection();

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue = session.createQueue(getQueueName());
         MessageProducer producer = session.createProducer(queue);

         TextMessage message = session.createTextMessage();
         message.setText("hello");
         producer.send(message, DeliveryMode.PERSISTENT, 5, 0);

         message = session.createTextMessage();
         message.setText("hello + 9");
         producer.send(message, DeliveryMode.PERSISTENT, 9, 0);

         QueueBrowser browser = session.createBrowser(queue);
         Enumeration<?> enumeration = browser.getEnumeration();
         int count = 0;
         while (enumeration.hasMoreElements()) {
            Message m = (Message) enumeration.nextElement();
            assertTrue(m instanceof TextMessage);
            count++;
         }

         assertEquals(2, count);

         MessageConsumer consumer = session.createConsumer(queue, "JMSPriority > 8");
         Message msg = consumer.receive(2000);
         assertNotNull(msg);
         assertTrue(msg instanceof TextMessage);
         assertEquals("hello + 9", ((TextMessage) msg).getText());
      } finally {
         connection.close();
      }
   }

   @Test(timeout = 30000)
   public void testSelectorsWithJMSXGroupIDOnTopic() throws Exception {
      doTestSelectorsWithJMSXGroupID(true);
   }

   @Test(timeout = 30000)
   public void testSelectorsWithJMSXGroupIDOnQueue() throws Exception {
      doTestSelectorsWithJMSXGroupID(false);
   }

   private void doTestSelectorsWithJMSXGroupID(boolean topic) throws Exception {

      Connection connection = createConnection();

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Destination destination = null;
         if (topic) {
            destination = session.createTopic(getTopicName());
         } else {
            destination = session.createQueue(getQueueName());
         }

         MessageProducer producer = session.createProducer(destination);
         MessageConsumer consumer = session.createConsumer(destination, "JMSXGroupID = '1'");

         TextMessage message = session.createTextMessage();
         message.setText("group 1 - 1");
         message.setStringProperty("JMSXGroupID", "1");
         message.setIntProperty("JMSXGroupSeq", 1);
         producer.send(message);

         message = session.createTextMessage();
         message.setText("group 2");
         message.setStringProperty("JMSXGroupID", "2");
         producer.send(message);

         message = session.createTextMessage();
         message.setText("group 1 - 2");
         message.setStringProperty("JMSXGroupID", "1");
         message.setIntProperty("JMSXGroupSeq", -1);
         producer.send(message);

         connection.start();

         Message msg = consumer.receive(2000);
         assertNotNull(msg);
         assertTrue(msg instanceof TextMessage);
         assertEquals("group 1 - 1", ((TextMessage) msg).getText());
         msg = consumer.receive(2000);
         assertNotNull(msg);
         assertTrue(msg instanceof TextMessage);
         assertEquals("group 1 - 2", ((TextMessage) msg).getText());
      } finally {
         connection.close();
      }
   }

   @Test(timeout = 30000)
   public void testSelectorsWithJMSDeliveryOnQueue() throws Exception {
      final Connection connection = createConnection();

      String selector = "JMSDeliveryMode = 'PERSISTENT'";

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Destination destination = session.createQueue(getQueueName());

         MessageProducer producer = session.createProducer(destination);
         MessageConsumer consumer = session.createConsumer(destination, selector);

         TextMessage message1 = session.createTextMessage();
         message1.setText("non-persistent");
         producer.send(message1, DeliveryMode.NON_PERSISTENT, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

         TextMessage message2 = session.createTextMessage();
         message2.setText("persistent");
         producer.send(message2, DeliveryMode.PERSISTENT, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

         connection.start();

         Message msg = consumer.receive(2000);
         assertNotNull(msg);
         assertTrue(msg instanceof TextMessage);
         assertEquals("Unexpected JMSDeliveryMode value", DeliveryMode.PERSISTENT, msg.getJMSDeliveryMode());
         assertEquals("Unexpected message content", "persistent", ((TextMessage) msg).getText());
      } finally {
         connection.close();
      }
   }

   @Test(timeout = 30000)
   public void testSelectorsWithJMSTimestampOnQueue() throws Exception {
      final Connection connection = createConnection();

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Destination destination = session.createQueue(getQueueName());

         MessageProducer producer = session.createProducer(destination);

         TextMessage message1 = session.createTextMessage();
         message1.setText("filtered");
         producer.send(message1, DeliveryMode.PERSISTENT, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

         // short delay to prevent the timestamps from being the same
         Thread.sleep(2);

         TextMessage message2 = session.createTextMessage();
         message2.setText("expected");
         producer.send(message2, DeliveryMode.PERSISTENT, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

         MessageConsumer consumer = session.createConsumer(destination, "JMSTimestamp = " + message2.getJMSTimestamp());

         connection.start();

         Message msg = consumer.receive(2000);
         assertNotNull(msg);
         assertTrue(msg instanceof TextMessage);
         assertEquals("Unexpected JMSTimestamp value", message2.getJMSTimestamp(), msg.getJMSTimestamp());
         assertEquals("Unexpected message content", "expected", ((TextMessage) msg).getText());
      } finally {
         connection.close();
      }
   }

   @Test(timeout = 30000)
   public void testSelectorsWithJMSExpirationOnQueue() throws Exception {
      final Connection connection = createConnection();

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Destination destination = session.createQueue(getQueueName());

         MessageProducer producer = session.createProducer(destination);

         TextMessage message1 = session.createTextMessage();
         message1.setText("filtered");
         producer.send(message1, DeliveryMode.PERSISTENT, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

         TextMessage message2 = session.createTextMessage();
         message2.setText("expected");
         producer.send(message2, DeliveryMode.PERSISTENT, Message.DEFAULT_PRIORITY, 60000);

         MessageConsumer consumer = session.createConsumer(destination, "JMSExpiration = " + message2.getJMSExpiration());

         connection.start();

         Message msg = consumer.receive(2000);
         assertNotNull(msg);
         assertTrue(msg instanceof TextMessage);
         assertEquals("Unexpected JMSExpiration value", message2.getJMSExpiration(), msg.getJMSExpiration());
         assertEquals("Unexpected message content", "expected", ((TextMessage) msg).getText());
      } finally {
         connection.close();
      }
   }

   @Test(timeout = 60000)
   public void testJMSSelectorFiltersJMSMessageIDOnTopic() throws Exception {
      Connection connection = createConnection();

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue = session.createQueue(getQueueName());
         MessageProducer producer = session.createProducer(queue);

         // Send one to receive
         TextMessage message = session.createTextMessage();
         producer.send(message);

         // Send another to filter
         producer.send(session.createTextMessage());

         connection.start();

         // First one should make it through
         MessageConsumer messageConsumer = session.createConsumer(queue, "JMSMessageID = '" + message.getJMSMessageID() + "'");
         TextMessage m = (TextMessage) messageConsumer.receive(5000);
         assertNotNull(m);
         assertEquals(message.getJMSMessageID(), m.getJMSMessageID());

         // The second one should not be received.
         assertNull(messageConsumer.receive(1000));
      } finally {
         connection.close();
      }
   }

   @Test(timeout = 60000)
   public void testZeroPrefetchWithTwoConsumers() throws Exception {
      JmsConnection connection = (JmsConnection) createConnection();
      ((JmsDefaultPrefetchPolicy) connection.getPrefetchPolicy()).setAll(0);
      connection.start();

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      javax.jms.Queue queue = session.createQueue(getQueueName());

      MessageProducer producer = session.createProducer(queue);
      producer.send(session.createTextMessage("Msg1"));
      producer.send(session.createTextMessage("Msg2"));

      // now lets receive it
      MessageConsumer consumer1 = session.createConsumer(queue);
      MessageConsumer consumer2 = session.createConsumer(queue);
      TextMessage answer = (TextMessage) consumer1.receive(5000);
      assertNotNull(answer);
      assertEquals("Should have received a message!", answer.getText(), "Msg1");
      answer = (TextMessage) consumer2.receive(5000);
      assertNotNull(answer);
      assertEquals("Should have received a message!", answer.getText(), "Msg2");

      answer = (TextMessage) consumer2.receiveNoWait();
      assertNull("Should have not received a message!", answer);
   }

   @Test(timeout = 30000)
   public void testProduceAndConsumeLargeNumbersOfTopicMessagesClientAck() throws Exception {
      doTestProduceAndConsumeLargeNumbersOfMessages(true, Session.CLIENT_ACKNOWLEDGE);
   }

   @Test(timeout = 30000)
   public void testProduceAndConsumeLargeNumbersOfQueueMessagesClientAck() throws Exception {
      doTestProduceAndConsumeLargeNumbersOfMessages(false, Session.CLIENT_ACKNOWLEDGE);
   }

   @Test(timeout = 30000)
   public void testProduceAndConsumeLargeNumbersOfTopicMessagesAutoAck() throws Exception {
      doTestProduceAndConsumeLargeNumbersOfMessages(true, Session.AUTO_ACKNOWLEDGE);
   }

   @Test(timeout = 30000)
   public void testProduceAndConsumeLargeNumbersOfQueueMessagesAutoAck() throws Exception {
      doTestProduceAndConsumeLargeNumbersOfMessages(false, Session.AUTO_ACKNOWLEDGE);
   }

   public void doTestProduceAndConsumeLargeNumbersOfMessages(boolean topic, int ackMode) throws Exception {

      final int MSG_COUNT = 1000;
      final CountDownLatch done = new CountDownLatch(MSG_COUNT);

      JmsConnection connection = (JmsConnection) createConnection();
      connection.setForceAsyncSend(true);
      connection.start();

      Session session = connection.createSession(false, ackMode);
      final Destination destination;
      if (topic) {
         destination = session.createTopic(getTopicName());
      } else {
         destination = session.createQueue(getQueueName());
      }

      MessageConsumer consumer = session.createConsumer(destination);
      consumer.setMessageListener(new MessageListener() {

         @Override
         public void onMessage(Message message) {
            try {
               message.acknowledge();
               done.countDown();
            } catch (JMSException ex) {
               LOG.debug("Caught exception.", ex);
            }
         }
      });

      MessageProducer producer = session.createProducer(destination);

      TextMessage textMessage = session.createTextMessage();
      textMessage.setText("messageText");

      for (int i = 0; i < MSG_COUNT; i++) {
         producer.send(textMessage);
      }

      assertTrue("Did not receive all messages: " + MSG_COUNT, done.await(15, TimeUnit.SECONDS));
   }

   @Test(timeout = 60000)
   public void testPrefetchedMessagesAreNotConsumedOnConsumerClose() throws Exception {
      final int NUM_MESSAGES = 10;

      Connection connection = createConnection();

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue = session.createQueue(getQueueName());
         MessageProducer producer = session.createProducer(queue);

         byte[] bytes = new byte[2048];
         new Random().nextBytes(bytes);
         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage message = session.createTextMessage();
            message.setText("msg:" + i);
            producer.send(message);
         }

         connection.close();

         Queue queueView = getProxyToQueue(getQueueName());
         Wait.assertEquals(NUM_MESSAGES, queueView::getMessageCount);

         // Create a consumer and prefetch the messages
         connection = createConnection();
         session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = session.createConsumer(queue);

         Thread.sleep(100);

         consumer.close();
         connection.close();

         Wait.assertEquals(NUM_MESSAGES, queueView::getMessageCount);
      } finally {
         connection.close();
      }
   }

   @Test(timeout = 60000)
   public void testMessagesReceivedInParallel() throws Throwable {
      final int numMessages = 50000;
      long time = System.currentTimeMillis();

      final ArrayList<Throwable> exceptions = new ArrayList<>();

      Thread t = new Thread(new Runnable() {
         @Override
         public void run() {
            Connection connectionConsumer = null;
            try {
               connectionConsumer = createConnection();
               connectionConsumer.start();
               Session sessionConsumer = connectionConsumer.createSession(false, Session.AUTO_ACKNOWLEDGE);
               final javax.jms.Queue queue = sessionConsumer.createQueue(getQueueName());
               final MessageConsumer consumer = sessionConsumer.createConsumer(queue);

               long n = 0;
               int count = numMessages;
               while (count > 0) {
                  try {
                     if (++n % 1000 == 0) {
                        LOG.debug("received {} messages", n);
                     }

                     Message m = consumer.receive(5000);
                     Assert.assertNotNull("Could not receive message count=" + count + " on consumer", m);
                     count--;
                  } catch (JMSException e) {
                     e.printStackTrace();
                     break;
                  }
               }
            } catch (Throwable e) {
               exceptions.add(e);
               e.printStackTrace();
            } finally {
               try {
                  connectionConsumer.close();
               } catch (Throwable ignored) {
                  // NO OP
               }
            }
         }
      });

      Connection connection = createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      javax.jms.Queue queue = session.createQueue(getQueueName());

      t.start();

      MessageProducer p = session.createProducer(queue);
      p.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
      for (int i = 0; i < numMessages; i++) {
         BytesMessage message = session.createBytesMessage();
         message.writeUTF("Hello world!!!!" + i);
         message.setIntProperty("count", i);
         p.send(message);
      }

      // Wait for the consumer thread to completely read the Queue
      t.join();

      if (!exceptions.isEmpty()) {
         throw exceptions.get(0);
      }

      Queue queueView = getProxyToQueue(getQueueName());

      connection.close();
      Wait.assertEquals(0, queueView::getMessageCount);

      long taken = (System.currentTimeMillis() - time);
      LOG.debug("Microbenchamrk ran in {} milliseconds, sending/receiving {}", taken, numMessages);

      double messagesPerSecond = ((double) numMessages / (double) taken) * 1000;

      LOG.debug("{} messages per second", ((int) messagesPerSecond));
   }

   @Test(timeout = 60000)
   public void testClientAckMessages() throws Exception {
      final int numMessages = 10;

      Connection connection = createConnection();

      try {
         long time = System.currentTimeMillis();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue = session.createQueue(getQueueName());
         MessageProducer producer = session.createProducer(queue);

         byte[] bytes = new byte[2048];
         new Random().nextBytes(bytes);
         for (int i = 0; i < numMessages; i++) {
            TextMessage message = session.createTextMessage();
            message.setText("msg:" + i);
            producer.send(message);
         }
         connection.close();
         Queue queueView = getProxyToQueue(getQueueName());

         Wait.assertEquals(numMessages, queueView::getMessageCount);

         // Now create a new connection and receive and acknowledge
         connection = createConnection();
         session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageConsumer consumer = session.createConsumer(queue);

         for (int i = 0; i < numMessages; i++) {
            Message msg = consumer.receive(5000);
            Assert.assertNotNull("" + i, msg);
            Assert.assertTrue("" + msg, msg instanceof TextMessage);
            String text = ((TextMessage) msg).getText();
            // System.out.println("text = " + text);
            Assert.assertEquals(text, "msg:" + i);
            msg.acknowledge();
         }

         consumer.close();
         connection.close();

         Wait.assertEquals(0, queueView::getMessageCount);
      } finally {
         connection.close();
      }
   }

   @Test(timeout = 30000)
   public void testTimedOutWaitingForWriteLogOnConsumer() throws Throwable {
      String name = "exampleQueue1";
      // disable auto-delete as it causes thrashing during the test
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setAutoDeleteQueues(false));

      final int numMessages = 40;

      Connection connection = createConnection();
      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue = session.createQueue(name);
         MessageProducer producer = session.createProducer(queue);
         for (int i = 0; i < numMessages; i++) {
            TextMessage message = session.createTextMessage();
            message.setText("Message temporary");
            producer.send(message);
         }
         producer.close();
         session.close();

         for (int i = 0; i < numMessages; i++) {
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            queue = session.createQueue(name);
            MessageConsumer c = session.createConsumer(queue);
            Assert.assertNotNull(c.receive(1000));
            session.close();
         }

         session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         queue = session.createQueue(name);
         MessageConsumer c = session.createConsumer(queue);
         for (int i = 0; i < numMessages; i++) {
            Assert.assertNull(c.receive(1));
         }
         producer.close();
         session.close();
      } finally {
         connection.close();
      }
   }

   @Test
   public void testConcurrentSharedConsumerConnections() throws Exception {
      final int concurrentConnections = 20;
      final ExecutorService executorService = Executors.newFixedThreadPool(concurrentConnections);

      final AtomicBoolean failedToSubscribe = new AtomicBoolean(false);
      for (int i = 1; i < concurrentConnections; i++) {
         executorService.submit(() -> {
            try (Connection connection = createConnection()) {
               connection.start();
               @SuppressWarnings("resource")
               final Session session = connection.createSession();
               final Topic topic = session.createTopic("topics.foo");
               session.createSharedConsumer(topic, "MY_SUB");
               Thread.sleep(100);
            } catch (final Exception ex) {
               ex.printStackTrace();
               failedToSubscribe.set(true);
            }
         });
      }
      executorService.shutdown();
      executorService.awaitTermination(30, TimeUnit.SECONDS);

      assertFalse(failedToSubscribe.get());
   }

   @Test(timeout = 30000)
   public void testBrokerRestartAMQPProducerAMQPConsumer() throws Exception {
      Connection connection = createFailoverConnection(); //AMQP
      Connection connection2 = createFailoverConnection(); //AMQP
      testBrokerRestart(connection, connection2);
   }

   private void testBrokerRestart(Connection connection1, Connection connection2) throws Exception {
      try {
         Session session1 = connection1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         javax.jms.Queue queue1 = session1.createQueue(getQueueName());
         javax.jms.Queue queue2 = session2.createQueue(getQueueName());

         final MessageConsumer consumer2 = session2.createConsumer(queue2);

         MessageProducer producer = session1.createProducer(queue1);
         producer.setDeliveryMode(DeliveryMode.PERSISTENT);
         connection1.start();

         TextMessage message = session1.createTextMessage();
         message.setText("hello");
         producer.send(message);

         Message received = consumer2.receive(100);

         assertNotNull("Should have received a message by now.", received);
         assertTrue("Should be an instance of TextMessage", received instanceof TextMessage);
         assertEquals(DeliveryMode.PERSISTENT, received.getJMSDeliveryMode());


         server.stop();
         Wait.waitFor(() -> !server.isStarted(), 1000);

         server.start();

         TextMessage message2 = session1.createTextMessage();
         message2.setText("hello");
         producer.send(message2);

         Message received2 = consumer2.receive(100);

         assertNotNull("Should have received a message by now.", received2);
         assertTrue("Should be an instance of TextMessage", received2 instanceof TextMessage);
         assertEquals(DeliveryMode.PERSISTENT, received2.getJMSDeliveryMode());


      } finally {
         connection1.close();
         connection2.close();
      }
   }
}
