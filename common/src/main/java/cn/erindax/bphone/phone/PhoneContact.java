package cn.erindax.bphone.phone;

public record PhoneContact(String name, String number) {
    public PhoneContact {
        name = name == null ? "" : name;
        number = PhoneData.normalizePhoneNumber(number);
    }

    public String displayName() {
        return name.isBlank() ? number : name;
    }
}
