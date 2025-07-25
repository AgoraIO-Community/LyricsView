package io.agora.karaoke_view_ex.internal.constants;

/**
 * Enumeration of supported lyrics file formats.
 * This enum defines the different types of lyric files that can be parsed and displayed.
 */
public enum LyricType {
    /**
     * XML format lyrics, typically containing detailed timing and pitch information
     */
    XML,

    /**
     * LRC format lyrics, a simple text-based format with timestamps
     */
    LRC,

    /**
     * KRC format lyrics, Karaoke-specific format with enhanced timing and pitch data
     */
    KRC;
}
