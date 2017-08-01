import org.junit.Test;

import static org.hamcrest.core.Is.is;              //  see https://github.com/hamcrest
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThat;



public class DummyEmailValidatorTest {
    @Test
    public void emailValidator_CorrectEmailSimple_ReturnsTrue() {
        assertThat(DummyEmailValidator.isValidEmail("name@email.com"), is(true));
    }
}