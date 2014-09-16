package org.ihtsdo.telemetry.server;

import org.apache.activemq.transport.TransportDisposedIOException;
import org.ihtsdo.telemetry.core.Constants;
import org.ihtsdo.telemetry.core.JmsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TelemetryProcessor {

	private static final int ONE_SECOND = 1000;

	private final Session jmsSession;
	private final Map<String, BufferedWriter> streamWriters;
	private Boolean shutdown;
	private final MessageConsumer consumer;
	private Logger logger = LoggerFactory.getLogger(TelemetryProcessor.class);

	@Autowired
	public TelemetryProcessor(final StreamFactory streamFactory) throws JMSException {
		shutdown = false;
		streamWriters = new HashMap<>();
		jmsSession = new JmsFactory().createSession();

		consumer = jmsSession.createConsumer(jmsSession.createQueue(Constants.QUEUE_RELEASE_EVENTS));

		Thread messageConsumerThread = new Thread(new Runnable() {
			@Override
			public void run() {
				boolean printedWaiting = false;
				while (!shutdown) {
					try {
						if (!printedWaiting) {
							logger.debug("Waiting for message");
							printedWaiting = true;
						}
						TextMessage message = (TextMessage) consumer.receive(ONE_SECOND);

						if (message != null) {
							printedWaiting = false;
							logger.debug("Got message '{}', correlationID {}", message.getText(), message.getJMSCorrelationID());
							String text = message.getText();
							String correlationID = message.getJMSCorrelationID();
							if (correlationID != null) {
								if (Constants.START_STREAM.equals(text)) {
									// Start new stream
									String streamUri = message.getStringProperty(Constants.STREAM_URI);
									BufferedWriter streamWriter = streamFactory.createStreamWriter(correlationID, streamUri);
									streamWriters.put(correlationID, streamWriter);
								} else if (Constants.FINISH_STREAM.equals(text)) {
									BufferedWriter writer = streamWriters.get(correlationID);
									if (writer != null) {
										writer.close();
										streamWriters.remove(correlationID);
									} else {
										logger.error("Attempting to close stream but no open stream for correlationID {}", correlationID);
									}
								} else {
									BufferedWriter writer = streamWriters.get(correlationID);
									if (writer != null) {
										writer.write(text);
									} else {
										logger.error("Attempting to write to stream but no open stream for correlationID {}", correlationID);
									}
								}
							}
						}
					} catch (JMSException e) {
						Exception linkedException = e.getLinkedException();
						if (linkedException != null && linkedException.getClass().equals(TransportDisposedIOException.class)) {
							logger.info("Transport disposed. Shutting down telemetry consumer.");
							shutdown = true;
						} else {
							logger.error("JMSException", e);
						}
					} catch (IOException e) {
						logger.error("Problem with output writer.", e);
					}
				}
			}
		});
		messageConsumerThread.start();
	}

	public void shutdown() throws InterruptedException, JMSException {
		this.shutdown = true;
		consumer.close();
	}
}
