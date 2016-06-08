package example;

import net.hamnaberg.json.Codecs;
import net.hamnaberg.json.Json;
import net.hamnaberg.json.JsonCodec;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PersonTest {
    private JsonCodec<Person> personCodec = Codecs.codec2(PersonIso.INSTANCE, Codecs.StringCodec, Codecs.intCodec).apply("name", "age");
    private Json.JObject json = Json.jObject(
            Json.entry("name", "Erlend"),
            Json.entry("age", 35)
    );
    private Person testPerson = new Person("Erlend", 35);

    @Test
    public void testSerialize() {

        Person person = personCodec.fromJsonUnsafe(json);
        assertEquals(testPerson, person);

        Json.JValue computed = personCodec.toJson(person).get();
        assertEquals(json, computed);
    }

    @Test
    public void testGenerated() {

        Person person = GeneratedCodecs.PersonCodec.fromJsonUnsafe(json);
        assertEquals(testPerson, person);

        Json.JValue computed = GeneratedCodecs.PersonCodec.toJson(person).get();
        assertEquals(json, computed);

        assertEquals(testPerson, personCodec.fromJsonUnsafe(computed));

    }
}
