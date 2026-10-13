package org.opentibia.canary.kv

import org.opentibia.canary.protobuf.kv.ValueWrapper as ProtoValueWrapper

/**
 * Port of `ProtoHelpers` (`src/kv/value_wrapper_proto.cpp:16-48`): the oneof writers
 * used by [ProtoSerializable.toProto].
 */
object ProtoHelpers {

    fun setProtoStringValue(protoValue: ProtoValueWrapper, arg: String) {
        protoValue.setStrValue(arg)
    }

    fun setProtoBooleanValue(protoValue: ProtoValueWrapper, arg: Boolean) {
        protoValue.setBoolValue(arg)
    }

    fun setProtoIntValue(protoValue: ProtoValueWrapper, arg: Int) {
        protoValue.setIntValue(arg)
    }

    fun setProtoDoubleValue(protoValue: ProtoValueWrapper, arg: Double) {
        protoValue.setDoubleValue(arg)
    }

    fun setProtoArrayValue(protoValue: ProtoValueWrapper, arg: List<ValueWrapper>) {
        val arrayValue = protoValue.mutableArrayValue()
        for (element in arg) {
            arrayValue.addValues(ProtoSerializable.toProto(element))
        }
    }

    fun setProtoMapValue(protoValue: ProtoValueWrapper, arg: Map<String, ValueWrapper>) {
        val mapValue = protoValue.mutableMapValue()
        for ((key, value) in arg) {
            mapValue.addItems().apply {
                setKey(key)
                mutableValue().mergeFrom(ProtoSerializable.toProto(value))
            }
        }
    }
}

/**
 * Port of `ProtoSerializable` (`src/kv/value_wrapper_proto.hpp:28-31`,
 * `value_wrapper_proto.cpp:50-109`): [ValueWrapper] to the typed
 * `Canary.protobuf.kv` messages and back.
 */
object ProtoSerializable {

    /** `ProtoSerializable::toProto`. */
    fun toProto(obj: ValueWrapper): ProtoValueWrapper {
        val protoValue = ProtoValueWrapper()
        when (val variant = obj.getVariant()) {
            is ValueVariant.StringValue -> ProtoHelpers.setProtoStringValue(protoValue, variant.value)
            is ValueVariant.BooleanValue -> ProtoHelpers.setProtoBooleanValue(protoValue, variant.value)
            is ValueVariant.IntValue -> ProtoHelpers.setProtoIntValue(protoValue, variant.value)
            is ValueVariant.DoubleValue -> ProtoHelpers.setProtoDoubleValue(protoValue, variant.value)
            is ValueVariant.ArrayValue -> ProtoHelpers.setProtoArrayValue(protoValue, variant.values)
            is ValueVariant.MapValue -> ProtoHelpers.setProtoMapValue(protoValue, variant.values)
        }
        return protoValue
    }

    /**
     * `ProtoSerializable::fromProto`: the timestamp is attached to the value and every
     * nested entry; an unset oneof yields the default empty string variant exactly like
     * the C++ default-constructed `ValueVariant`.
     */
    fun fromProto(protoValue: ProtoValueWrapper, timestamp: Long): ValueWrapper =
        when (protoValue.valueCase()) {
            ProtoValueWrapper.ValueCase.STR_VALUE ->
                ValueWrapper(ValueVariant.StringValue(protoValue.strValue()), timestamp)

            ProtoValueWrapper.ValueCase.BOOL_VALUE ->
                ValueWrapper(ValueVariant.BooleanValue(protoValue.boolValue()), timestamp)

            ProtoValueWrapper.ValueCase.INT_VALUE ->
                ValueWrapper(ValueVariant.IntValue(protoValue.intValue()), timestamp)

            ProtoValueWrapper.ValueCase.DOUBLE_VALUE ->
                ValueWrapper(ValueVariant.DoubleValue(protoValue.doubleValue()), timestamp)

            ProtoValueWrapper.ValueCase.ARRAY_VALUE ->
                ValueWrapper(ValueVariant.ArrayValue(protoValue.arrayValue().values().map { fromProto(it, timestamp) }), timestamp)

            ProtoValueWrapper.ValueCase.MAP_VALUE ->
                ValueWrapper(
                    ValueVariant.MapValue(
                        protoValue.mapValue().items().associate { it.key() to fromProto(it.value(), timestamp) },
                    ),
                    timestamp,
                )

            ProtoValueWrapper.ValueCase.VALUE_NOT_SET ->
                ValueWrapper(ValueVariant.StringValue(""), timestamp)
        }
}
