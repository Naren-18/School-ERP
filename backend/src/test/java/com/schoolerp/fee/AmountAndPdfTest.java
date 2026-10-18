package com.schoolerp.fee;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.math.BigDecimal;

import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.junit.jupiter.api.Test;

import com.schoolerp.common.money.AmountInWords;

class AmountAndPdfTest {

	@Test
	void indianAmountInWords() {
		assertThat(AmountInWords.rupees(new BigDecimal("0"))).isEqualTo("Rupees Zero Only");
		assertThat(AmountInWords.rupees(new BigDecimal("15"))).isEqualTo("Rupees Fifteen Only");
		assertThat(AmountInWords.rupees(new BigDecimal("1000.05"))).isEqualTo("Rupees One Thousand and Five Paise Only");
		assertThat(AmountInWords.rupees(new BigDecimal("123456.50")))
			.isEqualTo("Rupees One Lakh Twenty Three Thousand Four Hundred Fifty Six and Fifty Paise Only");
		assertThat(AmountInWords.rupees(new BigDecimal("10000000"))).isEqualTo("Rupees One Crore Only");
		assertThat(AmountInWords.rupees(new BigDecimal("99999999.99"))).isEqualTo(
				"Rupees Nine Crore Ninety Nine Lakh Ninety Nine Thousand Nine Hundred Ninety Nine and Ninety Nine Paise Only");
	}

	@Test
	void bundledPdfFontHasTheRupeeSign() throws Exception {
		try (InputStream in = getClass().getResourceAsStream("/net/sf/jasperreports/fonts/dejavu/DejaVuSans.ttf")) {
			assertThat(in).as("DejaVu Sans on the classpath").isNotNull();
			TrueTypeFont font = new TTFParser().parse(new RandomAccessReadBuffer(in));
			assertThat(font.getUnicodeCmapLookup().getGlyphId('₹')).as("glyph for U+20B9").isPositive();
		}
	}

}
