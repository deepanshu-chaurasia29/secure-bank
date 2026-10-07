package com.deepu.securebank.util;

/** Hides most of an account number (FR-D5): 123456789012 becomes XXXXXXXX9012. */
public final class AccountMasker {

    private AccountMasker() {
    }

    public static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return null;
        }
        return "XXXXXXXX" + accountNumber.substring(accountNumber.length() - 4);
    }
}
