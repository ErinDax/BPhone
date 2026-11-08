package cn.erindax.bphone.api.economy;

public enum EconomyResult {
    SUCCESS,
    INSUFFICIENT_FUNDS,
    INVALID_AMOUNT,
    ACCOUNT_UNAVAILABLE,
    UNAVAILABLE,
    FAILED;

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
