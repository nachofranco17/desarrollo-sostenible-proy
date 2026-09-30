package uy.edu.um.xperience.profile;

/** Cambio parcial: ausente no modifica; valor aplica; Clear solo para campos anulables. */
public sealed interface FieldChange<T> {
    record Absent<T>() implements FieldChange<T> {}
    record Clear<T>() implements FieldChange<T> {}
    record SetValue<T>(T value) implements FieldChange<T> {}

    static <T> FieldChange<T> absent() { return new Absent<>(); }
    static <T> FieldChange<T> clear() { return new Clear<>(); }
    static <T> FieldChange<T> of(T value) { return new SetValue<>(value); }

    default boolean isPresent() { return !(this instanceof Absent<?>); }

    default T orElse(T current) {
        if (this instanceof Absent<?>) {
            return current;
        }
        if (this instanceof Clear<?>) {
            return null;
        }
        return ((SetValue<T>) this).value();
    }
}
