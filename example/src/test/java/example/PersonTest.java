package example;

import net.hamnaberg.json.Codecs;
import net.hamnaberg.json.Json;
import net.hamnaberg.json.JsonCodec;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PersonTest {
    private JsonCodec<Person> personCodec = Codecs.codec2(PersonIso.INSTANCE, Codecs.StringCodec, Codecs.intCodec).apply("name", "age");

    @Test
    public void testSerialize() {
        Json.JObject json = Json.jObject(
                Json.entry("name", "Erlend"),
                Json.entry("age", 35)
        );

        Person person = personCodec.fromJsonUnsafe(json);
        assertEquals("Erlend", person.name);
        assertEquals(35, person.age);

        Json.JValue computed = personCodec.toJson(person).get();
        assertEquals(json, computed);
    }
}
