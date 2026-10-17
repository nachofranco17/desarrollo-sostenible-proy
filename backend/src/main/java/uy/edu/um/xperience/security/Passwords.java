package uy.edu.um.xperience.security;

import java.nio.CharBuffer;
import java.util.Arrays;

/** The caller owns the array. Views never copy its contents and must not be retained. */
public final class Passwords {
    private Passwords() {}

    public static CharSequence view(char[] password) {
        return CharBuffer.wrap(password);
    }

    public static void clear(char[] password) {
        if (password != null) Arrays.fill(password, '\0');
    }
}
