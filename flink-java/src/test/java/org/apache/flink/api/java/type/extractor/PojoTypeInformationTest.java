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


package org.apache.flink.api.java.type.extractor;

import static org.junit.Assert.assertTrue;

import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.typeutils.CompositeType;
import org.apache.flink.api.java.typeutils.PojoTypeInfo;
import org.apache.flink.api.java.typeutils.TypeExtractor;
import org.junit.Assert;
import org.junit.Test;

@SuppressWarnings("unused")
public class PojoTypeInformationTest {

	public static class SimplePojo {
		String str;
		Boolean Bl;
		boolean bl;
		Byte Bt;
		byte bt;
		Short Shrt;
		short shrt;
		Integer Intgr;
		int intgr;
		Long Lng;
		long lng;
		Float Flt;
		float flt;
		Double Dbl;
		double dbl;
		Character Ch;
		char ch;
		int[] primIntArray;
		Integer[] intWrapperArray;
	}

	@Test
	public void testSimplePojoTypeExtraction() {
		TypeInformation<SimplePojo> type = TypeExtractor.getForClass(SimplePojo.class);
		assertTrue("Extracted type is not a composite/pojo type but should be.", type instanceof CompositeType);
	}

	public static class NestedPojoInner {
		private String field;
	}

	public static class NestedPojoOuter {
		private Integer intField;
		NestedPojoInner inner;
	}

	@Test
	public void testNestedPojoTypeExtraction() {
		TypeInformation<NestedPojoOuter> type = TypeExtractor.getForClass(NestedPojoOuter.class);
		assertTrue("Extracted type is not a Pojo type but should be.", type instanceof CompositeType);
	}

	public static class Recursive1Pojo {
		private Integer intField;
		Recursive2Pojo rec;
	}

	public static class Recursive2Pojo {
		private String strField;
		Recursive1Pojo rec;
	}

	@Test
	public void testRecursivePojoTypeExtraction() {
		// This one tests whether a recursive pojo is detected using the set of visited
		// types in the type extractor. The recursive field will be handled using the generic serializer.
		TypeInformation<Recursive1Pojo> type = TypeExtractor.getForClass(Recursive1Pojo.class);
		assertTrue("Extracted type is not a Pojo type but should be.", type instanceof CompositeType);
	}
	
	@Test
	public void testRecursivePojoObjectTypeExtraction() {
		TypeInformation<Recursive1Pojo> type = TypeExtractor.getForObject(new Recursive1Pojo());
		assertTrue("Extracted type is not a Pojo type but should be.", type instanceof CompositeType);
	}
	
	@Test
	public void testNextKeyField() {
		int[] input = {25,-1,44,1,99,32};
		Assert.assertEquals(new Tuple2<Integer, Integer>(1, 3), PojoTypeInfo.nextKeyField(input));
		
		int[] input1 = {-1,44};
		Assert.assertEquals(new Tuple2<Integer, Integer>(44, 1), PojoTypeInfo.nextKeyField(input1));
		Assert.assertEquals(5, PojoTypeInfo.countPositiveInts(input));
		Assert.assertEquals(1, PojoTypeInfo.countPositiveInts(input1));
	}
}
