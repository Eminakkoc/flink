/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.connectors.kinesis;

import com.amazonaws.services.kinesis.model.Shard;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.streaming.connectors.kinesis.config.KinesisConfigConstants;
import org.apache.flink.streaming.connectors.kinesis.internals.KinesisDataFetcher;
import org.apache.flink.streaming.connectors.kinesis.model.KinesisStreamShard;
import org.apache.flink.streaming.connectors.kinesis.model.SequenceNumber;
import org.apache.flink.streaming.connectors.kinesis.model.KinesisStreamShardState;
import org.apache.flink.streaming.connectors.kinesis.testutils.KinesisShardIdGenerator;
import org.apache.flink.streaming.connectors.kinesis.testutils.TestableFlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.util.KinesisConfigUtil;
import org.apache.flink.streaming.util.serialization.SimpleStringSchema;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.Assert.assertTrue;

/**
 * Suite of FlinkKinesisConsumer tests for the methods called throughout the source life cycle.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({FlinkKinesisConsumer.class, KinesisConfigUtil.class})
public class FlinkKinesisConsumerTest {

	@Rule
	private ExpectedException exception = ExpectedException.none();

	// ----------------------------------------------------------------------
	// FlinkKinesisConsumer.validatePropertiesConfig() tests
	// ----------------------------------------------------------------------

	@Test
	public void testMissingAwsRegionInConfig() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("The AWS region ('" + KinesisConfigConstants.CONFIG_AWS_REGION + "') must be set in the config.");

		Properties testConfig = new Properties();
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_ACCESSKEYID, "accessKey");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_SECRETKEY, "secretKey");

		KinesisConfigUtil.validateConfiguration(testConfig);
	}

	@Test
	public void testUnrecognizableAwsRegionInConfig() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("Invalid AWS region");

		Properties testConfig = new Properties();
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_REGION, "wrongRegionId");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_ACCESSKEYID, "accessKeyId");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_SECRETKEY, "secretKey");

		KinesisConfigUtil.validateConfiguration(testConfig);
	}

	@Test
	public void testCredentialProviderTypeDefaultToBasicButNoCredentialsSetInConfig() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("Please set values for AWS Access Key ID ('"+KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_ACCESSKEYID+"') " +
				"and Secret Key ('" + KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_SECRETKEY + "') when using the BASIC AWS credential provider type.");

		Properties testConfig = new Properties();
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_REGION, "us-east-1");

		KinesisConfigUtil.validateConfiguration(testConfig);
	}

	@Test
	public void testCredentialProviderTypeSetToBasicButNoCredentialSetInConfig() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("Please set values for AWS Access Key ID ('"+KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_ACCESSKEYID+"') " +
				"and Secret Key ('" + KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_SECRETKEY + "') when using the BASIC AWS credential provider type.");

		Properties testConfig = new Properties();
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_REGION, "us-east-1");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_TYPE, "BASIC");

		KinesisConfigUtil.validateConfiguration(testConfig);
	}

	@Test
	public void testUnrecognizableCredentialProviderTypeInConfig() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("Invalid AWS Credential Provider Type");

		Properties testConfig = new Properties();
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_REGION, "us-east-1");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_TYPE, "wrongProviderType");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_ACCESSKEYID, "accessKeyId");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_SECRETKEY, "secretKey");

		KinesisConfigUtil.validateConfiguration(testConfig);
	}

	@Test
	public void testUnrecognizableStreamInitPositionTypeInConfig() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("Invalid initial position in stream");

		Properties testConfig = new Properties();
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_REGION, "us-east-1");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_TYPE, "BASIC");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_ACCESSKEYID, "accessKeyId");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_SECRETKEY, "secretKey");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_STREAM_INIT_POSITION_TYPE, "wrongInitPosition");

		KinesisConfigUtil.validateConfiguration(testConfig);
	}

	@Test
	public void testUnparsableLongForDescribeStreamBackoffBaseMillisInConfig() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("Invalid value given for describe stream operation base backoff milliseconds");

		Properties testConfig = new Properties();
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_REGION, "us-east-1");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_ACCESSKEYID, "accessKeyId");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_SECRETKEY, "secretKey");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_STREAM_DESCRIBE_BACKOFF_BASE, "unparsableLong");

		KinesisConfigUtil.validateConfiguration(testConfig);
	}

	@Test
	public void testUnparsableLongForDescribeStreamBackoffMaxMillisInConfig() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("Invalid value given for describe stream operation max backoff milliseconds");

		Properties testConfig = new Properties();
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_REGION, "us-east-1");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_ACCESSKEYID, "accessKeyId");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_SECRETKEY, "secretKey");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_STREAM_DESCRIBE_BACKOFF_MAX, "unparsableLong");

		KinesisConfigUtil.validateConfiguration(testConfig);
	}

	@Test
	public void testUnparsableDoubleForDescribeStreamBackoffExponentialConstantInConfig() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("Invalid value given for describe stream operation backoff exponential constant");

		Properties testConfig = new Properties();
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_REGION, "us-east-1");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_ACCESSKEYID, "accessKeyId");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_SECRETKEY, "secretKey");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_STREAM_DESCRIBE_BACKOFF_EXPONENTIAL_CONSTANT, "unparsableDouble");

		KinesisConfigUtil.validateConfiguration(testConfig);
	}

	@Test
	public void testUnparsableIntForGetRecordsRetriesInConfig() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("Invalid value given for maximum retry attempts for getRecords shard operation");

		Properties testConfig = new Properties();
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_REGION, "us-east-1");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_ACCESSKEYID, "accessKeyId");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_SECRETKEY, "secretKey");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_SHARD_GETRECORDS_RETRIES, "unparsableInt");

		KinesisConfigUtil.validateConfiguration(testConfig);
	}

	@Test
	public void testUnparsableIntForGetRecordsMaxCountInConfig() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("Invalid value given for maximum records per getRecords shard operation");

		Properties testConfig = new Properties();
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_REGION, "us-east-1");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_ACCESSKEYID, "accessKeyId");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_SECRETKEY, "secretKey");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_SHARD_GETRECORDS_MAX, "unparsableInt");

		KinesisConfigUtil.validateConfiguration(testConfig);
	}

	@Test
	public void testUnparsableLongForGetRecordsBackoffBaseMillisInConfig() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("Invalid value given for get records operation base backoff milliseconds");

		Properties testConfig = new Properties();
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_REGION, "us-east-1");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_ACCESSKEYID, "accessKeyId");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_SECRETKEY, "secretKey");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_SHARD_GETRECORDS_BACKOFF_BASE, "unparsableLong");

		KinesisConfigUtil.validateConfiguration(testConfig);
	}

	@Test
	public void testUnparsableLongForGetRecordsBackoffMaxMillisInConfig() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("Invalid value given for get records operation max backoff milliseconds");

		Properties testConfig = new Properties();
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_REGION, "us-east-1");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_ACCESSKEYID, "accessKeyId");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_SECRETKEY, "secretKey");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_SHARD_GETRECORDS_BACKOFF_MAX, "unparsableLong");

		KinesisConfigUtil.validateConfiguration(testConfig);
	}

	@Test
	public void testUnparsableDoubleForGetRecordsBackoffExponentialConstantInConfig() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("Invalid value given for get records operation backoff exponential constant");

		Properties testConfig = new Properties();
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_REGION, "us-east-1");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_ACCESSKEYID, "accessKeyId");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_SECRETKEY, "secretKey");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_SHARD_GETRECORDS_BACKOFF_EXPONENTIAL_CONSTANT, "unparsableDouble");

		KinesisConfigUtil.validateConfiguration(testConfig);
	}

	@Test
	public void testUnparsableIntForGetShardIteratorRetriesInConfig() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("Invalid value given for maximum retry attempts for getShardIterator shard operation");

		Properties testConfig = new Properties();
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_REGION, "us-east-1");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_ACCESSKEYID, "accessKeyId");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_SECRETKEY, "secretKey");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_SHARD_GETITERATOR_RETRIES, "unparsableInt");

		KinesisConfigUtil.validateConfiguration(testConfig);
	}

	@Test
	public void testUnparsableLongForGetShardIteratorBackoffBaseMillisInConfig() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("Invalid value given for get shard iterator operation base backoff milliseconds");

		Properties testConfig = new Properties();
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_REGION, "us-east-1");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_ACCESSKEYID, "accessKeyId");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_SECRETKEY, "secretKey");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_SHARD_GETITERATOR_BACKOFF_BASE, "unparsableLong");

		KinesisConfigUtil.validateConfiguration(testConfig);
	}

	@Test
	public void testUnparsableLongForGetShardIteratorBackoffMaxMillisInConfig() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("Invalid value given for get shard iterator operation max backoff milliseconds");

		Properties testConfig = new Properties();
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_REGION, "us-east-1");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_ACCESSKEYID, "accessKeyId");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_SECRETKEY, "secretKey");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_SHARD_GETITERATOR_BACKOFF_MAX, "unparsableLong");

		KinesisConfigUtil.validateConfiguration(testConfig);
	}

	@Test
	public void testUnparsableDoubleForGetShardIteratorBackoffExponentialConstantInConfig() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("Invalid value given for get shard iterator operation backoff exponential constant");

		Properties testConfig = new Properties();
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_REGION, "us-east-1");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_ACCESSKEYID, "accessKeyId");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_SECRETKEY, "secretKey");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_SHARD_GETITERATOR_BACKOFF_EXPONENTIAL_CONSTANT, "unparsableDouble");

		KinesisConfigUtil.validateConfiguration(testConfig);
	}

	@Test
	public void testUnparsableLongForShardDiscoveryIntervalMillisInConfig() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("Invalid value given for shard discovery sleep interval in milliseconds");

		Properties testConfig = new Properties();
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_REGION, "us-east-1");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_ACCESSKEYID, "accessKeyId");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_SECRETKEY, "secretKey");
		testConfig.setProperty(KinesisConfigConstants.CONFIG_SHARD_DISCOVERY_INTERVAL_MILLIS, "unparsableLong");

		KinesisConfigUtil.validateConfiguration(testConfig);
	}

	// ----------------------------------------------------------------------
	// Tests related to state initialization
	// ----------------------------------------------------------------------

	@Test
	public void testSnapshotStateShouldBeNullIfSourceNotOpened() throws Exception {
		Properties config = new Properties();
		config.setProperty(KinesisConfigConstants.CONFIG_AWS_REGION, "us-east-1");
		config.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_ACCESSKEYID, "accessKeyId");
		config.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_SECRETKEY, "secretKey");

		FlinkKinesisConsumer<String> consumer = new FlinkKinesisConsumer<>("fakeStream", new SimpleStringSchema(), config);

		assertTrue(consumer.snapshotState(123, 123) == null); //arbitrary checkpoint id and timestamp
	}

	@Test
	public void testSnapshotStateShouldBeNullIfSourceNotRun() throws Exception {
		Properties config = new Properties();
		config.setProperty(KinesisConfigConstants.CONFIG_AWS_REGION, "us-east-1");
		config.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_ACCESSKEYID, "accessKeyId");
		config.setProperty(KinesisConfigConstants.CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_SECRETKEY, "secretKey");

		FlinkKinesisConsumer<String> consumer = new FlinkKinesisConsumer<>("fakeStream", new SimpleStringSchema(), config);
		consumer.open(new Configuration()); // only opened, not run

		assertTrue(consumer.snapshotState(123, 123) == null); //arbitrary checkpoint id and timestamp
	}

	// ----------------------------------------------------------------------
	// Tests related to fetcher initialization
	// ----------------------------------------------------------------------

	@Test
	@SuppressWarnings("unchecked")
	public void testFetcherShouldNotBeRestoringFromFailureIfNotRestoringFromCheckpoint() throws Exception {
		KinesisDataFetcher mockedFetcher = Mockito.mock(KinesisDataFetcher.class);
		PowerMockito.whenNew(KinesisDataFetcher.class).withAnyArguments().thenReturn(mockedFetcher);

		// assume the given config is correct
		PowerMockito.mockStatic(KinesisConfigUtil.class);
		PowerMockito.doNothing().when(KinesisConfigUtil.class);

		TestableFlinkKinesisConsumer consumer = new TestableFlinkKinesisConsumer(
			"fakeStream", new Properties(), 10, 2);
		consumer.open(new Configuration());
		consumer.run(Mockito.mock(SourceFunction.SourceContext.class));

		Mockito.verify(mockedFetcher).setIsRestoringFromFailure(false);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFetcherShouldBeCorrectlySeededIfRestoringFromCheckpoint() throws Exception {
		KinesisDataFetcher mockedFetcher = Mockito.mock(KinesisDataFetcher.class);
		PowerMockito.whenNew(KinesisDataFetcher.class).withAnyArguments().thenReturn(mockedFetcher);

		// assume the given config is correct
		PowerMockito.mockStatic(KinesisConfigUtil.class);
		PowerMockito.doNothing().when(KinesisConfigUtil.class);

		HashMap<KinesisStreamShard, SequenceNumber> fakeRestoredState = new HashMap<>();
		fakeRestoredState.put(
			new KinesisStreamShard("fakeStream1",
				new Shard().withShardId(KinesisShardIdGenerator.generateFromShardOrder(0))),
			new SequenceNumber(UUID.randomUUID().toString()));
		fakeRestoredState.put(
			new KinesisStreamShard("fakeStream1",
				new Shard().withShardId(KinesisShardIdGenerator.generateFromShardOrder(1))),
			new SequenceNumber(UUID.randomUUID().toString()));
		fakeRestoredState.put(
			new KinesisStreamShard("fakeStream1",
				new Shard().withShardId(KinesisShardIdGenerator.generateFromShardOrder(2))),
			new SequenceNumber(UUID.randomUUID().toString()));
		fakeRestoredState.put(
			new KinesisStreamShard("fakeStream2",
				new Shard().withShardId(KinesisShardIdGenerator.generateFromShardOrder(0))),
			new SequenceNumber(UUID.randomUUID().toString()));
		fakeRestoredState.put(
			new KinesisStreamShard("fakeStream2",
				new Shard().withShardId(KinesisShardIdGenerator.generateFromShardOrder(1))),
			new SequenceNumber(UUID.randomUUID().toString()));

		TestableFlinkKinesisConsumer consumer = new TestableFlinkKinesisConsumer(
			"fakeStream", new Properties(), 10, 2);
		consumer.restoreState(fakeRestoredState);
		consumer.open(new Configuration());
		consumer.run(Mockito.mock(SourceFunction.SourceContext.class));

		Mockito.verify(mockedFetcher).setIsRestoringFromFailure(true);
		for (Map.Entry<KinesisStreamShard, SequenceNumber> restoredShard : fakeRestoredState.entrySet()) {
			Mockito.verify(mockedFetcher).advanceLastDiscoveredShardOfStream(
				restoredShard.getKey().getStreamName(), restoredShard.getKey().getShard().getShardId());
			Mockito.verify(mockedFetcher).registerNewSubscribedShardState(
				new KinesisStreamShardState(restoredShard.getKey(), restoredShard.getValue()));
		}
	}
}
