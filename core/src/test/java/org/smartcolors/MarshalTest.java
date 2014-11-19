package org.smartcolors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;

import org.junit.Before;
import org.junit.Test;
import org.smartcolors.marshal.BytesDeserializer;
import org.smartcolors.marshal.BytesSerializer;
import org.smartcolors.marshal.Deserializer;
import org.smartcolors.marshal.HashableSerializable;
import org.smartcolors.marshal.MerbinnerTree;
import org.smartcolors.marshal.Serializer;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Created by devrandom on 2014-Nov-17.
 */
public class MarshalTest {
	private ObjectMapper mapper;

	@Before
	public void setUp() {
		mapper = new ObjectMapper();
	}

	static class BoxedVaruint extends HashableSerializable {
		public long value;

		public BoxedVaruint(long value) {
			this.value = value;
		}
		@Override
		public void serialize(Serializer ser) {
			ser.write(value);
		}

		@Override
		public byte[] getHmacKey() {
			return Utils.HEX.decode("dd2617248e435da6db7c119c17cc19cd");
		}

		public static BoxedVaruint deserialize(Deserializer ser) {
			return new BoxedVaruint(ser.readVaruint());
		}
	}

	static class BoxedBytes extends HashableSerializable {
		public byte[] bytes;
		private byte[] cachedHash;

		public BoxedBytes(byte[] bytes) {
			this.bytes = bytes;
		}

		public void serialize(Serializer serializer) {
			serializer.writeWithLength(bytes);
		}

		public void serializeFixed(Serializer ser) {
			ser.write(bytes);
		}

		@Override
		public byte[] getHmacKey() {
			return Utils.HEX.decode("f690a4d282810e868a0d7d59578a6585");
		}

		public static BoxedBytes deserialize(Deserializer ser, Integer expectedLength) {
			if (expectedLength != null)
				return new BoxedBytes(ser.readBytes(expectedLength));
			else
				return new BoxedBytes(ser.readBytes());
		}
	}

	static class BoxedObj extends HashableSerializable {
		public BoxedBytes buf;
		public BoxedVaruint i;
		private byte[] cachedHash;

		public BoxedObj(byte[] buf, long i) {
			this.buf = new BoxedBytes(buf);
			this.i = new BoxedVaruint(i);
		}

		public BoxedObj() {
		}

		public void serialize(Serializer ser) {
			ser.write(buf);
			ser.write(i);
		}

		public static BoxedObj deserialize(Deserializer des) {
			BoxedObj obj = new BoxedObj();
			obj.buf = BoxedBytes.deserialize(des, null);
			obj.i = BoxedVaruint.deserialize(des);
			return obj;
		}

		@Override
		public byte[] getHmacKey() {
			return Utils.HEX.decode("296d566c10ebb4b92e8a7f6e909eb191");
		}
	}

	@Test
	public void testVaruint() throws IOException {
		List<List<String>> items =
				mapper.readValue(FixtureHelpers.fixture("marshal/valid_varuints.json"),
						new TypeReference<List<List<String>>>(){});
		for (List<String> entry : items) {
			BytesSerializer serializer = new BytesSerializer();
			if (entry.size() == 1) continue; // comment
			byte[] expected = Utils.HEX.decode(entry.get(0));
			long value = Long.parseLong(entry.get(1));
			BoxedVaruint boxed = new BoxedVaruint(value);
			boxed.serialize(serializer);
			byte[] bytes = serializer.getBytes();
			assertArrayEquals(entry.get(0), expected, bytes);
			BytesDeserializer deserializer = new BytesDeserializer(bytes);
			assertEquals(value, BoxedVaruint.deserialize(deserializer).value);
		}
	}

	@Test
	public void testBytes() throws IOException {
		List<List<String>> items =
				mapper.readValue(FixtureHelpers.fixture("marshal/valid_bytes.json"),
						new TypeReference<List<List<String>>>(){});
		for (List<String> entry : items) {
			BytesSerializer serializer = new BytesSerializer();
			if (entry.size() == 1) continue; // comment
			byte[] expectedBytes = Utils.HEX.decode(entry.get(0));
			byte[] value = Utils.HEX.decode(entry.get(1));
			Integer expectedLength = entry.get(2) == null ? null : Integer.valueOf(entry.get(2));
			BoxedBytes boxed = new BoxedBytes(value);
			if (expectedLength != null)
				boxed.serializeFixed(serializer);
			else
				boxed.serialize(serializer);
			byte[] bytes = serializer.getBytes();
			assertArrayEquals(entry.get(0), expectedBytes, bytes);
			BytesDeserializer deserializer = new BytesDeserializer(bytes);
			assertArrayEquals(value, BoxedBytes.deserialize(deserializer, expectedLength).bytes);
		}
		System.out.println(items);
	}

