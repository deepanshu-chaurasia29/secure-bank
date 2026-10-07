package com.deepu.securebank.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/** Request body for a transfer. The SENDER is never in the body: it comes from the JWT. */
public class TransferRequest {

    @NotBlank(message = "Receiver account number is required")
    @Pattern(regexp = "\\d{12}", message = "Receiver account number must be exactly 12 digits")
    private String toAccountNumber;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Minimum amount is 1.00")
    @Digits(integer = 13, fraction = 2, message = "Amount can have at most 2 decimal places")
    private BigDecimal amount;

    @Size(max = 255, message = "Remark can be at most 255 characters")
    private String remark;

    public String getToAccountNumber() { return toAccountNumber; }
    public void setToAccountNumber(String toAccountNumber) { this.toAccountNumber = toAccountNumber; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
