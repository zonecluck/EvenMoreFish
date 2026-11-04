package org.junit.jupiter.api;
public final class Assertions {
    public static void assertTrue(boolean b){if(!b)throw new AssertionError("Expected true");}
    public static void assertEquals(Object expected,Object actual){if(!java.util.Objects.equals(expected,actual))throw new AssertionError(expected+" != "+actual);}
    public static void assertEquals(long expected,long actual){if(expected!=actual)throw new AssertionError(expected+" != "+actual);}
}
