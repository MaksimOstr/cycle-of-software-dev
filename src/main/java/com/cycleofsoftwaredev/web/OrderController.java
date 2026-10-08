package com.cycleofsoftwaredev.web;

import com.cycleofsoftwaredev.delivery.api.DeliveryMethod;
import com.cycleofsoftwaredev.ordering.api.OrderStatus;
import com.cycleofsoftwaredev.ordering.application.CheckoutResult;
import com.cycleofsoftwaredev.ordering.application.CheckoutService;
import com.cycleofsoftwaredev.ordering.application.OrderQueryService;
import com.cycleofsoftwaredev.ordering.application.OrderService;
import com.cycleofsoftwaredev.ordering.application.PlaceOrderCommand;
import com.cycleofsoftwaredev.ordering.domain.ContactInfo;
import com.cycleofsoftwaredev.ordering.domain.Order;
import com.cycleofsoftwaredev.ordering.domain.OrderItem;
import com.cycleofsoftwaredev.ordering.domain.OrderStatusChange;
import com.cycleofsoftwaredev.ordering.domain.PaymentMethod;
import com.cycleofsoftwaredev.shared.domain.Actor;
import com.cycleofsoftwaredev.shared.domain.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private final CheckoutService checkout;
    private final OrderService orderService;
    private final OrderQueryService orderQueries;
    private final CurrentUserResolver currentUser;

    public OrderController(CheckoutService checkout, OrderService orderService, OrderQueryService orderQueries,
                           CurrentUserResolver currentUser) {
        this.checkout = checkout;
        this.orderService = orderService;
        this.orderQueries = orderQueries;
        this.currentUser = currentUser;
    }

    public record PlaceOrderRequest(@NotNull UUID cartId, @NotBlank String fullName, @NotBlank @Email String email,
                                    @NotBlank String phone, @NotNull DeliveryMethod deliveryMethod,
                                    String deliveryAddress, @NotNull PaymentMethod paymentMethod) {
    }

    public record CancelRequest(String reason) {
    }

    public record StatusChangeRequest(@NotNull OrderStatus status, String trackingNumber) {
    }

    public record OrderResponse(String number, OrderStatus status, PaymentMethod paymentMethod,
                                DeliveryMethod deliveryMethod, String deliveryAddress, String trackingNumber,
                                List<OrderItem> items, Money subtotal, Money discount, Money deliveryCost,
                                Money total, Instant createdAt, Instant paymentDeadline,
                                List<OrderStatusChange> history) {

        static OrderResponse of(Order order) {
            return new OrderResponse(order.number(), order.status(), order.paymentMethod(), order.delivery().method(),
                    order.delivery().address(), order.delivery().trackingNumber(), order.items(), order.subtotal(),
                    order.discount(), order.deliveryCost(), order.total(), order.createdAt(),
                    order.paymentDeadline(), order.history());
        }
    }

    /** Checkout. The SPA sends the same Idempotency-Key when it repeats the request. */
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutResult placeOrder(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                     @RequestHeader(value = CurrentUserResolver.HEADER, required = false) UUID userId,
                                     @Valid @RequestBody PlaceOrderRequest request) {
        UUID customerId = userId == null ? null : currentUser.requireUser(userId).userId();
        PlaceOrderCommand command = new PlaceOrderCommand(request.cartId(), customerId,
                new ContactInfo(request.fullName(), request.email(), request.phone()),
                request.deliveryMethod(), request.deliveryAddress(), request.paymentMethod());
        return checkout.placeOrder(command, idempotencyKey);
    }

    /**
     * Order tracking by number. Order numbers are sequential and easy to guess, so a customer's order is shown
     * only to its owner and to staff; a guest order is shown to whoever knows the email used at checkout.
     */
    @GetMapping("/orders/{number}")
    public OrderResponse getOrder(@PathVariable String number,
                                  @RequestHeader(value = CurrentUserResolver.HEADER, required = false) UUID userId,
                                  @RequestParam(required = false) String email) {
        Order order = orderQueries.getByNumber(number);
        requireAccess(order, userId, email);
        return OrderResponse.of(order);
    }

    private void requireAccess(Order order, UUID userId, String email) {
        if (userId != null) {
            Actor actor = currentUser.requireUser(userId);
            if (actor.isStaff() || actor.userId().equals(order.customerId())) {
                return;
            }
            throw new ForbiddenException("You can view only your own orders");
        }
        boolean guestKnowsEmail = order.customerId() == null && email != null
                && email.trim().equalsIgnoreCase(order.contact().email());
        if (!guestKnowsEmail) {
            throw new UnauthorizedException("Sign in or provide the email used for the order");
        }
    }

    /** Order history of the signed-in customer. */
    @GetMapping("/me/orders")
    public List<OrderResponse> myOrders(@RequestHeader(value = CurrentUserResolver.HEADER, required = false) UUID userId) {
        Actor actor = currentUser.requireUser(userId);
        return orderQueries.customerOrders(actor.userId()).stream().map(OrderResponse::of).toList();
    }

    @PostMapping("/orders/{number}/cancel")
    public OrderResponse cancel(@PathVariable String number,
                                @RequestHeader(value = CurrentUserResolver.HEADER, required = false) UUID userId,
                                @RequestBody(required = false) CancelRequest request) {
        Actor actor = currentUser.requireUser(userId);
        String reason = request == null || request.reason() == null ? "Cancelled by customer" : request.reason();
        Order order = orderQueries.getByNumber(number);
        return OrderResponse.of(orderService.cancelOrder(order.id(), actor, reason));
    }

    /** Requirement "Order status update" for managers. */
    @PatchMapping("/management/orders/{number}/status")
    public OrderResponse changeStatus(@PathVariable String number,
                                      @RequestHeader(value = CurrentUserResolver.HEADER, required = false) UUID userId,
                                      @Valid @RequestBody StatusChangeRequest request) {
        Actor manager = currentUser.requireStaff(userId);
        Order order = orderQueries.getByNumber(number);
        return OrderResponse.of(orderService.changeStatus(order.id(), request.status(), manager, request.trackingNumber()));
    }
}
