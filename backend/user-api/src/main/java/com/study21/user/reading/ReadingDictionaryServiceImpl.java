package com.study21.user.reading;

import com.study21.common.core.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 語彙・読みの引き当ての実装（2.0 の `api/word/translateByAi` 相当）。
 *
 * <p>流れ:</p>
 * <ol>
 *   <li>語を正規化する（前後の空白＝全角空白も落とす・連続空白は 1 つ・英語は小文字）</li>
 *   <li>`RED_語彙辞書情報` を先に見る。**行があればそれを返す（外部へ行かない）**</li>
 *   <li>無ければ外部の辞書・翻訳 API を引く（英語＝ExcelAPI と有道 / 中国語＝有道）</li>
 *   <li>取れた項目があれば upsert して返す。**何も取れなければ行を作らない**（次回また試せる）</li>
 * </ol>
 *
 * <p>外部が落ちていても例外にしない（200 で null と message を返す）。</p>
 */
@Service
public class ReadingDictionaryServiceImpl implements ReadingDictionaryService {

    /** 提供元コード（DDL の 取得元コード と同じ語彙）。 */
    static final String SOURCE_EXCELAPI = "EXCELAPI";
    static final String SOURCE_YOUDAO = "YOUDAO";
    /** 引き当てを使わない言語（日本語の本） */
    private static final String LANGUAGE_JAPANESE = "日本語";
    private static final String LANGUAGE_ENGLISH = "英語";
    private static final List<String> ALLOWED_LANGUAGES = ReadingModels.LANGUAGES;

    private final ReadingDictionaryMapper dictionaryMapper;
    private final ReadingDictionaryClient client;

    public ReadingDictionaryServiceImpl(ReadingDictionaryMapper dictionaryMapper,
                                        ReadingDictionaryClient client) {
        this.dictionaryMapper = dictionaryMapper;
        this.client = client;
    }

    @Override
    @Transactional
    public ReadingModels.LookupResult lookup(ReadingModels.LookupRequest request) {
        String language = requireLanguage(request == null ? null : request.language());
        String headword = normalize(request == null ? null : request.text(), language);
        if (headword == null) {
            throw new ValidationException("語を入力してください。");
        }

        // 日本語の本では引き当てを使わない（画面も出さない）
        if (LANGUAGE_JAPANESE.equals(language)) {
            return new ReadingModels.LookupResult(headword, language, null, null, null, null, null, false,
                    "日本語の本では語彙の引き当ては使いません。");
        }

        // 1. キャッシュ（同じ語を何度も外部へ引きに行かない）
        ReadingDictionaryEntity cached = dictionaryMapper.find(language, headword);
        if (cached != null) {
            return toResult(cached, true, "辞書のキャッシュから返しました。");
        }

        // 2. 外部へ問い合わせる
        ReadingDictionaryEntity fetched = LANGUAGE_ENGLISH.equals(language)
                ? fetchEnglish(headword)
                : fetchChinese(headword);
        if (fetched == null) {
            return new ReadingModels.LookupResult(headword, language, null, null, null, null, null, false,
                    "辞書から意味を取得できませんでした。");
        }

        // 3. 取れた項目だけを貯める（取れなかったときは行を作らない）
        dictionaryMapper.upsert(fetched);
        return toResult(fetched, false, "辞書から意味を取得しました。");
    }

    // ---------------------------------------------------------------- 英語の本

    /** 日本語訳（ExcelAPI）と中国語訳（有道）。両方取れなければ null（行を作らない）。 */
    private ReadingDictionaryEntity fetchEnglish(String word) {
        List<String> sources = new ArrayList<>();
        String japanese = ReadingDictionaryParsers.excelApiJapanese(client.englishToJapanese(word));
        if (japanese != null) {
            sources.add(SOURCE_EXCELAPI);
        }
        String chinese = ReadingDictionaryParsers.youdaoSuggestChinese(client.englishToChinese(word));
        if (chinese != null) {
            sources.add(SOURCE_YOUDAO);
        }
        if (japanese == null && chinese == null) {
            return null;
        }
        ReadingDictionaryEntity entity = new ReadingDictionaryEntity();
        entity.setLanguage(LANGUAGE_ENGLISH);
        entity.setHeadword(word);
        entity.setJapanese(japanese);
        entity.setChinese(chinese);
        entity.setSource(String.join("+", sources));
        return entity;
    }

    // -------------------------------------------------------------- 中国語の本

    /** 拼音と解説（有道）。どちらも取れなければ null（行を作らない）。 */
    private ReadingDictionaryEntity fetchChinese(String word) {
        ReadingDictionaryParsers.ChineseEntry entry =
                ReadingDictionaryParsers.youdaoJsonApi(client.chineseEntry(word));
        if (entry == null) {
            return null;
        }
        ReadingDictionaryEntity entity = new ReadingDictionaryEntity();
        entity.setLanguage("中国語");
        entity.setHeadword(word);
        entity.setPinyin(entry.pinyin());
        entity.setExplanation(entry.explanation());
        // 中国語の語にとっての「中国語の説明」でもあるので、画面の代替表示に使えるよう入れておく
        entity.setChinese(entry.explanation());
        entity.setSource(SOURCE_YOUDAO);
        return entity;
    }

    // -------------------------------------------------------------------- 内部

    private static String requireLanguage(String language) {
        String value = language == null ? null : language.trim();
        if (value == null || value.isEmpty()) {
            throw new ValidationException("言語を指定してください。");
        }
        if (!ALLOWED_LANGUAGES.contains(value)) {
            throw new ValidationException("言語は " + String.join(" / ", ALLOWED_LANGUAGES)
                    + " のいずれかを指定してください。");
        }
        return value;
    }

    /**
     * 見出し語の正規化（DB にはこの形で入れる）:
     * 全角空白を半角に・前後の空白を除去・連続する空白を 1 つに・英語は小文字に。
     */
    static String normalize(String text, String language) {
        if (text == null) {
            return null;
        }
        String value = text.replace('\u3000', ' ').replaceAll("\\s+", " ").trim();
        if (value.isEmpty()) {
            return null;
        }
        if (LANGUAGE_ENGLISH.equals(language)) {
            value = value.toLowerCase(Locale.ROOT);
        }
        return value.length() > 100 ? value.substring(0, 100) : value;
    }

    private static ReadingModels.LookupResult toResult(ReadingDictionaryEntity entity, boolean cached,
                                                       String message) {
        return new ReadingModels.LookupResult(entity.getHeadword(), entity.getLanguage(), entity.getJapanese(),
                entity.getChinese(), entity.getPinyin(), entity.getExplanation(), entity.getSource(), cached,
                message);
    }
}
