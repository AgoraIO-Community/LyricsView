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

class LyricsParserXml {
    private static final String TAG = Constants.TAG + "-LyricsParserXml";

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
     * 从文件内容解析歌词
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
                for (LyricsLineModel line : lines) {
                    line.duration = line.getEndTime() - line.getStartTime();
                }
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
