package com.exradar.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 例外処理の共通化。ユーザー向けにはJava例外名・SQLエラー・スタックトレースなどの内部情報を
 * 一切表示せず、既存デザインに合わせた日本語のエラー画面を返す。
 * 開発者向けには、原因調査に必要な情報(発生日時・URL・メソッド・userId・スタックトレース)を
 * サーバーログにのみ出力する(パスワード・トークン等の機密情報は出力しない)。
 */
@ControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ResourceNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  String notFound(ResourceNotFoundException e, HttpServletRequest request, Principal principal, Model model) {
    log.warn(
        "Resource not found: method={} path={} userId={} message={}",
        request.getMethod(),
        request.getRequestURI(),
        userId(principal),
        e.getMessage());
    model.addAttribute("message", e.getMessage());
    return "error/404";
  }

  @ExceptionHandler(ForbiddenOperationException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  String forbidden(ForbiddenOperationException e, HttpServletRequest request, Principal principal, Model model) {
    log.warn(
        "Forbidden operation: method={} path={} userId={} message={}",
        request.getMethod(),
        request.getRequestURI(),
        userId(principal),
        e.getMessage());
    model.addAttribute("message", e.getMessage());
    return "error/403";
  }

  @ExceptionHandler(AccessDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  String accessDenied(AccessDeniedException e, HttpServletRequest request, Principal principal, Model model) {
    log.warn(
        "Access denied: method={} path={} userId={}",
        request.getMethod(),
        request.getRequestURI(),
        userId(principal));
    model.addAttribute("message", "この操作を行う権限がありません。");
    return "error/403";
  }

  /**
   * URLパスの型不一致(例: /experiences/abc/edit のようにIDが数値でない)や
   * 必須リクエストパラメータの欠落は、サーバー内部の異常ではなくリクエスト側の不備のため
   * 400として扱う。下の汎用ハンドラ(Exception.class)より先に、より具体的な型として
   * マッチするため、誤って500として扱われることはない。
   */
  @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  String badRequest(Exception e, HttpServletRequest request, Principal principal, Model model) {
    log.warn(
        "Bad request: method={} path={} userId={} message={}",
        request.getMethod(),
        request.getRequestURI(),
        userId(principal),
        e.getMessage());
    model.addAttribute("message", "リクエストの内容に誤りがあります。");
    model.addAttribute("status", 400);
    return "error";
  }

  /**
   * URLに対応するハンドラ・静的ファイルが存在しない場合にSpring自身が投げる例外。
   * 元々フレームワークが404として正しく解決してくれていたものなので、下の汎用ハンドラ
   * (Exception.class)に奪われて誤って500として扱われないよう、具体的な型として先に受け止める。
   */
  @ExceptionHandler(NoResourceFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  String noResourceFound(NoResourceFoundException e, HttpServletRequest request, Principal principal, Model model) {
    log.warn(
        "No resource found: method={} path={} userId={}",
        request.getMethod(),
        request.getRequestURI(),
        userId(principal));
    model.addAttribute("message", "指定されたページが見つかりません。");
    return "error/404";
  }

  /**
   * 上記以外の想定外の例外はここで受け止める。従来はSpring Bootのデフォルトエラーページ
   * (Java例外名やスタックトレースが露出しうる)に流れていたため、ユーザーには日本語の
   * 汎用エラー画面のみを見せ、詳細はサーバーログにのみ記録する。
   * catchして握りつぶすのではなく、必ずログに残したうえでユーザーに分かる形で応答する。
   */
  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  String unexpectedError(Exception e, HttpServletRequest request, Principal principal, Model model) {
    log.error(
        "Unexpected error: method={} path={} userId={}",
        request.getMethod(),
        request.getRequestURI(),
        userId(principal),
        e);
    model.addAttribute("message", "処理中にエラーが発生しました。もう一度お試しください。");
    return "error/500";
  }

  private String userId(Principal principal) {
    return principal == null ? "anonymous" : principal.getName();
  }
}
