package net.tutla.manhuntPlus.application;

public final class Messages {
    public static final String PREFIX = "§8[§bManhunt+§8] ";

    private Messages() {
    }

    public static String info(String msg) {
        return PREFIX + "§e" + msg;
    }

    public static String ok(String msg) {
        return PREFIX + "§a" + msg;
    }

    public static String error(String msg) {
        return PREFIX + "§c" + msg;
    }
}
