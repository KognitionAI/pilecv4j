/*
 * Copyright 2022 Jim Carroll
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.kognition.pilecv4j.nr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TestMinimizer {

    @Test
    public void testMinimizer() throws Throwable {
        final Minimizer m = new Minimizer(x -> ((x[0] - 2.0) * (x[0] - 2.0)) - 3.0);

        final double minVal = m.minimize(new double[] {-45.0});
        final double minParam = m.getFinalPostion()[0];

        assertEquals(-3.0, minVal, 0.0001);
        assertEquals(2.0, minParam, 0.0001);
    }

    @Test
    public void testMinimizerException() throws Throwable {
        final MinimizerException expected = assertThrows(MinimizerException.class, () -> {
            new Minimizer(x -> {
                throw new RuntimeException("Yo");
            }).minimize(new double[] {-45.0});
        });

        assertTrue(expected.getLocalizedMessage().contains("Exception ocurred in function"));
    }

    @Test
    public void testMinimizerPowellFailure() throws Throwable {
        final MinimizerException expected = assertThrows(MinimizerException.class, () -> {
            new Minimizer(x -> {
                // whatever the current is, just return the negative. This "moving target" can't be minimized.
                return -x[0];
            }).minimize(new double[] {-45.0});
        });

        assertTrue(expected.getLocalizedMessage().contains("iterations"));
    }
}
