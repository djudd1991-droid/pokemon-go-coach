import com.david.gocoach.Reading;
import com.david.gocoach.ScreenKind;

public class ScreenKindCheck {
  static void check(String text, boolean expected) {
    if (ScreenKind.hasPokemonDetails(Reading.parse(text)) != expected)
      throw new AssertionError(text);
  }

  public static void main(String[] args) {
    check("POKÉMON GO\nSCOP ЕLY\nTHE POKÉMON COMPANY\nNIANTIC", false);
    check("GO Coach 0.9.17\nWaiting for the map\n11:36 AM", false);
    check("", false);
    check("Noibat CP 302", true);
    check("62 / 62 HP", true);
    check("This Noibat was caught on 09/09/2026", true);
    System.out.println(
        "PASS: loading/coach/empty screens skip detail analysis; CP, HP and appraisal screens"
            + " remain supported.");
  }
}
