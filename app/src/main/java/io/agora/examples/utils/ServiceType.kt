package io.agora.examples.utils

enum class ServiceType(val type: Int) {
    MCC(0),
    MCC_EX(1);

    companion object {
        // 根据 type 的 Int 类型值获取枚举值
        fun fromType(type: Int): ServiceType? {
            return enumValues<ServiceType>().firstOrNull { it.type == type }
        }
    }
}