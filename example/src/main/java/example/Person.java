package example;


import net.hamnaberg.codec.annotations.JsonClass;
import net.hamnaberg.codec.annotations.JsonFactory;
import net.hamnaberg.codec.annotations.JsonField;

@JsonClass
public class Person {
    @JsonField("name")
    public final String name;
    @JsonField("age")
    public final int age;

    @JsonFactory
    public Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Person person = (Person) o;

        if (age != person.age) return false;
        return name.equals(person.name);

    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + age;
        return result;
    }
}
