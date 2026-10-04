package com.deepu.securebank.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Request body for deposit and withdraw. */
public class AmountRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Minimum amount is 1.00")
    @Digits(integer = 13, fraction = 2, message = "Amount can have at most 2 decimal places")
    private BigDecimal amount;

    @Size(max = 255, message = "Remark can be at most 255 characters")
    private String remark;

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
