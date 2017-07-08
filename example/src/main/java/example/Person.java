package example;


import io.vavr.control.Option;
import net.hamnaberg.codec.annotations.JsonClass;
import net.hamnaberg.codec.annotations.JsonFactory;
import net.hamnaberg.codec.annotations.JsonField;

@JsonClass
public class Person {
    @JsonField("name")
    public final String name;
    @JsonField("age")
    public final int age;

    @JsonField("address")
    public final Option<Address> address;

    @JsonFactory
    public Person(String name, int age, Option<Address> address) {
        this.name = name;
        this.age = age;
        this.address = address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Person person = (Person) o;

        if (age != person.age) return false;
        if (!name.equals(person.name)) return false;
        return address.equals(person.address);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + age;
        result = 31 * result + address.hashCode();
        return result;
    }
}
