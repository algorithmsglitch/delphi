package delphi;

/**
 * Thrown to unwind the call stack when a function/procedure returns.
 */
public class ReturnException extends RuntimeException {
    private final Object value;

    public ReturnException(Object value) {
        super(null, null, true, false);
        this.value = value;
    }

    public Object getValue() { return value; }
}
