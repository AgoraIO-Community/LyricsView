package io.agora.karaoke_view_ex.internal.lyric.parse;

import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import io.agora.karaoke_view_ex.constants.Constants;
import io.agora.karaoke_view_ex.internal.constants.LyricType;
import io.agora.karaoke_view_ex.internal.model.LyricsLineModel;
import io.agora.karaoke_view_ex.internal.utils.LogUtils;
import io.agora.karaoke_view_ex.model.LyricModel;

/**
 * XML format lyrics parser
 * Used to parse lyrics from XML files or byte arrays
 */
class LyricsParserXml {
    private static final String TAG = Constants.TAG + "-LyricsParserXml";

    /**
     * Song data structure containing general information and MIDI data
     */
    public static class Song {
        /**
         * General song information
         */
        public SongGeneral general;
        /**
         * MIDI lyrics data
         */
        public SongMidi midi;
    }

    /**
     * General song information
     */
    public static class SongGeneral {
        /**
         * Song name
         */
        public String name;
        /**
         * Singer name
         */
        public String singer;
        /**
         * Song type
         */
        public int type;
        /**
         * Mode type
         */
        public String modeType;
    }

    /**
     * MIDI lyrics data
     */
    public static class SongMidi {
        /**
         * List of paragraphs in the song
         */
        public List<Paragraph> paragraphs;
    }

    /**
     * Paragraph containing multiple lines of lyrics
     */
    public static class Paragraph {
        /**
         * List of lyric lines in the paragraph
         */
        public List<LyricsLineModel> lines;
    }

    /**
     * Parse lyrics from an XML file
     *
     * @param xmlFile The XML file containing lyrics data
     * @return LyricModel object containing parsed lyrics, or null if parsing fails
     */
    public static LyricModel parseXml(File xmlFile) {
        if (xmlFile == null || !xmlFile.exists()) {
            LogUtils.e("unexpected lyrics file " + xmlFile);
            return null;
        }

        try (FileInputStream in = new FileInputStream(xmlFile)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            parser.nextTag();
            return parseLrcByXmlParse(parser);
        } catch (Exception e) {
            LogUtils.e(Log.getStackTraceString(e));
        }

        return null;
    }

    /**
     * Parse lyrics from XML file content as byte array
     *
     * @param xmlFileData Byte array containing XML file data
     * @return LyricModel object containing parsed lyrics, or null if parsing fails
     */
    public static LyricModel parseXml(byte[] xmlFileData) {
        if (xmlFileData == null || xmlFileData.length == 0) {
            LogUtils.e("lyrics file data is empty");
            return null;
        }

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(new StringReader(new String(xmlFileData, Constants.UTF_8)));
            parser.nextTag();

            return parseLrcByXmlParse(parser);
        } catch (Exception e) {
            LogUtils.e(Log.getStackTraceString(e));
        }

