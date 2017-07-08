package example;

import net.hamnaberg.json.annotations.JsonClass;
import net.hamnaberg.json.annotations.JsonField;

@JsonClass
public final class Address {
    @JsonField("street")
    public final String street;
    @JsonField("city")
    public final String city;

    public Address(String street, String city) {
        this.street = street;
        this.city = city;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Address address = (Address) o;

        if (!street.equals(address.street)) return false;
        return city.equals(address.city);

    }

    @Override
    public int hashCode() {
        int result = street.hashCode();
        result = 31 * result + city.hashCode();
        return result;
    }
}
