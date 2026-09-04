package com.exradar.entity;

/**
 * Stripe Webhookイベントの処理状態。「受信済み(行が存在する)」と「正常処理済み」を区別するために持つ。
 * PROCESSED以外(RECEIVED/PROCESSING/FAILED)は、同一イベントIDの再送を受けたときに
 * 再処理を許可する対象とする(受信済み≠正常処理済み)。
 */
public enum StripeWebhookEventStatus {
  /** 受信直後、まだ業務処理を開始していない。 */
  RECEIVED,
  /** 業務処理を実行中。 */
  PROCESSING,
  /** 業務処理が正常に完了した(再送されても再処理しない)。 */
  PROCESSED,
  /** 業務処理が例外で失敗した(再送時に再処理を許可する)。 */
  FAILED
}
