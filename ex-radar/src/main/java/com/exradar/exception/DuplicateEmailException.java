package com.exradar.exception;

public class DuplicateEmailException extends RuntimeException {
  public DuplicateEmailException() {
    super("このメールアドレスは既に登録されています");
  }
}
