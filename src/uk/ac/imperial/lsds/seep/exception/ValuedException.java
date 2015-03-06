package uk.ac.imperial.lsds.seep.exception;

public class ValuedException extends Exception {
    final int value;

    public ValuedException(int value, String message) {
        super(message);
        this.value = value;
    }

    public int getValue() {
        return value;   
    }
}