package example;

import io.vavr.control.Option;
import net.hamnaberg.json.codec.Codecs;
import net.hamnaberg.json.Json;
import net.hamnaberg.json.codec.JsonCodec;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PersonTest {
    private JsonCodec<Address> addressCodec = Codecs.codec(
            AddressIso.INSTANCE,
            Codecs.CString.field("street"),
            Codecs.CString.field("city")
    );
    private JsonCodec<Person> personCodec = Codecs.codec(
            PersonIso.INSTANCE,
            Codecs.CString.field("name"),
            Codecs.CInt.field("age"),
            Codecs.OptionCodec(addressCodec).field("address")
    );

    private Json.JObject json = Json.jObject(
            Json.tuple("name", "Erlend"),
            Json.tuple("age", 35),
            Json.tuple("address", Json.jObject(
                    Json.tuple("street", "Ensjøveien 30 A"),
                    Json.tuple("city", "Oslo")
            ))
    );
    private Person testPerson = new Person("Erlend", 35, Option.of(new Address("Ensjøveien 30 A", "Oslo")));

    @Test
    public void testSerialize() {

        Person person = personCodec.fromJsonUnsafe(json);
        assertEquals(testPerson, person);

        Json.JValue computed = personCodec.toJson(person);
        assertEquals(json, computed);
    }

    @Test
    public void testGenerated() {

        Person person = GeneratedCodecs.PersonCodec.fromJsonUnsafe(json);
        assertEquals(testPerson, person);

        Json.JValue computed = GeneratedCodecs.PersonCodec.toJson(person);
        assertEquals(json, computed);

        assertEquals(testPerson, personCodec.fromJsonUnsafe(computed));

    }
}
