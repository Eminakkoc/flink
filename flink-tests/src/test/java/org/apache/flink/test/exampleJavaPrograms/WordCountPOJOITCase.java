/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.test.exampleJavaPrograms;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.test.testdata.WordCountData;
import org.apache.flink.test.util.JavaProgramTestBase;
import org.apache.flink.util.Collector;

import java.io.Serializable;
import java.util.Date;


public class WordCountPOJOITCase extends JavaProgramTestBase implements Serializable {

	protected String textPath;
	protected String resultPath;

	
	@Override
	protected void preSubmit() throws Exception {
		textPath = createTempFile("text.txt", WordCountData.TEXT);
		resultPath = getTempDirPath("result");
	}

	@Override
	protected void postSubmit() throws Exception {
		compareResultsByLinesInMemory(WordCountData.COUNTS, resultPath);
	}
	
	@Override
	protected void testProgram() throws Exception {
		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		DataSet<String> text = env.readTextFile(textPath);

		DataSet<WC> counts = text
				.flatMap(new Tokenizer())
				.groupBy("complex.word.f2")
				.reduce(new ReduceFunction<WC>() {
					public WC reduce(WC value1, WC value2) {
						return new WC(value1.complex.word.f2, value1.count + value2.count);
					}
				});

		counts.writeAsText(resultPath);

		env.execute("WordCount with custom data types example");
	}

	public static final class Tokenizer implements FlatMapFunction<String, WC> {

		@Override
		public void flatMap(String value, Collector<WC> out) {
			// normalize and split the line
			String[] tokens = value.toLowerCase().split("\\W+");

			// emit the pairs
			for (String token : tokens) {
				if (token.length() > 0) {
					out.collect(new WC(token, 1));
				}
			}
		}
	}

	public static class ComplexNestedClass {
		public Date date;
		public Integer someNumber;
		public float someFloat;
		public Tuple3<Long, Long, String> word;
	//	public Object nothing;
	//  public IntWritable hadoopCitizen
	}
	
	/**
	 * Flat fields: [date, someNumber, someFloat, f0, f1, f2, count]
	 * Accessor chains
	 * [
	 * 	[complex, date],
	 *  [complex, someNumber],
	 *  [complex, someFloat],
	 *  [complex, word, f0],
	 *  [complex, word, f1],
	 *  [complex, word, f2]
	 *  [count]
	 * ]
	 *
	 */
	public static class WC {
		ComplexNestedClass complex;
		int count;

		public WC() {
		}

		public WC(String word, int count) {
			this.count = count;
			this.complex = new ComplexNestedClass();
			this.complex.word = new Tuple3<Long, Long, String>(0L, 0L, word);
		}

		public String toString() {
			return this.complex.word.f2 + " " + count;
		}
	}
}
