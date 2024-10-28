package io.agora.karaoke_view_ex.internal.constants;

public enum LyricType {
    XML(0),
    LRC(1),
    LRC_WITH_PITCHS(2),
    KRC(3);

    private final int value;

    LyricType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static LyricType fromValue(int value) {
        for (LyricType type : LyricType.values()) {
            if (type.value() == value) {
                return type;
            }
        }
        return XML;
    }
}