        return null;
    }

    /**
     * Parse lyrics from an XML parser
     *
     * @param parser XmlPullParser with XML content
     * @return LyricModel object containing parsed lyrics, or null if parsing fails
     */
    private static LyricModel parseLrcByXmlParse(XmlPullParser parser) {
        try {
            Song song = readXml(parser);
            if (song.midi == null || song.midi.paragraphs == null) {
                LogUtils.e(" no midi or paragraph");
                return null;
            }

            LyricModel lyrics = new LyricModel(LyricType.XML);
            List<LyricsLineModel> lines = new ArrayList<>();
            for (Paragraph paragraph : song.midi.paragraphs) {
                lines.addAll(paragraph.lines);
            }
            if (!lines.isEmpty()) {
                lyrics.duration = lines.get(lines.size() - 1).getEndTime();
            }
            // Always the first line of lyrics
            lyrics.preludeEndPosition = lines.get(0).getStartTime();
            lyrics.name = song.general.name;
            lyrics.singer = song.general.singer;
            lyrics.lines = lines;

            if (lyrics.duration <= 0 || lyrics.preludeEndPosition < 0) {
                LogUtils.e(" no sentence or tone or unexpected timestamp of tone:" + lyrics.preludeEndPosition + " " + lyrics.duration);
                // Invalid lyrics
                return null;
            }
            if (!lyrics.lines.isEmpty()) {
                lyrics.hasPitch = lyrics.lines.get(0).tones.get(0).pitch != 0;
            }
            return lyrics;
        } catch (Exception e) {
            LogUtils.e(Log.getStackTraceString(e));
        }
        return null;
    }

    /**
     * Read XML content and parse into Song object
     *
     * @param parser XmlPullParser with XML content
     * @return Song object containing parsed data
     * @throws XmlPullParserException If XML parsing fails
     * @throws IOException            If reading fails
     */
    private static Song readXml(XmlPullParser parser) throws XmlPullParserException, IOException {
        Song song = new Song();
//        parser.require(XmlPullParser.START_TAG, null, "song");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            // Starts by looking for the entry tag
            if (name.equals("general")) {
                song.general = new SongGeneral();
                readGeneral(parser, song.general);
            } else if (name.equals("midi_lrc")) {
                song.midi = new SongMidi();
                readMidiLrc(parser, song.midi);
            } else {
                skip(parser);
            }
        }
        return song;
    }

    /**
     * Read general song information
     *
     * @param parser  XmlPullParser with XML content
     * @param general SongGeneral object to populate
     * @throws XmlPullParserException If XML parsing fails
     * @throws IOException            If reading fails
     */
    private static void readGeneral(XmlPullParser parser, SongGeneral general) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "general");

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals("name")) {
                general.name = readText(parser).trim(); // Trim blank chars starts or ends with the title
            } else if (name.equals("singer")) {
                general.singer = readText(parser).trim(); // Trim blank chars starts or ends with the artist
//            } else if (name.equals("type")) {
//                general.type = Integer.parseInt(readText(parser));
            } else if (name.equals("mode_type")) {
                general.modeType = readText(parser);
            } else {
                skip(parser);
            }
        }
    }

    /**
     * Read MIDI lyrics data
     *
     * @param parser XmlPullParser with XML content
     * @param midi   SongMidi object to populate
     * @throws XmlPullParserException If XML parsing fails
     * @throws IOException            If reading fails
     */
    private static void readMidiLrc(XmlPullParser parser, SongMidi midi) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "midi_lrc");

        midi.paragraphs = new ArrayList<>();
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }

            String name = parser.getName();
            if (name.equals("paragraph")) {
                Paragraph mParagraph = new Paragraph();
                midi.paragraphs.add(mParagraph);
                readParagraph(parser, mParagraph);
            } else {
                skip(parser);
            }
        }
    }

    /**
     * Read paragraph data
     *
     * @param parser    XmlPullParser with XML content
     * @param paragraph Paragraph object to populate
     * @throws XmlPullParserException If XML parsing fails
     * @throws IOException            If reading fails
     */
    private static void readParagraph(XmlPullParser parser, Paragraph paragraph) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "paragraph");

        paragraph.lines = new ArrayList<>();
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }

            String name = parser.getName();
            if (name.equals("sentence")) {
                List<LyricsLineModel> lines = new ArrayList<>();
                readLines(parser, lines);
                for (LyricsLineModel line : lines) {
                    line.duration = line.getEndTime() - line.getStartTime();
                }
                paragraph.lines.addAll(lines);
            } else {
                skip(parser);
            }
        }
    }

    /**
     * Read lyrics lines data
     *
     * @param parser XmlPullParser with XML content
     * @param list   List to populate with LyricsLineModel objects
     * @throws XmlPullParserException If XML parsing fails
     * @throws IOException            If reading fails
     */
    private static void readLines(XmlPullParser parser, List<LyricsLineModel> list) throws XmlPullParserException, IOException {
        LyricsLineModel line = new LyricsLineModel(new ArrayList<>());
        list.add(line);
        parser.require(XmlPullParser.START_TAG, null, "sentence");
        String m = parser.getAttributeValue(null, "mode");
        if (m != null) {
            if (m.equals("man")) {
                // Man;
            } else {
                // Woman;
            }
        }

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }

            String name = parser.getName();
            if (name.equals("tone")) {
                LyricsLineModel.Tone tone = new LyricsLineModel.Tone();
                line.tones.add(tone);
                readTone(parser, tone);
            } else if (name.equals("monolog")) {
                LyricsLineModel.Monolog monolog = new LyricsLineModel.Monolog();
                line.tones.add(monolog);
                readMonolog(parser, monolog);
            } else {
                skip(parser);
            }
        }
    }

    /**
     * Check if the song is in English based on language attribute
     *
     * @param parser XmlPullParser with XML content
     * @return true if the song is in English, false otherwise
     */
    private static boolean isEnglishSong(XmlPullParser parser) {
        String lang = parser.getAttributeValue(null, "lang");
        return lang != null && !"1".equals(lang);
    }

    /**
     * Read tone data
     *
     * @param parser XmlPullParser with XML content
     * @param tone   Tone object to populate
     * @return true if the tone is in English, false otherwise
     * @throws XmlPullParserException If XML parsing fails
     * @throws IOException            If reading fails
     */
    private static boolean readTone(XmlPullParser parser, LyricsLineModel.Tone tone) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "tone");

        boolean isEnglish = false;

        // read tone attributes
        tone.begin = (long) (Float.parseFloat(parser.getAttributeValue(null, "begin")) * 1000L);
        tone.end = (long) (Float.parseFloat(parser.getAttributeValue(null, "end")) * 1000L);
        String t = parser.getAttributeValue(null, "pitch");
        int pitch = 0;
        if (t != null) {
            try {
                pitch = Integer.parseInt(t.trim());
            } catch (NumberFormatException e) {
                try {
                    double doubleValue = Double.parseDouble(t.trim());
                    pitch = (int) doubleValue;
                } catch (NumberFormatException ex) {
                    LogUtils.e("readTone: " + t);
                }
            }
        }
        tone.pitch = pitch;

        String pronounce = parser.getAttributeValue(null, "pronounce");
        String lang = parser.getAttributeValue(null, "lang");

        if (lang == null || "1".equals(lang)) {
            tone.lang = LyricsLineModel.Lang.Chinese;
        } else {
            tone.lang = LyricsLineModel.Lang.English;
            isEnglish = true;
        }

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals("word")) {
                tone.word = readText(parser);
                // protect in case lang field missed
                if (lang == null) {
                    isEnglish = checkLang(tone.word);
                    if (isEnglish) {
                        tone.lang = LyricsLineModel.Lang.English;
                    }
                }
            } else {
                skip(parser);
            }
        }
        return isEnglish;
    }

    /**
     * Read monolog data
     *
     * @param parser  XmlPullParser with XML content
     * @param monolog Monolog object to populate
     * @return true if the monolog is in English, false otherwise
     * @throws XmlPullParserException If XML parsing fails
     * @throws IOException            If reading fails
     */
    private static boolean readMonolog(XmlPullParser parser, LyricsLineModel.Monolog monolog) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "monolog");

        boolean isEnglish = false;

        // read tone attributes
        monolog.begin = (long) (Float.parseFloat(parser.getAttributeValue(null, "begin")) * 1000L);
        monolog.end = (long) (Float.parseFloat(parser.getAttributeValue(null, "end")) * 1000L);
        String t = parser.getAttributeValue(null, "pitch");
        int pitch = 0;
        if (t != null) {
            try {
                pitch = Integer.parseInt(t.trim());
            } catch (NumberFormatException e) {
            }
        }
        monolog.pitch = pitch;

        String pronounce = parser.getAttributeValue(null, "pronounce");
        String lang = parser.getAttributeValue(null, "lang");

        if (lang == null || "1".equals(lang)) {
            monolog.lang = LyricsLineModel.Lang.Chinese;
        } else {
            monolog.lang = LyricsLineModel.Lang.English;
            isEnglish = true;
        }

        monolog.word = readText(parser);
        return isEnglish;
    }

    /**
     * Check if a word is in Chinese or not
     *
     * @param word The word to check
     * @return true if the word is not in Chinese, false otherwise
     */
    private static boolean checkLang(String word) {
        int n;
        for (int i = 0; i < word.length(); i++) {
            n = word.charAt(i);
            if (!(19968 <= n && n < 40869)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Read text content from XML
     *
     * @param parser XmlPullParser with XML content
     * @return The text content
     * @throws IOException            If reading fails
     * @throws XmlPullParserException If XML parsing fails
     */
    private static String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }

    /**
     * Skip the current XML tag and its children
     *
     * @param parser XmlPullParser with XML content
     * @throws XmlPullParserException If XML parsing fails
     * @throws IOException            If reading fails
     */
    private static void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }
}
