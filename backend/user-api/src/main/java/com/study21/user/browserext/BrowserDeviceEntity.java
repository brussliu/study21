package com.study21.user.browserext;

import java.sql.Timestamp;

/**
 * NET_ブラウザ端末情報 の 1 行（拡張が登録したブラウザ 1 つ）。
 */
public class BrowserDeviceEntity {

    private Long registrationId;
    private Long accountId;
    private String deviceId;
    private String deviceName;
    private String osType;
    private String browserType;
    private String browserVersion;
    private String extensionVersion;
    private String profileId;
    private String status;
    private Timestamp lastStartedAt;
    private Timestamp lastSentAt;
    private Timestamp lastHeartbeatAt;
    private int version;
    private Timestamp createdAt;

    public Long getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(Long registrationId) {
        this.registrationId = registrationId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getOsType() {
        return osType;
    }

    public void setOsType(String osType) {
        this.osType = osType;
    }

    public String getBrowserType() {
        return browserType;
    }

    public void setBrowserType(String browserType) {
        this.browserType = browserType;
    }

    public String getBrowserVersion() {
        return browserVersion;
    }

    public void setBrowserVersion(String browserVersion) {
        this.browserVersion = browserVersion;
    }

    public String getExtensionVersion() {
        return extensionVersion;
    }

    public void setExtensionVersion(String extensionVersion) {
        this.extensionVersion = extensionVersion;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getLastStartedAt() {
        return lastStartedAt;
    }

    public void setLastStartedAt(Timestamp lastStartedAt) {
        this.lastStartedAt = lastStartedAt;
    }

    public Timestamp getLastSentAt() {
        return lastSentAt;
    }

    public void setLastSentAt(Timestamp lastSentAt) {
        this.lastSentAt = lastSentAt;
    }

    public Timestamp getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(Timestamp lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
