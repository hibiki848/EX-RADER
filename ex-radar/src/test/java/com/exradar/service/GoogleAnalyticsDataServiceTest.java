package com.exradar.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * GoogleAnalyticsDataServiceの単体テスト。実際のGA4 Data APIへは通信しないため、
 * ここではプロパティIDの正規化(normalizePropertyId)と、認証情報未設定時の
 * isConfigured()の挙動のみを検証する(HTTPリクエスト自体のモックは行わない)。
 */
class GoogleAnalyticsDataServiceTest {

  @Test
  void normalizePropertyIdStripsPropertiesPrefix() {
    // GA4管理画面からそのままコピーした場合等、"properties/"接頭辞付きで設定されるミスを吸収する。
    // 接頭辞付きのままだとURLが"properties/properties/123"のように二重になり404になる。
    assertThat(GoogleAnalyticsDataService.normalizePropertyId("properties/123456789"))
        .isEqualTo("123456789");
  }

  @Test
  void normalizePropertyIdLeavesPlainIdUnchanged() {
    assertThat(GoogleAnalyticsDataService.normalizePropertyId("123456789")).isEqualTo("123456789");
  }

  @Test
  void normalizePropertyIdTrimsWhitespace() {
    assertThat(GoogleAnalyticsDataService.normalizePropertyId("  123456789  ")).isEqualTo("123456789");
  }

  @Test
  void normalizePropertyIdHandlesNullAsEmpty() {
    assertThat(GoogleAnalyticsDataService.normalizePropertyId(null)).isEmpty();
  }

  @Test
  void isConfiguredIsFalseWhenPropertyIdAndCredentialsAreBothUnset() {
    var service = new GoogleAnalyticsDataService("", "", RestClient.builder());
    assertThat(service.isConfigured()).isFalse();
  }

  @Test
  void isConfiguredIsFalseWhenOnlyPropertyIdIsSet() {
    // credentialsが未設定(空文字)の場合、propertyIdだけ設定されていても未設定として扱う。
    var service = new GoogleAnalyticsDataService("123456789", "", RestClient.builder());
    assertThat(service.isConfigured()).isFalse();
  }

  @Test
  void isConfiguredIsFalseWhenServiceAccountKeyIsInvalidJson() {
    // 秘密鍵の値そのものを使わずに検証できるよう、意図的に壊れたJSONを渡す。
    // GoogleCredentials.fromStream()が例外を投げ、credentialsはnullのままになるはず。
    var service =
        new GoogleAnalyticsDataService("123456789", "{not-valid-json", RestClient.builder());
    assertThat(service.isConfigured()).isFalse();
  }
}
