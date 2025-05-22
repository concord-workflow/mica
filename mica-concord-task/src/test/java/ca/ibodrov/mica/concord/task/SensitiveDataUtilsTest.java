package ca.ibodrov.mica.concord.task;

/*-
 * ~~~~~~
 * Mica
 * ------
 * Copyright (C) 2023 - 2025 Mica Authors
 * ------
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ======
 */

import com.walmartlabs.concord.runtime.v2.runner.SensitiveDataHolder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ca.ibodrov.mica.concord.task.SensitiveDataUtils.hideSensitiveData;
import static org.junit.jupiter.api.Assertions.*;

public class SensitiveDataUtilsTest {

    @Test
    public void validate() {
        var holder = SensitiveDataHolder.getInstance();
        holder.addAll(List.of("foo", "bar"));

        var aString = "Hello, foo!";
        assertEquals("Hello, _*****!", hideSensitiveData(aString));

        var aMap = Map.of("key", "Hello, foo!");
        assertEquals("Hello, _*****!", hideSensitiveData(aMap).get("key"));

        var aMap2 = Map.of("foo!", "!bar!");
        var resultMap = hideSensitiveData(aMap2);
        assertEquals("!_*****!", resultMap.get("_*****!"));
        assertFalse(resultMap.containsKey("foo!"));

        var aList = List.of("Hello, foo!", "Bye, bar!");
        var resultList = hideSensitiveData(aList);
        assertEquals("Hello, _*****!", resultList.get(0));
        assertEquals("Bye, _*****!", resultList.get(1));

        var complex = List.of(
                Map.of("foo", List.of("bar_1", "bar_2", "baz")),
                Map.of("qux", List.of(1, 2, 3, "foo")));
        var resultComplex = hideSensitiveData(complex);
        assertEquals("_*****_1", resultComplex.get(0).get("_*****").get(0));
        assertEquals("_*****_2", resultComplex.get(0).get("_*****").get(1));
        assertEquals("baz", resultComplex.get(0).get("_*****").get(2));
        assertEquals(1, resultComplex.get(1).get("qux").get(0));
        assertEquals("_*****", resultComplex.get(1).get("qux").get(3));
    }

    @Test
    public void tooDeep() {
        var holder = SensitiveDataHolder.getInstance();
        holder.addAll(List.of("foo", "bar"));

        var depth = SensitiveDataUtils.MAX_DEPTH + 1;

        var data = new ArrayList<Object>(List.of("foo_1", "bar_2"));
        var pointer = data;
        for (int i = 0; i < depth - 1; i++) {
            var next = new ArrayList<Object>(List.of("foo_1", "bar_2"));
            pointer.add(next);
            pointer = next;
        }

        var result = (List<Object>) hideSensitiveData(data);
        for (int i = 0; i < SensitiveDataUtils.MAX_DEPTH; i++) {
            assertEquals("_*****_1", result.get(0));
            assertEquals("_*****_2", result.get(1));
            assertInstanceOf(List.class, result.get(2));
            result = (List<Object>) result.get(2);
        }
        assertEquals("...to deep", result.get(0));
        assertEquals("...to deep", result.get(1));
    }

    @Test
    public void exclusionsMustBeRespected() {
        var holder = SensitiveDataHolder.getInstance();
        holder.addAll(List.of("foo", "bar"));
        var aMap = Map.of("foo", "Hello, bar!");
        var result = hideSensitiveData(aMap, Set.of("foo"));
        assertEquals("Hello, _*****!", result.get("foo"));
        result = hideSensitiveData(aMap, Set.of("bar"));
        assertEquals("Hello, bar!", result.get("_*****"));
    }

    @Test
    public void maskingWorksWithStandardKeysAsExpected() {
        var holder = SensitiveDataHolder.getInstance();
        holder.addAll(List.of("foo", "bar"));
        var aMap = Map.of("name", "foo", "kind", "foo", "foo", "Hello, bar!");
        var result = hideSensitiveData(aMap, Set.of("foo"));
        assertEquals("foo", result.get("name"));
        assertEquals("foo", result.get("kind"));
        assertEquals("Hello, _*****!", result.get("foo"));
    }
}