	@Test
	public void testObjs() throws IOException {
		List<List<String>> items =
				mapper.readValue(FixtureHelpers.fixture("marshal/valid_boxed_objs.json"),
						new TypeReference<List<List<String>>>(){});
		for (List<String> entry : items) {
			BytesSerializer serializer = new BytesSerializer();
			if (entry.size() == 1) continue; // comment
			byte[] expectedBytes = Utils.HEX.decode(entry.get(0));
			byte[] buf = Utils.HEX.decode(entry.get(1));
			long i = Long.parseLong(entry.get(2));
			byte[] expectedHash = Utils.HEX.decode(entry.get(3));
			BoxedObj boxed = new BoxedObj(buf, i);
			serializer.write(boxed);
			byte[] bytes = serializer.getBytes();
			assertArrayEquals(entry.get(0), expectedBytes, bytes);
			BytesDeserializer deserializer = new BytesDeserializer(bytes);
			BoxedObj actual = BoxedObj.deserialize(deserializer);
			assertArrayEquals(actual.buf.bytes, buf);
			assertEquals(actual.i.value, i);
			serializer = new BytesSerializer();
			serializer.write(actual);
			byte[] roundTrip = serializer.getBytes();
			assertArrayEquals(expectedBytes, roundTrip);
			byte[] hash = boxed.getHash();
			assertArrayEquals(expectedHash, hash);
		}
	}

	static class TestMerbinnerTree extends MerbinnerTree<byte[], byte[]> {
		public static class TestNode extends Node<byte[], byte[]> {
			public TestNode(byte[] key, byte[] value) {
				this.key = key;
				this.value = value;
			}

			public TestNode() {
			}

			@Override
			public void serializeKey(Serializer ser) {
				ser.write(key);
			}

			@Override
			public void serializeValue(Serializer ser) {
				ser.write(value);
			}

			@Override
			public long getSum() {
				return 0;
			}

			@Override
			public byte[] getKeyHash() {
				return key;
			}
		}

		@Override
		protected MerbinnerTree.Node deserializeNode(Deserializer des) {
			byte[] key = des.readBytes(4);
			byte[] value = des.readBytes(4);
			TestNode node = new TestNode(key, value);
			return node;
		}

		@Override
		public byte[] getHmacKey() {
			return Utils.HEX.decode("92e8898fcfa8b86b60b32236d6990da0");
		}

		TestMerbinnerTree(Set<Node<byte[], byte[]>> nodes) {
			super(nodes);
		}
	}

	@Test
	public void testMerbinner() throws IOException {
		List<List<Object>> items =
				mapper.readValue(FixtureHelpers.fixture("marshal/merbinnertree_hashes.json"),
						new TypeReference<List<List<Object>>>() {
						});
		for (List<Object> entry : items) {
			if (entry.size() == 1) continue; // comment
			Map<String, String> map = (Map<String, String>) entry.get(0);
			String mode = (String) entry.get(1);
			String expectedHex = (String) entry.get(2);
			byte[] expected = Utils.HEX.decode(expectedHex.replaceAll(" ", ""));
			Set<MerbinnerTree.Node<byte[], byte[]>> nodes = Sets.newHashSet();
			for (String keyString : map.keySet()) {
				byte[] value = Utils.HEX.decode(map.get(keyString));
				byte[] key = Utils.HEX.decode(keyString);
				TestMerbinnerTree.TestNode node = new TestMerbinnerTree.TestNode(key, value);
				nodes.add(node);
			}
			TestMerbinnerTree tree = new TestMerbinnerTree(nodes);

			if (mode.equals("serialize")) {
				BytesSerializer ser = new BytesSerializer();
				ser.write(tree);
				assertArrayEquals(expected, ser.getBytes());
				BytesDeserializer des = new BytesDeserializer(expected);
				TestMerbinnerTree tree2 = new TestMerbinnerTree(Sets.<MerbinnerTree.Node<byte[], byte[]>>newHashSet());
				tree2.deserialize(des);
				BytesSerializer ser1 = new BytesSerializer();
				tree2.serialize(ser1);
				assertArrayEquals(expected, ser1.getBytes());
			} else if (mode.equals("hash")) {
				assertArrayEquals(expected, tree.getHash());
			} else {
				fail(mode);
			}
		}
	}
}
