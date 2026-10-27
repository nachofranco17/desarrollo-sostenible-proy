package uy.edu.um.xperience.security;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/** NotBlank does not support char[]. Keep the same validation without creating a String. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NotBlankPassword.Validator.class)
public @interface NotBlankPassword {
    String message() default "La contraseña no puede estar vacía.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<NotBlankPassword, char[]> {
        @Override public boolean isValid(char[] value, ConstraintValidatorContext context) {
            if (value == null) return false;
            return Passwords.view(value).codePoints().anyMatch(cp -> !Character.isWhitespace(cp));
        }
    }
}
