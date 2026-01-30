package com.backend.service.ai.chat;

import com.backend.domain.order.OrderStatus;
import com.backend.dto.order.response.OrderSummaryResponse;
import com.backend.dto.response.AIChatResponse;
import com.backend.dto.response.IntentClassificationResult;
import com.backend.service.member.CurrentMemberService;
import com.backend.service.order.OrderService;
import com.backend.util.AIChatUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;

/**
 * DELIVERY_QUERY 의도 처리 서비스 구현
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryChatServiceImpl implements DeliveryChatService {

    private final OrderService orderService;
    private final CurrentMemberService currentMemberService;
    private final GeneralChatService generalChatService;

    @Override
    public AIChatResponse handleDelivery(IntentClassificationResult classification) {
        String action = classification.getAction();
        
        if (action == null) {
            log.warn("DELIVERY_QUERY intent에서 action이 null입니다. 일반 채팅으로 처리");
            return generalChatService.handleGeneralChat(classification);
        }

        return switch (action.toUpperCase()) {
            case "QUERY" -> handleDeliveryQuery(classification);
            case "RECOMMEND" -> handleDeliveryRecommend(classification);
            case "MODIFY" -> handleDeliveryModify(classification);
            default -> {
                log.info("DELIVERY_QUERY intent에서 지원하지 않는 action: {}, 일반 채팅으로 처리", action);
                yield generalChatService.handleGeneralChat(classification);
            }
        };
    }

    /**
     * DELIVERY의 QUERY 액션 처리 (소분류: action)
     * 
     * - entities에서 date, product_name, delivery_status 추출
     * - OrderService를 통해 주문 조회
     * - 조회 결과를 자연어 메시지로 포맷팅
     */
    private AIChatResponse handleDeliveryQuery(IntentClassificationResult classification) {
        var entities = classification.getEntities();
        Object dateObj = entities != null ? entities.get("date") : null;
        Object productNameObj = entities != null ? entities.get("product_name") : null;
        Object deliveryStatusObj = entities != null ? entities.get("delivery_status") : null;

        LocalDate targetDate = dateObj != null ? AIChatUtils.resolveDate(dateObj) : null;
        String productName = productNameObj != null ? productNameObj.toString() : null;
        OrderStatus status = AIChatUtils.parseOrderStatus(deliveryStatusObj);

        Long memberId = currentMemberService.getCurrentMemberOrThrow().getId();
        List<OrderSummaryResponse> orders = orderService.getOrdersByFilters(memberId, targetDate, productName, status);

        String message = formatDeliveryMessage(orders, targetDate, productName, status);

        return AIChatResponse.builder()
            .message(message)
            .intent("DELIVERY_QUERY")
            .data(orders)
            .build();
    }

    /**
     * DELIVERY의 RECOMMEND 액션 처리 (소분류: action)
     * 
     * - 배송 추천 기능
     * - 추후 구현 예정
     */
    private AIChatResponse handleDeliveryRecommend(IntentClassificationResult classification) {
        // TODO: 추후 구현
        log.info("DELIVERY RECOMMEND 요청 (추후 구현): {}", classification);
        
        return AIChatResponse.builder()
            .message("배송 추천 기능은 곧 제공될 예정입니다.")
            .intent("DELIVERY_QUERY")
            .build();
    }

    /**
     * DELIVERY의 MODIFY 액션 처리 (소분류: action)
     * 
     * - 배송지 수정, 배송 상태 변경 등
     * - 추후 구현 예정
     */
    private AIChatResponse handleDeliveryModify(IntentClassificationResult classification) {
        // TODO: 추후 구현
        log.info("DELIVERY MODIFY 요청 (추후 구현): {}", classification);
        
        return AIChatResponse.builder()
            .message("배송 수정 기능은 곧 제공될 예정입니다.")
            .intent("DELIVERY_QUERY")
            .build();
    }

    /**
     * 배송 현황 조회 결과를 자연어 메시지로 포맷팅
     */
    private String formatDeliveryMessage(List<OrderSummaryResponse> orders, LocalDate date, String productName, OrderStatus status) {
        StringBuilder sb = new StringBuilder();

        if (orders.isEmpty()) {
            if (date != null) {
                sb.append(AIChatUtils.formatDateForMessage(date)).append(" 배송 현황이 없어요.");
            } else if (productName != null) {
                sb.append("'").append(productName).append("' 상품의 배송 현황이 없어요.");
            } else if (status != null) {
                sb.append(formatOrderStatus(status)).append(" 상태의 배송 현황이 없어요.");
            } else {
                sb.append("배송 현황이 없어요.");
            }
            sb.append(" 주문 내역을 확인해보시거나 새로운 주문을 해보세요! 🛒");
            return sb.toString();
        }

        if (date == null && productName == null && status == null) {
            // 최신 정보만 조회한 경우
            sb.append("최신 배송 현황을 확인했어요!\n\n");
        } else {
            sb.append("배송 현황을 확인했어요!");
            if (date != null || productName != null || status != null) {
                sb.append(" (");
                boolean needComma = false;
                if (date != null) {
                    sb.append(AIChatUtils.formatDateForMessage(date));
                    needComma = true;
                }
                if (productName != null) {
                    if (needComma) sb.append(", ");
                    sb.append("'").append(productName).append("'");
                    needComma = true;
                }
                if (status != null) {
                    if (needComma) sb.append(", ");
                    sb.append(formatOrderStatus(status));
                }
                sb.append(")");
            }
            sb.append("\n\n");
        }

        for (int i = 0; i < orders.size(); i++) {
            OrderSummaryResponse order = orders.get(i);
            
            // 배송 상태에 따른 이모지
//            String statusEmoji = getStatusEmoji(order.getStatus());
//            sb.append(statusEmoji).append(" ");
            
            if (order.getFirstProductName() != null) {
                sb.append("   📦 상품: ").append(order.getFirstProductName());
                if (order.getItemCount() > 1) {
                    sb.append(" 외 ").append(order.getItemCount() - 1).append("개");
                }
                sb.append("\n");
            }
            
            String statusKr = formatOrderStatus(order.getStatus());
            sb.append("   🚚 배송 상태: ").append(statusKr).append("\n");
            
            if (order.getCreatedAt() != null) {
                LocalDate orderDate = order.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                sb.append("   📅 주문일: ").append(AIChatUtils.formatDateForMessage(orderDate)).append("\n");
            }
            
            if (order.getTotalPayableAmount() != null) {
                DecimalFormat formatter = new DecimalFormat("###,###");
                sb.append("   💰 결제 금액: ").append(formatter.format(order.getTotalPayableAmount())).append("원\n");
            }
            
            if (i < orders.size() - 1) {
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 배송 상태에 따른 이모지 반환
     */
    private String getStatusEmoji(OrderStatus status) {
        if (status == null) {
            return "❓";
        }
        return switch (status) {
            case CREATED -> "📝";
            case PAYMENT_PENDING -> "⏳";
            case PAID -> "💳";
            case SHIPPED -> "🚚";
            case DELIVERED -> "✅";
            case CANCELED -> "❌";
        };
    }

    /**
     * OrderStatus를 한국어로 변환
     */
    private String formatOrderStatus(OrderStatus status) {
        if (status == null) {
            return "알 수 없음";
        }
        return switch (status) {
            case CREATED -> "주문 생성";
            case PAYMENT_PENDING -> "결제 대기";
            case PAID -> "결제 완료";
            case SHIPPED -> "배송중";
            case DELIVERED -> "배송완료";
            case CANCELED -> "취소됨";
        };
    }
}

