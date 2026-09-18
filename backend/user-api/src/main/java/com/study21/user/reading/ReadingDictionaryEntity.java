package com.study21.user.reading;

import java.sql.Timestamp;

/**
 * RED_語彙辞書情報 の 1 行（語彙・読みの引き当て結果のキャッシュ）。
 *
 * <p>外部の無料辞書・翻訳 API に問い合わせた結果を貯めるだけで、利用者のデータではない。
 * 取得できなかった項目は null（行があっても全部 null なら「引けたが情報なし」）。</p>
 */
public class ReadingDictionaryEntity {

    private Long dictionaryId;
    /** 本の言語（中国語 / 英語 / 日本語）。引き当ての向きを決める */
    private String language;
    /** 正規化した見出し語（前後空白除去・英字は小文字・連続空白は 1 つ） */
    private String headword;
    /** 日本語での意味（英語の本） */
    private String japanese;
    /** 中国語での意味（英語の本） */
    private String chinese;
    /** ピンイン（中国語の本） */
    private String pinyin;
    /** 解説（中国語の本） */
    private String explanation;
    /** EXCELAPI / YOUDAO / GOOGLE / AI / MANUAL（複数なら '+' で連結） */
    private String source;
    private Timestamp fetchedAt;
    private Timestamp updatedAt;

    public Long getDictionaryId() { return dictionaryId; }
    public void setDictionaryId(Long dictionaryId) { this.dictionaryId = dictionaryId; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getHeadword() { return headword; }
    public void setHeadword(String headword) { this.headword = headword; }
    public String getJapanese() { return japanese; }
    public void setJapanese(String japanese) { this.japanese = japanese; }
    public String getChinese() { return chinese; }
    public void setChinese(String chinese) { this.chinese = chinese; }
    public String getPinyin() { return pinyin; }
    public void setPinyin(String pinyin) { this.pinyin = pinyin; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Timestamp getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Timestamp fetchedAt) { this.fetchedAt = fetchedAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
