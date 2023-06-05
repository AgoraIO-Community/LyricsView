package io.agora.karaoke_view.v11.utils;

import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.agora.karaoke_view.v11.logging.LogManager;
import io.agora.karaoke_view.v11.model.LyricsLineModel;
import io.agora.karaoke_view.v11.model.LyricsModel;

/**
 * 加载 xml 歌词(逐字)
 *
 * @author chenhengfei(Aslanchen)
 * @date 2021/7/6
 */
class LyricsParserXml {
    private static final String TAG = "LyricsParserXml";

    public static class Song {
        public SongGeneral general;
        public SongMidi midi;
    }

    public static class SongGeneral {
        public String name;
        public String singer;
        public int type;
        public String mode_type;
    }

    public static class SongMidi {
        public List<Paragraph> paragraphs;
    }

    public static class Paragraph {
        public List<LyricsLineModel> lines;
    }

    /**
     * 从文件解析歌词
     */
    public static LyricsModel parseLrc(File lrcFile) {
        if (lrcFile == null || !lrcFile.exists()) {
            LogManager.instance().error(TAG, "unexpected lyrics file " + lrcFile);
            return null;
        }

        try (FileInputStream in = new FileInputStream(lrcFile)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            parser.nextTag();
            Song song = readLrc(parser);
            if (song.midi == null || song.midi.paragraphs == null) {
                LogManager.instance().error(TAG, "no midi or paragraph");
                return null;
            }

            LyricsModel lyrics = new LyricsModel(LyricsModel.Type.Xml);
            List<LyricsLineModel> lines = new ArrayList<>();
            for (Paragraph paragraph : song.midi.paragraphs) {
                lines.addAll(paragraph.lines);
            }
            if (lines.size() > 0) {
                lyrics.duration = lines.get(lines.size() - 1).getEndTime();
            }
            lyrics.startOfVerse = lines.get(0).getStartTime(); // Always the first line of lyrics
            lyrics.title = song.general.name;
            lyrics.artist = song.general.singer;
            lyrics.lines = lines;

            if (lyrics.duration <= 0 || lyrics.startOfVerse < 0) {
                LogManager.instance().error(TAG, "no sentence or tone or unexpected timestamp of tone: " + lyrics.startOfVerse + " " + lyrics.duration);
                return null; // Invalid lyrics
            }
            return lyrics;
        } catch (Exception e) {
            LogManager.instance().error(TAG, Log.getStackTraceString(e));
        }

        return null;
    }

    private static Song readLrc(XmlPullParser parser) throws XmlPullParserException, IOException {
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
                general.mode_type = readText(parser);
            } else {
                skip(parser);
            }
        }
    }

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
                paragraph.lines.addAll(lines);
            } else {
                skip(parser);
            }
        }
    }

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

    private static boolean isEnglishSong(XmlPullParser parser) {
        String lang = parser.getAttributeValue(null, "lang");
        return lang != null && !"1".equals(lang);
    }

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

    private static String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }

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
