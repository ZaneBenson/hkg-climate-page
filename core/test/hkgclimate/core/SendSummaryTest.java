package hkgclimate.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.ArrayList;
import org.junit.Test;

public class SendSummaryTest {
    private static final String OK_D = "sent setFrontSeatHeatDrvStep 8";
    private static final String OK_A = "sent setFrontSeatHeatAstStep 8";
    private static final String BAD_A = "send failed setFrontSeatHeatAstStep 8: boom";

    @Test
    public void emptyOrNullIsNull() {
        assertNull(SendSummary.worst(new ArrayList<String>()));
        assertNull(SendSummary.worst(null));
        assertNull(SendSummary.worst(Arrays.asList((String) null)));
    }

    @Test
    public void laterSendWinsAmongSends() {
        assertEquals(OK_A, SendSummary.worst(Arrays.asList(OK_D, OK_A)));
    }

    @Test
    public void aFailureBeatsALaterSuccess() {
        assertEquals(BAD_A, SendSummary.worst(Arrays.asList(BAD_A, OK_D)));
    }

    @Test
    public void aFailureAfterASuccessWins() {
        assertEquals(BAD_A, SendSummary.worst(Arrays.asList(OK_D, BAD_A)));
    }

    @Test
    public void aRefusalCountsAsBad() {
        String refused = "send refused setFrontSeatHeatDrvStep 9";
        assertEquals(refused, SendSummary.worst(Arrays.asList(refused, OK_A)));
    }
}
