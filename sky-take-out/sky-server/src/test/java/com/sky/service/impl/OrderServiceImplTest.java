package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.*;
import com.sky.mapper.*;
import com.sky.service.DeliveryRangeService;
import com.sky.utils.WeChatPayUtil;
import com.sky.websocket.WebSocketServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {
    @InjectMocks OrderServiceImpl service;
    @Mock OrderMapper orders;
    @Mock OrderDetailMapper details;
    @Mock ShoppingCartMapper carts;
    @Mock AddressBookMapper addresses;
    @Mock UserMapper users;
    @Mock DishMapper dishes;
    @Mock SetmealMapper setmeals;
    @Mock WeChatPayUtil payments;
    @Mock DeliveryRangeService range;
    @Mock WebSocketServer socket;

    @BeforeEach void setup() {
        BaseContext.setCurrentId(7L);
        ReflectionTestUtils.setField(service, "packagingFeePerItem", 1);
        ReflectionTestUtils.setField(service, "deliveryFee", new BigDecimal("6"));
    }
    @AfterEach void clear() { BaseContext.removeCurrentId(); }

    private Orders order(int status, int payStatus) {
        return Orders.builder().id(10L).number("order-10").userId(7L)
                .status(status).payStatus(payStatus).amount(new BigDecimal("48.00")).build();
    }

    @Test void checkoutRepricesCartAndIgnoresClientTotals() {
        OrdersSubmitDTO request = new OrdersSubmitDTO();
        request.setAddressBookId(1L);
        request.setAmount(new BigDecimal("0.01"));
        request.setPackAmount(-100);
        when(addresses.getById(1L)).thenReturn(AddressBook.builder().id(1L).userId(7L)
                .cityName("武汉").districtName("洪山").detail("街道").build());
        ShoppingCart item = ShoppingCart.builder().id(1L).userId(7L).dishId(2L)
                .number(2).amount(new BigDecimal("1")).build();
        when(carts.listForUpdate(7L)).thenReturn(Collections.singletonList(item));
        when(dishes.getById(2L)).thenReturn(Dish.builder().id(2L).status(1)
                .name("菜品").price(new BigDecimal("20")).build());
        doAnswer(invocation -> { ((Orders) invocation.getArgument(0)).setId(10L); return null; })
                .when(orders).insert(any());
        assertEquals(0, new BigDecimal("48").compareTo(service.submitOrder(request).getOrderAmount()));
        ArgumentCaptor<Orders> saved = ArgumentCaptor.forClass(Orders.class);
        verify(orders).insert(saved.capture());
        assertEquals(2, saved.getValue().getPackAmount());
        assertEquals(new BigDecimal("20"), item.getAmount());
        verify(carts).deleteByUserId(7L);
    }

    @Test void foreignAddressIsRejectedBeforeNetworkOrWrites() {
        OrdersSubmitDTO request = new OrdersSubmitDTO(); request.setAddressBookId(1L);
        when(addresses.getById(1L)).thenReturn(AddressBook.builder().userId(8L).build());
        assertThrows(AddressBookBusinessException.class, () -> service.submitOrder(request));
        verifyNoInteractions(range, orders, carts);
    }

    @Test void userCannotReadRepeatOrRemindForeignOrders() {
        Orders foreign = order(1, 0); foreign.setUserId(8L);
        when(orders.getById(10L)).thenReturn(foreign);
        assertThrows(OrderBusinessException.class, () -> service.detailsForUser(10L));
        assertThrows(OrderBusinessException.class, () -> service.repetition(10L));
        assertThrows(OrderBusinessException.class, () -> service.reminder(10L));
        verifyNoInteractions(details, carts, socket);
    }

    @Test void userCannotCancelForeignOrder() {
        Orders foreign = order(1, 0); foreign.setUserId(8L);
        when(orders.getByIdForUpdate(10L)).thenReturn(foreign);
        assertThrows(OrderBusinessException.class, () -> service.userCancelById(10L));
        verify(orders, never()).update(any());
        verifyNoInteractions(payments);
    }

    @Test void paidCancellationRefundsOriginalAmount() throws Exception {
        when(orders.getByIdForUpdate(10L)).thenReturn(order(2, 1));
        service.userCancelById(10L);
        verify(payments).refund("order-10", "order-10", new BigDecimal("48.00"), new BigDecimal("48.00"));
        ArgumentCaptor<Orders> saved = ArgumentCaptor.forClass(Orders.class);
        verify(orders).update(saved.capture());
        assertEquals(Orders.CANCELLED, saved.getValue().getStatus());
        assertEquals(Orders.REFUND, saved.getValue().getPayStatus());
    }

    @Test void failedRefundDoesNotMarkCancelled() throws Exception {
        when(orders.getByIdForUpdate(10L)).thenReturn(order(2, 1));
        when(payments.refund(anyString(), anyString(), any(), any())).thenThrow(new java.io.IOException("unavailable"));
        assertThrows(java.io.IOException.class, () -> service.userCancelById(10L));
        verify(orders, never()).update(any());
    }

    @Test void completionUpdatesCorrectIdAndCompletedStatus() {
        when(orders.getByIdForUpdate(10L)).thenReturn(order(4, 1));
        service.complete(10L);
        ArgumentCaptor<Orders> saved = ArgumentCaptor.forClass(Orders.class);
        verify(orders).update(saved.capture());
        assertEquals(10L, saved.getValue().getId());
        assertEquals(Orders.COMPLETED, saved.getValue().getStatus());
        assertNotNull(saved.getValue().getDeliveryTime());
    }

    @Test void callbacksDoNotRequireUserThreadContext() throws Exception {
        BaseContext.removeCurrentId();
        when(orders.getByNumberForUpdate("order-10")).thenReturn(order(1, 0));
        service.paySuccess("order-10", new BigDecimal("48.00"));
        verify(orders).update(argThat(o -> Orders.PAID.equals(o.getPayStatus()) && o.getId().equals(10L)));
        verify(socket).sendToAllClient(anyString());
    }

    @Test void callbacksRejectWrongAmount() {
        when(orders.getByNumberForUpdate("order-10")).thenReturn(order(1, 0));
        assertThrows(OrderBusinessException.class, () -> service.paySuccess("order-10", new BigDecimal("0.01")));
        verify(orders, never()).update(any());
        verifyNoInteractions(socket, payments);
    }

    @Test void duplicateCallbackDoesNotRegressCompletedOrder() throws Exception {
        when(orders.getByNumberForUpdate("order-10")).thenReturn(order(5, 1));
        service.paySuccess("order-10", new BigDecimal("48.00"));
        verify(orders, never()).update(any());
        verifyNoInteractions(socket, payments);
    }

    @Test void latePaymentForCancelledOrderRequestsRefund() throws Exception {
        when(orders.getByNumberForUpdate("order-10")).thenReturn(order(6, 0));
        service.paySuccess("order-10", new BigDecimal("48.00"));
        verify(payments).refund("order-10", "order-10", new BigDecimal("48.00"), new BigDecimal("48.00"));
        verify(orders).update(argThat(o -> Orders.REFUND.equals(o.getPayStatus()) && o.getStatus() == null));
        verifyNoInteractions(socket);
    }
}
