package eu.stratosphere.streaming.api.streamcomponent;

import java.util.List;

import eu.stratosphere.configuration.Configuration;
import eu.stratosphere.nephele.event.task.EventListener;
import eu.stratosphere.nephele.io.ChannelSelector;
import eu.stratosphere.nephele.io.RecordReader;
import eu.stratosphere.nephele.io.RecordWriter;
import eu.stratosphere.nephele.template.AbstractInvokable;
import eu.stratosphere.streaming.api.AckEvent;
import eu.stratosphere.streaming.api.AckEventListener;
import eu.stratosphere.streaming.api.FailEvent;
import eu.stratosphere.streaming.api.FailEventListener;
import eu.stratosphere.streaming.api.FaultTolerancyBuffer;
import eu.stratosphere.streaming.api.invokable.DefaultSinkInvokable;
import eu.stratosphere.streaming.api.invokable.DefaultTaskInvokable;
import eu.stratosphere.streaming.api.invokable.StreamInvokable;
import eu.stratosphere.streaming.api.invokable.UserSinkInvokable;
import eu.stratosphere.streaming.partitioner.DefaultPartitioner;
import eu.stratosphere.streaming.partitioner.FieldsPartitioner;
import eu.stratosphere.types.Key;
import eu.stratosphere.types.Record;
import eu.stratosphere.types.StringValue;

public final class StreamComponentHelper<T extends AbstractInvokable> {

	public void setAckListener(FaultTolerancyBuffer recordBuffer,
			String sourceInstanceID, List<RecordWriter<Record>> outputs) {
		EventListener eventListener = new AckEventListener(sourceInstanceID,
				recordBuffer);
		for (RecordWriter<Record> output : outputs) {
			// TODO: separate outputs
			output.subscribeToEvent(eventListener, AckEvent.class);
		}
	}

	public void setFailListener(FaultTolerancyBuffer recordBuffer,
			String sourceInstanceID, List<RecordWriter<Record>> outputs) {
		EventListener eventListener = new FailEventListener(sourceInstanceID,
				recordBuffer);
		for (RecordWriter<Record> output : outputs) {
			// TODO: separate outputs
			output.subscribeToEvent(eventListener, FailEvent.class);
		}
	}

	public void setConfigInputs(T taskBase, Configuration taskConfiguration,
			List<RecordReader<Record>> inputs) throws Exception {
		int numberOfInputs = taskConfiguration.getInteger("numberOfInputs", 0);
		for (int i = 0; i < numberOfInputs; i++) {
			if (taskBase instanceof StreamTask) {
				inputs.add(new RecordReader<Record>((StreamTask) taskBase,
						Record.class));
			} else if (taskBase instanceof StreamSink) {
				inputs.add(new RecordReader<Record>((StreamSink) taskBase,
						Record.class));
			} else {
				throw new Exception(
						"Nonsupported object passed to setConfigInputs");
			}
		}
	}

	public void setConfigOutputs(T taskBase, Configuration taskConfiguration,
			List<RecordWriter<Record>> outputs,
			List<ChannelSelector<Record>> partitioners) throws Exception {
		int numberOfOutputs = taskConfiguration
				.getInteger("numberOfOutputs", 0);
		for (int i = 1; i <= numberOfOutputs; i++) {
			setPartitioner(taskConfiguration, i, partitioners);
		}
		for (ChannelSelector<Record> outputPartitioner : partitioners) {
			if (taskBase instanceof StreamTask) {
				outputs.add(new RecordWriter<Record>((StreamTask) taskBase,
						Record.class, outputPartitioner));
			} else if (taskBase instanceof StreamSource) {
				outputs.add(new RecordWriter<Record>((StreamSource) taskBase,
						Record.class, outputPartitioner));
			} else {
				throw new Exception(
						"Nonsupported object passed to setConfigOutputs");
			}
		}
	}

	public UserSinkInvokable getUserFunction(Configuration taskConfiguration) {

		Class<? extends UserSinkInvokable> userFunctionClass = taskConfiguration
				.getClass("userfunction", DefaultSinkInvokable.class,
						UserSinkInvokable.class);
		UserSinkInvokable userFunction = null;

		try {
			userFunction = userFunctionClass.newInstance();
		} catch (Exception e) {

		}
		return userFunction;
	}

	public StreamInvokable getUserFunction(Configuration taskConfiguration,
			List<RecordWriter<Record>> outputs, String instanceID,
			FaultTolerancyBuffer recordBuffer) {

		// Default value is a TaskInvokable even if it was called from a source
		Class<? extends StreamInvokable> userFunctionClass = taskConfiguration
				.getClass("userfunction", DefaultTaskInvokable.class,
						StreamInvokable.class);
		StreamInvokable userFunction = null;

		try {
			userFunction = userFunctionClass.newInstance();
			userFunction.declareOutputs(outputs, instanceID, recordBuffer);
		} catch (Exception e) {

		}
		return userFunction;
	}

	private void setPartitioner(Configuration taskConfiguration, int nrOutput,
			List<ChannelSelector<Record>> partitioners) {
		Class<? extends ChannelSelector<Record>> partitioner = taskConfiguration
				.getClass("partitionerClass_" + nrOutput,
						DefaultPartitioner.class, ChannelSelector.class);

		try {
			if (partitioner.equals(FieldsPartitioner.class)) {
				int keyPosition = taskConfiguration.getInteger(
						"partitionerIntParam_" + nrOutput, 1);
				Class<? extends Key> keyClass = taskConfiguration.getClass(
						"partitionerClassParam_" + nrOutput, StringValue.class,
						Key.class);

				partitioners.add(partitioner.getConstructor(int.class,
						Class.class).newInstance(keyPosition, keyClass));

			} else {
				partitioners.add(partitioner.newInstance());
			}
		} catch (Exception e) {
			System.out.println("partitioner error" + " " + "partitioner_"
					+ nrOutput);
			System.out.println(e);
		}
	}

}