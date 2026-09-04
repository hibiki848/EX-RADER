package com.exradar.controller;

import com.exradar.exception.PremiumOperationException;
import com.exradar.repository.UserRepository;
import com.exradar.service.PremiumService;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * プレミアム加入・特典利用の入口。実際のプラン有効化・特典USED確定はここでは一切行わず、
 * 必ずStripeWebhookController経由(PremiumServiceのonXxxメソッド)でのみ確定する
 * (success_urlへの復帰はユーザーへの一時的な案内表示のみに使う)。
 */
@Controller
public class PremiumController {
  private final PremiumService premium;
  private final UserRepository users;

  public PremiumController(PremiumService premium, UserRepository users) {
    this.premium = premium;
    this.users = users;
  }

  /**
   * 「この特典を使う」共通入口。既にStripe Subscriptionを持つユーザーはCheckoutを経由せず
   * 直接Subscriptionへ適用し、そうでなければ新規加入Checkoutへ進む(要件9・11の分岐)。
   */
  @PostMapping("/mypage/benefits/{id}/use")
  String use(@PathVariable Long id, Principal principal, HttpServletRequest request, RedirectAttributes redirect) {
    var user = users.findByEmailIgnoreCase(principal.getName()).orElseThrow();
    try {
      if (user.getStripeSubscriptionId() != null) {
        premium.applyBenefitToExistingSubscription(principal.getName(), id);
        redirect.addFlashAttribute("success", "特典を適用しました。次回のご請求から反映されます。");
        return "redirect:/mypage";
      }
      var successUrl = absoluteUrl(request, "/mypage/premium/checkout/success", null);
      var cancelUrl = absoluteUrl(request, "/mypage/premium/checkout/cancel", id);
      var start = premium.startNewSubscriptionCheckout(principal.getName(), id, successUrl, cancelUrl);
      return "redirect:" + start.redirectUrl();
    } catch (PremiumOperationException e) {
      redirect.addFlashAttribute("premiumError", e.getMessage());
      return "redirect:/mypage";
    }
  }

  /** 特典を使わず、通常のプレミアム加入だけを行う入口。 */
  @PostMapping("/mypage/premium/checkout")
  String checkout(Principal principal, HttpServletRequest request, RedirectAttributes redirect) {
    try {
      var successUrl = absoluteUrl(request, "/mypage/premium/checkout/success", null);
      var cancelUrl = absoluteUrl(request, "/mypage/premium/checkout/cancel", null);
      var start = premium.startNewSubscriptionCheckout(principal.getName(), null, successUrl, cancelUrl);
      return "redirect:" + start.redirectUrl();
    } catch (PremiumOperationException e) {
      redirect.addFlashAttribute("premiumError", e.getMessage());
      return "redirect:/mypage";
    }
  }

  /**
   * Checkout成功直後の復帰先。ここでの表示はあくまで「まもなく反映されます」という案内であり、
   * プレミアム有効化・特典USED確定はStripe Webhookが届いた時点で別途確定する
   * (このページの表示だけを理由に確定させない)。
   */
  @GetMapping("/mypage/premium/checkout/success")
  String checkoutSuccess(RedirectAttributes redirect) {
    redirect.addFlashAttribute("success", "お手続きありがとうございます。決済結果の反映まで少しお時間をいただく場合があります。");
    return "redirect:/mypage";
  }

  /**
   * cancel_urlへの復帰。ブラウザがここへ戻ってきたことだけを理由に特典をAVAILABLEへ戻さない
   * (Stripe Checkout Session自体が失効したとは限らないため)。PremiumService.cancelCheckoutが
   * サーバー側からStripe Checkout Sessionのexpireを試み、成功した場合のみ解放する。
   */
  @GetMapping("/mypage/premium/checkout/cancel")
  String checkoutCancel(
      @RequestParam(required = false) Long benefitId, Principal principal, RedirectAttributes redirect) {
    if (benefitId != null) premium.cancelCheckout(principal.getName(), benefitId);
    redirect.addFlashAttribute("success", "手続きをキャンセルしました。特典は引き続き保有されています。");
    return "redirect:/mypage";
  }

  private String absoluteUrl(HttpServletRequest request, String path, Long benefitId) {
    var builder = ServletUriComponentsBuilder.fromContextPath(request).path(path);
    if (benefitId != null) builder.queryParam("benefitId", benefitId);
    return builder.build().toUriString();
  }
}
