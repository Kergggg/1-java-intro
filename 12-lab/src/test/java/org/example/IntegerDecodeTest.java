package org.example;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class IntegerDecodeTest {

    // 1. Тест на пустую строку
    @Test
    public void testEmptyString() {
        assertThrows(NumberFormatException.class, () -> {
            Integer.decode("");
        });
    }

    // 2. Тест на отрицательное десятичное число
    @Test
    public void testNegativeDecimal() {
        assertEquals(-123, Integer.decode("-123"));
    }

    // 3. Тест на положительное число со знаком +
    @Test
    public void testPositiveWithSign() {
        assertEquals(456, Integer.decode("+456"));
    }

    // 4. Тест на шестнадцатеричное число с префиксом 0x
    @Test
    public void testHexWith0x() {
        assertEquals(0xFF, Integer.decode("0xFF")); // 255
        assertEquals(0x1A, Integer.decode("0X1A")); // 26
    }

    // 5. Тест на шестнадцатеричное число с префиксом #
    @Test
    public void testHexWithHash() {
        assertEquals(0xABC, Integer.decode("#ABC")); // 2748
    }

    // 6. Тест на восьмеричное число (начинается с 0)
    @Test
    public void testOctal() {
        assertEquals(0123, Integer.decode("0123")); // 83 в десятичной
    }

    // 7. Тест на неправильную позицию знака
    @Test
    public void testWrongSignPosition() {
        assertThrows(NumberFormatException.class, () -> {
            Integer.decode("0x-123"); // знак после префикса системы счисления
        });
    }

    // 8. Тест на Integer.MIN_VALUE (попадает в catch-блок)
    @Test
    public void testMinValue() {
        assertEquals(Integer.MIN_VALUE, Integer.decode("-2147483648"));
    }

    // 9. Тест на обычное десятичное число без знака
    @Test
    public void testSimpleDecimal() {
        assertEquals(789, Integer.decode("789"));
    }

    // 10. Тест на отрицательное восьмеричное число
    @Test
    public void testNegativeOctal() {
        assertEquals(-0123, Integer.decode("-0123")); // -83
    }

    // 11. Тест на отрицательное шестнадцатеричное с #
    @Test
    public void testNegativeHexWithHash() {
        assertEquals(-0xABC, Integer.decode("-#ABC"));
    }

    // 12. Тест на отрицательное шестнадцатеричное с 0x
    @Test
    public void testNegativeHexWith0x() {
        assertEquals(-0xFF, Integer.decode("-0xFF"));
    }

    // 13. Тест на строку только с "0" (не восьмеричная, а десятичный 0)
    @Test
    public void testSingleZero() {
        assertEquals(0, Integer.decode("0"));
    }

    // 14. Тест на строку только со знаком и нулем
    @Test
    public void testSignedZero() {
        assertEquals(0, Integer.decode("+0"));
        assertEquals(0, Integer.decode("-0"));
    }

    // 15. Тест на пограничный случай Integer.MAX_VALUE
    @Test
    public void testMaxValue() {
        assertEquals(Integer.MAX_VALUE, Integer.decode("2147483647"));
    }
}
