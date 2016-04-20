package org.apache.flink.streaming.connectors.kinesis.config;

/**
 *
 */
public class KinesisConfigConstants {

	// ------------------------------------------------------------------------
	//  Configuration Keys
	// ------------------------------------------------------------------------

	/** The max retries to retrieve metadata from a Kinesis stream using describeStream API
	 * (Note: describeStream attempts may be temporarily blocked due to AWS capping 5 attempts per sec)  */
	public static final String CONFIG_STREAM_DESCRIBE_RETRIES = "flink.stream.describe.retry";

	/** The backoff time between each describeStream attempt */
	public static final String CONFIG_STREAM_DESCRIBE_BACKOFF = "flink.stream.describe.backoff";

	/** The initial position to start reading Kinesis streams from (LATEST is used if not set) */
	public static final String CONFIG_STREAM_INIT_POSITION_TYPE = "flink.stream.initpos.type";

	/** The credential provider type to use when AWS credentials are required (BASIC is used if not set)*/
	public static final String CONFIG_AWS_CREDENTIALS_PROVIDER_TYPE = "aws.credentials.provider";

	/** The AWS access key ID to use when setting credentials provider type to BASIC */
	public static final String CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_ACCESSKEYID = "aws.credentials.provider.basic.accesskeyid";

	/** The AWS secret key to use when setting credentials provider type to BASIC */
	public static final String CONFIG_AWS_CREDENTIALS_PROVIDER_BASIC_SECRETKEY = "aws.credentials.provider.basic.secretkey";

	/** Optional configuration for profile path if credential provider type is set to be PROFILE */
	public static final String CONFIG_AWS_CREDENTIALS_PROVIDER_PROFILE_PATH = "aws.credentials.provider.profile.path";

	/** Optional configuration for profile name if credential provider type is set to be PROFILE */
	public static final String CONFIG_AWS_CREDENTIALS_PROVIDER_PROFILE_NAME = "aws.credentials.provider.profile.name";

	/** The AWS region of the Kinesis streams to be pulled ("us-east-1" is used if not set) */
	public static final String CONFIG_AWS_REGION = "aws.region";


	// ------------------------------------------------------------------------
	//  Default configuration values
	// ------------------------------------------------------------------------

	public static final String DEFAULT_AWS_REGION = "us-east-1";

	public static final int DEFAULT_STREAM_DESCRIBE_RETRY_TIMES = 3;

	public static final long DEFAULT_STREAM_DESCRIBE_BACKOFF = 1000L;

}