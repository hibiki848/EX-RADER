package com.exradar.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * お問い合わせフォーム(/contact)の短時間大量送信を防ぐための、最小限のサーバー側レート制限。
 * 未ログインでも送信できる公開フォームのため、外部CAPTCHAサービスは導入せず、送信元IPアドレス
 * ごとに直近の送信時刻だけをメモリ上に保持する(DBへ永続化しない = プライバシーポリシー上の
 * IPアドレス保存には該当しない)。単一インスタンス構成の現状規模に対して十分な軽量実装であり、
 * アプリ再起動で状態がリセットされても実害はない(スパム対策としては許容範囲)。
 */
@Component
public class ContactRateLimiter {
  private static final int MAX_ATTEMPTS = 5;
  private static final Duration WINDOW = Duration.ofMinutes(10);

  private final ConcurrentHashMap<String, Deque<Instant>> attemptsByIp = new ConcurrentHashMap<>();

  /** 送信を許可してよければtrueを返し、この呼び出し自体も1回分の送信として記録する。 */
  public synchronized boolean allow(String ip) {
    if (ip == null || ip.isBlank()) ip = "unknown";
    var now = Instant.now();
    var attempts = attemptsByIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
    while (!attempts.isEmpty() && Duration.between(attempts.peekFirst(), now).compareTo(WINDOW) > 0) {
      attempts.pollFirst();
    }
    if (attempts.size() >= MAX_ATTEMPTS) return false;
    attempts.addLast(now);
    return true;
  }
}
