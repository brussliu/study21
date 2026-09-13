package com.study21.admin.proxy;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * NET_サイト情報（サイト管理）の Mapper。
 *
 * <p>プロキシの許可判定はアプリ側で行う（判定方法コードの 4 種は SQL で表現しにくい）ため、
 * 承認済みかつ有効なサイトを全件読み込む。実績 175 件程度なので全件読み込みで十分
 * （database/サイト管理/NET_サイト管理・端末コントロール設計.md）。部分索引
 * idx_net_site_approved_active が効く。</p>
 */
@Mapper
public interface ProxySiteMapper {

    /**
     * 承認済み（承認ステータス='APPROVED'）かつ有効（状態='1'）のサイトを返す。
     * 判定に必要な サイトURL・ホスト名・判定方法コード・区分コード だけを読む
     * （2.1 の判定は サイト名称 を使わない）。
     */
    List<ApprovedSite> findApprovedActiveSites();

    /**
     * 許可判定に使うサイト 1 行分。
     *
     * <p>MyBatis の resultMap から生成するため引数なしコンストラクタを持つ。
     * テストで組み立てやすいように全項目のコンストラクタも用意する。</p>
     */
    class ApprovedSite {

        private String siteUrl;
        private String hostName;
        private String judgeMethodCode;
        private String kindCode;

        public ApprovedSite() {
        }

        public ApprovedSite(String siteUrl, String hostName, String judgeMethodCode, String kindCode) {
            this.siteUrl = siteUrl;
            this.hostName = hostName;
            this.judgeMethodCode = judgeMethodCode;
            this.kindCode = kindCode;
        }

        /** サイトURL（例: youtube.com、https://www.youtube.com/）。 */
        public String getSiteUrl() {
            return siteUrl;
        }

        public void setSiteUrl(String siteUrl) {
            this.siteUrl = siteUrl;
        }

        /** ホスト名（判定用の正規化列）。 */
        public String getHostName() {
            return hostName;
        }

        public void setHostName(String hostName) {
            this.hostName = hostName;
        }

        /** 判定方法コード（PREFIX / SUFFIX / CONTAINS / EXACT）。 */
        public String getJudgeMethodCode() {
            return judgeMethodCode;
        }

        public void setJudgeMethodCode(String judgeMethodCode) {
            this.judgeMethodCode = judgeMethodCode;
        }

        /** 区分コード（STUDY / NORMAL / BREAK / GAME）。 */
        public String getKindCode() {
            return kindCode;
        }

        public void setKindCode(String kindCode) {
            this.kindCode = kindCode;
        }
    }
}
