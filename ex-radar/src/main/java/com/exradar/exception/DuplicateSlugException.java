package com.exradar.exception;

public class DuplicateSlugException extends RuntimeException {
  public DuplicateSlugException() {
    super("このURL(スラッグ)は既に使用されています");
  }
}
