package com.flashlock.model;

public class PurchaseResponse {
    private Long requestId;
    private String status;
    private Long orderId;
    private Integer remainingStock;
    private String message;

    public PurchaseResponse(Long requestId, String status, Long orderId, Integer remainingStock, String message) {
        this.requestId = requestId;
        this.status = status;
        this.orderId = orderId;
        this.remainingStock = remainingStock;
        this.message = message;
    }

    public Long getRequestId() { return requestId; }
    public String getStatus() { return status; }
    public Long getOrderId() { return orderId; }
    public Integer getRemainingStock() { return remainingStock; }
    public String getMessage() { return message; }
}
