package cn.erindax.bphone.phone;

public record CallLogEntry(long id, String otherNumber, String otherName, String callType, long timestamp, int durationTicks) {
}
