package com.exradar.exception;

/** Stripe呼び出しの失敗など、プレミアム加入・特典利用処理中に起きた回復不能なエラーをControllerへ伝えるための例外。 */
public class PremiumOperationException extends RuntimeException {
  public PremiumOperationException(String message, Throwable cause) {
    super(message, cause);
  }

  public PremiumOperationException(String message) {
    super(message);
  }
}
