package org.opentibia.canary.protobuf.kv

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Wire-format tests for the ported `Canary.protobuf.kv` messages
 * (`src/protobuf/kv.proto`): `ValueWrapper`, `ArrayType`, `KeyValuePair` and `MapType`.
 *
 * The golden byte strings were produced with
 * `protoc --encode=Canary.protobuf.kv.ValueWrapper kv.proto` (libprotoc 33.4), so they
 * pin the codec to the canonical protobuf encoding Canary's `kv.pb.cc` emits.
 */
class KvProtoTest {

    private fun bytes(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun parse(hex: String): ValueWrapper {
        val message = ValueWrapper()
        assertThat(message.parseFromArray(bytes(hex))).isTrue()
        return message
    }

    @Test
    fun `empty message serializes to no bytes`() {
        assertThat(ValueWrapper().serializeToByteArray()).isEmpty()
    }

    @Test
    fun `string value is field one length delimited`() {
        val message = ValueWrapper().apply { setStrValue("hello") }

        assertThat(message.valueCase()).isEqualTo(ValueWrapper.ValueCase.STR_VALUE)
        assertThat(message.serializeToByteArray()).isEqualTo(bytes("0a0568656c6c6f"))
    }

    @Test
    fun `int value is field two varint`() {
        val message = ValueWrapper().apply { setIntValue(42) }

        assertThat(message.valueCase()).isEqualTo(ValueWrapper.ValueCase.INT_VALUE)
        assertThat(message.serializeToByteArray()).isEqualTo(bytes("102a"))
    }

    @Test
    fun `negative int is a ten byte sign extended varint`() {
        val message = ValueWrapper().apply { setIntValue(-1) }

        assertThat(message.serializeToByteArray()).isEqualTo(bytes("10ffffffffffffffffff01"))
    }

    @Test
    fun `double value is field three fixed64`() {
        val message = ValueWrapper().apply { setDoubleValue(3.14) }

        assertThat(message.valueCase()).isEqualTo(ValueWrapper.ValueCase.DOUBLE_VALUE)
        assertThat(message.serializeToByteArray()).isEqualTo(bytes("191f85eb51b81e0940"))
    }

    @Test
    fun `bool value is field six varint`() {
        val message = ValueWrapper().apply { setBoolValue(true) }

        assertThat(message.valueCase()).isEqualTo(ValueWrapper.ValueCase.BOOL_VALUE)
        assertThat(message.serializeToByteArray()).isEqualTo(bytes("3001"))
    }

    @Test
    fun `array value is field four with repeated field one entries`() {
        val message = ValueWrapper().apply {
            val array = mutableArrayValue()
            array.addValues().setIntValue(1)
            array.addValues().setStrValue("two")
            array.addValues().setBoolValue(true)
        }

        assertThat(message.valueCase()).isEqualTo(ValueWrapper.ValueCase.ARRAY_VALUE)
        assertThat(message.serializeToByteArray()).isEqualTo(bytes("220f0a0210010a050a0374776f0a023001"))
    }

    @Test
    fun `map value is field five with key value pairs in field order`() {
        val message = ValueWrapper().apply {
            val map = mutableMapValue()
            map.addItems().apply {
                setKey("answer")
                mutableValue().setIntValue(42)
            }
            map.addItems().apply {
                setKey("nested")
                mutableValue().mutableMapValue().addItems().apply {
                    setKey("flag")
                    mutableValue().setBoolValue(true)
                }
            }
        }

        assertThat(message.valueCase()).isEqualTo(ValueWrapper.ValueCase.MAP_VALUE)
        assertThat(message.serializeToByteArray()).isEqualTo(
            bytes("2a280a0c0a06616e737765721202102a0a180a066e6573746564120e2a0c0a0a0a04666c616712023001"),
        )
    }

    @Test
    fun `explicitly set default scalar values keep their oneof case`() {
        assertThat(ValueWrapper().apply { setStrValue("") }.serializeToByteArray()).isEqualTo(bytes("0a00"))
        assertThat(ValueWrapper().apply { setIntValue(0) }.serializeToByteArray()).isEqualTo(bytes("1000"))
        assertThat(ValueWrapper().apply { setBoolValue(false) }.serializeToByteArray()).isEqualTo(bytes("3000"))
        assertThat(ValueWrapper().apply { mutableArrayValue() }.serializeToByteArray()).isEqualTo(bytes("2200"))
        assertThat(ValueWrapper().apply { mutableMapValue() }.serializeToByteArray()).isEqualTo(bytes("2a00"))
    }

    @Test
    fun `parses a serialized string message`() {
        val message = parse("0a0568656c6c6f")

        assertThat(message.valueCase()).isEqualTo(ValueWrapper.ValueCase.STR_VALUE)
        assertThat(message.hasStrValue()).isTrue()
        assertThat(message.strValue()).isEqualTo("hello")
    }

    @Test
    fun `parses scalar cases`() {
        assertThat(parse("102a").intValue()).isEqualTo(42)
        assertThat(parse("10ffffffffffffffffff01").intValue()).isEqualTo(-1)
        assertThat(parse("191f85eb51b81e0940").doubleValue()).isEqualTo(3.14)
        assertThat(parse("3001").boolValue()).isTrue()
        assertThat(parse("1000").valueCase()).isEqualTo(ValueWrapper.ValueCase.INT_VALUE)
        assertThat(parse("3000").valueCase()).isEqualTo(ValueWrapper.ValueCase.BOOL_VALUE)
    }

    @Test
    fun `parses nested array and map messages`() {
        val message = parse("22080a0622040a021001")

        val nested = message.arrayValue().getValues(0).arrayValue().getValues(0)
        assertThat(nested.intValue()).isEqualTo(1)
    }

    @Test
    fun `the last oneof member wins and clears the previous scalar`() {
        val message = parse("102a0a026869")

        assertThat(message.valueCase()).isEqualTo(ValueWrapper.ValueCase.STR_VALUE)
        assertThat(message.strValue()).isEqualTo("hi")
        assertThat(message.intValue()).isZero()
        assertThat(message.hasIntValue()).isFalse()
    }

    @Test
    fun `unknown fields are skipped`() {
        // field 15 varint (tag 0x78) followed by the string field.
        val message = parse("78010a0568656c6c6f")

        assertThat(message.strValue()).isEqualTo("hello")
    }

    @Test
    fun `repeated occurrences of a message field merge`() {
        val message = parse("22020a00" + "22040a021001")

        assertThat(message.arrayValue().valuesCount()).isEqualTo(2)
        assertThat(message.arrayValue().getValues(0).valueCase()).isEqualTo(ValueWrapper.ValueCase.VALUE_NOT_SET)
        assertThat(message.arrayValue().getValues(1).valueCase()).isEqualTo(ValueWrapper.ValueCase.INT_VALUE)
    }

    @Test
    fun `malformed payloads fail to parse`() {
        assertThat(ValueWrapper().parseFromArray(byteArrayOf(0x80.toByte()))).isFalse()
        assertThat(ValueWrapper().parseFromArray(byteArrayOf(0x0a, 0x05, 0x68))).isFalse()
        assertThat(ValueWrapper().parseFromArray(byteArrayOf(0x1f))).isFalse()
    }

    @Test
    fun `mergeFrom copies the active oneof member`() {
        val source = ValueWrapper().apply { setIntValue(7) }
        val target = ValueWrapper().apply { setStrValue("old") }

        target.mergeFrom(source)

        assertThat(target.valueCase()).isEqualTo(ValueWrapper.ValueCase.INT_VALUE)
        assertThat(target.intValue()).isEqualTo(7)
        assertThat(target.strValue()).isEmpty()
    }

    @Test
    fun `mergeFrom appends repeated message entries`() {
        val source = ValueWrapper().apply {
            mutableArrayValue().addValues().setIntValue(2)
        }
        val target = ValueWrapper().apply {
            mutableArrayValue().addValues().setIntValue(1)
        }

        target.mergeFrom(source)

        assertThat(target.arrayValue().valuesCount()).isEqualTo(2)
        assertThat(target.arrayValue().getValues(1).intValue()).isEqualTo(2)
    }

    @Test
    fun `message fields expose counts and indexed access`() {
        val array = ArrayType()
        array.addValues().setIntValue(1)
        array.addValues(ValueWrapper().apply { setStrValue("two") })

        assertThat(array.valuesCount()).isEqualTo(2)
        assertThat(array.getValues(0).intValue()).isEqualTo(1)
        assertThat(array.values().map { it.valueCase() }).containsExactly(
            ValueWrapper.ValueCase.INT_VALUE,
            ValueWrapper.ValueCase.STR_VALUE,
        )

        val pair = KeyValuePair().apply {
            setKey("k")
            mutableValue().setBoolValue(true)
        }
        val map = MapType()
        map.addItems(pair)

        assertThat(map.itemsCount()).isEqualTo(1)
        assertThat(map.getItems(0).key()).isEqualTo("k")
        assertThat(map.getItems(0).hasValue()).isTrue()
        assertThat(map.getItems(0).value().boolValue()).isTrue()
    }
}
